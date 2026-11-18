package org.novaos.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NovaOS TV start page: favorites, all apps, TV settings. Operated with the remote (D-pad). */
public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    private TextView network;
    private List<AppEntry> apps = new ArrayList<>();
    private String shown = null;
    private BroadcastReceiver packageReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        rows = findViewById(R.id.rows);
        network = findViewById(R.id.network);
        TextClock date = findViewById(R.id.date);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);

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
        watchNetwork();
        rebuild();  // settings row right away, apps follow
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        if (networkCallback != null) {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        worker.execute(() -> {
            List<AppEntry> list = AppRepository.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                apps = list;
                StringBuilder sig = new StringBuilder(Favorites.raw(this));
                for (AppEntry e : list) sig.append('|').append(e.key()).append(e.label);
                if (!sig.toString().equals(shown)) {
                    shown = sig.toString();
                    rebuild();
                }
            });
        });
    }

    /** Re-creates the rows; keeps the focus on the same tile if it still exists. */
    private void rebuild() {
        View focused = getCurrentFocus();
        Object focusTag = focused != null ? focused.getTag() : null;
        rows.removeAllViews();

        // Favorites (only installed apps)
        Set<String> installed = new HashSet<>();
        for (AppEntry e : apps) installed.add(e.key());
        List<String> favKeys = KeyList.retain(Favorites.get(this), installed);
        LinearLayout favRow = addRow(getString(R.string.row_favorites));
        if (favKeys.isEmpty()) {
            FrameLayout t = Tiles.info(this, R.string.fav_empty);
            t.setTag("fav:empty");
            favRow.addView(t);
        }
        for (String k : favKeys) {
            AppEntry e = AppRepository.find(apps, k);
            if (e != null) favRow.addView(appTile(e, "fav:"));
        }

        // All apps (first tile: search)
        LinearLayout appRow = addRow(apps.isEmpty() ? getString(R.string.row_apps)
                : getString(R.string.row_apps) + "  ·  " + apps.size());
        FrameLayout search = Tiles.action(this, R.drawable.ic_search, R.string.search_apps, true);
        search.setTag("act:search");
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        appRow.addView(search);
        for (AppEntry e : apps) appRow.addView(appTile(e, "app:"));

        // TV settings
        LinearLayout setRow = addRow(getString(R.string.row_settings));
        addSetting(setRow, R.drawable.ic_network, R.string.tile_network, Settings.ACTION_WIFI_SETTINGS);
        addSetting(setRow, R.drawable.ic_bluetooth, R.string.tile_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS);
        addSetting(setRow, R.drawable.ic_display, R.string.tile_display, Settings.ACTION_DISPLAY_SETTINGS);
        addSetting(setRow, R.drawable.ic_sound, R.string.tile_sound, Settings.ACTION_SOUND_SETTINGS);
        addSetting(setRow, R.drawable.ic_date, R.string.tile_date, Settings.ACTION_DATE_SETTINGS);
        addSetting(setRow, R.drawable.ic_storage, R.string.tile_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        addSetting(setRow, R.drawable.ic_settings, R.string.tile_all_settings, Settings.ACTION_SETTINGS);
        FrameLayout about = Tiles.action(this, R.drawable.ic_info, R.string.tile_about, false);
        about.setTag("act:about");
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        setRow.addView(about);

        lockRowEdges();
        View target = focusTag != null ? rows.findViewWithTag(focusTag) : null;
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    /** Rows of tiles (the LinearLayouts inside the HorizontalScrollViews). */
    private List<LinearLayout> tileRows() {
        List<LinearLayout> out = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View v = rows.getChildAt(i);
            if (v instanceof HorizontalScrollView) out.add((LinearLayout) ((HorizontalScrollView) v).getChildAt(0));
        }
        return out;
    }

    private View firstTile() {
        for (LinearLayout row : tileRows()) {
            for (int i = 0; i < row.getChildCount(); i++) {
                if (row.getChildAt(i).isFocusable()) return row.getChildAt(i);
            }
        }
        return null;
    }

    /**
     * Left/right stays inside a row (like on every TV launcher): without this,
     * "right" on the last tile of a short row jumped diagonally into the next
     * row (found in the Android test).
     */
    private void lockRowEdges() {
        for (LinearLayout row : tileRows()) {
            View first = null, last = null;
            for (int i = 0; i < row.getChildCount(); i++) {
                View t = row.getChildAt(i);
                if (!t.isFocusable()) continue;
                if (t.getId() == View.NO_ID) t.setId(View.generateViewId());
                if (first == null) first = t;
                last = t;
            }
            if (first != null) {
                first.setNextFocusLeftId(first.getId());
                last.setNextFocusRightId(last.getId());
            }
        }
    }

    private LinearLayout addRow(String title) {
        int side = getResources().getDimensionPixelSize(R.dimen.margin_side);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.row_title));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(side, Tiles.dp(this, 14), side, 0);
        rows.addView(t);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(side, Tiles.dp(this, 10), side, Tiles.dp(this, 10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.addView(hs);
        return row;
    }

    private FrameLayout appTile(AppEntry e, String prefix) {
        FrameLayout t = Tiles.app(this, e);
        t.setTag(prefix + e.key());
        t.setOnClickListener(v -> Actions.launch(this, e));
        t.setOnLongClickListener(v -> {
            AppMenu.show(this, e, this::favoritesChanged);
            return true;
        });
        return t;
    }

    private void addSetting(LinearLayout row, int icon, int label, String action) {
        FrameLayout t = Tiles.action(this, icon, label, false);
        t.setTag("set:" + action);
        t.setOnClickListener(v -> Actions.settings(this, action));
        row.addView(t);
    }

    private void favoritesChanged() {
        shown = null;
        loadApps();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            Object e = f != null ? f.getTag(R.id.tag_entry) : null;
            if (e instanceof AppEntry) {
                AppMenu.show(this, (AppEntry) e, this::favoritesChanged);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // ------------------------------------------------------------ network
    private void watchNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        showNetwork(cm.getNetworkCapabilities(cm.getActiveNetwork()));
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
                runOnUiThread(() -> showNetwork(caps));
            }

            @Override
            public void onLost(Network n) {
                runOnUiThread(() -> showNetwork(null));
            }
        };
        try {
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ex) {
            networkCallback = null;
        }
    }

    private void showNetwork(NetworkCapabilities caps) {
        int res;
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            res = R.string.net_none;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            res = R.string.net_wifi;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            res = R.string.net_ethernet;
        } else {
            res = R.string.net_other;
        }
        network.setText(res);
    }
}
