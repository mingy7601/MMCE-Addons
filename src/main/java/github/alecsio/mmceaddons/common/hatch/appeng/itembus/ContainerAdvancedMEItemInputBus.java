package github.alecsio.mmceaddons.common.hatch.appeng.itembus;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotDisabled;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Container for the Advanced ME Item Input Bus GUI.
 * <p>
 * One read-only 4x4 grids (SlotDisabled) showing extracted items from AE2,
 * plus the player's hotbar and main inventory.
 */
public class ContainerAdvancedMEItemInputBus extends AEBaseContainer {

    private final AdvancedMEItemInputBus owner;

    // Grid positions
    private static final int RIGHT_GRID_X = 98;
    private static final int GRID_Y = 35;
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 4;

    public ContainerAdvancedMEItemInputBus(AdvancedMEItemInputBus owner, EntityPlayer player) {
        super(player.inventory, owner);
        this.owner = owner;

        // Player inventory (3 rows + hotbar)
        this.bindPlayerInventory(getInventoryPlayer(), 0, 195 - /* height of player inventory */ 72);

        // Right 4x4 grid — read-only display of extracted items (SlotDisabled)
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                addSlotToContainer(new SlotDisabled(owner.getInternalInventory(),
                        row * GRID_COLS + col,
                        RIGHT_GRID_X + col * 18, GRID_Y + row * 18));
            }
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
