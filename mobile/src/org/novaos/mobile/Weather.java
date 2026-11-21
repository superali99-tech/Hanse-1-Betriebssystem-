package org.novaos.mobile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * Weather from Open-Meteo (open-meteo.com: free, no key, no account). Android-independent
 * (addresses, answers, texts) so it is tested; Nova and the home screen fetch it.
 */
public final class Weather {
    private Weather() {}

    public static final class Place {
        public final String name;
        public final double lat, lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final class Now {
        public final double temp, max, min, wind;
        public final int code;

        Now(double temp, int code, double wind, double max, double min) {
            this.temp = temp;
            this.code = code;
            this.wind = wind;
            this.max = max;
            this.min = min;
        }
    }

    public static String geocodeUrl(String city, boolean german) {
        try {
            return "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language=" + (german ? "de" : "en")
                    + "&name=" + URLEncoder.encode(city.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String forecastUrl(double lat, double lon) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1", lat, lon);
    }

    /** The forecast for the Nova Wetter page: now, the next 24 hours, 7 days. */
    public static String detailUrl(double lat, double lon) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,wind_speed_10m,is_day&hourly=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=7", lat, lon);
    }

    public static final class Hour {
        public final String time;  // "2026-11-21T19:00" (local time of the place)
        public final double temp;
        public final int code;

        Hour(String time, double temp, int code) {
            this.time = time;
            this.temp = temp;
            this.code = code;
        }

        /** "19" */
        public String hour() {
            return time.length() >= 13 ? time.substring(11, 13) : time;
        }
    }

    public static final class Day {
        public final String date;  // "2026-11-22"
        public final int code;
        public final double max, min;

        Day(String date, int code, double max, double min) {
            this.date = date;
            this.code = code;
            this.max = max;
            this.min = min;
        }
    }

    public static final class Forecast {
        public Now now;
        public boolean day = true;
        public final java.util.List<Hour> hours = new java.util.ArrayList<>();  // from the current hour, 24 hours
        public final java.util.List<Day> days = new java.util.ArrayList<>();
    }

    /** The forecast of a detailUrl answer, or null. */
    public static Forecast forecast(String json) {
        try {
            Object j = MiniJson.parse(json);
            Now now = now(json);
            if (now == null) return null;
            Forecast f = new Forecast();
            f.now = now;
            Object isDay = MiniJson.get(j, "current", "is_day");
            f.day = !(isDay instanceof Double) || ((Double) isDay) >= 1;
            Object time = MiniJson.get(j, "current", "time");
            String hourNow = time instanceof String && ((String) time).length() >= 13 ? ((String) time).substring(0, 13) : "";
            List<?> ht = list(MiniJson.get(j, "hourly", "time")), htemp = list(MiniJson.get(j, "hourly", "temperature_2m")),
                    hcode = list(MiniJson.get(j, "hourly", "weather_code"));
            int start = 0;
            for (int i = 0; i < ht.size(); i++) if (String.valueOf(ht.get(i)).startsWith(hourNow)) { start = i; break; }
            for (int i = start; i < ht.size() && f.hours.size() < 24; i++) {
                if (i >= htemp.size() || i >= hcode.size() || !(htemp.get(i) instanceof Double) || !(hcode.get(i) instanceof Double)) continue;
                f.hours.add(new Hour(String.valueOf(ht.get(i)), (Double) htemp.get(i), ((Double) hcode.get(i)).intValue()));
            }
            List<?> dt = list(MiniJson.get(j, "daily", "time")), dcode = list(MiniJson.get(j, "daily", "weather_code")),
                    dmax = list(MiniJson.get(j, "daily", "temperature_2m_max")), dmin = list(MiniJson.get(j, "daily", "temperature_2m_min"));
            for (int i = 0; i < dt.size(); i++) {
                if (i >= dcode.size() || i >= dmax.size() || i >= dmin.size()) break;
                if (!(dcode.get(i) instanceof Double) || !(dmax.get(i) instanceof Double) || !(dmin.get(i) instanceof Double)) continue;
                f.days.add(new Day(String.valueOf(dt.get(i)), ((Double) dcode.get(i)).intValue(), (Double) dmax.get(i), (Double) dmin.get(i)));
            }
            return f;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : java.util.Collections.emptyList();
    }

    /** "Sonntag" / "So." for "2026-11-22" (English: "Sunday" / "Sun"). */
    public static String weekday(String date, boolean german, boolean shortName) {
        try {
            String[] p = date.split("-");
            java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            int d = c.get(java.util.Calendar.DAY_OF_WEEK) - 1;  // 0 = Sunday
            String[] de = {"Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"};
            String[] en = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            String name = (german ? de : en)[d];
            return shortName ? (german ? name.substring(0, 2) + "." : name.substring(0, 3)) : name;
        } catch (RuntimeException e) {
            return date;
        }
    }

    /** First place of a geocoding answer, or null. */
    public static Place place(String json) {
        try {
            Object results = MiniJson.get(MiniJson.parse(json), "results");
            if (!(results instanceof List) || ((List<?>) results).isEmpty()) return null;
            Object r = ((List<?>) results).get(0);
            Object name = MiniJson.get(r, "name"), lat = MiniJson.get(r, "latitude"), lon = MiniJson.get(r, "longitude");
            if (!(name instanceof String) || !(lat instanceof Double) || !(lon instanceof Double)) return null;
            return new Place((String) name, (Double) lat, (Double) lon);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Current weather of a forecast answer, or null. */
    public static Now now(String json) {
        try {
            Object j = MiniJson.parse(json);
            Object t = MiniJson.get(j, "current", "temperature_2m"), c = MiniJson.get(j, "current", "weather_code");
            Object w = MiniJson.get(j, "current", "wind_speed_10m");
            Object max = first(MiniJson.get(j, "daily", "temperature_2m_max")), min = first(MiniJson.get(j, "daily", "temperature_2m_min"));
            if (!(t instanceof Double) || !(c instanceof Double)) return null;
            return new Now((Double) t, ((Double) c).intValue(), w instanceof Double ? (Double) w : 0,
                    max instanceof Double ? (Double) max : Double.NaN, min instanceof Double ? (Double) min : Double.NaN);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Object first(Object list) {
        return list instanceof List && !((List<?>) list).isEmpty() ? ((List<?>) list).get(0) : null;
    }

    /** WMO weather code as words. */
    public static String describe(int code, boolean german) {
        String[] t;
        if (code == 0) t = new String[] {"sonnig und klar", "sunny and clear"};
        else if (code == 1) t = new String[] {"überwiegend sonnig", "mostly sunny"};
        else if (code == 2) t = new String[] {"teilweise bewölkt", "partly cloudy"};
        else if (code == 3) t = new String[] {"bedeckt", "overcast"};
        else if (code == 45 || code == 48) t = new String[] {"neblig", "foggy"};
        else if (code >= 51 && code <= 57) t = new String[] {"Nieselregen", "drizzle"};
        else if (code >= 61 && code <= 67) t = new String[] {"Regen", "rain"};
        else if (code >= 71 && code <= 77) t = new String[] {"Schnee", "snow"};
        else if (code >= 80 && code <= 82) t = new String[] {"Regenschauer", "rain showers"};
        else if (code == 85 || code == 86) t = new String[] {"Schneeschauer", "snow showers"};
        else if (code >= 95) t = new String[] {"Gewitter", "thunderstorms"};
        else t = new String[] {"wechselhaft", "changeable"};
        return german ? t[0] : t[1];
    }

    /** Short symbol for the home screen. */
    public static String symbol(int code) {
        if (code == 0 || code == 1) return "☀";
        if (code == 2) return "⛅";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }

    /** What Nova says, e.g. "In Hamburg sind es gerade 12 Grad, teilweise bewölkt. Heute 8 bis 14 Grad." */
    public static String sentence(String place, Now n, boolean german) {
        long t = Math.round(n.temp);
        StringBuilder s = new StringBuilder();
        if (german) {
            s.append(place == null || place.isEmpty() ? "Hier" : "In " + place).append(" sind es gerade ").append(t)
                    .append(" Grad, ").append(describe(n.code, true)).append('.');
            if (!Double.isNaN(n.max) && !Double.isNaN(n.min)) {
                s.append(" Heute ").append(Math.round(n.min)).append(" bis ").append(Math.round(n.max)).append(" Grad.");
            }
        } else {
            s.append(place == null || place.isEmpty() ? "It's " : "In " + place + " it's ").append(t).append(" degrees, ")
                    .append(describe(n.code, false)).append('.');
            if (!Double.isNaN(n.max) && !Double.isNaN(n.min)) {
                s.append(" Today ").append(Math.round(n.min)).append(" to ").append(Math.round(n.max)).append(" degrees.");
            }
        }
        return s.toString();
    }
}
