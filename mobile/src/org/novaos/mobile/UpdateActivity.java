package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Software update (in the NovaOS settings), designed like the system update of Samsung
 * phones: a picture at the top, "Download und Installation", what is new, a bar in two
 * parts (download | confirmation) and one big button - "Aktualisieren", "Pause" or
 * "Fortsetzen". The update itself runs in UpdateEngine, so turning the phone or leaving
 * the page does not stop it. Without an update the page looks like One UI: a dark glow,
 * "NovaOS 1.0" and "Deine Software ist auf dem neuesten Stand." (tap: LastUpdateActivity).
 */
public class UpdateActivity extends Activity implements UpdateEngine.Listener {
    static final String EXTRA_INSTALL = "install";  // "Aktualisieren" in the notification
    private static final int NAVY = 0xFF0E1430, CYAN = 0xFF5CC8FF;
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Handler ui = new Handler(Looper.getMainLooper());
    private UpdateEngine engine;
    private UpdateInfo update;
    private boolean checking;
    private int checkStatus = -1;  // after a check without update: Updates.OK (= up to date) or the error
    private UpdateArt art;
    private TextView title, subtitle, description, notes, hint, label1, label2, detail;
    private View bar1, bar2;
    private LinearLayout bars;
    private TextView button;
    // up to date (like One UI): dark page, glow, "NovaOS 1.0", "Deine Software ist auf dem neuesten Stand."
    private LinearLayout main;
    private FrameLayout current;
    private UpdateGlow glow;
    private TextView currentText, retry;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        engine = UpdateEngine.get(this);
        Updates.logInstalled(this);
        build();
        UpdateInfo known = Updates.pending(this);
        if (engine.running()) {
            update = engine.update();
        } else if (known != null) {
            update = known;
            if (state == null && getIntent().getBooleanExtra(EXTRA_INSTALL, false)) install();
        } else {
            check();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.addListener(this);
        updateChanged();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        engine.removeListener(this);
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (engine.running()) render();
            ui.postDelayed(this, 200);
        }
    };

    @Override
    public void updateChanged() {
        if (!isDestroyed()) render();
    }

    // ------------------------------------------------------------ layout (portrait: picture on top; landscape: on the left)

    private void build() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int statusBar = statusBarHeight();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        main = new LinearLayout(this);
        main.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout top = new FrameLayout(this);
        art = new UpdateArt(this);
        top.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageButton back = round(R.drawable.ic_back, R.string.back);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        bl.setMargins(Ui.dp(this, 14), statusBar + Ui.dp(this, 10), 0, 0);
        top.addView(back, bl);
        ImageButton more = round(R.drawable.ic_more, R.string.more_options);
        more.setOnClickListener(this::menu);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        ml.gravity = Gravity.END;
        ml.setMargins(0, statusBar + Ui.dp(this, 10), Ui.dp(this, 14), 0);
        top.addView(more, ml);
        int height = getResources().getDisplayMetrics().heightPixels;
        main.addView(top, landscape ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (height * 0.40f)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        main.addView(right, landscape ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.dp(this, 24));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 26);
        text.setPadding(side, landscape ? statusBar + Ui.dp(this, 14) : Ui.dp(this, 22), side, Ui.dp(this, 12));
        title = Ui.text(this, "", 27, CYAN, true);
        title.setTypeface(Fonts.bold(this));
        text.addView(title);
        subtitle = Ui.text(this, "", 14, 0xB3FFFFFF, false);
        subtitle.setPadding(0, Ui.dp(this, 2), 0, 0);
        text.addView(subtitle);
        description = paragraph(text, 16, Color.WHITE, 12);
        notes = paragraph(text, 16, Color.WHITE, 18);
        hint = paragraph(text, 15, 0xCCFFFFFF, 18);
        sv.addView(text);
        right.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // bottom: the bar in two parts and the big button
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 14));
        bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tracks = new LinearLayout(this);
        bar1 = new View(this);
        bar2 = new View(this);
        tracks.addView(track(bar1), new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1));
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1);
        t2.leftMargin = Ui.dp(this, 10);
        tracks.addView(track(bar2), t2);
        bars.addView(tracks);
        LinearLayout labels = new LinearLayout(this);
        label1 = Ui.text(this, "", 16, Color.WHITE, true);
        label1.setGravity(Gravity.CENTER);
        label2 = Ui.text(this, "", 16, 0x99FFFFFF, true);
        label2.setGravity(Gravity.CENTER);
        labels.addView(label1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(label2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.setPadding(0, Ui.dp(this, 8), 0, 0);
        bars.addView(labels);
        detail = Ui.text(this, "", 13, 0x99FFFFFF, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, Ui.dp(this, 4), 0, 0);
        bars.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottom.addView(bars);
        button = Ui.text(this, "", 18, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFF6F5BD6, 0xFF5B6FE0});
        bg.setCornerRadius(Ui.dp(this, 30));
        button.setBackground(bg);
        button.setOnClickListener(v -> pressed());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        bp.topMargin = Ui.dp(this, 14);
        int inset = Ui.dp(this, landscape ? 20 : 34);
        bp.leftMargin = inset;
        bp.rightMargin = inset;
        bottom.addView(button, bp);
        right.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildCurrent(root, statusBar);
        // keep text and button clear of the navigation bar (below in portrait, at the side in landscape)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            right.setPadding(0, 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            main.setPadding(insets.getSystemWindowInsetLeft(), 0, 0, 0);
            current.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
    }

    /** The page when there is no update: like One UI - only the glow, the version and one sentence. */
    private void buildCurrent(FrameLayout root, int statusBar) {
        current = new FrameLayout(this);
        glow = new UpdateGlow(this);
        current.addView(glow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(this, 28);
        middle.setPadding(side, 0, side, 0);
        TextView name = Ui.text(this, UpdateFacts.title(), 44, Color.WHITE, true);
        name.setTypeface(Fonts.bold(this));
        name.setGravity(Gravity.CENTER);
        middle.addView(name);
        currentText = Ui.text(this, "", 17, 0xFFB9B9C0, false);
        currentText.setGravity(Gravity.CENTER);
        currentText.setPadding(0, Ui.dp(this, 4), 0, 0);
        middle.addView(currentText);
        retry = Ui.text(this, getString(R.string.update_retry), 16, Color.WHITE, true);
        retry.setGravity(Gravity.CENTER);
        GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(Ui.dp(this, 22));
        rb.setStroke(Ui.dp(this, 1), 0x66FFFFFF);
        retry.setBackground(rb);
        retry.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        retry.setOnClickListener(v -> {
            if (!checking) check();
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 22);
        middle.addView(retry, rl);
        // the version opens "Letztes erfolgreiches Update"
        name.setOnClickListener(v -> lastUpdate());
        currentText.setOnClickListener(v -> lastUpdate());
        FrameLayout.LayoutParams mid = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mid.gravity = Gravity.CENTER;
        current.addView(middle, mid);
        // top: back and ⋮, without circles (on the dark page)
        ImageButton back = plain(R.drawable.ic_back, R.string.back);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        bl.setMargins(Ui.dp(this, 10), statusBar + Ui.dp(this, 8), 0, 0);
        current.addView(back, bl);
        ImageButton more = plain(R.drawable.ic_more, R.string.more_options);
        more.setOnClickListener(this::menu);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        ml.gravity = Gravity.END;
        ml.setMargins(0, statusBar + Ui.dp(this, 8), Ui.dp(this, 10), 0);
        current.addView(more, ml);
        current.setVisibility(View.GONE);
        root.addView(current, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private ImageButton plain(int icon, int label) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 14);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        return b;
    }

    private ImageButton round(int icon, int label) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xCC1B2549);
        b.setBackground(bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 12);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        return b;
    }

    private TextView paragraph(LinearLayout parent, float sp, int color, int topDp) {
        TextView t = Ui.text(this, "", sp, color, false);
        t.setLineSpacing(0, 1.18f);
        t.setPadding(0, Ui.dp(this, topDp), 0, 0);
        parent.addView(t);
        return t;
    }

    private FrameLayout track(View fill) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(Ui.dp(this, 3));
        tb.setColor(0x33FFFFFF);
        f.setBackground(tb);
        GradientDrawable fb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFF3AA8F5, 0xFF5CC8FF});
        fb.setCornerRadius(Ui.dp(this, 3));
        fill.setBackground(fb);
        fill.setPivotX(0);
        fill.setScaleX(0);
        f.addView(fill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    // ------------------------------------------------------------ what the page shows

    private void render() {
        boolean running = engine.running(), paused = engine.paused();
        art.setMoving(running && !paused);
        String version = installedLine();
        String generic = getString(R.string.update_generic);
        bars.setVisibility(running ? View.VISIBLE : View.GONE);
        button.setEnabled(true);
        button.setAlpha(1f);
        if (running) {
            current.setVisibility(View.GONE);
            main.setVisibility(View.VISIBLE);
            glow.setBusy(false);
            UpdateInfo u = engine.update();
            title.setText(paused ? R.string.update_paused_title : R.string.update_running_title);
            subtitle.setText(getString(R.string.update_version, u.label(), UpdateInfo.size(u.size, german)));
            description.setText(paused ? R.string.update_paused_text : R.string.update_running_text);
            notes.setText(notesText(u));
            hint.setText(generic);
            int stage = engine.stage();
            float d = engine.download(), k = engine.confirmation();
            bar1.setScaleX(d);
            bar2.setScaleX(k);
            label1.setText(stage == UpdateEngine.DOWNLOAD ? (int) (d * 100) + " %" : "✓ " + getString(R.string.update_part_download));
            label1.setTextColor(stage == UpdateEngine.DOWNLOAD ? Color.WHITE : 0x99FFFFFF);
            label2.setText(stage == UpdateEngine.DOWNLOAD ? getString(R.string.update_part_confirm)
                    : getString(R.string.update_part_confirm) + " " + (int) (k * 100) + " %");
            label2.setTextColor(stage == UpdateEngine.DOWNLOAD ? 0x99FFFFFF : Color.WHITE);
            detail.setText(detailLine(stage));
            if (stage == UpdateEngine.INSTALL) {
                button.setText(R.string.update_button_installing);
                button.setEnabled(false);
                button.setAlpha(0.5f);
            } else {
                button.setText(paused ? R.string.update_button_resume : R.string.update_button_pause);
            }
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (update == null) {
            // looking for updates, up to date, or no connection: the One UI page
            current.setVisibility(View.VISIBLE);
            main.setVisibility(View.GONE);
            glow.setBusy(checking);
            boolean failed = !checking && checkStatus != -1 && checkStatus != Updates.OK;
            currentText.setText(checking ? getString(R.string.update_checking)
                    : !failed ? getString(R.string.update_uptodate)
                    : getString(checkStatus == Updates.NOT_PUBLIC ? R.string.update_not_public
                    : checkStatus == Updates.INVALID ? R.string.update_invalid : R.string.update_unreachable));
            retry.setVisibility(failed ? View.VISIBLE : View.GONE);
            return;
        }
        current.setVisibility(View.GONE);
        main.setVisibility(View.VISIBLE);
        glow.setBusy(false);
        String error = engine.error();
        if (!error.isEmpty() && update != null) {
            title.setText(R.string.update_failed_title);
            subtitle.setText(getString(R.string.update_version, update.label(), UpdateInfo.size(update.size, german)));
            description.setText(error);
            notes.setText(notesText(update));
            hint.setText(version);
            button.setText(R.string.update_install);
        } else if (update != null) {
            boolean ready = Updates.readyApk(this, update) != null;
            title.setText(R.string.update_available_title);
            subtitle.setText(getString(R.string.update_version, update.label(), UpdateInfo.size(update.size, german)));
            description.setText(ready ? R.string.update_ready_text : R.string.update_available_text);
            notes.setText(notesText(update));
            hint.setText(version + "\n\n" + generic);
            button.setText(R.string.update_install);
            subtitle.setText(getString(R.string.update_version, update.label(), UpdateInfo.size(update.size, german)));
        }
    }

    /** "Installiert: NovaOS 0.1.1.64" + when it was installed and when NovaOS last looked for updates. */
    private String installedLine() {
        StringBuilder b = new StringBuilder(getString(R.string.update_current, Updates.displayName(this)));
        long at = Updates.installedAt(this), checked = Updates.lastChecked(this);
        if (at > 0) b.append('\n').append(getString(R.string.update_last_installed, UpdateNotes.when(this, at)));
        if (checked > 0) b.append('\n').append(getString(R.string.update_last_checked, UpdateNotes.when(this, checked)));
        return b.toString();
    }

    /** "• Neu …" lines of the update (or of the installed version when there is none). */
    private String notesText(UpdateInfo u) {
        List<String> lines = u != null ? u.notes(german) : UpdateInfo.whatsNew(UpdateNotes.raw(this, R.raw.whatsnew), german);
        if (lines.isEmpty()) return "";
        StringBuilder b = new StringBuilder(u != null ? getString(R.string.update_menu_coming, u.label())
                : getString(R.string.update_whatsnew_installed, Updates.displayName(this))).append('\n');
        for (String l : lines) b.append("\n•  ").append(l);
        return b.toString();
    }

    private String detailLine(int stage) {
        long ms = engine.left();
        String left = getString(R.string.update_left, ms / 60000, String.format(Locale.ROOT, "%02d", (ms / 1000) % 60));
        if (stage != UpdateEngine.DOWNLOAD) {
            int what = stage == UpdateEngine.VERIFY ? R.string.update_stage_verify : stage == UpdateEngine.PREPARE
                    ? R.string.update_stage_prepare : R.string.update_installing;
            return getString(what) + (engine.paused() ? "" : "  ·  " + left);
        }
        if (engine.preloaded()) return UpdateInfo.size(engine.size(), german) + "  ·  " + getString(R.string.update_preloaded) + "  ·  " + left;
        String speed = engine.speed() > 0 ? "  ·  " + UpdateInfo.size((long) engine.speed(), german) + "/s" : "";
        return getString(R.string.update_bytes, UpdateInfo.size(engine.bytes(), german), UpdateInfo.size(engine.size(), german))
                + speed + (engine.paused() ? "" : "  ·  " + left);
    }

    // ------------------------------------------------------------ the button

    private void pressed() {
        if (engine.running()) {
            if (engine.paused()) engine.resume();
            else engine.pause();
        } else if (update != null) {
            install();
        } else if (!checking) {
            check();
        }
        render();
    }

    private void install() {
        if (update == null || engine.running()) return;
        if (!Installer.allowedOrAsk(this)) {
            new AlertDialog.Builder(this).setMessage(R.string.update_allow).setPositiveButton(R.string.ok, null).show();
            return;
        }
        engine.start(update);
    }

    private void check() {
        checking = true;
        render();
        new Thread(() -> {
            UpdateInfo u = Updates.check(getApplicationContext());
            int st = Updates.status(getApplicationContext());
            runOnUiThread(() -> {
                checking = false;
                if (isDestroyed()) return;
                if (u != null && u.newerThan(Updates.installedCode(this))) {
                    update = u;
                } else {
                    update = null;
                    checkStatus = u != null ? Updates.OK : st;
                }
                render();
            });
        }).start();
    }

    // ------------------------------------------------------------ ⋮ : last update, what's new, version history, check again

    private void lastUpdate() {
        startActivity(new android.content.Intent(this, LastUpdateActivity.class));
    }

    private void menu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 4, 0, R.string.update_menu_last);
        m.getMenu().add(0, 1, 1, R.string.update_menu_whatsnew);
        m.getMenu().add(0, 2, 2, R.string.update_menu_history);
        m.getMenu().add(0, 3, 3, R.string.update_check);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 4) lastUpdate();
            else if (item.getItemId() == 1) UpdateNotes.whatsNew(this);
            else if (item.getItemId() == 2) UpdateNotes.history(this, R.string.update_menu_history);
            else if (!checking && !engine.running()) check();
            return true;
        });
        m.show();
    }
}
