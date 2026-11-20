package org.novaos.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the phone starts: Nova Family protects a child's phone again at once. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Family.child(c)) FamilyService.start(c);
    }
}
