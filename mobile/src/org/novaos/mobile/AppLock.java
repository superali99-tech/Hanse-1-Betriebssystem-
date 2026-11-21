package org.novaos.mobile;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * Protects the Nova apps with personal data (Chat, Mail, Family, Notes, Files, Gallery,
 * settings and account) with the phone's own screen lock: PIN, pattern, password or
 * fingerprint, asked by Android itself (NovaOS never sees them). Without a screen lock
 * nothing is asked. Once confirmed, the apps stay open until the screen turns off or no
 * protected app was used for 5 minutes.
 */
final class AppLock {
    private static final Class<?>[] PROTECTED = {ChatActivity.class, RoomActivity.class, ChatContactsActivity.class,
            MailActivity.class, MailReadActivity.class, MailComposeActivity.class, MailContactsActivity.class,
            FamilyActivity.class, NotesActivity.class, NoteEditActivity.class, FilesActivity.class, GalleryActivity.class,
            ViewerActivity.class, SettingsActivity.class, AccountActivity.class};

    private static boolean locked = true;
    private static long lastUsed;
    private static boolean asking;
    private static WeakReference<Activity> gated = new WeakReference<>(null);

    private AppLock() {}

    static boolean enabled(Context c) {
        return Profile.prefs(c).getBoolean("app_lock", true);
    }

    static void setEnabled(Context c, boolean on) {
        Profile.prefs(c).edit().putBoolean("app_lock", on).apply();
    }

    /** Whether the phone has a screen lock (PIN, pattern, password). */
    static boolean secure(Context c) {
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    static boolean isProtected(Activity a) {
        for (Class<?> k : PROTECTED) if (k.isInstance(a)) return true;
        return false;
    }

    /** Called once by NovaApp: watches all screens of NovaOS and the screen turning off. */
    static void install(Application app) {
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                locked = true;
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                if (asking || a instanceof ConfirmActivity) return;
                if (!LockRule.needed(enabled(a), secure(a), isProtected(a), locked, System.currentTimeMillis(), lastUsed)) {
                    if (isProtected(a)) lastUsed = System.currentTimeMillis();
                    return;
                }
                asking = true;
                gated = new WeakReference<>(a);
                a.startActivity(new Intent(a, ConfirmActivity.class).putExtra(ConfirmActivity.EXTRA_GATE, true)
                        .putExtra(ConfirmActivity.EXTRA_REASON, a.getString(R.string.lock_reason_app)));
            }

            @Override
            public void onActivityPaused(Activity a) {
                if (isProtected(a) && !locked) lastUsed = System.currentTimeMillis();
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {}

            @Override
            public void onActivityStarted(Activity a) {}

            @Override
            public void onActivityStopped(Activity a) {}

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override
            public void onActivityDestroyed(Activity a) {}
        });
    }

    /** Result of the question when opening a protected app. */
    static void answered(boolean ok) {
        asking = false;
        if (ok) {
            locked = false;
            lastUsed = System.currentTimeMillis();
        } else {
            Activity a = gated.get();
            if (a != null && !a.isFinishing()) a.finish();  // without PIN/fingerprint the app closes
        }
        gated = new WeakReference<>(null);
    }

    /** After confirming for a change (sign out ...): the apps count as unlocked, too. */
    static void confirmedNow() {
        locked = false;
        lastUsed = System.currentTimeMillis();
    }
}
