package github.alecsio.mmceaddons.common.hatch.appeng.itembus;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure utility for selecting the top-16 most abundant item types from an AE2 storage list.
 * <p>
 */
public final class SelectTop16 {

    private static final int MAX_ITEMS = 16;

    private SelectTop16() {
    }

    /**
     * Returns the 16 most abundant item types from the given list, sorted by descending quantity.
     * <p>
     * When fewer than 16 distinct item types exist, returns all of them (no padding with empty stacks).
     * Selection is deterministic for tied quantities — uses stable sort so original order is preserved among equals.
     *
     * @param itemList the AE2 item list to sample from (typically {@code IMEMonitor.getAvailableItems()})
     * @return a new list containing up to 16 non-empty stacks, sorted by quantity descending
     */
    public static List<IAEItemStack> select(IItemList<IAEItemStack> itemList) {
        // Collect all non-empty stacks into a mutable list
        List<IAEItemStack> items = new ArrayList<>();
        for (IAEItemStack stack : itemList) {
            if (stack.getStackSize() > 0) {
                items.add(stack);
            }
        }

        // Stable sort by quantity descending — Collections.sort is guaranteed stable in Java
        items.sort((a, b) -> Long.compare(b.getStackSize(), a.getStackSize()));

        int size = Math.min(items.size(), MAX_ITEMS);
        return items.subList(0, size);
    }

    /**
     * Convenience overload that accepts an {@link Iterable} of stacks.
     *
     * @param stacks iterable of AE2 item stacks (may include empty stacks)
     * @return top-16 non-empty stacks sorted by quantity descending
     */
    public static List<IAEItemStack> select(Iterable<IAEItemStack> stacks) {
        List<IAEItemStack> items = new ArrayList<>();
        for (IAEItemStack stack : stacks) {
            if (stack.getStackSize() > 0) {
                items.add(stack);
            }
        }

        items.sort((a, b) -> Long.compare(b.getStackSize(), a.getStackSize()));

        int size = Math.min(items.size(), MAX_ITEMS);
        return items.subList(0, size);
    }
}
