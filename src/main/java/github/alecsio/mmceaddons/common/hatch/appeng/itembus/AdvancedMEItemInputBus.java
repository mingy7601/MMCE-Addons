package github.alecsio.mmceaddons.common.hatch.appeng.itembus;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import github.alecsio.mmceaddons.common.registry.ModularMachineryAddonsBlocks;
import github.alecsio.mmceaddons.common.hatch.handler.AdaptiveSnapshotRefreshScheduler;

import github.kasuminova.mmce.common.tile.base.MEMachineComponent;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Core tile entity for the Advanced ME Item Input Bus.
 * <p>
 * Extends MMCE's MEMachineComponent to reuse AE2 networking and capability exposure.
 * On load, performs an initial snapshot of available items from the connected AE2 network.
 * When no AE2 channel is active (grid node unavailable), does not tick or snapshot.
 * Respects a configurable polling interval (default: 20 ticks / 1 second).
 * <p>
 * After draining items into inventory slots, re-snapshots to reflect updated AE2 availability.
 */
public class AdvancedMEItemInputBus extends MEMachineComponent implements IGridTickable {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Default polling interval in ticks (20 ticks = 1 second). */
    public static final int DEFAULT_POLLING_INTERVAL_TICKS = 20;

    /** Minimum polling interval in ticks. */
    public static final int MIN_POLLING_INTERVAL_TICKS = 1;

    /** Maximum polling interval in ticks. 72000 = 1h */
    public static final int MAX_POLLING_INTERVAL_TICKS = 72000; // 30 seconds

    private static final int SLOT_COUNT = 16;

    /** The internal slot inventory for draining items from AE2. */
    protected IOInventory inventory;

    private static int[] IN_SLOTS = new int[SLOT_COUNT];
    private static int[] OUT_SLOTS = new int[0];

    /** Snapshot of the top-16 most abundant item types, refreshed at polling intervals. */
    protected volatile List<IAEItemStack> snapshot = new ArrayList<>();

    /** Configurable polling interval in ticks (default: 20). */
    private int pollingIntervalTicks = DEFAULT_POLLING_INTERVAL_TICKS;

    /** Tick counter for the polling interval. */
    private int tickCounter = 0;

    /** Scheduler for adaptive snapshot refresh. */
    protected final AdaptiveSnapshotRefreshScheduler refreshScheduler = new AdaptiveSnapshotRefreshScheduler(this::updateSnapshot);

    /** Lock protecting snapshot reads/writes. */
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    public AdvancedMEItemInputBus() {
    }

    @Override
    public void validate() {
        super.validate();
        if (inventory == null && !world.isRemote) {
            inventory = new IOInventory(this, IN_SLOTS.clone(), OUT_SLOTS.clone());
        }
    }

    @Nonnull
    @Override
    public ItemStack getVisualItemStack() {
        return new ItemStack(ModularMachineryAddonsBlocks.blockAdvancedMEItemInputBus);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateSnapshot();
    }

