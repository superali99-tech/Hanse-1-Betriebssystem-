package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * A Nova app in the Nova Store, like an app page in the Play Store: icon, "NovaOS", facts,
 * "Öffnen" (and "Aktualisieren" when the waiting NovaOS update changes it), "Neuerungen •"
 * and "Über diese App" (both open the details page).
 */
public class NovaAppDetailActivity extends Activity {
    static final String EXTRA_KEY = "key";
    private NovaApps.App app;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        app = NovaApps.get(String.valueOf(getIntent().getStringExtra(EXTRA_KEY)));
        if (app == null) {
            finish();
            return;
        }
        content = Page.setup(this, R.string.app_store, "");
        addMenu(this, () -> show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    /** ⋮ at the top right: "Neuerungen anzeigen" on/off (also used by the store pages). */
    static void addMenu(Activity a, Runnable changed) {
        ViewGroup header = (ViewGroup) a.findViewById(R.id.page_title).getParent().getParent();
        ImageButton more = new ImageButton(a);
        more.setImageResource(R.drawable.ic_more);
        more.setBackgroundResource(R.drawable.round_bg);
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(a, 11);
        more.setPadding(p, p, p, p);
        more.setContentDescription(a.getString(R.string.more_options));
        more.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(a, v);
            m.getMenu().add(0, 1, 0, R.string.news_show).setCheckable(true).setChecked(AppNews.enabled(a));
            m.setOnMenuItemClickListener(item -> {
                AppNews.setEnabled(a, !AppNews.enabled(a));
                changed.run();
                return true;
            });
            m.show();
        });
        header.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
    }

    private void show() {
        content.removeAllViews();
        // icon, name, NovaOS
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(this, 8), 0, 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(app.icon);
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.text(this, getString(app.name), 26, Color.WHITE, true));
        names.addView(Ui.text(this, "NovaOS", 16, 0xFFB9A8FF, true));
        names.addView(Ui.text(this, getString(R.string.store_nova_included, Updates.installedName(this)), 13, getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nl.leftMargin = Ui.dp(this, 16);
        head.addView(names, nl);
        content.addView(head);

        // facts (true ones: no ratings or downloads that NovaOS does not have)
        LinearLayout facts = new LinearLayout(this);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        facts.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        fact(facts, Updates.installedName(this), getString(R.string.store_detail_version), false);
        fact(facts, getString(R.string.nova_detail_free), getString(R.string.nova_detail_price), true);
        fact(facts, getString(R.string.nova_detail_none), getString(R.string.nova_detail_ads), true);
        fact(facts, getString(R.string.nova_detail_on_phone), getString(R.string.nova_detail_data), true);
        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        fs.addView(facts);
        content.addView(fs);

        // Öffnen / Aktualisieren
        UpdateInfo pending = Updates.pending(this);
        boolean update = pending != null && pending.apps.contains(app.key);
        LinearLayout buttons = new LinearLayout(this);
        Button open = button(R.string.store_open, !update, v -> startActivity(new Intent(this, app.activity)));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
        buttons.addView(open, ol);
        if (update) {
            ol.rightMargin = Ui.dp(this, 10);
            buttons.addView(button(R.string.store_update, true, v -> startActivity(new Intent(this, UpdateActivity.class)
                    .putExtra(UpdateActivity.EXTRA_INSTALL, true))), new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.dp(this, 14);
        content.addView(buttons, bl);

        // Neuerungen • (3 hours after the update, or when the next update changes this app)
        AppNews.Item news = AppNews.nova(this, app.key);
        if (news != null) content.addView(newsBlock(this, news, () -> details()));

        // Über diese App
        LinearLayout aboutHead = sectionWithArrow(this, getString(R.string.store_detail_about), false, () -> details());
        content.addView(aboutHead);
        TextView about = Ui.text(this, getString(app.text), 15, 0xE6FFFFFF, false);
        about.setMaxLines(3);
        about.setEllipsize(TextUtils.TruncateAt.END);
        about.setOnClickListener(v -> details());
        content.addView(about);
        TextView chip = Ui.text(this, getString(R.string.store_nova_apps), 13, Color.WHITE, false);
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(Ui.dp(this, 16));
        cb.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
        chip.setBackground(cb);
        chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(this, 14);
        content.addView(chip, cl);
    }

    private void details() {
        startActivity(new Intent(this, NewsActivity.class).putExtra(NewsActivity.EXTRA_NOVA, app.key));
    }

    // ------------------------------------------------------------ shared pieces (also used by the F-Droid details page)

    /** "Neuerungen •  →", "Zuletzt aktualisiert: …" and the first lines. */
    static View newsBlock(Activity a, AppNews.Item news, Runnable more) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sectionWithArrow(a, a.getString(R.string.news_title), true, more));
        String when = news.upcoming ? a.getString(R.string.news_upcoming, news.version)
                : a.getString(R.string.news_updated, news.date > 0 ? DateUtils.formatDateTime(a, news.date,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_NUMERIC_DATE) : news.version);
        box.addView(Ui.text(a, when, 13, a.getColor(R.color.text_muted), false));
        StringBuilder b = new StringBuilder();
        for (String l : news.lines) b.append(b.length() == 0 ? "" : "\n").append("•  ").append(l);
        TextView t = Ui.text(a, b.toString(), 15, 0xE6FFFFFF, false);
        t.setLineSpacing(0, 1.15f);
        t.setMaxLines(4);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(a, 8), 0, 0);
        t.setOnClickListener(v -> more.run());
        box.addView(t);
        return box;
    }

    /** Title (with the blue dot) and the round → on the right. */
    static LinearLayout sectionWithArrow(Activity a, String title, boolean dot, Runnable click) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(a, 20), 0, Ui.dp(a, 4));
        TextView t = Ui.text(a, title, 19, Color.WHITE, true);
        row.addView(t);
        if (dot) {
            View d = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF8AB4F8);
            d.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(a, 9), Ui.dp(a, 9));
            dl.leftMargin = Ui.dp(a, 10);
            row.addView(d, dl);
        }
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        TextView arrow = Ui.text(a, "→", 20, Color.WHITE, true);
        arrow.setGravity(Gravity.CENTER);
        GradientDrawable ab = new GradientDrawable();
        ab.setShape(GradientDrawable.OVAL);
        ab.setColor(0x26FFFFFF);
        arrow.setBackground(ab);
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private void fact(LinearLayout row, String value, String label, boolean divider) {
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(0x33FFFFFF);
            row.addView(line, new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 30)));
        }
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        cell.addView(Ui.text(this, value, 15, Color.WHITE, true));
        cell.addView(Ui.text(this, label, 12, getColor(R.color.text_muted), false));
        row.addView(cell);
    }

    private Button button(int text, boolean accent, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        if (accent) {
            b.setBackgroundResource(R.drawable.accent_card_bg);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 23));
            bg.setStroke(Ui.dp(this, 1), 0x88FFFFFF);
            b.setBackground(bg);
        }
        b.setOnClickListener(click);
        return b;
    }
}
