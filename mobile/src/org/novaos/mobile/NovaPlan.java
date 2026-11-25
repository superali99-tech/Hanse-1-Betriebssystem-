package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** What is stored for Nova AI on this phone: the chosen model, Nova Pro 5 (free while testing) and today's questions. */
final class NovaPlan {
    private NovaPlan() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("nova_ai", Context.MODE_PRIVATE);
    }

    static NovaAi.Model chosen(Context c) {
        return NovaAi.Model.of(prefs(c).getString("model", "nova5"));
    }

    static void choose(Context c, NovaAi.Model m) {
        prefs(c).edit().putString("model", m.key).apply();
    }

    /** The model that answers now (Pro 5 only with the subscription). */
    static NovaAi.Model model(Context c) {
        return NovaAi.effective(chosen(c), pro(c));
    }

    static boolean pro(Context c) {
        return prefs(c).getBoolean("pro", false);
    }

    static String plan(Context c) {
        return prefs(c).getString("plan", "month");
    }

    static void setPro(Context c, boolean on, String plan) {
        prefs(c).edit().putBoolean("pro", on).putString("plan", plan).putLong("since", System.currentTimeMillis()).apply();
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
    }

    static int usedToday(Context c) {
        return NovaAi.usedToday(prefs(c).getString("day", ""), prefs(c).getInt("count", 0), today());
    }

    static void count(Context c) {
        prefs(c).edit().putString("day", today()).putInt("count", usedToday(c) + 1).apply();
    }
}
