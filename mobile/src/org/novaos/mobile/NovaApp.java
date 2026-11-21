package org.novaos.mobile;

import android.app.Application;

/** NovaOS as a whole: switches on the protection of the Nova apps (AppLock). */
public class NovaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLock.install(this);
    }
}