    /**
     * Updates the snapshot by querying AE2's storage grid.
     * <p>
     * This is a single read-only call to IMEMonitor.getAvailableItems() — no repeated lookups.
     */
    protected void updateSnapshot() {
        Optional<IMEInventory<IAEItemStack>> optInventory = getStorageInventory();
        if (!optInventory.isPresent()) {
            return;
        }

        IItemList<IAEItemStack> availableItems = new appeng.util.item.ItemList();
        optInventory.get().getAvailableItems(availableItems);

        // Pure function: select top 16 by quantity
        List<IAEItemStack> newSnapshot = SelectTop16.select(availableItems);

        lock.writeLock().lock();
        try {
            snapshot = newSnapshot;
            refreshScheduler.recordSuccess();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the AE2 storage inventory for this bus.
     */
    protected Optional<IMEInventory<IAEItemStack>> getStorageInventory() {
        IGridNode gridNode = this.getGridNode(AEPartLocation.UP);
        if (gridNode == null) {
            return Optional.empty();
        }

        IGrid grid = gridNode.getGrid();
        if (grid == null) {
            return Optional.empty();
        }

        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (storage == null) {
            return Optional.empty();
        }

        return Optional.of(storage.getInventory(getChannel()));
    }

    /**
     * Gets the AE2 item storage channel.
     */
    protected IItemStorageChannel getChannel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    /**
     * Drains items from AE2 into the bus's internal inventory slots.
     * <p>
     * Only drains slots that are empty (selective per-slot drain).
     * Uses storage poweredExtraction() to respect AE2 channel power limits.
     */
    public void drainIntoInventory() {
        lock.readLock().lock();
        List<IAEItemStack> snapshotCopy;
        try {
            snapshotCopy = new ArrayList<>(snapshot);
        } finally {
            lock.readLock().unlock();
        }

        if (snapshotCopy.isEmpty()) {
            return;
        }

        IEnergyGrid energyGrid = getEnergyGrid();
        if (energyGrid == null) {
            return;
        }

        Optional<IMEInventory<IAEItemStack>> optInventory = getStorageInventory();
        if (!optInventory.isPresent()) {
            return;
        }

        IMEInventory<IAEItemStack> meInventory = optInventory.get();
        appeng.api.networking.security.IActionSource actionSource = getActionSource();

        for (int i = 0; i < SLOT_COUNT && i < snapshotCopy.size(); i++) {
            IAEItemStack toDrain = snapshotCopy.get(i);
            if (toDrain == null || toDrain.getItem() == null) {
                continue;
            }

            // Check if the slot is empty — only drain into empty slots (selective drain)
            ItemStack existingStack = this.inventory != null ? this.inventory.getStackInSlot(i) : ItemStack.EMPTY;
            if (!existingStack.isEmpty()) {
                // Slot already has items — skip (selective drain: only fill empty slots)
                continue;
            }

            // Drain one stack worth into the slot via storage poweredExtraction()
            IAEItemStack extracted = appeng.api.AEApi.instance().storage().poweredExtraction(
                    energyGrid, meInventory, toDrain, actionSource, Actionable.SIMULATE);
            if (extracted != null && extracted.getStackSize() > 0) {
                ItemStack stackInSlot = this.inventory != null ? this.inventory.getStackInSlot(i) : ItemStack.EMPTY;
                long canFit = stackInSlot.isEmpty() ? Long.MAX_VALUE : Math.max(0, this.inventory.getSlotLimit(i) - stackInSlot.getCount());
                if (canFit > 0) {
                    long toExtract = Math.min(extracted.getStackSize(), canFit);
                    IAEItemStack actualExtracted = appeng.api.AEApi.instance().storage().poweredExtraction(
                            energyGrid, meInventory, toDrain, actionSource, Actionable.MODULATE);
                    if (actualExtracted != null && actualExtracted.getStackSize() > 0) {
                        this.inventory.setStackInSlot(i, actualExtracted.createItemStack());
                    }
                }
            }
        }

        // After draining, re-snapshot to reflect updated AE2 availability
        updateSnapshot();
    }

    /**
     * Gets the AE2 action source for extraction requests.
     */
    protected appeng.api.networking.security.IActionSource getActionSource() {
        return new MachineSource(this);
    }

    /**
     * Gets the AE2 energy grid for powered extraction.
     */
    protected IEnergyGrid getEnergyGrid() {
        IGridNode node = this.getGridNode(AEPartLocation.UP);
        if (node == null) {
            return null;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return null;
        }
        return grid.getCache(IEnergyGrid.class);
    }

    // ---- IGridTickable implementation ----

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(1, Integer.MAX_VALUE, false, true);
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLast) {
        // Check if AE2 channel is active — if not, do nothing
        if (!isChannelActive()) {
            return TickRateModulation.SAME;
        }

        tickCounter += ticksSinceLast;

        // Only update snapshot at or after the polling interval boundary
        if (tickCounter >= pollingIntervalTicks) {
            tickCounter = 0;
            updateSnapshot();

            // Drain items into inventory slots after snapshot
            drainIntoInventory();
        }

        return TickRateModulation.SAME;
    }

    /**
     * Checks whether the AE2 channel is active.
     * When no AE2 channel is active, the bus does not tick or snapshot.
     */
    protected boolean isChannelActive() {
        IGridNode node = this.getGridNode(AEPartLocation.UP);
        if (node == null) {
            return false;
        }
        // Check that the grid node has a valid grid attached and is active
        IGrid grid = node.getGrid();
        return grid != null && node.isActive() && node.meetsChannelRequirements();
    }

    // ---- MachineComponentTile implementation ----

    /**
     * Provides a standard MachineComponent.ItemBus(IOType.INPUT) that wraps this tile's inventory.
     */
    @Nullable
    @Override
    public MachineComponent<?> provideComponent() {
        return new MachineComponent.ItemBus(IOType.INPUT) {
            @Nonnull
            @Override
            public IOInventory getContainerProvider() {
                return AdvancedMEItemInputBus.this.inventory;
            }

            @Override
            public boolean isAsyncSupported() {
                return true;
            }
        };
    }

    // ---- Configurable polling interval ----

    /**
     * Gets the current polling interval in ticks.
     */
    public int getPollingIntervalTicks() {
        return pollingIntervalTicks;
    }

    /**
     * Sets the polling interval in ticks (clamped to valid range).
     */
    public void setPollingIntervalTicks(int ticks) {
        this.pollingIntervalTicks = Math.max(MIN_POLLING_INTERVAL_TICKS, Math.min(MAX_POLLING_INTERVAL_TICKS, ticks));
    }

    /**
     * Forces an immediate snapshot and drain — used for manual re-scan from GUI.
     */
    public void forceRescan() {
        updateSnapshot();
        drainIntoInventory();
    }

    // ---- NBT serialization ----

    private static final String NBT_INVENTORY = "inventory";

    @Override
    public void readCustomNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        if (nbt.hasKey("pollingInterval")) {
            this.pollingIntervalTicks = nbt.getInteger("pollingInterval");
        }
        // Restore inventory contents from NBT — mirrors TileInventory pattern
        if (inventory != null && nbt.hasKey(NBT_INVENTORY, Constants.NBT.TAG_COMPOUND)) {
            inventory.readNBT(nbt.getCompoundTag(NBT_INVENTORY));
        }
    }

    @Override
    public void writeCustomNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        nbt.setInteger("pollingInterval", this.pollingIntervalTicks);
        // Save inventory contents to NBT — mirrors TileInventory pattern
        if (inventory != null) {
            nbt.setTag(NBT_INVENTORY, inventory.writeNBT());
        }
    }
}
