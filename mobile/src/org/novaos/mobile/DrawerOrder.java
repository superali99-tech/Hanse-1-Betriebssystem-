package org.novaos.mobile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The order of the app overview, like on Samsung phones: not alphabetical, but the user's
 * own order - new apps come at the end, removed ones disappear, moved ones stay where they
 * were put. Also the pages (rows × columns) and the home screen grid. Android-independent, tested.
 */
final class DrawerOrder {
    private DrawerOrder() {}

    /**
     * The order to show: the stored one (only apps still there), then the new apps in the
     * given order (oldest installation first).
     */
    static List<String> arrange(List<String> stored, List<String> present) {
        Set<String> there = new LinkedHashSet<>(present);
        List<String> out = new ArrayList<>();
        for (String k : stored) if (there.remove(k)) out.add(k);
        out.addAll(there);
        return out;
    }

    /** Moves key by one place (-1 = forward, +1 = back); false if it cannot move. */
    static boolean move(List<String> order, String key, int by) {
        int i = order.indexOf(key), j = i + by;
        if (i < 0 || j < 0 || j >= order.size()) return false;
        order.remove(i);
        order.add(j, key);
        return true;
    }

    static int pages(int count, int perPage) {
        return perPage <= 0 ? 1 : Math.max(1, (count + perPage - 1) / perPage);
    }

    /** The apps of one page. */
    static <T> List<T> page(List<T> all, int page, int perPage) {
        int from = Math.min(all.size(), page * perPage), to = Math.min(all.size(), from + perPage);
        return new ArrayList<>(all.subList(from, to));
    }

    /** How many rows of icons fit (between 3 and 7). */
    static int rows(float heightDp, float cellDp) {
        return Math.max(3, Math.min(7, (int) (heightDp / cellDp)));
    }

    /**
     * Home screen page 1 like One UI: the widget in the middle of the first two rows with one
     * app on each side, the other apps in the bottom rows. Returns per app index its cell
     * (row * columns + column) for the first page; apps that do not fit get -1 (next pages).
     */
    static int[] homeCells(int apps, int rows, int cols) {
        int[] cell = new int[apps];
        java.util.Arrays.fill(cell, -1);
        int i = 0;
        // around the widget (it takes the middle columns of rows 0 and 1)
        int[] around = {0, cols - 1, cols, 2 * cols - 1};
        for (int c : around) {
            if (i >= apps) return cell;
            cell[i++] = c;
        }
        // the rest from the bottom: as many full rows as needed, the last one filled from the left
        int freeRows = rows - 2;
        int rest = apps - i, need = Math.min(freeRows, (rest + cols - 1) / cols);
        int first = rows - need;
        for (int r = first; r < rows && i < apps; r++) {
            for (int c = 0; c < cols && i < apps; c++) cell[i++] = r * cols + c;
        }
        return cell;
    }
}
