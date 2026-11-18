package org.novaos.tv;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/** Version, device and privacy information; reset favorites. */
public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_about);
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // keep "?"
        }
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.about_version, version));
        ((TextView) findViewById(R.id.device)).setText(getString(R.string.about_device,
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE));
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.reset).setOnClickListener(v -> {
            Favorites.clear(this);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.back).requestFocus();
    }
}
