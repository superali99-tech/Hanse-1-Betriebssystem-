package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finding places for Nova Karte with Photon (komoot, data from OpenStreetMap): "Hamburg
 * Hauptbahnhof", addresses, cafés - near the own position first. Android-independent, tested.
 */
public final class Places {
    public static final class Place {
        public String name = "", street = "", city = "", kind = "";
        public Geo.Point at;

        /** "Südsteg, 20099 Hamburg" */
        public String address() {
            StringBuilder b = new StringBuilder(street);
            if (!city.isEmpty()) b.append(b.length() > 0 ? ", " : "").append(city);
            return b.toString();
        }

        public String title() {
            return !name.isEmpty() ? name : !street.isEmpty() ? street : city;
        }
    }

    private Places() {}

    public static String searchUrl(String q, Geo.Point near, boolean german) {
        String u = "https://photon.komoot.io/api/?q=" + MusicCatalog.enc(q) + "&limit=8&lang=" + (german ? "de" : "en");
        if (near != null) u += String.format(java.util.Locale.ROOT, "&lat=%.5f&lon=%.5f", near.lat, near.lon);
        return u;
    }

    public static String reverseUrl(Geo.Point p, boolean german) {
        return String.format(java.util.Locale.ROOT, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f&lang=%s", p.lat, p.lon, german ? "de" : "en");
    }

    public static List<Place> parse(String json) {
        List<Place> out = new ArrayList<>();
        Object root;
        try {
            root = MiniJson.parse(json);
        } catch (RuntimeException e) {
            return out;
        }
        Object features = root instanceof Map ? ((Map<?, ?>) root).get("features") : null;
        if (!(features instanceof List)) return out;
        for (Object f : (List<?>) features) {
            if (!(f instanceof Map)) continue;
            Object props = ((Map<?, ?>) f).get("properties"), geom = ((Map<?, ?>) f).get("geometry");
            if (!(props instanceof Map) || !(geom instanceof Map)) continue;
            Object coords = ((Map<?, ?>) geom).get("coordinates");
            if (!(coords instanceof List) || ((List<?>) coords).size() < 2) continue;
            Object lon = ((List<?>) coords).get(0), lat = ((List<?>) coords).get(1);
            if (!(lon instanceof Number) || !(lat instanceof Number)) continue;
            Map<?, ?> p = (Map<?, ?>) props;
            Place place = new Place();
            place.at = new Geo.Point(((Number) lat).doubleValue(), ((Number) lon).doubleValue());
            place.name = str(p.get("name"));
            String street = str(p.get("street")), number = str(p.get("housenumber"));
            place.street = street + (street.isEmpty() || number.isEmpty() ? "" : " " + number);
            String postcode = str(p.get("postcode")), city = str(p.get("city"));
            if (city.isEmpty()) city = str(p.get("district"));
            place.city = (postcode.isEmpty() ? "" : postcode + " ") + city;
            place.kind = str(p.get("osm_value"));
            if (!place.title().isEmpty()) out.add(place);
        }
        return out;
    }

    private static String str(Object o) {
        return o instanceof String ? ((String) o).trim() : "";
    }
}
