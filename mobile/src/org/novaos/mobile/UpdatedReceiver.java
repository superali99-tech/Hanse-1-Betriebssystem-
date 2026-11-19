package org.novaos.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After a software update Android ends NovaOS and then sends MY_PACKAGE_REPLACED:
 * NovaOS starts again on its own, with the start animation and "what's new"
 * (HomeActivity shows it once per new version). Newer Android versions may not allow
 * this from the background - then it appears the next time the home screen is shown.
 */
public class UpdatedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        UpdateNotice.cancel(c);  // the update is installed
        UpdateJob.schedule(c);
        if (!Updates.justUpdated(c)) return;
        try {
            c.startActivity(new Intent(c, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) {
            // not allowed right now: the animation comes with the next home screen
        }
    }
}
