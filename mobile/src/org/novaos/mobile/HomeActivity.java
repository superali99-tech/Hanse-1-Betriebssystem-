package org.novaos.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.novaos.tv.KeyList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NovaOS Mobile home screen, like on Samsung phones (NovaOS 1.5): the weather widget between
 * the first apps, the other apps in the bottom rows, more pages to the right, the search bar
 * and the dock with phone, browser, camera and messages. Swipe up = all apps, swipe down =
 * notifications, hold an empty spot = NovaOS settings. Unread chats and mails, and waiting
 * updates, show as red numbers on the apps.
 */
public class HomeActivity extends Activity {
    /** The Nova apps on a new (or just updated to 1.5) home screen, in this order. */
    private static final Class<?>[] DEFAULTS = {ChatActivity.class, MailActivity.class, MapActivity.class, MusicActivity.class,
            NovaActivity.class, StoreActivity.class, CameraActivity.class, GalleryActivity.class,
            TvActivity.class, FilmeActivity.class, FamilyActivity.class, SettingsActivity.class};

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final HeyNova heyNova = new HeyNova(this);  // "Hey Nova" while the home screen is visible
    private final ChatSync.Listener chatListener = () -> {
        if (!isDestroyed()) loadApps();  // unread messages on Nova Chat
    };
    // the background update check (UpdateJob) found or downloaded an update: show it at once
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener updateListener = (p, key) -> {
        if ("last".equals(key) || "ready_sha".equals(key)) {
            runOnUiThread(() -> {
                if (!isDestroyed()) {
                    shown = null;
                    loadApps();
                }
            });
        }
    };
    private Pager pager;
    private Pager.Dots dots;
    private LinearLayout dock;
    private TextView updatePill;
    private List<App> apps = new ArrayList<>();
    private String shown;
    private int builtFor;
    private BroadcastReceiver packageReceiver;
    private GestureDetector gestures;
    private boolean bootShown, weatherLoading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        build();
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vy) < Math.abs(vx) * 1.5f || Math.abs(vy) < 900) return false;
                float dy = e2.getY() - e1.getY();
                if (dy < -Ui.dp(HomeActivity.this, 60)) {
                    openApps(false);
                    return true;
                }
                if (dy > Ui.dp(HomeActivity.this, 60)) {
                    expandNotifications();
                    return true;
                }
                return false;
            }
        });

        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadApps();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        UpdateJob.schedule(this);  // looks for updates in the background every few hours
        if (!MailStore.accounts(this).isEmpty()) MailSync.MailJob.schedule(this);  // new mails every 15 minutes
        if (Family.child(this)) FamilyService.start(this);  // Nova Family protects this child's phone
        Family.listen(this);  // parents: the children's requests arrive at once
        ChatSync.get(this).start();  // Nova Chat: messages and calls arrive while NovaOS runs
        ChatSync.get(this).addListener(chatListener);
        Updates.prefs(this).registerOnSharedPreferenceChangeListener(updateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Updates.justUpdated(this) && !bootShown) {
            bootShown = true;  // start animation after an update or on the first start
            startActivity(new Intent(this, BootActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } else if (Family.state(this).locked) {
            // child's phone locked by the parents: no home screen (the overlay normally covers it)
            startActivity(new Intent(this, LockActivity.class));
        } else if (!SetupActivity.done(this)) {
            startActivity(new Intent(this, SetupActivity.class));  // first start: set up NovaOS
        } else {
            UpdateNotice.askPermission(this);  // Android 13+: allow the update notification
            heyNova.start();
        }
        ChatSync.get(this).start();
        loadApps();
        loadWeather();
        if (MailSync.due(this)) {
            worker.execute(() -> {
                MailSync.checkNew(getApplicationContext());
                runOnUiThread(() -> {
                    if (!isDestroyed()) loadApps();
                });
            });
        }
        if (Updates.due(this)) {
            worker.execute(() -> {
                Updates.checkAndPrepare(getApplicationContext());
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        shown = null;
                        loadApps();
                    }
                });
            });
        }
    }

    @Override
    protected void onPause() {
        heyNova.stop();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        pager.setPage(0, true);  // home button pressed again: back to the first page
    }

    @Override
    public void onBackPressed() {
        pager.setPage(0, true);  // the home screen does not close
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        ChatSync.get(this).removeListener(chatListener);
        Updates.prefs(this).unregisterOnSharedPreferenceChangeListener(updateListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures.onTouchEvent(ev)) {
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------ the screen

    private void build() {
        FrameLayout root = new FrameLayout(this);
        ImageView wall = new ImageView(this);
        wall.setImageResource(R.drawable.wallpaper_portrait);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(wall, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.home_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            col.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        // a waiting software update: a small note at the top
        updatePill = Ui.text(this, "", 14, Color.WHITE, true);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xCC5B6FE0);
        pb.setCornerRadius(Ui.dp(this, 18));
        updatePill.setBackground(pb);
        updatePill.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        updatePill.setOnClickListener(v -> startActivity(new Intent(this, UpdateActivity.class)));
        updatePill.setVisibility(View.GONE);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ul.gravity = Gravity.CENTER_HORIZONTAL;
        ul.topMargin = Ui.dp(this, 10);
        col.addView(updatePill, ul);

        pager = new Pager(this);
        pager.setListener(page -> dots.set(pager.pages(), page));
        col.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int size = (r - l) * 10000 + (b - t);
            if (size != builtFor && r > l && b > t) pager.post(this::buildPages);
        });
        dots = new Pager.Dots(this);
        col.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 22)));

        // search bar: tap = search apps, microphone = Nova
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0xD93A3A3E);
        sb.setCornerRadius(Ui.dp(this, 28));
        search.setBackground(sb);
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_nova_nova);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        search.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        TextView hint = Ui.text(this, getString(R.string.search_apps), 15, 0xFFB9B9C0, false);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        hl.leftMargin = Ui.dp(this, 12);
        search.addView(hint, hl);
        ImageView lens = icon(R.drawable.ic_search, R.string.search_apps);
        lens.setOnClickListener(v -> openApps(true));
        search.addView(lens, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        ImageView mic = icon(R.drawable.ic_mic, R.string.nova_listen);
        mic.setOnClickListener(v -> startActivity(new Intent(this, NovaActivity.class).putExtra(NovaActivity.EXTRA_LISTEN, true)));
        search.addView(mic, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
        search.setOnClickListener(v -> openApps(true));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        int side = Math.max(Ui.dp(this, 16), (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 560)) / 2);
        sl.setMargins(side, Ui.dp(this, 4), side, Ui.dp(this, 10));
        col.addView(search, sl);

        // dock: phone, browser, camera, messages (icons only)
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.setMargins(side - Ui.dp(this, 6), 0, side - Ui.dp(this, 6), Ui.dp(this, 8));
        col.addView(dock, dl);
        setContentView(root);
    }

    private ImageView icon(int res, int label) {
        ImageView b = new ImageView(this);
        b.setImageResource(res);
        int p = Ui.dp(this, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        return b;
    }

    private void openApps(boolean search) {
        startActivity(new Intent(this, AppsActivity.class).putExtra(AppsActivity.EXTRA_SEARCH, search));
    }

    private void loadApps() {
        worker.execute(() -> {
            List<App> list = Apps.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                apps = list;
                addDefaults();
                StringBuilder sig = new StringBuilder(Favorites.raw(this));
                UpdateInfo u = Updates.pending(this);
                if (u != null) sig.append("|update").append(u.versionCode).append(Updates.readyApk(this, u) != null);
                sig.append("|chat").append(chatUnread()).append("|mail").append(mailUnread());
                for (App a : list) sig.append('|').append(a.key()).append(a.label);
                if (!sig.toString().equals(shown)) {
                    shown = sig.toString();
                    rebuild();
                }
            });
        });
    }

    /** Once (new phone, or the update to NovaOS 1.5): the Nova apps join the home screen. */
    private void addDefaults() {
        android.content.SharedPreferences p = getSharedPreferences("novaos_mobile", MODE_PRIVATE);
        if (p.getBoolean("home15", false) || apps.isEmpty()) return;
        List<String> favs = Favorites.get(this);
        for (Class<?> cls : DEFAULTS) {
            App a = Apps.own(apps, cls);
            if (a != null && !favs.contains(a.key())) favs.add(a.key());
        }
        Favorites.set(this, favs);
        p.edit().putBoolean("home15", true).apply();
    }

    private void loadWeather() {
        if (weatherLoading || !HomeWeather.due(this)) return;
        weatherLoading = true;
        worker.execute(() -> {
            HomeWeather.Data d = HomeWeather.fetch(getApplicationContext());
            runOnUiThread(() -> {
                weatherLoading = false;
                if (d != null && !isDestroyed()) {
                    builtFor = 0;
                    buildPages();
                }
            });
        });
    }

    private int chatUnread() {
        if (!ChatDb.loggedIn(this)) return 0;
        int n = 0;
        for (ChatDb.RoomRow r : ChatDb.get(this).rooms()) n += Math.max(0, r.unread) + (r.invite ? 1 : 0);
        return n;
    }

    /** Unread mails in the inboxes of all Nova Mail accounts. */
    private int mailUnread() {
        int n = 0;
        for (MailStore.Account a : MailStore.accounts(this)) n += MailStore.get(this).unreadCount(a.id, "INBOX");
        return n;
    }

    private void rebuild() {
        UpdateInfo update = Updates.pending(this);
        if (update != null) {
            boolean ready = Updates.readyApk(this, update) != null;
            updatePill.setText("⟳  " + getString(ready ? R.string.home_update_ready : R.string.home_update_sub, update.label()));
            updatePill.setVisibility(View.VISIBLE);
        } else {
            updatePill.setVisibility(View.GONE);
        }
        builtFor = 0;
        buildPages();
        buildDock();
    }

    /** The red numbers: unread chats and mails, Nova apps an update changes, a waiting update. */
    private int badge(App a) {
        String cls = a.component.getClassName();
        if (!a.packageName().equals(getPackageName())) return 0;
        if (cls.equals(ChatActivity.class.getName())) return chatUnread();
        if (cls.equals(MailActivity.class.getName())) return mailUnread();
        UpdateInfo u = Updates.pending(this);
        if (u == null) return 0;
        if (cls.equals(StoreActivity.class.getName())) return Math.max(1, u.apps.size());
        if (cls.equals(SettingsActivity.class.getName())) return -1;
        return 0;
    }

    /** Page 1: widget in the middle of the first two rows, an app on each side, the rest at the bottom; more pages to the right. */
    private void buildPages() {
        int w = pager.getWidth(), h = pager.getHeight();
        if (w == 0 || h == 0) return;
        builtFor = w * 10000 + h;
        float d = getResources().getDisplayMetrics().density;
        int cols = Greeting.columns((int) (w / d) - 8, 86, 4, 6);
        int rows = DrawerOrder.rows(h / d, 104);
        Set<String> installed = new HashSet<>();
        for (App a : apps) installed.add(a.key());
        List<App> home = new ArrayList<>();
        for (String k : KeyList.retain(Favorites.get(this), installed)) home.add(Apps.find(apps, k));
        int[] cells = DrawerOrder.homeCells(home.size(), rows, cols);
        int cw = w / cols, ch = h / rows, keep = pager.page();
        pager.removeAllViews();

        FrameLayout first = page();
        int m = Ui.dp(this, 8);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams((cols - 2) * cw - 2 * m, 2 * ch - 2 * m);
        wl.leftMargin = cw + m;
        wl.topMargin = m;
        first.addView(widget(), wl);
        List<App> rest = new ArrayList<>();
        for (int i = 0; i < home.size(); i++) {
            if (cells[i] < 0) {
                rest.add(home.get(i));
                continue;
            }
            place(first, home.get(i), cells[i] % cols, cells[i] / cols, cw, ch);
        }
        pager.addView(first);
        int per = rows * cols;
        for (int pg = 0; pg < DrawerOrder.pages(rest.size(), per) && !rest.isEmpty(); pg++) {
            FrameLayout page = page();
            List<App> on = DrawerOrder.page(rest, pg, per);
            for (int i = 0; i < on.size(); i++) place(page, on.get(i), i % cols, i / cols, cw, ch);
            pager.addView(page);
        }
        pager.setPage(Math.min(keep, pager.pages() - 1), false);
        dots.set(pager.pages(), pager.page());
    }

    private FrameLayout page() {
        FrameLayout page = new FrameLayout(this);
        // hold an empty spot: NovaOS settings (wallpaper, home screen)
        page.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        return page;
    }

    private void place(FrameLayout page, App a, int col, int row, int cw, int ch) {
        FrameLayout box = new FrameLayout(this);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.gravity = Gravity.CENTER;
        box.addView(appView(a, true, badge(a)), cl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(cw, ch);
        bl.leftMargin = col * cw;
        bl.topMargin = row * ch;
        page.addView(box, bl);
    }

    /** The widget: the weather here (like Samsung's), or the clock while there is none. */
    private View widget() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xE6404C86, 0xE65A6AA6});
        g.setCornerRadius(Ui.dp(this, 26));
        card.setBackground(g);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        boolean german = Locale.getDefault().getLanguage().equals("de");
        HomeWeather.Data wd = HomeWeather.cached(this);
        if (wd != null) {
            card.addView(Ui.text(this, Math.round(wd.temp) + "°  " + Weather.symbol(wd.code), 22, Color.WHITE, true));
            String sky = Weather.describe(wd.code, german);
            if (!sky.isEmpty()) sky = Character.toUpperCase(sky.charAt(0)) + sky.substring(1);
            TextView what = Ui.text(this, sky, 18, Color.WHITE, false);
            what.setMaxLines(2);
            what.setPadding(0, Ui.dp(this, 4), 0, 0);
            card.addView(what);
            card.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            if (!Double.isNaN(wd.max) && !Double.isNaN(wd.min)) {
                card.addView(Ui.text(this, getString(R.string.home_weather_range, Math.round(wd.max), Math.round(wd.min)), 14, 0xE6FFFFFF, false));
            }
            if (!wd.place.isEmpty()) {
                TextView where = Ui.text(this, "📍 " + wd.place, 15, 0xE6FFFFFF, false);
                where.setSingleLine(true);
                where.setPadding(0, Ui.dp(this, 2), 0, 0);
                card.addView(where);
            }
            card.setContentDescription(Weather.sentence(wd.place, new Weather.Now(wd.temp, wd.code, 0, wd.max, wd.min), german));
        } else {
            TextClock clock = new TextClock(this);
            clock.setFormat12Hour("h:mm");
            clock.setFormat24Hour("HH:mm");
            clock.setTextColor(Color.WHITE);
            clock.setTextSize(40);
            clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            card.addView(clock);
            TextClock date = new TextClock(this);
            String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
            date.setFormat12Hour(pattern);
            date.setFormat24Hour(pattern);
            date.setTextColor(0xE6FFFFFF);
            date.setTextSize(15);
            card.addView(date);
            card.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            card.addView(Ui.text(this, getString(R.string.home_weather_wait), 13, 0xCCFFFFFF, false));
        }
        // tap: Nova tells the weather
        card.setOnClickListener(v -> startActivity(new Intent(this, NovaActivity.class)
                .putExtra(NovaActivity.EXTRA_QUERY, getString(R.string.nova_try_weather))));
        return card;
    }

    private View appView(App a, boolean label, int badge) {
        LinearLayout cell = Ui.appCell(this, a, label, badge);
        cell.setOnClickListener(v -> Launch.app(this, a, v));
        cell.setOnLongClickListener(v -> {
            AppMenu.show(this, a, () -> {
                shown = null;
                loadApps();
            });
            return true;
        });
        return cell;
    }

    /** Dock: phone, browser, camera, messages - the phone's default apps (Nova Kamera without one). */
    private void buildDock() {
        dock.removeAllViews();
        App camera = Apps.defaultFor(this, apps, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        App[] slots = {
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_DIAL)),
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_VIEW, Uri.parse("https://"))),
            camera != null ? camera : Apps.own(apps, CameraActivity.class),
            Apps.defaultFor(this, apps, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        };
        for (App a : slots) {
            if (a == null) {
                dock.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));  // phone without this app
                continue;
            }
            dock.addView(appView(a, false, 0), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    // ------------------------------------------------------------ system

    @SuppressLint("WrongConstant")
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception | LinkageError e) {
            // not allowed on this phone: nothing happens
        }
    }
}
