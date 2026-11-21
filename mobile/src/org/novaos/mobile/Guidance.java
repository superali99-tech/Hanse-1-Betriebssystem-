package org.novaos.mobile;

import java.util.HashSet;
import java.util.Set;

/**
 * Turn-by-turn guidance of Nova Karte: with every new position it finds the place on the
 * route, the next manoeuvre and what Nova says now ("In 400 Metern …", "In 150 Metern …",
 * "Jetzt …"), and notices when the route was left. Android-independent, tested.
 */
public final class Guidance {
    /** What the screen shows and Nova says after a position. */
    public static final class State {
        public int next;                 // index of the coming manoeuvre in route.steps
        public double toManeuver, remaining, remainingSeconds, along, offLine;
        public boolean offRoute, arrived;
        public String say;               // null: say nothing now
        public Geo.Point onRoute;        // the position on the route
    }

    private final NavRoute route;
    private final boolean german;
    private final double[] thresholds;
    private final double offLimit;
    private final Set<String> said = new HashSet<>();
    private int offCount;
    private double lastAlong;
    private boolean arrivedSaid;

    public Guidance(NavRoute route, boolean german) {
        this.route = route;
        this.german = german;
        // when to announce (metres before the manoeuvre): earlier at car speed
        thresholds = NavRoute.CAR.equals(route.mode) ? new double[] {1000, 400, 150, 30}
                : NavRoute.BIKE.equals(route.mode) ? new double[] {300, 100, 20} : new double[] {150, 50, 15};
        offLimit = NavRoute.FOOT.equals(route.mode) ? 30 : 45;
    }

    public NavRoute route() {
        return route;
    }

    /** First words when the navigation starts: "Fahre Richtung Norden auf Plan. Dann in 80 Metern links abbiegen …". */
    public String start() {
        NavRoute.Step first = route.steps.get(0);
        String s = NavText.instruction(first, route.mode, german) + ".";
        if (route.steps.size() > 1) {
            NavRoute.Step next = route.steps.get(1);
            double d = next.along - first.along;
            s += " " + (german ? "Dann " : "Then ") + lowerFirst(NavText.announcement(next, d, route.mode, german)) + ".";
            for (double t : thresholds) if (d <= t) said.add(1 + ":" + t);  // already said
        }
        return s;
    }

    public State update(Geo.Point p) {
        State st = new State();
        Geo.Snap snap = Geo.snap(route.points, route.cumulative, p);
        st.offLine = snap.offLine;
        st.onRoute = snap.point;
        // a position on a part of the route that crosses itself: do not jump back by more than 60 m
        double along = snap.along < lastAlong - 60 ? lastAlong : snap.along;
        lastAlong = Math.max(lastAlong, along);
        st.along = along;
        offCount = snap.offLine > offLimit ? offCount + 1 : 0;
        st.offRoute = offCount >= 3;  // three positions in a row away from the route
        double total = route.cumulative[route.cumulative.length - 1];
        st.remaining = Math.max(0, total - along);
        st.remainingSeconds = total <= 0 ? 0 : route.duration * st.remaining / total;
        int next = route.steps.size() - 1;
        for (int i = 1; i < route.steps.size(); i++) {
            if (route.steps.get(i).along > along + 5) {
                next = i;
                break;
            }
        }
        st.next = next;
        NavRoute.Step step = route.steps.get(next);
        st.toManeuver = Math.max(0, step.along - along);
        st.arrived = st.remaining < 20 || ("arrive".equals(step.type) && st.toManeuver < 20);
        if (st.arrived) {
            if (!arrivedSaid) {
                arrivedSaid = true;
                st.say = NavText.instruction(route.steps.get(route.steps.size() - 1), route.mode, german) + ".";
            }
            return st;
        }
        if (st.offRoute) return st;
        // the smallest threshold the manoeuvre is already within, once per manoeuvre and threshold
        Double when = null;
        for (double t : thresholds) if (st.toManeuver <= t) when = t;
        if (when != null && said.add(next + ":" + when)) {
            // also the thresholds just ahead: after 157 m no second "In 150 Metern" at 147 m
            double margin = Math.max(20, st.toManeuver * 0.25);
            for (double t : thresholds) if (t >= when || t >= st.toManeuver - margin) said.add(next + ":" + t);
            if ("arrive".equals(step.type) && st.toManeuver < 40) return st;  // "Du hast dein Ziel erreicht" comes on arrival
            String text = NavText.announcement(step, st.toManeuver, route.mode, german);
            // two manoeuvres close together: say the second one, too
            if (next + 1 < route.steps.size() && !"arrive".equals(step.type)) {
                NavRoute.Step after = route.steps.get(next + 1);
                if (after.along - step.along < 80 && !"arrive".equals(after.type)) {
                    text += german ? ", danach " + NavText.side(after.modifier, true) : ", then " + NavText.side(after.modifier, false);
                }
            }
            st.say = text + ".";
        }
        return st;
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
