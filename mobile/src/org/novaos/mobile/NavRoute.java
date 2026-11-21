package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A route of Nova Karte from the routing servers of the FOSSGIS (OSRM, data from
 * OpenStreetMap): the line to draw and the steps ("turn right onto Hauptstraße").
 * Android-independent, tested.
 */
public final class NavRoute {
    public static final String CAR = "car", BIKE = "bike", FOOT = "foot";

    /** One manoeuvre (OSRM step). */
    public static final class Step {
        public String type = "", modifier = "", name = "", ref = "";
        public int exit;              // roundabout exit
        public double distance, duration, bearingAfter;
        public Geo.Point at;          // where the manoeuvre is
        public double along;          // metres from the start of the route to the manoeuvre
        int index;                    // first point of this step in points
    }

    public final String mode;
    public double distance, duration;
    public final List<Geo.Point> points = new ArrayList<>();
    public double[] cumulative = new double[0];
    public final List<Step> steps = new ArrayList<>();

    NavRoute(String mode) {
        this.mode = mode;
    }

    /** https://routing.openstreetmap.de/routed-car/route/v1/driving/lon,lat;lon,lat?… */
    public static String url(String mode, Geo.Point from, Geo.Point to) {
        String profile = BIKE.equals(mode) ? "routed-bike" : FOOT.equals(mode) ? "routed-foot" : "routed-car";
        return "https://routing.openstreetmap.de/" + profile + "/route/v1/driving/"
                + coord(from) + ";" + coord(to) + "?overview=false&steps=true&geometries=polyline&alternatives=false";
    }

    private static String coord(Geo.Point p) {
        return String.format(java.util.Locale.ROOT, "%.6f,%.6f", p.lon, p.lat);
    }

    /** null if the answer has no route (e.g. "NoRoute" across the sea). */
    public static NavRoute parse(String json, String mode) {
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(root instanceof Map) || !"Ok".equals(((Map<?, ?>) root).get("code"))) return null;
        Object routes = ((Map<?, ?>) root).get("routes");
        if (!(routes instanceof List) || ((List<?>) routes).isEmpty()) return null;
        Map<?, ?> r = (Map<?, ?>) ((List<?>) routes).get(0);
        NavRoute route = new NavRoute(mode);
        route.distance = num(r.get("distance"));
        route.duration = num(r.get("duration"));
        Object legs = r.get("legs");
        if (!(legs instanceof List)) return null;
        for (Object leg : (List<?>) legs) {
            Object steps = leg instanceof Map ? ((Map<?, ?>) leg).get("steps") : null;
            if (!(steps instanceof List)) continue;
            for (Object o : (List<?>) steps) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> s = (Map<?, ?>) o;
                Step step = new Step();
                step.name = str(s.get("name"));
                step.ref = str(s.get("ref"));
                step.distance = num(s.get("distance"));
                step.duration = num(s.get("duration"));
                Object m = s.get("maneuver");
                if (m instanceof Map) {
                    Map<?, ?> mm = (Map<?, ?>) m;
                    step.type = str(mm.get("type"));
                    step.modifier = str(mm.get("modifier"));
                    step.exit = (int) num(mm.get("exit"));
                    step.bearingAfter = num(mm.get("bearing_after"));
                    Object loc = mm.get("location");
                    if (loc instanceof List && ((List<?>) loc).size() == 2) {
                        step.at = new Geo.Point(num(((List<?>) loc).get(1)), num(((List<?>) loc).get(0)));
                    }
                }
                // the line of this step; the steps follow each other, so the end of one is the start of the next
                List<Geo.Point> line = Geo.decode(str(s.get("geometry")));
                step.index = route.points.isEmpty() ? 0 : route.points.size() - 1;
                if (!route.points.isEmpty() && !line.isEmpty()) line.remove(0);
                if (step.at == null && !line.isEmpty()) step.at = line.get(0);
                route.points.addAll(line);
                if (step.at != null) route.steps.add(step);
            }
        }
        if (route.points.isEmpty() || route.steps.isEmpty()) return null;
        route.cumulative = Geo.cumulative(route.points);
        for (Step st : route.steps) st.along = route.cumulative[Math.min(st.index, route.cumulative.length - 1)];
        return route;
    }

    private static String str(Object o) {
        return o instanceof String ? ((String) o).trim() : "";
    }

    private static double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }
}
