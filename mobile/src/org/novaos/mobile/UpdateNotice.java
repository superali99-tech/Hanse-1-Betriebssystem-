package org.novaos.mobile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** The notification "System software update available" (like on Samsung phones). */
final class UpdateNotice {
    private static final String CHANNEL = "update";
    private static final int ID = 1;
    private static final String POST = "android.permission.POST_NOTIFICATIONS";

    private UpdateNotice() {}

    /** Shows the notification once per version and state (available / ready to install). */
    static void show(Context c, UpdateInfo u, boolean ready) {
        long key = u.versionCode * 10 + (ready ? 1 : 0);
        if (Updates.prefs(c).getLong("notified", 0) == key) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.notify_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(POST) != PackageManager.PERMISSION_GRANTED) return;
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, u.storeOnly ? StoreActivity.class : UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (u.storeOnly) {
            // only Nova apps change: "App-Updates im Nova Store", no software update
            StringBuilder names = new StringBuilder();
            for (String app : u.apps) {
                NovaApps.App a = NovaApps.get(app);
                if (a != null) names.append(names.length() == 0 ? "" : ", ").append(c.getString(a.name));
            }
            @SuppressWarnings("deprecation")
            Notification.Builder sb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            String st = c.getString(R.string.notify_store_text, names.toString());
            sb.setSmallIcon(R.drawable.ic_refresh).setColor(c.getColor(R.color.nova_violet))
                    .setContentTitle(c.getString(R.string.notify_store_title)).setContentText(st)
                    .setStyle(new Notification.BigTextStyle().bigText(st)).setContentIntent(open).setAutoCancel(true).setShowWhen(true);
            nm.notify(ID, sb.build());
            Updates.prefs(c).edit().putLong("notified", key).apply();
            return;
        }
        // button "Aktualisieren": opens the update page and starts the installation
        PendingIntent update = PendingIntent.getActivity(c, 1,
                new Intent(c, UpdateActivity.class).putExtra(UpdateActivity.EXTRA_INSTALL, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        String text = c.getString(ready ? R.string.notify_ready : R.string.notify_available, u.label());
        b.setSmallIcon(R.drawable.ic_refresh)
                .setColor(c.getColor(R.color.nova_violet))
                .setContentTitle(c.getString(R.string.home_update_banner))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_refresh),
                        c.getString(R.string.notify_action), update).build())
                .setAutoCancel(true)
                .setShowWhen(true);
        nm.notify(ID, b.build());
        Updates.prefs(c).edit().putLong("notified", key).apply();
    }

    static void cancel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID);
    }

    /** Android 13+: asks once whether NovaOS may show notifications (for the update notice). */
    static void askPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(POST) == PackageManager.PERMISSION_GRANTED) return;
        if (Updates.prefs(a).getBoolean("asked_notify", false)) return;
        Updates.prefs(a).edit().putBoolean("asked_notify", true).apply();
        a.requestPermissions(new String[] {POST}, 9);
    }
}
