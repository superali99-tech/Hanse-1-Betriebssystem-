package org.novaos.mobile;

import java.util.List;

/**
 * Which position to believe. Phones report several: GPS (a few metres), the "fused" one
 * (GPS + Wi-Fi + mobile network) and the mobile network alone (often a few kilometres off -
 * the next village). A newer position only replaces the current one if it is not much worse,
 * so a mobile-network fix never pushes a good GPS fix aside. Android-independent, tested.
 */
final class Fixes {
    static final long STALE_MS = 2 * 60_000;  // after this, any newer fix is better than an old one
    static final float GOOD_M = 150;           // up to this accuracy (metres) a position is good enough to navigate

    static final class Fix {
        final String provider;
        final double lat, lon;
        final float accuracy;  // metres, 0 = unknown
        final long time;       // ms

        Fix(String provider, double lat, double lon, float accuracy, long time) {
            this.provider = provider == null ? "" : provider;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.time = time;
        }

        float accuracy() {
            return accuracy > 0 ? accuracy : 5000;
        }

        boolean good() {
            return accuracy > 0 && accuracy <= GOOD_M;
        }
    }

    private Fixes() {}

    /** Whether next should replace current (the Android guide's rule, a bit stricter). */
    static boolean better(Fix next, Fix current) {
        if (next == null) return false;
        if (current == null) return true;
        long dt = next.time - current.time;
        if (dt > STALE_MS) return true;     // the current one is old: the phone may have moved
        if (dt < -STALE_MS) return false;   // next is old
        float da = next.accuracy() - current.accuracy();
        if (da <= 0) return true;                                            // at least as accurate
        if (dt < 0) return false;                                            // older and worse
        if (next.provider.equals(current.provider) && da <= 200) return true;  // the same source moving on
        return false;  // e.g. the mobile network (2 km) after GPS (8 m)
    }

    /** The best of the positions the phone still knows (from all sources), or null. */
    static Fix best(List<Fix> known) {
        Fix best = null;
        for (Fix f : known) if (better(f, best)) best = f;
        return best;
    }
}
