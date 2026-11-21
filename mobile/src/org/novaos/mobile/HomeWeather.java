package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Locale;

/**
 * The weather widget of the home screen: Open-Meteo for where the phone is (or the city Nova
 * was last asked about), the place name from Photon. Kept for 30 minutes.
 */
final class HomeWeather {
    static final long FRESH_MS = 30 * 60_000;

    static final class Data {
        double temp, max, min;
        int code;
        String place = "";
        long time;
    }

    private HomeWeather() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("home_weather", Context.MODE_PRIVATE);
    }

    static Data cached(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("time")) return null;
        Data d = new Data();
        d.temp = p.getFloat("temp", 0);
        d.max = p.getFloat("max", Float.NaN);
        d.min = p.getFloat("min", Float.NaN);
        d.code = p.getInt("code", 0);
        d.place = p.getString("place", "");
        d.time = p.getLong("time", 0);
        return d;
    }

    static boolean due(Context c) {
        Data d = cached(c);
        return d == null || System.currentTimeMillis() - d.time > FRESH_MS || System.currentTimeMillis() < d.time;
    }

    /** Fetches the weather (not on the main thread); null without position and city, or offline. */
    static Data fetch(Context c) {
        boolean german = Locale.getDefault().getLanguage().equals("de");
        try {
            double lat, lon;
            String place = "";
            Fixes.Fix f = Where.best(c);
            if (f != null) {
                lat = f.lat;
                lon = f.lon;
                try {
                    List<Places.Place> found = Places.parse(Download.text(Places.reverseUrl(new Geo.Point(lat, lon), german)));
                    if (!found.isEmpty()) place = town(found.get(0));
                } catch (Exception ignored) {
                    // no name - the weather still counts
                }
            } else {
                String city = c.getSharedPreferences("nova", Context.MODE_PRIVATE).getString("city", "");
                if (city.isEmpty()) return null;
                Weather.Place p = Weather.place(Download.text(Weather.geocodeUrl(city, german)));
                if (p == null) return null;
                lat = p.lat;
                lon = p.lon;
                place = p.name;
            }
            Weather.Now now = Weather.now(Download.text(Weather.forecastUrl(lat, lon)));
            if (now == null) return null;
            Data d = new Data();
            d.temp = now.temp;
            d.max = now.max;
            d.min = now.min;
            d.code = now.code;
            d.place = place;
            d.time = System.currentTimeMillis();
            prefs(c).edit().putFloat("temp", (float) d.temp).putFloat("max", (float) d.max).putFloat("min", (float) d.min)
                    .putInt("code", d.code).putString("place", d.place).putLong("time", d.time).apply();
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** "21033 Mölln" -> "Mölln"; a place without town: its name. */
    static String town(Places.Place p) {
        String t = p.city.replaceFirst("^\\d+\\s*", "").trim();
        return t.isEmpty() ? p.name : t;
    }
}
