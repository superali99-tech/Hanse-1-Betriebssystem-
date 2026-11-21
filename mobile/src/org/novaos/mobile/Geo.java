package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Map maths for Nova Karte: Web Mercator (the tiles of OpenStreetMap), distances on the
 * earth, compass directions, Google's polyline format and "where on the route am I".
 * Android-independent, tested.
 */
public final class Geo {
    public static final double EARTH_M = 6_371_000.0;

    private Geo() {}

    /** A point: latitude, longitude in degrees. */
    public static final class Point {
        public final double lat, lon;

        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.5f,%.5f", lat, lon);
        }
    }

    // ------------------------------------------------------------ Web Mercator: world pixels at a zoom level

    /** x in pixels of the whole world map (tile size 256 at zoom 0). */
    public static double x(double lon, double zoom, double tile) {
        return (lon + 180.0) / 360.0 * tile * Math.pow(2, zoom);
    }

    public static double y(double lat, double zoom, double tile) {
        double l = Math.max(-85.05112878, Math.min(85.05112878, lat));
        double s = Math.sin(Math.toRadians(l));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * tile * Math.pow(2, zoom);
    }

    public static double lon(double x, double zoom, double tile) {
        return x / (tile * Math.pow(2, zoom)) * 360.0 - 180.0;
    }

    public static double lat(double y, double zoom, double tile) {
        double n = Math.PI - 2 * Math.PI * y / (tile * Math.pow(2, zoom));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    // ------------------------------------------------------------ distances and directions

    /** Great-circle distance in metres. */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double distance(Point a, Point b) {
        return distance(a.lat, a.lon, b.lat, b.lon);
    }

    /** Compass direction from a to b, 0..360 (0 = north, 90 = east). */
    public static double bearing(Point a, Point b) {
        double f1 = Math.toRadians(a.lat), f2 = Math.toRadians(b.lat), dl = Math.toRadians(b.lon - a.lon);
        double y = Math.sin(dl) * Math.cos(f2), x = Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** 0..360 -> "Norden", "Nordosten", ... (8 directions). */
    public static String compass(double bearing, boolean german) {
        String[] de = {"Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten"};
        String[] en = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int i = (int) Math.round(((bearing % 360) + 360) % 360 / 45.0) % 8;
        return german ? de[i] : en[i];
    }

    // ------------------------------------------------------------ polyline ("_p~iF~ps|U...", precision 5)

    public static List<Point> decode(String encoded) {
        List<Point> out = new ArrayList<>();
        if (encoded == null) return out;
        int index = 0, lat = 0, lon = 0;
        while (index < encoded.length()) {
            int[] r = next(encoded, index);
            if (r == null) break;
            lat += r[0];
            index = r[1];
            r = next(encoded, index);
            if (r == null) break;
            lon += r[0];
            index = r[1];
            out.add(new Point(lat / 1e5, lon / 1e5));
        }
        return out;
    }

    private static int[] next(String s, int index) {
        int result = 0, shift = 0, b;
        do {
            if (index >= s.length()) return null;
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        return new int[] {(result & 1) != 0 ? ~(result >> 1) : (result >> 1), index};
    }

    // ------------------------------------------------------------ where on a line am I

    /** Result of snapping a position to a line: distance to it, segment, and metres along the line. */
    public static final class Snap {
        public double offLine = Double.MAX_VALUE, along;
        public int segment;
        public Point point;
    }

    /**
     * The nearest point of the line to p (flat-earth maths per segment - exact enough for the
     * few hundred metres around the phone). cumulative[i] = metres from the start to points[i].
     */
    public static Snap snap(List<Point> points, double[] cumulative, Point p) {
        Snap best = new Snap();
        if (points.isEmpty()) return best;
        if (points.size() == 1) {
            best.offLine = distance(p, points.get(0));
            best.point = points.get(0);
            return best;
        }
        double cos = Math.cos(Math.toRadians(p.lat));
        for (int i = 0; i + 1 < points.size(); i++) {
            Point a = points.get(i), b = points.get(i + 1);
            double ax = (a.lon - p.lon) * cos, ay = a.lat - p.lat, bx = (b.lon - p.lon) * cos, by = b.lat - p.lat;
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            Point q = new Point(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t);
            double d = distance(p, q);
            if (d < best.offLine) {
                best.offLine = d;
                best.segment = i;
                best.point = q;
                best.along = cumulative[i] + distance(a, q);
            }
        }
        return best;
    }

    /** Metres from the first point to every point of the line. */
    public static double[] cumulative(List<Point> points) {
        double[] c = new double[points.size()];
        for (int i = 1; i < points.size(); i++) c[i] = c[i - 1] + distance(points.get(i - 1), points.get(i));
        return c;
    }

    /** The point that lies `metres` along the line. */
    public static Point at(List<Point> points, double[] cumulative, double metres) {
        if (points.isEmpty()) return null;
        if (metres <= 0) return points.get(0);
        for (int i = 0; i + 1 < points.size(); i++) {
            if (cumulative[i + 1] >= metres) {
                double seg = cumulative[i + 1] - cumulative[i];
                double t = seg <= 0 ? 0 : (metres - cumulative[i]) / seg;
                Point a = points.get(i), b = points.get(i + 1);
                return new Point(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t);
            }
        }
        return points.get(points.size() - 1);
    }

    // ------------------------------------------------------------ "1,2 km", "12 Min."

    /** Shown distance: "80 m", "450 m", "1,2 km", "23 km". */
    public static String distanceText(double m, boolean german) {
        String s;
        if (m < 1000) s = (m < 100 ? Math.round(m / 10) * 10 : Math.round(m / 50) * 50) + " m";
        else if (m < 10_000) s = String.format(Locale.ROOT, "%.1f km", m / 1000);
        else s = Math.round(m / 1000) + " km";
        return german ? s.replace('.', ',') : s;
    }

    /** "12 Min.", "1 Std. 5 Min." / "12 min", "1 h 5 min". */
    public static String durationText(double seconds, boolean german) {
        long min = Math.max(1, Math.round(seconds / 60));
        if (min < 60) return min + (german ? " Min." : " min");
        long h = min / 60, m = min % 60;
        return h + (german ? " Std." : " h") + (m > 0 ? " " + m + (german ? " Min." : " min") : "");
    }
}
