package org.novaos.mobile;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;

/**
 * Asks for the phone's screen lock - PIN, pattern, password or fingerprint/face - with
 * Android's own dialog (BiometricPrompt, older Androids: the lock screen confirmation).
 * NovaOS only learns "confirmed" or "cancelled". Result: RESULT_OK or RESULT_CANCELED.
 */
public class ConfirmActivity extends Activity {
    static final String EXTRA_REASON = "reason", EXTRA_GATE = "gate";
    private static final int REQUEST = 1;
    private boolean done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;  // recreated while Android's dialog is open
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || !km.isDeviceSecure()) {
            finish(true);
            return;
        }
        String title = getString(R.string.lock_confirm_title);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        if (reason == null) reason = "";
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this).setTitle(title).setSubtitle(reason);
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                //noinspection deprecation
                b.setDeviceCredentialAllowed(true);
            }
            b.build().authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    finish(true);
                }

                @Override
                public void onAuthenticationError(int code, CharSequence message) {
                    finish(false);
                }
            });
        } else {
            //noinspection deprecation
            Intent i = km.createConfirmDeviceCredentialIntent(title, reason);
            if (i == null) finish(true);
            else startActivityForResult(i, REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == REQUEST) finish(result == RESULT_OK);
    }

    private void finish(boolean ok) {
        if (done) return;
        done = true;
        if (getIntent().getBooleanExtra(EXTRA_GATE, false)) AppLock.answered(ok);
        else if (ok) AppLock.confirmedNow();
        setResult(ok ? RESULT_OK : RESULT_CANCELED);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        finish(false);
    }
}
