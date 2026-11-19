package org.novaos.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Result of an installation; asks the user to confirm when Android requires it. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String pkg = intent.getStringExtra(Installer.EXTRA_PKG);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (RuntimeException e) {
                    Installer.report(pkg, false, e.getMessage());
                }
            }
            return;
        }
        Installer.report(pkg, status == PackageInstaller.STATUS_SUCCESS,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
