package org.novaos.mobile;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Nova Family as device administrator on a child's phone: NovaOS cannot simply be uninstalled
 * (first this protection must be switched off in the Android settings - and the parents get a
 * message when that happens).
 */
public class FamilyAdmin extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context c, Intent intent) {
        return c.getString(R.string.family_admin_warning);
    }

    @Override
    public void onDisabled(Context c, Intent intent) {
        if (Family.child(c)) Family.sendAsync(c, MiniJson.obj("type", "alert", "what", "admin_off"));
    }
}
