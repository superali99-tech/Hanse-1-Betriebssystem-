package org.novaos.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Nova Wetter (NovaOS 1.5): the weather where the phone is - a sky in the colours of the
 * weather, the temperature big, then glass cards with the next 24 hours and 7 days. Open-Meteo
 * (no key, no account); the last answer is kept for when there is no connection.
 */
public class WeatherActivity extends Activity {
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private LinearLayout page;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        root = new FrameLayout(this);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.round_bg);
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 12);
        back.setPadding(p, p, p, p);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        root.addView(back, bl);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            bl.setMargins(insets.getSystemWindowInsetLeft() + Ui.dp(this, 14), top + Ui.dp(this, 8), 0, 0);
            back.setLayoutParams(bl);
            page.setPadding(insets.getSystemWindowInsetLeft() + Ui.dp(this, 16), top + Ui.dp(this, 56),
                    insets.getSystemWindowInsetRight() + Ui.dp(this, 16), insets.getSystemWindowInsetBottom() + Ui.dp(this, 24));
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        String cached = prefs().getString("json", "");
        Weather.Forecast f = Weather.forecast(cached);
        if (f != null) show(f, prefs().getString("place", ""), prefs().getLong("time", 0));
        else showMessage(getString(R.string.weather_loading));
        load();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("weather_detail", Context.MODE_PRIVATE);
    }

    /** Where the phone is (or the city Nova was asked about), then the forecast. */
    private void load() {
        new Thread(() -> {
            String json = null, place = "";
            try {
                double lat, lon;
                Fixes.Fix fix = Where.best(this);
                if (fix != null) {
                    lat = fix.lat;
                    lon = fix.lon;
                    try {
                        List<Places.Place> found = Places.parse(Download.text(Places.reverseUrl(new Geo.Point(lat, lon), german)));
                        if (!found.isEmpty()) place = HomeWeather.town(found.get(0));
                    } catch (Exception ignored) {
                        // no name - the weather still counts
                    }
                } else {
                    String city = getSharedPreferences("nova", MODE_PRIVATE).getString("city", "");
                    Weather.Place pl = city.isEmpty() ? null : Weather.place(Download.text(Weather.geocodeUrl(city, german)));
                    if (pl == null) throw new IllegalStateException("no place");
                    lat = pl.lat;
                    lon = pl.lon;
                    place = pl.name;
                }
                json = Download.text(Weather.detailUrl(lat, lon));
            } catch (Exception ignored) {
                // offline or no place: the kept answer stays
            }
            String j = json, pl = place;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Weather.Forecast f = Weather.forecast(j);
                if (f != null) {
                    long now = System.currentTimeMillis();
                    prefs().edit().putString("json", j).putString("place", pl).putLong("time", now).apply();
                    show(f, pl, now);
                } else if (Weather.forecast(prefs().getString("json", "")) == null) {
                    showMessage(getString(Where.best(this) == null ? R.string.weather_no_place : R.string.weather_failed));
                }
            });
        }).start();
    }

    private void showMessage(String text) {
        root.setBackground(sky(3, true));
        page.removeAllViews();
        TextView t = Ui.text(this, text, 17, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 120), 0, 0);
        page.addView(t);
    }

    private void show(Weather.Forecast f, String place, long time) {
        root.setBackground(sky(f.now.code, f.day));
        page.removeAllViews();
        TextView city = Ui.text(this, place.isEmpty() ? getString(R.string.weather_here) : place, 30, Color.WHITE, false);
        city.setGravity(Gravity.CENTER);
        page.addView(city);
        TextView temp = Ui.text(this, Math.round(f.now.temp) + "°", 92, Color.WHITE, false);
        temp.setGravity(Gravity.CENTER);
        temp.setIncludeFontPadding(false);
        page.addView(temp);
        TextView sky = Ui.text(this, capital(Weather.describe(f.now.code, german)), 20, 0xE6FFFFFF, false);
        sky.setGravity(Gravity.CENTER);
        page.addView(sky);
        if (!Double.isNaN(f.now.max) && !Double.isNaN(f.now.min)) {
            TextView hl = Ui.text(this, getString(R.string.weather_hl, Math.round(f.now.max), Math.round(f.now.min)), 18, 0xE6FFFFFF, false);
            hl.setGravity(Gravity.CENTER);
            page.addView(hl);
        }
        if (System.currentTimeMillis() - time > 60 * 60_000L && time > 0) {
            TextView old = Ui.text(this, getString(R.string.weather_as_of, UpdateNotes.when(this, time)), 13, 0xB3FFFFFF, false);
            old.setGravity(Gravity.CENTER);
            page.addView(old);
        }

        // the next 24 hours
        LinearLayout hours = card(getString(R.string.weather_hourly));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < f.hours.size(); i++) {
            Weather.Hour h = f.hours.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.addView(Ui.text(this, i == 0 ? getString(R.string.weather_now) : h.hour(), 14, Color.WHITE, i == 0));
            TextView sym = Ui.text(this, Weather.symbol(h.code), 22, Color.WHITE, false);
            sym.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            cell.addView(sym);
            cell.addView(Ui.text(this, Math.round(h.temp) + "°", 17, Color.WHITE, true));
            row.addView(cell, new LinearLayout.LayoutParams(Ui.dp(this, 54), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        hs.addView(row);
        hours.addView(hs);

        // 7 days, with a bar from the lowest to the highest temperature of the week
        LinearLayout days = card(getString(R.string.weather_days));
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Weather.Day d : f.days) {
            lo = Math.min(lo, d.min);
            hi = Math.max(hi, d.max);
        }
        for (int i = 0; i < f.days.size(); i++) {
            Weather.Day d = f.days.get(i);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            r.addView(Ui.text(this, i == 0 ? getString(R.string.weather_today) : Weather.weekday(d.date, german, true), 17, Color.WHITE, true),
                    new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(Ui.text(this, Weather.symbol(d.code), 20, Color.WHITE, false), new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView min = Ui.text(this, Math.round(d.min) + "°", 17, 0xB3FFFFFF, false);
            min.setGravity(Gravity.END);
            r.addView(min, new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams barL = new LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1);
            barL.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            r.addView(new RangeBar(this, (float) ((d.min - lo) / Math.max(1, hi - lo)), (float) ((d.max - lo) / Math.max(1, hi - lo))), barL);
            r.addView(Ui.text(this, Math.round(d.max) + "°", 17, Color.WHITE, false), new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            days.addView(r);
        }
        TextView source = Ui.text(this, getString(R.string.weather_source), 12, 0x99FFFFFF, false);
        source.setGravity(Gravity.CENTER);
        source.setPadding(0, Ui.dp(this, 16), 0, 0);
        page.addView(source);
    }

    /** A glass card with a small heading, as wide as the page (at most 560 dp). */
    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        c.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        TextView h = Ui.text(this, title.toUpperCase(Locale.getDefault()), 12, 0xB3FFFFFF, true);
        h.setPadding(0, 0, 0, Ui.dp(this, 8));
        c.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32),
                Ui.dp(this, 560)), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        page.addView(c, lp);
        return c;
    }

    /** The sky: blue on a clear day, grey with clouds, slate with rain, light with snow, dark at night. */
    static GradientDrawable sky(int code, boolean day) {
        int[] c;
        if (!day) c = new int[] {0xFF0B1638, 0xFF1E2E57, 0xFF33456E};
        else if (code <= 1) c = new int[] {0xFF2E7CD6, 0xFF5AA2E6, 0xFF8CC3F0};
        else if (code >= 71 && code <= 77 || code == 85 || code == 86) c = new int[] {0xFF7F97B8, 0xFFA9BCD6, 0xFFD3DEEC};
        else if (code >= 51) c = new int[] {0xFF38495F, 0xFF566A82, 0xFF7A8DA3};
        else c = new int[] {0xFF56708F, 0xFF7C93AE, 0xFFA3B4C8};
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The temperature bar of a day within the week. */
    private static final class RangeBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float from, to;

        RangeBar(Context c, float from, float to) {
            super(c);
            this.from = Math.max(0, Math.min(1, from));
            this.to = Math.max(this.from, Math.min(1, to));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(0, 0, w, h, r, r, p);
            p.setColor(0xFFFFFFFF);  // full strength: the track's transparency would dim the colours
            p.setShader(new android.graphics.LinearGradient(0, 0, w, 0, 0xFF7CC4F5, 0xFFF5B54A, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(from * w, 0, Math.max(from * w + h, to * w), h, r, r, p);
        }
    }
}
