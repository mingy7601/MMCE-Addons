package github.alecsio.mmceaddons.common.hatch.appeng.itembus;

import appeng.container.slot.SlotDisabled;
import github.alecsio.mmceaddons.common.hatch.appeng.itembus.AdvancedMEItemInputBus;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * Container for the Advanced ME Item Input Bus GUI.
 * <p>
 * One read-only 4x4 grids (SlotDisabled) showing extracted items from AE2,
 * plus the player's hotbar and main inventory.
 */
public class ContainerAdvancedMEItemInputBus extends Container {

    private final AdvancedMEItemInputBus owner;
    private static final int SLOT_COUNT = 16;

    // Grid positions
    private static final int RIGHT_GRID_X = 98;
    private static final int GRID_Y = 35;
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 4;

    // Player inventory positions
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 123;

    public ContainerAdvancedMEItemInputBus(AdvancedMEItemInputBus owner, EntityPlayer player) {
        this.owner = owner;

        IOInventory inv = owner.inventory != null ? owner.inventory : new IOInventory(owner, new int[SLOT_COUNT], new int[0]);

        // Right 4x4 grid — read-only display of extracted items (SlotDisabled)
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                addSlotToContainer(new SlotDisabled(inv, row * GRID_COLS + col,
                        RIGHT_GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Player inventory (36 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new net.minecraft.inventory.Slot(player.inventory,
                        36 + row * 9 + col, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }

        // Player hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new net.minecraft.inventory.Slot(player.inventory,
                    col, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + 58));
        }
    }

    /**
     * Gets the polling interval in ticks from the tile entity.
     */
    public int getPollingInterval() {
        return owner != null ? owner.getPollingIntervalTicks() : AdvancedMEItemInputBus.DEFAULT_POLLING_INTERVAL_TICKS;
    }

    /**
     * Sets the polling interval in ticks on the tile entity.
     */
    public void setPollingInterval(int ticks) {
        if (owner != null) {
            owner.setPollingIntervalTicks(ticks);
        }
    }

    /**
     * Gets the owner tile entity.
     */
    public AdvancedMEItemInputBus getAdvancedOwner() {
        return owner;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }
}
