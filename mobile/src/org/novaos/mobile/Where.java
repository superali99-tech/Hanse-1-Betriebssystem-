package org.novaos.mobile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import java.util.ArrayList;
import java.util.List;

/** The best position the phone knows (see Fixes: not simply the newest - that may be the mobile network's). */
final class Where {
    private Where() {}

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static Fixes.Fix fix(Location l) {
        return new Fixes.Fix(l.getProvider(), l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 0, l.getTime());
    }

    @SuppressWarnings("MissingPermission")
    static Fixes.Fix best(Context c) {
        if (!allowed(c)) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return null;
        List<Fixes.Fix> known = new ArrayList<>();
        for (String provider : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null) known.add(fix(l));
            } catch (RuntimeException ignored) {
                // this source is not allowed
            }
        }
        return Fixes.best(known);
    }
}
