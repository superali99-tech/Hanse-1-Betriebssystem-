package org.novaos.mobile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * NovaOS start animation: after an update, on the first start and for "Restart NovaOS".
 * Aurora fades in, the NovaOS star grows with a glowing ring, and the real start-up
 * work is shown (preparing the apps, cleaning up temporary files). After an update
 * it ends with "what's new".
 */
public class BootActivity extends Activity {
    static final String EXTRA_RESTART = "restart";
    private static final long MIN_MS = 4200;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private ProgressBar bar;
    private FrameLayout root;
    private long started;
    private boolean restart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this, true);
        restart = getIntent().getBooleanExtra(EXTRA_RESTART, false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        ImageView wall = new ImageView(this);
        wall.setImageResource(R.drawable.wallpaper_portrait);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setAlpha(0f);
        root.addView(wall, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View dim = new View(this);
        dim.setBackgroundColor(0x990B0E1F);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int logo = Ui.dp(this, 112);
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Ui.dp(this, 120));
        g.setColors(new int[] {0x997C5CFF, 0x403D7BFF, 0x000B0E1F});
        glow.setBackground(g);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(Ui.dp(this, 260), Ui.dp(this, 260), Gravity.CENTER);
        gl.bottomMargin = Ui.dp(this, 90);
        root.addView(glow, gl);

        ImageView star = new ImageView(this);
        star.setImageResource(R.drawable.nova_logo);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(logo, logo, Gravity.CENTER);
        sl.bottomMargin = Ui.dp(this, 90);
        root.addView(star, sl);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = Ui.text(this, "NovaOS", 34, Color.WHITE, true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        texts.addView(name);
        boolean first = Updates.firstStart(this);
        TextView title = Ui.text(this, getString(restart ? R.string.boot_restarting
                : first ? R.string.boot_welcome : R.string.boot_updating), 16, 0xFFC9D2FF, false);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        texts.addView(title);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 200), Ui.dp(this, 6));
        bl.topMargin = Ui.dp(this, 22);
        texts.addView(bar, bl);
        status = Ui.text(this, "", 13, 0xFFB8BEDD, false);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, Ui.dp(this, 10), 0, 0);
        texts.addView(status);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tl.topMargin = Ui.dp(this, 130);
        root.addView(texts, tl);

        // animation: aurora fades in, the star grows with an overshoot, the glow breathes
        wall.animate().alpha(1f).setDuration(1400).start();
        star.setScaleX(0.3f);
        star.setScaleY(0.3f);
        star.setAlpha(0f);
        star.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(250).setDuration(900)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        glow.setAlpha(0f);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.35f, 1f);
        pulse.setDuration(1300);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setStartDelay(500);
        pulse.start();
        name.setAlpha(0f);
        name.setTranslationY(Ui.dp(this, 16));
        name.animate().alpha(1f).translationY(0).setStartDelay(700).setDuration(700)
                .setInterpolator(new DecelerateInterpolator()).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setStartDelay(1000).setDuration(700).start();

        started = System.currentTimeMillis();
        new Thread(this::work, "novaos-start").start();
    }

    @Override
    public void onBackPressed() {
        // the start cannot be interrupted (it takes only a few seconds)
    }

    /** The real start-up work, shown step by step. */
    private void work() {
        List<App> apps = Apps.load(getApplicationContext());
        step(35, getString(R.string.boot_apps, apps.size()));
        Channels.get(this);
        pause(900);
        step(65, getString(R.string.boot_cleanup));
        // installed APKs go away; the one of a pending update (downloaded in the background) stays
        UpdateInfo pending = Updates.pending(this);
        File keep = pending != null ? Updates.apkFile(this, pending) : null;
        int removed = cleanCache(new File(getCacheDir(), "apk"), 0, keep)
                + cleanCache(new File(getCacheDir(), "thumbs"), 14L * 24 * 3600 * 1000, null);
        pause(900);
        step(100, getString(R.string.boot_ready));
        long rest = MIN_MS - (System.currentTimeMillis() - started);
        ui.postDelayed(this::finishStart, Math.max(300, rest));
        android.util.Log.i("NovaOS", "start: " + apps.size() + " apps, " + removed + " old cache files removed");
    }

    private void step(int pct, String text) {
        runOnUiThread(() -> {
            ObjectAnimator.ofInt(bar, "progress", bar.getProgress(), pct).setDuration(500).start();
            status.setText(text);
        });
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Deletes files older than maxAge (0 = all) except "keep" - downloaded APKs, old preview pictures. */
    private static int cleanCache(File dir, long maxAgeMs, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int n = 0;
        long now = System.currentTimeMillis();
        for (File f : files) {
            if (f.isFile() && !f.equals(keep) && (maxAgeMs == 0 || now - f.lastModified() > maxAgeMs) && f.delete()) n++;
        }
        return n;
    }

    private void finishStart() {
        if (isDestroyed()) return;
        boolean wasUpdate = !restart && !Updates.firstStart(this);
        Updates.markSeen(this);
        if (restart) {
            restartHome();
        } else if (wasUpdate) {
            whatsNew();
        } else {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    /** "What's new" in this version (res/raw/whatsnew.txt). */
    private void whatsNew() {
        boolean german = Locale.getDefault().getLanguage().equals("de");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xF2161A33);
        card.setCornerRadius(Ui.dp(this, 26));
        card.setStroke(Ui.dp(this, 1), 0x33FFFFFF);
        panel.setBackground(card);
        panel.setElevation(Ui.dp(this, 12));
        int p = Ui.dp(this, 22);
        panel.setPadding(p, p, p, p);
        panel.addView(Ui.text(this, getString(R.string.whatsnew_title, Updates.installedName(this)), 20, Color.WHITE, true));
        for (String line : UpdateInfo.whatsNew(raw(R.raw.whatsnew), german)) {
            TextView t = Ui.text(this, "•  " + line, 15, Color.WHITE, false);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            panel.addView(t);
        }
        Button go = new Button(this);
        go.setText(R.string.whatsnew_go);
        go.setAllCaps(false);
        go.setTextColor(Color.WHITE);
        go.setBackgroundResource(R.drawable.accent_card_bg);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(this, 20);
        panel.addView(go, gl);
        go.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pl.leftMargin = pl.rightMargin = Ui.dp(this, 22);
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.dp(this, 40));
        for (int i = 1; i < root.getChildCount(); i++) root.getChildAt(i).animate().alpha(0.15f).setDuration(400).start();
        root.addView(panel, pl);
        panel.animate().alpha(1f).translationY(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
    }

    /**
     * Restart: when NovaOS is the phone's home screen, its process is ended and Android
     * starts it again right away (a real restart of NovaOS - the phone itself keeps running).
     */
    private void restartHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, 0);
        boolean isHome = ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finishAffinity();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        if (isHome) ui.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 600);
    }

    private String raw(int res) {
        try (InputStream in = getResources().openRawResource(res)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
