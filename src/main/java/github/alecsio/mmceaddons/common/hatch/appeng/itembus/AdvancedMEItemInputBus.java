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
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import github.alecsio.mmceaddons.common.registry.ModularMachineryAddonsBlocks;
import github.alecsio.mmceaddons.common.hatch.handler.AdaptiveSnapshotRefreshScheduler;
import github.kasuminova.mmce.common.tile.base.MEItemBus;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Core tile entity for the Advanced ME Item Input Bus.
 * <p>
 * Extends MMCE's MEItemBus to reuse AE2 networking, capability exposure, and NBT handling.
 * On load, performs an initial snapshot of available items from the connected AE2 network.
 * When no AE2 channel is active (proxy inactive), does not tick or snapshot.
 * Respects a configurable polling interval (default: 20 ticks / 1 second).
 * <p>
 * After draining items into inventory slots, re-snapshots to reflect updated AE2 availability.
 */
public class AdvancedMEItemInputBus extends MEItemBus implements IGridTickable {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Default polling interval in ticks (20 ticks = 1 second). */
    public static final int DEFAULT_POLLING_INTERVAL_TICKS = 20;

    /** Minimum polling interval in ticks. */
    public static final int MIN_POLLING_INTERVAL_TICKS = 1;

    /** Maximum polling interval in ticks (72000 = 1 hour). */
    public static final int MAX_POLLING_INTERVAL_TICKS = 72000;

    private static final int SLOT_COUNT = 16;

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

    // ---- Inventory construction ----
    @Override
    public IOInventory buildInventory() {
        int[] slotIndices = new int[SLOT_COUNT];
        Arrays.setAll(slotIndices, i -> i);
        IOInventory inv = new IOInventory(this, slotIndices, new int[0]);
        // Each slot can hold one unique item type up to AE2's internal cap.
        inv.setStackLimit(Integer.MAX_VALUE, slotIndices);
        return inv;
    }

    // ---- Visual identity ----
    @Nonnull
    @Override
    public ItemStack getVisualItemStack() {
        return new ItemStack(ModularMachineryAddonsBlocks.blockAdvancedMEItemInputBus);
    }

    // ---- Lifecycle ----
    @Override
    public void onLoad() {
        super.onLoad();
        updateSnapshot();
    }

    // ---- Snapshot logic ----
    /**
     * Updates the snapshot by querying AE2's storage grid.
     * <p>
     * Single read-only call to getAvailableItems() — no repeated lookups.
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
     * Gets the AE2 storage inventory via inherited proxy and channel.
     */
    protected Optional<IMEInventory<IAEItemStack>> getStorageInventory() {
        IGridNode gridNode = this.getGridNode(AEPartLocation.UP);
        if (gridNode == null) {
            return Optional.empty();
        }

        IGrid grid = gridNode.getGrid();
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        return Optional.of(storage.getInventory(channel));
    }

    /**
     * Drains items from AE2 into the bus's internal inventory slots.
     * <p>
     * Called once per poll cycle by {@link #tickingRequest} after snapshot refresh.
     * Rules:
     * <ul>
     *   <li>Slot holds less than Integer.MAX_VALUE → merge matching items into existing slot</li>
     *   <li>Merge would exceed cap → extract up to Integer.MAX_VALUE (partial fill, don't reject)</li>
     *   <li>Slot already at Integer.MAX_VALUE → skip that slot entirely</li>
     * </ul>
     */
    public void drainIntoInventory() {
        lock.readLock().lock();
        List<IAEItemStack> snap;
        try {
            snap = snapshot.isEmpty() ? null : new ArrayList<>(snapshot);
        } finally {
            lock.readLock().unlock();
        }

        if (snap == null || snap.isEmpty()) {
            return;
        }

        GridContext gridCtx = getGridContext();
        if (gridCtx == null) {
            return;
        }

        IEnergyGrid energyGrid = gridCtx.energyGrid;
        IMEInventory<IAEItemStack> meInv = gridCtx.meInventory;
        int slots = inventory != null ? inventory.getSlots() : 0;

        // ---- Phase 1 — merge matching items into existing non-empty slots. ----
        for (int i = 0; i < slots; i++) {
            ItemStack cur = inventory.getStackInSlot(i);
            if (cur.isEmpty()) continue;

            // Skip slots already at full capacity.
            long slotLimit = inventory.getSlotLimit(i);
            if (cur.getCount() >= slotLimit) {
                continue;
            }

            IAEItemStack curAe = appeng.util.item.AEItemStack.fromItemStack(cur);
            for (int j = 0; j < snap.size(); j++) {
                IAEItemStack src = snap.get(j);
                if (src == null || src.getItem() == null) continue;

                // Match by item registry name (ignores NBT to avoid false mismatches)
                if (!curAe.getItem().getRegistryName().equals(src.getItem().getRegistryName())) {
                    continue;
                }

                long canFit = slotLimit - cur.getCount();
                IAEItemStack toDrain = src.copy();
                // Extract up to what fits (partial fill if AE2 stack is larger than remaining space).
                long drainAmount = Math.min(src.getStackSize(), canFit);
                toDrain.setStackSize(drainAmount);

                IAEItemStack extracted = appeng.api.AEApi.instance().storage()
                        .poweredExtraction(energyGrid, meInv, toDrain, source, Actionable.MODULATE);
                if (extracted != null && extracted.getStackSize() > 0) {
                    cur.grow((int) extracted.getStackSize());
                    inventory.setStackInSlot(i, cur);
                    snap.set(j, null); // mark as drained
                }
                break;
            }
        }

        // ---- Collect item types already assigned to non-empty slots. ----
        // Ensures Phase 2 never claims a slot for an item type that's already
        // present elsewhere — excess stays in AE2, not duplicated across slots.
        Set<String> occupiedTypes = new HashSet<>();
        for (int i = 0; i < slots; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem().getRegistryName() != null) {
                occupiedTypes.add(s.getItem().getRegistryName().toString());
            }
        }

