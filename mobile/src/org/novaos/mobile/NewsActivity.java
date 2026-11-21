package org.novaos.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Details of an app (like "Details" in the Play Store): "Neue Funktionen •" with everything
 * that is new, and the whole "Über diese App". For Nova apps (EXTRA_NOVA) and Nova Store
 * apps (EXTRA_PKG).
 */
public class NewsActivity extends Activity {
    static final String EXTRA_NOVA = "nova", EXTRA_PKG = "pkg", EXTRA_LATEST = "latest", EXTRA_LATEST_NAME = "latest_name";
    private final boolean german = Locale.getDefault().getLanguage().equals("de");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.app_store, getString(R.string.news_details));
        String key = getIntent().getStringExtra(EXTRA_NOVA), pkg = getIntent().getStringExtra(EXTRA_PKG);
        AppNews.Item news;
        String about;
        if (key != null && NovaApps.get(key) != null) {
            NovaApps.App app = NovaApps.get(key);
            ((TextView) findViewById(R.id.page_title)).setText(app.name);
            header(content, app.icon);
            news = AppNews.nova(this, key);
            about = getString(app.text) + "\n\n" + getString(R.string.news_nova_about);
        } else {
            StoreApp app = pkg == null ? null : StoreCatalog.app(this, pkg);
            if (app == null) {
                finish();
                return;
            }
            ((TextView) findViewById(R.id.page_title)).setText(app.name);
            StoreMeta m = StoreCatalog.meta(this, pkg);
            news = AppNews.fdroid(this, app, getIntent().getLongExtra(EXTRA_LATEST, -1), String.valueOf(getIntent().getStringExtra(EXTRA_LATEST_NAME)));
            String desc = m == null ? "" : german && !m.descDe.isEmpty() ? m.descDe : m.descEn;
            about = desc.isEmpty() ? app.text(german) : desc;
        }
        if (news != null) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 2));
            row.addView(Ui.text(this, getString(R.string.news_features), 19, Color.WHITE, true));
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF8AB4F8);
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
            dl.leftMargin = Ui.dp(this, 10);
            row.addView(dot, dl);
            content.addView(row);
            content.addView(Ui.text(this, news.upcoming ? getString(R.string.news_upcoming, news.version)
                    : getString(R.string.news_version, news.version), 13, getColor(R.color.text_muted), false));
            StringBuilder b = new StringBuilder();
            for (String l : news.lines) b.append(b.length() == 0 ? "" : "\n").append("•  ").append(l);
            TextView t = Ui.text(this, b.toString(), 16, 0xE6FFFFFF, false);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            content.addView(t);
            if (!news.link.isEmpty()) {
                TextView link = Ui.text(this, "↗  " + getString(R.string.news_all_changes), 15, 0xFF9DB8FF, true);
                link.setPadding(0, Ui.dp(this, 12), 0, 0);
                link.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(news.link)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                });
                content.addView(link);
            }
            View line = new View(this);
            line.setBackgroundColor(0x33FFFFFF);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
            ll.topMargin = Ui.dp(this, 20);
            content.addView(line, ll);
        }
        TextView aboutTitle = Ui.text(this, getString(R.string.store_detail_about), 19, Color.WHITE, true);
        aboutTitle.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        content.addView(aboutTitle);
        TextView a = Ui.text(this, about, 16, 0xE6FFFFFF, false);
        a.setLineSpacing(0, 1.2f);
        content.addView(a);
    }

    private void header(LinearLayout content, int icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView t = Ui.text(this, "NovaOS", 15, 0xFFB9A8FF, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = Ui.dp(this, 12);
        row.addView(t, tl);
        content.addView(row);
    }
}
