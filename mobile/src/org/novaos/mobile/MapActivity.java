package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nova Karte: map (OpenStreetMap), search for places and addresses, routes by car, bike or on
 * foot, and navigation like in Google Maps - Nova says "In 200 Metern rechts abbiegen auf …",
 * finds a new route when you take another way and says when you have arrived.
 */
public class MapActivity extends Activity implements MapView.Listener, LocationListener {
    static final String EXTRA_TO = "to", EXTRA_GO = "go";
    private static final int PERMISSION = 41;
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private final Handler ui = new Handler(Looper.getMainLooper());

    private MapView map;
    private EditText query;
    private LinearLayout searchCard, results, sheet, banner, navBottom;
    private ScrollView resultsScroll;
    private ImageButton locate;
    private LinearLayout fabs;
    private FrameLayout overlay;
    private Runnable afterLayout;
    private ManeuverView arrow;
    private TextView bannerDistance, bannerText, bannerThen, navTime, navInfo, muteButton;

    private Geo.Point me;
    private Geo.Point origin;  // without a position: the middle of the map before a place was shown
    private float myBearing = Float.NaN, myAccuracy;
    private boolean follow = true;
    private Places.Place target;
    private String mode = NavRoute.CAR;
    private NavRoute route;
    private Guidance guidance;
    private boolean navigating, simulating, muted, goWhenReady;
    private boolean startWhenLocated;  // "Starten" before the own position was known
    private double simAlong;
    private long lastReroute;
    private int searchToken;
    private Speaker speaker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        speaker = new Speaker(this);
        mode = prefs().getString("mode", NavRoute.CAR);
        build();
        Location last = lastKnown();
        if (last != null) {
            me = new Geo.Point(last.getLatitude(), last.getLongitude());
            map.show(me, 15);
            map.setPosition(me, Float.NaN, last.getAccuracy());
        } else {
            map.show(new Geo.Point(51.16, 10.45), 6);  // Germany
        }
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLocation();
    }

    @Override
    protected void onPause() {
        // while navigating the positions keep coming (the screen stays on)
        if (!navigating) stopLocation();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopLocation();
        ui.removeCallbacksAndMessages(null);
        speaker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (navigating) confirmStop();
        else if (route != null || target != null) clear();
        else if (resultsScroll.getVisibility() == View.VISIBLE) hideResults();
        else super.onBackPressed();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("map", MODE_PRIVATE);
    }

    /** Nova: "Navigiere nach Hamburg Hauptbahnhof"; other apps: geo:53.55,10.0?q=… */
    private void handle(Intent i) {
        if (i == null) return;
        String to = i.getStringExtra(EXTRA_TO);
        Uri geo = i.getData();
        if (to == null && geo != null && "geo".equals(geo.getScheme())) {
            String q = geo.getQueryParameter("q");
            if (q != null && !q.isEmpty()) {
                to = q;
            } else {
                String[] ll = geo.getSchemeSpecificPart().split("[,?]");
                try {
                    Places.Place p = new Places.Place();
                    p.at = new Geo.Point(Double.parseDouble(ll[0]), Double.parseDouble(ll[1]));
                    p.name = getString(R.string.map_marked);
                    showPlace(p);
                    return;
                } catch (RuntimeException ignored) {
                    // not a position
                }
            }
        }
        if (to == null || to.trim().isEmpty()) return;
        goWhenReady = i.getBooleanExtra(EXTRA_GO, false);
        query.setText(to);
        search(to, true);
    }

    // ------------------------------------------------------------ the screen

    private void build() {
        FrameLayout root = new FrameLayout(this);
        map = new MapView(this);
        map.setListener(this);
        root.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // everything on top of the map keeps clear of the navigation bar (below, or at the side in landscape)
        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        int status = statusBar(), side = Ui.dp(this, 12);

        // search at the top
        searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(card(0xF2FFFFFF, 28));
        bar.setElevation(Ui.dp(this, 6));
        ImageButton back = icon(R.drawable.ic_back, R.string.back, 0xFF3C4043);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        query = new EditText(this);
        query.setHint(R.string.map_search);
        query.setSingleLine(true);
        query.setBackground(null);
        query.setTextColor(0xFF202124);
        query.setHintTextColor(0xFF5F6368);
        query.setTextSize(16);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v, a, e) -> {
            search(query.getText().toString(), false);
            keyboard(false);
            return true;
        });
        query.setOnFocusChangeListener((v, focus) -> {
            if (focus && query.getText().length() == 0) showRecent();
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!query.hasFocus()) return;
                int token = ++searchToken;
                String q = s.toString();
                if (q.trim().length() < 3) {
                    if (q.isEmpty()) showRecent();
                    return;
                }
                ui.postDelayed(() -> {
                    if (token == searchToken) search(q, false);
                }, 700);  // after a short pause in typing (fair to the free search server)
            }
        });
        bar.addView(query, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        ImageButton clear = icon(R.drawable.ic_close, R.string.search_clear, 0xFF5F6368);
        clear.setOnClickListener(v -> {
            query.setText("");
            clear();
        });
        bar.addView(clear, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        searchCard.addView(bar);
        resultsScroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        resultsScroll.addView(results);
        resultsScroll.setBackground(card(0xFAFFFFFF, 20));
        resultsScroll.setElevation(Ui.dp(this, 6));
        resultsScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 8);
        searchCard.addView(resultsScroll, rl);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMargins(side, status + Ui.dp(this, 8), side, 0);
        overlay.addView(searchCard, sl);

        // navigation banner at the top (green, like in Google Maps)
        banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(card(0xFF0F7B3E, 24));
        banner.setElevation(Ui.dp(this, 8));
        banner.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        arrow = new ManeuverView(this);
        left.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        bannerDistance = Ui.text(this, "", 18, Color.WHITE, true);
        left.addView(bannerDistance);
        banner.addView(left, new LinearLayout.LayoutParams(Ui.dp(this, 86), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        bannerText = Ui.text(this, "", 21, Color.WHITE, true);
        texts.addView(bannerText);
        bannerThen = Ui.text(this, "", 14, 0xD9FFFFFF, false);
        bannerThen.setPadding(0, Ui.dp(this, 4), 0, 0);
        texts.addView(bannerThen);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 8);
        banner.addView(texts, tl);
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.setMargins(side, status + Ui.dp(this, 8), side, 0);
        overlay.addView(banner, bl);

        // buttons on the right: zoom and own position
        fabs = new LinearLayout(this);
        fabs.setOrientation(LinearLayout.VERTICAL);
        ImageButton plus = roundButton(R.drawable.ic_plus, R.string.map_zoom_in);
        plus.setOnClickListener(v -> map.zoomBy(1));
        ImageButton minus = roundButton(R.drawable.ic_minus, R.string.map_zoom_out);
        minus.setOnClickListener(v -> map.zoomBy(-1));
        locate = roundButton(R.drawable.ic_locate, R.string.map_my_position);
        locate.setOnClickListener(v -> centerOnMe());
        fabs.addView(plus, fabLp(0));
        fabs.addView(minus, fabLp(8));
        fabs.addView(locate, fabLp(16));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.gravity = Gravity.END | Gravity.BOTTOM;
        fl.setMargins(0, 0, side, Ui.dp(this, 16));
        overlay.addView(fabs, fl);

        // bottom card: place, route, arrival
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(card(0xFFFFFFFF, 26));
        sheet.setElevation(Ui.dp(this, 10));
        sheet.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        sheet.setVisibility(View.GONE);
        FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shl.gravity = Gravity.BOTTOM;
        overlay.addView(sheet, shl);

        // bottom bar while navigating: time left, distance, arrival, sound, stop
        navBottom = new LinearLayout(this);
        navBottom.setGravity(Gravity.CENTER_VERTICAL);
        navBottom.setBackground(card(0xFFFFFFFF, 26));
        navBottom.setElevation(Ui.dp(this, 10));
        navBottom.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        TextView stop = Ui.text(this, "✕", 20, Color.WHITE, true);
        stop.setGravity(Gravity.CENTER);
        stop.setBackground(card(0xFFD93025, 24));
        stop.setContentDescription(getString(R.string.map_stop));
        stop.setOnClickListener(v -> confirmStop());
        navBottom.addView(stop, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        navTime = Ui.text(this, "", 24, 0xFF188038, true);
        mid.addView(navTime);
        navInfo = Ui.text(this, "", 14, 0xFF5F6368, false);
        mid.addView(navInfo);
        navBottom.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        muteButton = Ui.text(this, "🔊", 22, Color.BLACK, false);
        muteButton.setGravity(Gravity.CENTER);
        muteButton.setBackground(card(0xFFE8EAED, 24));
        muteButton.setOnClickListener(v -> {
            muted = !muted;
            muteButton.setText(muted ? "🔇" : "🔊");
            if (muted) speaker.stop();
        });
        navBottom.addView(muteButton, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        navBottom.setVisibility(View.GONE);
        FrameLayout.LayoutParams nbl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nbl.gravity = Gravity.BOTTOM;
        overlay.addView(navBottom, nbl);
        // whenever a card changes: the free part of the map, then what waited for the layout
        overlay.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            updateFree();
            if (afterLayout != null) {
                Runnable run = afterLayout;
                afterLayout = null;
                ui.post(run);
            }
        });
        applyOrientation();
        setContentView(root);
    }

    private boolean wide() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Landscape: the cards narrow on the left (the map stays visible on the right), like Google Maps. */
    private void applyOrientation() {
        boolean side = wide();
        int width = side ? Math.min(Ui.dp(this, 400), getResources().getDisplayMetrics().widthPixels / 2) : ViewGroup.LayoutParams.MATCH_PARENT;
        int gap = Ui.dp(this, 12);
        for (View v : new View[]{searchCard, banner, sheet, navBottom}) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            lp.width = width;
            lp.gravity = (lp.gravity & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.START;
            if (v == sheet || v == navBottom) lp.setMargins(side ? gap : 0, 0, 0, side ? gap : 0);
            v.setLayoutParams(lp);
        }
    }

    /** The part of the map no card covers: places, routes and the own arrow are shown in its middle. */
    private void updateFree() {
        View bottomCard = navBottom.getVisibility() == View.VISIBLE ? navBottom : sheet.getVisibility() == View.VISIBLE ? sheet : null;
        View topCard = banner.getVisibility() == View.VISIBLE ? banner : searchCard;
        int right = overlay.getPaddingRight();  // navigation bar at the side
        if (wide()) {
            // cards on the left: the map between them and the buttons on the right
            if (fabs.getVisibility() == View.VISIBLE && fabs.getWidth() > 0) right = map.getWidth() - fabs.getLeft();
            map.setFree(bottomCard != null ? bottomCard.getRight() : 0, bottomCard != null ? statusBar() : topCard.getBottom(), right, 0);
        } else {
            map.setFree(0, topCard.getBottom(), right, bottomCard != null ? map.getHeight() - bottomCard.getTop() : 0);
        }
    }

    /** The route and the place itself (it can lie a bit off the road, the pin must be visible too). */
    private List<Geo.Point> routeAndTarget() {
        List<Geo.Point> all = new ArrayList<>(route.points);
        if (target != null) all.add(target.at);
        return all;
    }

    /** A row of buttons; scrolls sideways if the card is narrow (landscape) instead of breaking words. */
    private void addButtons(LinearLayout buttons) {
        android.widget.HorizontalScrollView row = new android.widget.HorizontalScrollView(this);
        row.setHorizontalScrollBarEnabled(false);
        row.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.addView(buttons);
        sheet.addView(row);
    }

    /** Runs r once the cards have their new size (after the next layout). */
    private void whenLaidOut(Runnable r) {
        afterLayout = r;
        overlay.requestLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyOrientation();
        placeButtons();
        whenLaidOut(() -> {
            if (navigating) return;  // the next position puts the arrow in place
            if (route != null) map.fit(routeAndTarget());
            else if (target != null) map.show(target.at, map.zoom());
        });
    }

    /** The zoom buttons sit above the card at the bottom, and hide while the search results are open. */
    private void placeButtons() {
        ui.post(() -> {
            boolean searching = resultsScroll.getVisibility() == View.VISIBLE;
            fabs.setVisibility(searching || navigating ? View.GONE : View.VISIBLE);
            View below = wide() ? null : navigating ? navBottom : sheet.getVisibility() == View.VISIBLE ? sheet : null;
            int bottom = (below == null ? 0 : below.getHeight()) + Ui.dp(this, 16);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fabs.getLayoutParams();
            if (lp.bottomMargin != bottom) {
                lp.bottomMargin = bottom;
                fabs.setLayoutParams(lp);
            }
            // not enough room between the search bar and the card (landscape): only the position button
            int room = ((View) fabs.getParent()).getHeight() - bottom - (wide() ? statusBar() : searchCard.getBottom());
            for (int i = 0; i < 2; i++) fabs.getChildAt(i).setVisibility(room < Ui.dp(this, 200) ? View.GONE : View.VISIBLE);
        });
    }

    private LinearLayout.LayoutParams fabLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }

    private GradientDrawable card(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dp(this, radiusDp));
        return g;
    }

    private ImageButton icon(int res, int label, int tint) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(tint);
        b.setBackground(null);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 13);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        return b;
    }

    private ImageButton roundButton(int res, int label) {
        ImageButton b = icon(res, label, 0xFF1A73E8);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.WHITE);
        b.setBackground(g);
        b.setElevation(Ui.dp(this, 6));
        return b;
    }

    private int statusBar() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 24);
    }

    private void keyboard(boolean show) {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (show) im.showSoftInput(query, 0);
        else im.hideSoftInputFromWindow(query.getWindowToken(), 0);
        if (!show) query.clearFocus();
    }

    // ------------------------------------------------------------ position

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    private Location lastKnown() {
        if (!hasLocation()) return null;
        LocationManager lm = getSystemService(LocationManager.class);
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(provider);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        return best;
    }

    @SuppressWarnings("MissingPermission")
    private void startLocation() {
        if (!hasLocation()) {
            if (!prefs().getBoolean("asked", false)) {
                prefs().edit().putBoolean("asked", true).apply();
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION);
            }
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        try {
            for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.getAllProviders().contains(provider)) lm.requestLocationUpdates(provider, 1000, 0, this, Looper.getMainLooper());
            }
        } catch (RuntimeException ignored) {
            // no location on this device
        }
    }

    private void stopLocation() {
        try {
            getSystemService(LocationManager.class).removeUpdates(this);
        } catch (RuntimeException ignored) {
            // nothing to stop
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        if (code == PERMISSION && hasLocation()) {
            startLocation();
            Location l = lastKnown();
            if (l != null) onLocationChanged(l);
            centerOnMe();
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (simulating) return;
        // the GPS position is better than the one of the mobile network
        if (LocationManager.NETWORK_PROVIDER.equals(l.getProvider()) && me != null && myAccuracy > 0 && myAccuracy < l.getAccuracy() && navigating) return;
        Geo.Point now = new Geo.Point(l.getLatitude(), l.getLongitude());
        if (l.hasBearing() && l.getSpeed() > 1) myBearing = l.getBearing();
        else if (me != null && Geo.distance(me, now) > 4) myBearing = (float) Geo.bearing(me, now);
        me = now;
        myAccuracy = l.getAccuracy();
        map.setPosition(me, myBearing, myAccuracy);
        if (startWhenLocated && !navigating && target != null) {
            // "Starten" waited for this: the route from here, then go
            startWhenLocated = false;
            goWhenReady = true;
            routeTo(target);
            return;
        }
        if (navigating) guide(me);
        else if (follow) map.show(me, Math.max(map.zoom(), 15));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    /** "Starten" before the position is known: ask for it (permission) and start as soon as it is there. */
    private void waitForPosition() {
        startWhenLocated = true;
        if (!hasLocation()) {
            Toast.makeText(this, R.string.map_allow_location, Toast.LENGTH_LONG).show();
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION);
        } else {
            Toast.makeText(this, R.string.map_wait_position, Toast.LENGTH_LONG).show();
        }
    }

    private void centerOnMe() {
        if (me == null) {
            Toast.makeText(this, hasLocation() ? R.string.map_no_position : R.string.map_allow_location, Toast.LENGTH_LONG).show();
            if (!hasLocation()) requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION);
            return;
        }
        follow = true;
        map.show(me, navigating ? 17 : Math.max(map.zoom(), 16));
    }

    @Override
    public void moved() {
        follow = false;
    }

    @Override
    public void longPress(Geo.Point p) {
        if (navigating) return;
        Places.Place pl = new Places.Place();
        pl.at = p;
        pl.name = getString(R.string.map_marked);
        showPlace(pl);
        new Thread(() -> {  // the address of the place
            try {
                List<Places.Place> found = Places.parse(Download.text(Places.reverseUrl(p, german)));
                if (found.isEmpty()) return;
                Places.Place f = found.get(0);
                f.at = p;
                runOnUiThread(() -> {
                    if (target == pl && route == null) showPlace(f);
                });
            } catch (IOException ignored) {
                // keep "Markierter Ort"
            }
        }).start();
    }

    // ------------------------------------------------------------ search

    private void search(String q, boolean first) {
        if (q.trim().isEmpty()) return;
        int token = ++searchToken;
        Geo.Point near = me != null ? me : map.center();
        new Thread(() -> {
            List<Places.Place> found;
            String error = null;
            try {
                found = Places.parse(Download.text(Places.searchUrl(q.trim(), near, german)));
            } catch (IOException e) {
                found = new ArrayList<>();
                error = getString(R.string.map_offline);
            }
            List<Places.Place> f = found;
            String err = error;
            runOnUiThread(() -> {
                if (token != searchToken || isDestroyed()) return;
                if (first) {  // Nova: take the best match right away
                    if (f.isEmpty()) {
                        Toast.makeText(this, err != null ? err : getString(R.string.map_nothing, q), Toast.LENGTH_LONG).show();
                        goWhenReady = false;
                        return;
                    }
                    choose(f.get(0));
                    return;
                }
                showResults(f, err);
            });
        }).start();
    }

    private void showResults(List<Places.Place> list, String error) {
        results.removeAllViews();
        if (error != null || list.isEmpty()) {
            TextView t = Ui.text(this, error != null ? error : getString(R.string.search_none), 15, 0xFF5F6368, false);
            t.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
            results.addView(t);
        }
        for (Places.Place p : list) results.addView(resultRow(p, "📍"));
        resultsScroll.setVisibility(View.VISIBLE);
        limitResults();
    }

    private void limitResults() {
        placeButtons();
        resultsScroll.post(() -> {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
            if (resultsScroll.getHeight() > max) {
                ViewGroup.LayoutParams lp = resultsScroll.getLayoutParams();
                lp.height = max;
                resultsScroll.setLayoutParams(lp);
            }
        });
    }

    private View resultRow(Places.Place p, String symbol) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        TextView s = Ui.text(this, symbol, 20, Color.BLACK, false);
        r.addView(s, new LinearLayout.LayoutParams(Ui.dp(this, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, p.title(), 16, 0xFF202124, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(title);
        String sub = p.address();
        if (me != null) sub = Geo.distanceText(Geo.distance(me, p.at), german) + (sub.isEmpty() ? "" : " · " + sub);
        TextView a = Ui.text(this, sub, 13, 0xFF5F6368, false);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(a);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.setOnClickListener(v -> choose(p));
        return r;
    }

    private void hideResults() {
        resultsScroll.setVisibility(View.GONE);
        placeButtons();
        ViewGroup.LayoutParams lp = resultsScroll.getLayoutParams();
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        resultsScroll.setLayoutParams(lp);
    }

    /** The last destinations, when the search field is empty. */
    private void showRecent() {
        List<Places.Place> recent = recent();
        if (recent.isEmpty()) {
            hideResults();
            return;
        }
        results.removeAllViews();
        TextView h = Ui.text(this, getString(R.string.map_recent), 13, 0xFF5F6368, true);
        h.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), 0, Ui.dp(this, 2));
        results.addView(h);
        for (Places.Place p : recent) results.addView(resultRow(p, "🕘"));
        resultsScroll.setVisibility(View.VISIBLE);
        limitResults();
    }

    private List<Places.Place> recent() {
        List<Places.Place> out = new ArrayList<>();
        try {
            Object o = MiniJson.parse(prefs().getString("recent", "[]"));
            if (o instanceof List) for (Object e : (List<?>) o) {
                if (!(e instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) e;
                Places.Place p = new Places.Place();
                p.name = String.valueOf(m.get("name"));
                p.street = String.valueOf(m.get("street"));
                p.city = String.valueOf(m.get("city"));
                p.at = new Geo.Point(((Number) m.get("lat")).doubleValue(), ((Number) m.get("lon")).doubleValue());
                out.add(p);
            }
        } catch (RuntimeException ignored) {
            // nothing saved yet
        }
        return out;
    }

    private void remember(Places.Place p) {
        List<Object> list = new ArrayList<>();
        list.add(MiniJson.obj("name", p.name, "street", p.street, "city", p.city, "lat", p.at.lat, "lon", p.at.lon));
        for (Places.Place o : recent()) {
            if (list.size() >= 6) break;
            if (Geo.distance(o.at, p.at) > 30) list.add(MiniJson.obj("name", o.name, "street", o.street, "city", o.city, "lat", o.at.lat, "lon", o.at.lon));
        }
        prefs().edit().putString("recent", MiniJson.write(list)).apply();
    }

    private void choose(Places.Place p) {
        hideResults();
        keyboard(false);
        query.setText(p.title());
        remember(p);
        showPlace(p);
        if (goWhenReady) {
            // navigation starts from the real position, not from the middle of the map
            if (me == null) {
                goWhenReady = false;
                waitForPosition();
            } else {
                routeTo(p);
            }
        }
    }

    // ------------------------------------------------------------ place and route cards

    private void showPlace(Places.Place p) {
        if (p != target) startWhenLocated = false;  // waiting was for the place before
        if (me == null && (target == null || origin == null)) origin = map.center();
        target = p;
        route = null;
        map.setRoute(null, null);
        map.setDestination(p.at);
        follow = false;
        double z = Math.max(map.zoom(), 15);
        whenLaidOut(() -> map.show(p.at, z));
        sheet.removeAllViews();
        TextView title = Ui.text(this, p.title(), 22, 0xFF202124, true);
        sheet.addView(title);
        String sub = p.address();
        if (me != null) sub = Geo.distanceText(Geo.distance(me, p.at), german) + " " + getString(R.string.map_away) + (sub.isEmpty() ? "" : " · " + sub);
        TextView a = Ui.text(this, sub, 14, 0xFF5F6368, false);
        a.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 14));
        sheet.addView(a);
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(pill("↱  " + getString(R.string.map_route), true, v -> routeTo(p)));
        buttons.addView(pill("▶  " + getString(R.string.map_start), false, v -> {
            if (me == null) {
                waitForPosition();
                return;
            }
            goWhenReady = true;
            routeTo(p);
        }));
        addButtons(buttons);
        sheet.setVisibility(View.VISIBLE);
        placeButtons();
    }

    private TextView pill(String text, boolean primary, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 15, primary ? Color.WHITE : 0xFF1A73E8, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        GradientDrawable g = card(primary ? 0xFF1A73E8 : Color.WHITE, 22);
        if (!primary) g.setStroke(Ui.dp(this, 1), 0xFFDADCE0);
        t.setBackground(g);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 11), Ui.dp(this, 18), Ui.dp(this, 11));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 10);
        t.setLayoutParams(lp);
        return t;
    }

    /** Start: the own position, without one the middle of the map (said on the card). */
    private Geo.Point startPoint() {
        return me != null ? me : origin != null ? origin : map.center();
    }

    private void routeTo(Places.Place p) {
        target = p;
        Geo.Point from = startPoint();
        String m = mode;
        sheet.removeAllViews();
        TextView wait = Ui.text(this, getString(R.string.map_routing), 16, 0xFF5F6368, false);
        wait.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        sheet.addView(wait);
        sheet.setVisibility(View.VISIBLE);
        new Thread(() -> {
            NavRoute r = null;
            String error = null;
            try {
                r = NavRoute.parse(Download.text(NavRoute.url(m, from, p.at)), m);
                if (r == null) error = getString(R.string.map_no_route);
            } catch (IOException e) {
                android.util.Log.w("NovaKarte", "route", e);
                error = getString(R.string.map_offline);
            }
            NavRoute res = r;
            String err = error;
            runOnUiThread(() -> {
                if (isDestroyed() || target != p) return;
                if (res == null) {
                    goWhenReady = false;
                    showPlace(p);
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    return;
                }
                route = res;
                map.setRoute(res.points, res.cumulative);
                showRoute();
                if (goWhenReady) {
                    goWhenReady = false;
                    startNavigation(false);
                }
            });
        }).start();
    }

    private void showRoute() {
        sheet.removeAllViews();
        // car / bike / on foot
        LinearLayout modes = new LinearLayout(this);
        String[][] all = {{NavRoute.CAR, "🚗 " + getString(R.string.map_car)}, {NavRoute.BIKE, "🚲 " + getString(R.string.map_bike)},
                {NavRoute.FOOT, "🚶 " + getString(R.string.map_foot)}};
        for (String[] m : all) {
            boolean on = m[0].equals(mode);
            TextView t = Ui.text(this, m[1], 14, on ? 0xFF0B57D0 : 0xFF3C4043, on);
            t.setBackground(card(on ? 0xFFD3E3FD : 0xFFF1F3F4, 18));
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            t.setOnClickListener(v -> {
                if (m[0].equals(mode)) return;
                mode = m[0];
                prefs().edit().putString("mode", mode).apply();
                routeTo(target);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            modes.addView(t, lp);
        }
        sheet.addView(modes);
        LinearLayout sum = new LinearLayout(this);
        sum.setGravity(Gravity.BOTTOM);
        sum.setPadding(0, Ui.dp(this, 14), 0, 0);
        sum.addView(Ui.text(this, Geo.durationText(route.duration, german), 26, 0xFF188038, true));
        TextView km = Ui.text(this, "  (" + Geo.distanceText(route.distance, german) + ")", 18, 0xFF5F6368, false);
        sum.addView(km);
        sheet.addView(sum);
        String arrival = android.text.format.DateFormat.getTimeFormat(this).format(new Date(System.currentTimeMillis() + (long) route.duration * 1000));
        String via = via(route);
        TextView info = Ui.text(this, getString(R.string.map_arrival, arrival) + (via.isEmpty() ? "" : " · " + getString(R.string.map_via, via))
                + (me == null ? "\n" + getString(R.string.map_from_center) : ""), 14, 0xFF5F6368, false);
        info.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 14));
        sheet.addView(info);
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(pill("▶  " + getString(R.string.map_start), true, v -> startNavigation(false)));
        buttons.addView(pill(getString(R.string.map_preview), false, v -> startNavigation(true)));
        buttons.addView(pill(getString(R.string.map_steps), false, v -> steps()));
        addButtons(buttons);
        sheet.setVisibility(View.VISIBLE);
        placeButtons();
        NavRoute shown = route;
        whenLaidOut(() -> {
            if (route == shown && !navigating) map.fit(routeAndTarget());
        });
    }

    /** The two longest roads of the route: "über Ballindamm und Kirchenallee". */
    private String via(NavRoute r) {
        List<NavRoute.Step> named = new ArrayList<>();
        for (NavRoute.Step s : r.steps) if (!NavText.road(s).isEmpty()) named.add(s);
        named.sort((a, b) -> Double.compare(b.distance, a.distance));
        List<String> names = new ArrayList<>();
        for (NavRoute.Step s : named) {
            String n = NavText.road(s);
            if (!names.contains(n)) names.add(n);
            if (names.size() == 2) break;
        }
        return TextUtils.join(german ? " und " : " and ", names);
    }

    private void steps() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < route.steps.size(); i++) {
            NavRoute.Step s = route.steps.get(i);
            b.append(i + 1).append(".  ").append(NavText.instruction(s, route.mode, german));
            if (s.distance > 0 && !"arrive".equals(s.type)) b.append("  (").append(Geo.distanceText(s.distance, german)).append(')');
            b.append("\n\n");
        }
        new AlertDialog.Builder(this).setTitle(R.string.map_steps).setMessage(b.toString().trim()).setPositiveButton(R.string.ok, null).show();
    }

    private void clear() {
        target = null;
        origin = null;
        startWhenLocated = false;
        route = null;
        map.setRoute(null, null);
        map.setDestination(null);
        sheet.setVisibility(View.GONE);
        placeButtons();
    }

    // ------------------------------------------------------------ navigation

    private void startNavigation(boolean preview) {
        if (route == null) return;
        if (!preview && me == null) {
            waitForPosition();
            return;
        }
        startWhenLocated = false;
        navigating = true;
        simulating = preview;
        guidance = new Guidance(route, german);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        searchCard.setVisibility(View.GONE);
        sheet.setVisibility(View.GONE);
        banner.setVisibility(View.VISIBLE);
        navBottom.setVisibility(View.VISIBLE);
        map.setNavigating(true);
        placeButtons();
        follow = true;
        say((preview ? getString(R.string.map_preview_start) + " " : "") + guidance.start());
        Geo.Point start = preview ? route.points.get(0) : me;
        if (preview) {
            simulating = true;
            simAlong = 0;
            ui.post(simulate);
        }
        guide(start);
    }

    /** Preview: Nova Karte drives the route by itself (faster than in reality). */
    private final Runnable simulate = new Runnable() {
        @Override
        public void run() {
            if (!navigating || !simulating || route == null) return;
            double speed = NavRoute.CAR.equals(route.mode) ? 14 : NavRoute.BIKE.equals(route.mode) ? 5 : 1.5;  // m/s
            simAlong += speed * 0.5 * 3;  // every half second, three times as fast
            Geo.Point p = Geo.at(route.points, route.cumulative, simAlong);
            Geo.Point ahead = Geo.at(route.points, route.cumulative, simAlong + 8);
            map.setPosition(p, (float) Geo.bearing(p, ahead), 5);
            guide(p);
            if (navigating) ui.postDelayed(this, 500);
        }
    };

    private void guide(Geo.Point p) {
        Guidance.State st = guidance.update(p);
        map.setPassed(st.along);
        if (follow) {
            // the position low in the free part, so more of the way ahead is visible
            map.showAt(p, 17, 0.75f);
        }
        NavRoute r = guidance.route();
        NavRoute.Step step = r.steps.get(st.next);
        arrow.set(step);
        bannerDistance.setText(Geo.distanceText(st.toManeuver, german));
        bannerText.setText(NavText.instruction(step, r.mode, german));
        String then = "";
        if (st.next + 1 < r.steps.size()) {
            NavRoute.Step after = r.steps.get(st.next + 1);
            then = (german ? "Danach: " : "Then: ") + NavText.instruction(after, r.mode, german);
        }
        bannerThen.setText(then + (simulating ? (then.isEmpty() ? "" : "\n") + "▶ " + getString(R.string.map_preview) : ""));
        navTime.setText(Geo.durationText(st.remainingSeconds, german));
        String arrival = android.text.format.DateFormat.getTimeFormat(this).format(new Date(System.currentTimeMillis() + (long) st.remainingSeconds * 1000));
        navInfo.setText(Geo.distanceText(st.remaining, german) + " · " + arrival);
        if (st.say != null) say(st.say);
        if (st.arrived) {
            arrived();
            return;
        }
        if (st.offRoute && !simulating && System.currentTimeMillis() - lastReroute > 10_000) reroute(p);
    }

    /** Another way was taken: a new route from here. */
    private void reroute(Geo.Point from) {
        lastReroute = System.currentTimeMillis();
        say(getString(R.string.map_rerouting));
        Places.Place p = target;
        String m = route.mode;
        new Thread(() -> {
            try {
                NavRoute r = NavRoute.parse(Download.text(NavRoute.url(m, from, p.at)), m);
                if (r == null) return;
                runOnUiThread(() -> {
                    if (!navigating) return;
                    route = r;
                    guidance = new Guidance(r, german);
                    map.setRoute(r.points, r.cumulative);
                    say(guidance.start());
                });
            } catch (IOException ignored) {
                // try again at the next position
            }
        }).start();
    }

    private void arrived() {
        stopNavigation();
        sheet.removeAllViews();
        sheet.addView(Ui.text(this, "🏁  " + getString(R.string.map_arrived), 22, 0xFF202124, true));
        if (target != null) {
            TextView t = Ui.text(this, target.title(), 15, 0xFF5F6368, false);
            t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
            sheet.addView(t);
        }
        sheet.addView(pill(getString(R.string.ok), true, v -> clear()));
        sheet.setVisibility(View.VISIBLE);
        placeButtons();
    }

    private void confirmStop() {
        new AlertDialog.Builder(this).setMessage(R.string.map_stop_confirm)
                .setPositiveButton(R.string.map_stop, (d, w) -> {
                    stopNavigation();
                    if (route != null) showRoute();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void stopNavigation() {
        navigating = false;
        if (simulating) map.setPosition(me, myBearing, myAccuracy);  // back to the real position
        simulating = false;
        ui.removeCallbacks(simulate);
        speaker.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        banner.setVisibility(View.GONE);
        navBottom.setVisibility(View.GONE);
        searchCard.setVisibility(View.VISIBLE);
        map.setNavigating(false);
        map.setPassed(0);
        placeButtons();
    }

    private void say(String text) {
        if (text == null || text.isEmpty()) return;
        android.util.Log.i("NovaKarte", "Ansage: " + text);
        if (!muted) speaker.say(text, null);
    }

    /** For Nova: open the map and navigate. */
    static Intent navigate(Context c, String to) {
        return new Intent(c, MapActivity.class).putExtra(EXTRA_TO, to).putExtra(EXTRA_GO, true);
    }
}