        // ---- Phase 2 — fill empty slots with remaining snapshot items. ----
        for (int i = 0; i < slots; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) continue;

            long slotLimit = inventory.getSlotLimit(i);
            if (slotLimit <= 0) continue;

            boolean drained = false;
            for (int j = 0; j < snap.size() && !drained; j++) {
                IAEItemStack src = snap.get(j);
                if (src == null || src.getItem() == null) continue;

                // Skip item types already present in another slot.
                String regName = src.getItem().getRegistryName().toString();
                if (occupiedTypes.contains(regName)) {
                    continue;
                }
                IAEItemStack toDrain = src.copy();
                // Extract up to what fits (partial fill if AE2 stack exceeds remaining space).
                long drainAmount = Math.min(src.getStackSize(), slotLimit);
                toDrain.setStackSize(drainAmount);

                IAEItemStack extracted = appeng.api.AEApi.instance().storage()
                        .poweredExtraction(energyGrid, meInv, toDrain, source, Actionable.MODULATE);
                if (extracted != null && extracted.getStackSize() > 0) {
                    inventory.setStackInSlot(i, extracted.createItemStack());
                    snap.set(j, null); // mark as drained
                    drained = true;
                }
            }
        }
    }

    /**
     * Holds the AE2 grid context: energy grid and storage inventory.
     */
    private static final class GridContext {
        final IEnergyGrid energyGrid;
        final IMEInventory<IAEItemStack> meInventory;

        GridContext(IEnergyGrid energyGrid, IMEInventory<IAEItemStack> meInventory) {
            this.energyGrid = energyGrid;
            this.meInventory = meInventory;
        }
    }

    /**
     * Resolves the AE2 grid context in a single lookup.
     */
    private GridContext getGridContext() {
        IGridNode node = AdvancedMEItemInputBus.this.getGridNode(AEPartLocation.UP);
        if (node == null || !node.isActive()) {
            return null;
        }
        IGrid grid = node.getGrid();
        IEnergyGrid energyGrid = grid.getCache(IEnergyGrid.class);
        if (energyGrid == null) {
            return null;
        }
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (storage == null) {
            return null;
        }
        IMEInventory<IAEItemStack> meInv = storage.getInventory(channel);
        if (meInv == null) {
            return null;
        }
        return new GridContext(energyGrid, meInv);
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
        IGridNode tickNode = this.getGridNode(AEPartLocation.UP);
        boolean channelActive = tickNode != null && tickNode.isActive() && tickNode.meetsChannelRequirements();
        if (!channelActive) {
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
    /**
     * Overrides the base deserialization to resize tracking arrays before setting up the listener.
     * <p>
     * The base MEItemBus.readInventoryNBT() replaces the inventory with a new IOInventory from NBT,
     * then sets up a listener callback that writes to changedSlots[slot]. If the saved NBT contains
     * more slots than what buildInventory() originally created, the listener throws ArrayIndexOutOfBoundsException.
     * This override resizes changedSlots and failureCounter to match the deserialized inventory's slot count
     * before the listener is registered.
     */
    @Override
    public void readInventoryNBT(net.minecraft.nbt.NBTTagCompound tag) {
        this.inventory = hellfirepvp.modularmachinery.common.util.IOInventory.deserialize(this, tag);
        final int newSlotCount = inventory.getSlots();

        // Resize tracking arrays to match the deserialized inventory's slot count.
        if (newSlotCount != changedSlots.length) {
            boolean[] newChanged = new boolean[newSlotCount];
            System.arraycopy(changedSlots, 0, newChanged, 0, Math.min(changedSlots.length, newSlotCount));
            changedSlots = newChanged;
        }
        if (newSlotCount != failureCounter.length) {
            int[] newFailure = new int[newSlotCount];
            System.arraycopy(failureCounter, 0, newFailure, 0, Math.min(failureCounter.length, newSlotCount));
            failureCounter = newFailure;
        }

        this.inventory.setListener(slot -> {
            synchronized (this) {
                changedSlots[slot] = true;
            }
        });

        // Re-apply stack limit after deserialization — the parent's deserialize()
        // resets limits to defaults (64). Restore AE2-cap unlimited stacks.
        int[] allSlots = new int[inventory.getSlots()];
        Arrays.setAll(allSlots, i -> i);
        inventory.setStackLimit(Integer.MAX_VALUE, allSlots);
    }

    @Override
    public void readCustomNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        super.readCustomNBT(nbt);
        if (nbt.hasKey("pollingInterval")) {
            this.pollingIntervalTicks = nbt.getInteger("pollingInterval");
        }
    }

    @Override
    public void writeCustomNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        super.writeCustomNBT(nbt);
        nbt.setInteger("pollingInterval", this.pollingIntervalTicks);
    }
}
