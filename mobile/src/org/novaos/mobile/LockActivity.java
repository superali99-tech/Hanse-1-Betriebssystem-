package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Nova Family's lock screen as an activity: on a child's phone without the permission
 * "display over other apps" (the overlay covers more) and on the home screen.
 */
public class LockActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String shown = "";
    private boolean torchOn;
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            show();
            ui.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(check);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(check);
        super.onPause();
    }

    private void show() {
        FamilyPolicy.State s = Family.state(this);
        if (!s.locked) {
            finish();
            return;
        }
        FamilyPolicy p = Family.policy(this);
        String key = s.reason + s.until + p.version;
        if (key.equals(shown)) return;
        shown = key;
        setContentView(LockScreen.build(this, s, p, new LockScreen.Actions() {
            @Override
            public void call(String number) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
            }

            @Override
            public void torch() {
                torchOn = !torchOn;
                try {
                    android.hardware.camera2.CameraManager cm = getSystemService(android.hardware.camera2.CameraManager.class);
                    for (String id : cm.getCameraIdList()) {
                        if (Boolean.TRUE.equals(cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                            cm.setTorchMode(id, torchOn);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    // below
                }
                Toast.makeText(LockActivity.this, R.string.no_torch, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void ask(String kind, int minutes) {
                Family.sendAsync(LockActivity.this, MiniJson.obj("type", "request", "kind", kind, "minutes", minutes));
                Toast.makeText(LockActivity.this, R.string.lock_asked, Toast.LENGTH_LONG).show();
            }

            @Override
            public void pin(String pin) {
                if (Family.pinOk(LockActivity.this, pin)) {
                    Family.prefs(LockActivity.this).edit().putLong("local_free", System.currentTimeMillis() + 30 * 60 * 1000L).apply();
                    Family.sendAsync(LockActivity.this, MiniJson.obj("type", "alert", "what", "pin_unlock"));
                    Toast.makeText(LockActivity.this, R.string.lock_pin_ok, Toast.LENGTH_LONG).show();
                    FamilyService.refresh(LockActivity.this);
                    finish();
                } else {
                    Toast.makeText(LockActivity.this, R.string.lock_pin_wrong, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void home() {
                finish();
            }
        }));
    }

    @Override
    public void onBackPressed() {
        // locked: back does not leave the lock screen
    }
}
