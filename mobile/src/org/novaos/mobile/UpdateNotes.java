package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Was ist neu" and "Update-Verlauf" (also "Alle Änderungen"): dialogs with the points of each version. */
final class UpdateNotes {
    private UpdateNotes() {}

    private static boolean german() {
        return Locale.getDefault().getLanguage().equals("de");
    }

    static String raw(Activity a, int id) {
        try (java.io.InputStream in = a.getResources().openRawResource(id)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** "21. November 2026, 09:30" (today: "Heute, 09:30"). */
    static String when(Activity a, long ms) {
        String time = android.text.format.DateFormat.getTimeFormat(a).format(new java.util.Date(ms));
        if (android.text.format.DateUtils.isToday(ms)) return a.getString(R.string.update_today, time);
        return android.text.format.DateUtils.formatDateTime(a, ms, android.text.format.DateUtils.FORMAT_SHOW_DATE
                | android.text.format.DateUtils.FORMAT_SHOW_YEAR) + ", " + time;
    }

    static String bullets(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(b.length() == 0 ? "" : "\n").append("•  ").append(l);
        return b.toString();
    }

    /** Dialog with titled blocks of bullet points. */
    static void dialog(Activity a, int title, List<String[]> blocks) {
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 22);
        list.setPadding(p, Ui.dp(a, 8), p, p);
        for (String[] b : blocks) {
            if (!b[0].isEmpty()) {
                TextView h = Ui.text(a, b[0], 16, a.getColor(R.color.text), true);
                h.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
                list.addView(h);
            }
            TextView t = Ui.text(a, b[1], 15, a.getColor(R.color.text), false);
            t.setLineSpacing(0, 1.15f);
            list.addView(t);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(list);
        new AlertDialog.Builder(a).setTitle(title).setView(sv).setPositiveButton(R.string.ok, null).show();
    }

    /** What the waiting update brings and what the installed version brought. */
    static void whatsNew(Activity a) {
        boolean german = german();
        List<String[]> blocks = new ArrayList<>();
        UpdateInfo pending = Updates.pending(a);
        if (pending != null && !pending.notes(german).isEmpty()) {
            blocks.add(new String[] {a.getString(R.string.update_menu_coming, pending.versionName), bullets(pending.notes(german))});
        }
        blocks.add(new String[] {a.getString(R.string.update_menu_installed, Updates.installedName(a)),
                dated(a, Updates.installedAt(a), 0) + bullets(UpdateInfo.whatsNew(raw(a, R.raw.whatsnew), german))});
        dialog(a, R.string.update_menu_whatsnew, blocks);
    }

    /** All versions, the installed one first. */
    static void history(Activity a, int title) {
        boolean german = german();
        List<String[]> blocks = new ArrayList<>();
        java.util.Map<String, Long> log = Updates.installLog(a);
        blocks.add(new String[] {a.getString(R.string.update_menu_installed, Updates.installedName(a)),
                dated(a, Updates.installedAt(a), 0) + bullets(UpdateInfo.whatsNew(raw(a, R.raw.whatsnew), german))});
        for (History.Entry e : History.parse(raw(a, R.raw.history))) {
            if (e.version().equals(Updates.installedName(a))) continue;
            Long installed = log.get(e.version());
            blocks.add(new String[] {"NovaOS " + e.version(), dated(a, installed == null ? 0 : installed, e.released()) + bullets(e.lines(german))});
        }
        dialog(a, title, blocks);
    }

    /** "📅 Erschienen am …  ·  installiert am …" above the points of a version. */
    private static String dated(Activity a, long installed, long released) {
        StringBuilder b = new StringBuilder();
        if (released > 0) b.append(a.getString(R.string.update_released_on,
                android.text.format.DateUtils.formatDateTime(a, released, android.text.format.DateUtils.FORMAT_SHOW_DATE
                        | android.text.format.DateUtils.FORMAT_SHOW_YEAR)));
        if (installed > 0) b.append(b.length() > 0 ? "\n" : "").append(a.getString(R.string.update_installed_on, when(a, installed)));
        return b.length() == 0 ? "" : "📅 " + b.toString().replace("\n", "\n📅 ") + "\n\n";
    }
}
