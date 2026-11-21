package org.novaos.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.TimeZone;

/**
 * "Letztes erfolgreiches Update", like on Samsung phones: the big picture of NovaOS 1.0, when
 * the installed version came, what NovaOS brings, "Alle Änderungen" and the update facts.
 */
public class LastUpdateActivity extends Activity {
    private final boolean german = Locale.getDefault().getLanguage().equals("de");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int statusBar = statusBarHeight();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UpdateGlow.BACKGROUND);
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        sv.addView(page);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // the picture: about 40 % of the screen (in landscape a bit more of the width)
        int height = getResources().getDisplayMetrics().heightPixels;
        page.addView(new ReleaseArt(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (height * (landscape ? 0.62f : 0.40f))));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 26);
        text.setPadding(side, Ui.dp(this, 24), side, Ui.dp(this, 28));
        page.addView(text);
        TextView title = Ui.text(this, getString(R.string.last_title), 27, Color.WHITE, true);
        title.setTypeface(Fonts.bold(this));
        text.addView(title);
        long at = Updates.installedAt(this);
        if (at <= 0) at = packageTime();
        TextView when = Ui.text(this, UpdateFacts.dateTime(at, TimeZone.getDefault(), german), 16, Color.WHITE, false);
        when.setPadding(0, Ui.dp(this, 4), 0, 0);
        text.addView(when);

        TextView heading = Ui.text(this, getString(R.string.last_heading), 16, 0xFFB4B4BA, true);
        heading.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 2));
        text.addView(heading);
        TextView about = Ui.text(this, getString(R.string.last_text, UpdateFacts.title()), 16, 0xFF9E9EA6, false);
        about.setLineSpacing(0, 1.2f);
        text.addView(about);

        // what this version brought (like "Was ist neu")
        java.util.List<String> news = UpdateInfo.whatsNew(UpdateNotes.raw(this, R.raw.whatsnew), german);
        if (!news.isEmpty()) {
            TextView newsHead = Ui.text(this, getString(R.string.last_news, Updates.displayName(this)), 16, 0xFFB4B4BA, true);
            newsHead.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 4));
            text.addView(newsHead);
            TextView list = Ui.text(this, UpdateNotes.bullets(news), 16, 0xFFD2D2D8, false);
            list.setLineSpacing(0, 1.25f);
            text.addView(list);
        }
        TextView all = Ui.text(this, getString(R.string.last_all), 16, 0xFFB4B4BA, true);
        all.setPaintFlags(all.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        all.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 14));
        all.setOnClickListener(v -> UpdateNotes.history(this, R.string.last_all));
        text.addView(all);
        View line = new View(this);
        line.setBackgroundColor(0xFF3A3A40);
        text.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));

        TextView infoTitle = Ui.text(this, getString(R.string.last_info), 20, Color.WHITE, true);
        infoTitle.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 6));
        text.addView(infoTitle);
        StringBuilder b = new StringBuilder();
        for (String l : UpdateFacts.info(Build.VERSION.RELEASE, Updates.installedName(this), apkSize(), Build.VERSION.SECURITY_PATCH, german)) {
            b.append(b.length() == 0 ? "" : "\n").append("•  ").append(l);
        }
        long checked = Updates.lastChecked(this);
        if (checked > 0) b.append("\n•  ").append(getString(R.string.update_last_checked, UpdateNotes.when(this, checked)));
        TextView info = Ui.text(this, b.toString(), 16, 0xFFD2D2D8, false);
        info.setLineSpacing(0, 1.25f);
        text.addView(info);

        // back: a round dark button that stays on top of the picture
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE6303036);
        back.setBackground(bg);
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 13);
        back.setPadding(p, p, p, p);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        bl.setMargins(Ui.dp(this, 14), statusBar + Ui.dp(this, 10), 0, 0);
        root.addView(back, bl);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            sv.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
    }

    /** When Android installed this NovaOS version (if NovaOS did not note it itself). */
    private long packageTime() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    /** The size of the installed NovaOS. */
    private long apkSize() {
        try {
            return new java.io.File(getPackageManager().getApplicationInfo(getPackageName(), 0).sourceDir).length();
        } catch (Exception e) {
            return 0;
        }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }
}
