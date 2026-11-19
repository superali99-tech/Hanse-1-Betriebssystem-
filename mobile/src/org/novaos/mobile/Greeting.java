package org.novaos.mobile;

/** Greeting on the home screen by time of day (Android-independent, tested). */
public final class Greeting {
    private Greeting() {}

    public static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    /** 5-11 morning, 11-17 day, 17-22 evening, otherwise night. */
    public static int of(int hour) {
        if (hour >= 5 && hour < 11) return MORNING;
        if (hour >= 11 && hour < 17) return DAY;
        if (hour >= 17 && hour < 22) return EVENING;
        return NIGHT;
    }

    /** Number of grid columns for a width in dp (cells of cellDp, between min and max). */
    public static int columns(int widthDp, int cellDp, int min, int max) {
        int n = cellDp > 0 ? widthDp / cellDp : min;
        return Math.max(min, Math.min(max, n));
    }
}
