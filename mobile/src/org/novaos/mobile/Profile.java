package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** The NovaOS profile of the setup: name, account (e-mail), age, whether the setup is done. */
final class Profile {
    private Profile() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    static boolean setupDone(Context c) {
        return prefs(c).getBoolean("setup_done", false);
    }

    static void setSetupDone(Context c, boolean done) {
        prefs(c).edit().putBoolean("setup_done", done).apply();
    }

    static String name(Context c) {
        return prefs(c).getString("name", "");
    }

    /** First name for the greeting ("Guten Abend, Lisa"). */
    static String firstName(Context c) {
        String n = name(c).trim();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }

    static String email(Context c) {
        return prefs(c).getString("email", "");
    }

    /** google, microsoft, email or "" (no account). */
    static String provider(Context c) {
        return prefs(c).getString("provider", "");
    }

    static boolean verified(Context c) {
        return prefs(c).getBoolean("verified", false);
    }

    static void setAccount(Context c, String name, String email, String provider, boolean verified) {
        SharedPreferences.Editor e = prefs(c).edit().putString("email", email).putString("provider", provider).putBoolean("verified", verified);
        if (name != null && !name.trim().isEmpty()) e.putString("name", name.trim());
        e.apply();
    }

    static void setName(Context c, String name) {
        prefs(c).edit().putString("name", name.trim()).apply();
    }

    /** Age in years (0 = not given). */
    static int age(Context c) {
        return prefs(c).getInt("age", 0);
    }

    static void setAge(Context c, int age) {
        prefs(c).edit().putInt("age", age).apply();
    }

    static boolean child(Context c) {
        int a = age(c);
        return a > 0 && a < 16;
    }

    /** Sign out of NovaOS: name, account and age are removed; the setup starts again. */
    static void signOut(Context c) {
        prefs(c).edit().remove("name").remove("email").remove("provider").remove("verified").remove("age")
                .putBoolean("setup_done", false).apply();
    }
}
