package org.novaos.mobile;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * The glass widgets of the home screen (NovaOS 1.5 glass design): weather (tap: Nova Wetter),
 * calendar with the next appointment (tap: the phone's calendar), and the music while
 * something plays (with back, play/pause, next).
 */
final class HomeWidgets {
    static final class Event {
        final String title;
        final long begin;
        final boolean allDay;

        Event(String title, long begin, boolean allDay) {
            this.title = title;
            this.begin = begin;
            this.allDay = allDay;
        }
    }

    private HomeWidgets() {}

    /** Glass: a light, see-through surface with a lighter top and a light rim; tint = a colour shining through. */
    static GradientDrawable glass(Context c, int tint) {
        int top = (0x5C << 24) | (tint & 0xFFFFFF), bottom = (0x2E << 24) | (tint & 0xFFFFFF);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {top, bottom});
        g.setCornerRadius(Ui.dp(c, 28));
        g.setStroke(Math.max(1, Ui.dp(c, 1)), 0x47FFFFFF);
        return g;
    }

    private static LinearLayout card(Activity a, int tint) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(glass(a, tint));
        card.setElevation(Ui.dp(a, 4));
        int p = Ui.dp(a, 16);
        card.setPadding(p, Ui.dp(a, 14), p, Ui.dp(a, 14));
        return card;
    }

    private static boolean german() {
        return Locale.getDefault().getLanguage().equals("de");
    }

    // ------------------------------------------------------------ weather

    static View weather(Activity a, View.OnClickListener click) {
        LinearLayout card = card(a, 0x3D7BD9);
        HomeWeather.Data wd = HomeWeather.cached(a);
        if (wd != null) {
            TextView place = Ui.text(a, wd.place.isEmpty() ? a.getString(R.string.weather_here) : wd.place, 15, Color.WHITE, true);
            place.setSingleLine(true);
            place.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(place);
            TextView temp = Ui.text(a, Math.round(wd.temp) + "°", 40, Color.WHITE, false);
            temp.setIncludeFontPadding(false);
            card.addView(temp);
            card.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1));
            String sky = Weather.describe(wd.code, german());
            if (!sky.isEmpty()) sky = Character.toUpperCase(sky.charAt(0)) + sky.substring(1);
            TextView what = Ui.text(a, Weather.symbol(wd.code) + "  " + sky, 14, Color.WHITE, false);
            what.setSingleLine(true);
            what.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(what);
            if (!Double.isNaN(wd.max) && !Double.isNaN(wd.min)) {
                card.addView(Ui.text(a, a.getString(R.string.weather_hl, Math.round(wd.max), Math.round(wd.min)), 14, 0xE6FFFFFF, false));
            }
            card.setContentDescription(Weather.sentence(wd.place, new Weather.Now(wd.temp, wd.code, 0, wd.max, wd.min), german()));
        } else {
            card.addView(clock(a, 36));
            card.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1));
            card.addView(Ui.text(a, a.getString(R.string.home_weather_wait), 13, 0xCCFFFFFF, false));
        }
        card.setOnClickListener(click);
        return card;
    }

    private static View clock(Activity a, float sp) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(a);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(sp);
        clock.setTypeface(Fonts.bold(a));
        box.addView(clock);
        TextClock date = new TextClock(a);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(14);
        date.setTypeface(Fonts.regular(a));
        box.addView(date);
        return box;
    }

    // ------------------------------------------------------------ calendar

    static View calendar(Activity a, Event next, boolean allowed, View.OnClickListener click) {
        LinearLayout card = card(a, 0xFFFFFF);
        Calendar now = Calendar.getInstance();
        String day = android.text.format.DateUtils.formatDateTime(a, now.getTimeInMillis(), android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY);
        card.addView(Ui.text(a, day.toUpperCase(Locale.getDefault()), 13, 0xFFFF6B6B, true));
        TextView date = Ui.text(a, String.valueOf(now.get(Calendar.DAY_OF_MONTH)), 40, Color.WHITE, false);
        date.setIncludeFontPadding(false);
        card.addView(date);
        card.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1));
        if (!allowed) {
            card.addView(Ui.text(a, a.getString(R.string.cal_allow), 13, 0xE6FFFFFF, false));
        } else if (next == null) {
            card.addView(Ui.text(a, a.getString(R.string.cal_none), 14, 0xE6FFFFFF, false));
        } else {
            TextView t = Ui.text(a, next.title.isEmpty() ? a.getString(R.string.cal_untitled) : next.title, 14, Color.WHITE, true);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t);
            card.addView(Ui.text(a, when(a, next), 13, 0xCCFFFFFF, false));
        }
        card.setOnClickListener(click);
        return card;
    }

    /** "18:30", "Ganztägig", "Morgen, 09:00", "Montag, 09:00". */
    static String when(Context c, Event e) {
        Calendar b = Calendar.getInstance(), today = Calendar.getInstance();
        b.setTimeInMillis(e.begin);
        String time = e.allDay ? c.getString(R.string.cal_allday) : DateFormat.getTimeFormat(c).format(new java.util.Date(e.begin));
        int days = (b.get(Calendar.YEAR) - today.get(Calendar.YEAR)) * 366 + b.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR);
        if (days <= 0) return time;
        if (days == 1) return c.getString(R.string.cal_tomorrow, time);
        return android.text.format.DateUtils.formatDateTime(c, e.begin, android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY) + ", " + time;
    }

    /** The next appointment of the phone's calendars (today, tomorrow); null without one or without permission. */
    static Event nextEvent(Context c) {
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 12 * 3600_000L);
        ContentUris.appendId(b, now + 2 * 24 * 3600_000L);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY};
        try (Cursor cur = c.getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                long end = cur.getLong(2);
                boolean allDay = cur.getInt(3) != 0;
                if (end <= now) continue;
                long begin = cur.getLong(1);
                if (allDay) {
                    // all-day events are stored in UTC midnight: today's counts as today
                    Calendar local = Calendar.getInstance();
                    Calendar utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                    utc.setTimeInMillis(begin);
                    local.clear();
                    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
                    begin = local.getTimeInMillis();
                }
                return new Event(cur.getString(0) == null ? "" : cur.getString(0), begin, allDay);
            }
        } catch (RuntimeException e) {
            return null;  // no permission or no calendar
        }
        return null;
    }

    // ------------------------------------------------------------ music

    static View music(Activity a) {
        Music m = Music.get(a);
        MusicCatalog.Track t = m.current();
        LinearLayout card = card(a, 0xB14CD8);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        TextView note = Ui.text(a, "♪", 22, Color.WHITE, true);
        note.setGravity(Gravity.CENTER);
        GradientDrawable nb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFFFF5E8A, 0xFF8E6CFF});
        nb.setCornerRadius(Ui.dp(a, 12));
        note.setBackground(nb);
        card.addView(note, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        String now = m.nowPlaying();  // the song or show on the radio right now
        TextView title = Ui.text(a, t == null ? "" : !now.isEmpty() ? now : t.title, 15, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView artist = Ui.text(a, t == null ? "" : !now.isEmpty() ? t.title : t.artist, 13, 0xCCFFFFFF, false);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artist);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(a, 12);
        card.addView(texts, tl);
        card.addView(control(a, R.drawable.ic_skip_prev, R.string.music_previous, v -> m.previous()));
        card.addView(control(a, m.playing() ? R.drawable.ic_pause : R.drawable.ic_play_s, m.playing() ? R.string.music_pause : R.string.music_play,
                v -> m.toggle()));
        card.addView(control(a, R.drawable.ic_skip_next, R.string.music_next, v -> m.next()));
        card.setOnClickListener(v -> a.startActivity(new Intent(a, NowPlayingActivity.class)));
        return card;
    }

    private static View control(Activity a, int icon, int label, View.OnClickListener click) {
        android.widget.ImageButton b = new android.widget.ImageButton(a);
        b.setImageResource(icon);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(a, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(a.getString(label));
        android.util.TypedValue tv = new android.util.TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        return b;
    }

}
