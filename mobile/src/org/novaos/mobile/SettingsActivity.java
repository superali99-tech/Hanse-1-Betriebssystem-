package org.novaos.mobile;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** NovaOS settings: use as home screen, reset favorites, Android settings, about. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // keep "?"
        }
        LinearLayout content = Page.setup(this, R.string.settings_title,
                getString(R.string.about_version, version, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE));
        String who = Profile.email(this);
        content.addView(row(R.drawable.ic_account, R.string.settings_profile, who.isEmpty() ? getString(R.string.settings_profile_none)
                        : (Profile.name(this).isEmpty() ? "" : Profile.name(this) + " · ") + who,
                v -> startActivity(new android.content.Intent(this, SetupActivity.class))));
        UpdateInfo update = Updates.pending(this);
        content.addView(row(R.drawable.ic_refresh, R.string.update_title, update != null
                        ? getString(R.string.update_available) + " · NovaOS " + update.versionName
                        : getString(R.string.update_current, Updates.installedName(this)),
                v -> startActivity(new android.content.Intent(this, UpdateActivity.class))));
        content.addView(row(R.drawable.ic_novaos_restart, R.string.restart, getString(R.string.restart_sub),
                v -> startActivity(new android.content.Intent(this, BootActivity.class)
                        .putExtra(BootActivity.EXTRA_RESTART, true))));
        content.addView(row(R.drawable.ic_home_set, R.string.set_home, getString(R.string.set_home_sub),
                v -> Launch.settings(this, Settings.ACTION_HOME_SETTINGS)));
        content.addView(row(R.drawable.ic_refresh, R.string.reset_favorites, null, v -> {
            Favorites.clear(this);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        }));
        content.addView(row(R.drawable.ic_settings, R.string.android_settings, null,
                v -> Launch.settings(this, Settings.ACTION_SETTINGS)));
        content.addView(Ui.section(this, R.string.about_title));
        content.addView(paragraph(getString(R.string.about_text)));
        content.addView(paragraph(getString(R.string.about_privacy)));
    }

    private View row(int icon, int title, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        r.setPadding(p, p, p, p);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, getString(title), 16, getColor(R.color.text), true));
        if (sub != null) texts.addView(Ui.text(this, sub, 13, getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 16);
        r.addView(texts, tl);
        r.setClickable(true);
        r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        r.setLayoutParams(lp);
        return r;
    }

    private View paragraph(String s) {
        TextView t = Ui.text(this, s, 14, getColor(R.color.text), false);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 12));
        return t;
    }
}
