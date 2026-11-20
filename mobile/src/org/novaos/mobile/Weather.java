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
