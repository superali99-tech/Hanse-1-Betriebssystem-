package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps Nova Musik playing in the background (foreground service "media playback") and
 * shows the player notification; its buttons come back here.
 */
public class MusicService extends Service {
    static final String ACTION_PREFIX = "org.novaos.mobile.music.";
    private static MusicService running;
    private boolean foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        show(Music.get(this).notification(), true);  // within 5 seconds of startForegroundService
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (a != null && a.startsWith(ACTION_PREFIX)) Music.get(this).command(a.substring(ACTION_PREFIX.length()));
        refresh();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** The player changed: new notification; in the foreground only while playing. */
    static void update(Context c) {
        MusicService s = running;
        if (s != null) s.refresh();
    }

    private void refresh() {
        Music m = Music.get(this);
        if (m.current() == null) {
            stopForeground(true);
            foreground = false;
            stopSelf();
            return;
        }
        show(m.notification(), m.playing() || m.loading());
    }

    @SuppressWarnings("deprecation")
    private void show(Notification n, boolean playing) {
        if (playing) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Music.NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(Music.NOTIFICATION, n);
            foreground = true;
        } else {
            // paused: the notification stays (and can be swiped away), Android may end the service
            if (foreground) stopForeground(false);
            foreground = false;
            getSystemService(NotificationManager.class).notify(Music.NOTIFICATION, n);
        }
    }
}
