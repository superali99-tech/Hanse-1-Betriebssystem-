package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.novaos.tv.ImageLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova Musik: songs on the phone, radio stations and real songs (Deezer previews), with a
 * player at the bottom that keeps playing in the background, Bluetooth output and search.
 */
public class MusicActivity extends Activity implements Music.Listener {
    static final String EXTRA_RADIO = "radio", EXTRA_SONG = "song", EXTRA_LOCAL = "local";
    private static final int TAB_LOCAL = 0, TAB_RADIO = 1, TAB_SONGS = 2;
    private static final int PERMISSION = 31;

    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> covers = new LruCache<>(80);
    private LinearLayout content;
    private EditText search;
    private final TextView[] tabViews = new TextView[3];
    private int tab = TAB_SONGS;
    private String query = "";
    private int loadToken;
    private List<MusicCatalog.Track> local;
    private Music music;

    // player at the bottom
    private ImageView pCover;
    private TextView pTitle, pSub, pOutput;
    private ImageButton pToggle;
    private SeekBar pSeek;
    private View player;
    private boolean seeking;
    private AudioDeviceCallback devices;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_music, getString(R.string.music_sub));
        music = Music.get(this);
        search = Page.search(this, R.string.music_search_songs, q -> {
            query = q;
            int token = ++loadToken;
            ui.postDelayed(() -> {
                if (token == loadToken) show();  // after a short pause in typing
            }, tab == TAB_LOCAL ? 0 : 600);
        });
        addTabs();
        addPlayer();
        tab = getSharedPreferences("music", MODE_PRIVATE).getInt("tab", TAB_SONGS);
        handle(getIntent());
        selectTab(tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        music.addListener(this);
        AudioManager am = getSystemService(AudioManager.class);
        devices = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                showOutput();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                showOutput();
            }
        };
        am.registerAudioDeviceCallback(devices, ui);
        musicChanged();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        music.removeListener(this);
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(devices);
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Nova: "Spiel Radio SWR3", "Spiel Atemlos von Helene Fischer", "Spiel meine Musik". */
    private void handle(Intent i) {
        if (i == null) return;
        String radio = i.getStringExtra(EXTRA_RADIO), song = i.getStringExtra(EXTRA_SONG);
        if (radio != null) {
            tab = TAB_RADIO;
            playFirst(radio.isEmpty() ? MusicCatalog.radioTopUrl() : MusicCatalog.radioSearchUrl(radio), true);
            setQuery(radio);
        } else if (song != null && !song.isEmpty()) {
            tab = TAB_SONGS;
            playFirst(MusicCatalog.songSearchUrl(song), false);
            setQuery(song);
        } else if (i.getBooleanExtra(EXTRA_LOCAL, false)) {
            tab = TAB_LOCAL;
            if (hasPermission()) worker.execute(() -> {
                List<MusicCatalog.Track> l = loadLocal();
                runOnUiThread(() -> {
                    if (!l.isEmpty()) music.play(l, 0);
                    else Toast.makeText(this, R.string.music_local_empty, Toast.LENGTH_LONG).show();
                });
            });
        }
    }

    private void setQuery(String q) {
        query = q;
        if (search != null) search.setText(q);
    }

    private void playFirst(String url, boolean radio) {
        worker.execute(() -> {
            try {
                String json = Download.text(url);
                List<MusicCatalog.Track> l = radio ? MusicCatalog.parseRadio(json) : MusicCatalog.parseDeezer(json);
                runOnUiThread(() -> {
                    if (l.isEmpty()) Toast.makeText(this, R.string.search_none, Toast.LENGTH_LONG).show();
                    else music.play(radio ? Collections.singletonList(l.get(0)) : l, 0);
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.music_offline, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ------------------------------------------------------------ tabs

    private void addTabs() {
        LinearLayout row = new LinearLayout(this);
        int[] names = {R.string.music_tab_local, R.string.music_tab_radio, R.string.music_tab_songs};
        for (int i = 0; i < 3; i++) {
            TextView t = Ui.text(this, getString(names[i]), 15, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            int which = i;
            t.setOnClickListener(v -> selectTab(which));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.leftMargin = Ui.dp(this, 8);
            row.addView(t, lp);
            tabViews[i] = t;
        }
        ViewGroup column = (ViewGroup) findViewById(R.id.page_status).getParent();
        View box = (View) search.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = getResources().getDimensionPixelSize(R.dimen.side);
        lp.setMargins(side, Ui.dp(this, 10), side, 0);
        column.addView(row, column.indexOfChild(box), lp);
    }

    private void selectTab(int t) {
        if (t != tab) {
            tab = t;
            query = "";
            search.setText("");
        }
        getSharedPreferences("music", MODE_PRIVATE).edit().putInt("tab", tab).apply();
        for (int i = 0; i < 3; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 20));
            if (i == tab) {
                bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                bg.setColors(new int[] {0xFFFF5FA2, 0xFF7C3AED});
            } else {
                bg.setColor(0x26FFFFFF);
            }
            tabViews[i].setBackground(bg);
        }
        search.setHint(tab == TAB_LOCAL ? R.string.music_search_local : tab == TAB_RADIO ? R.string.music_search_radio
                : R.string.music_search_songs);
        show();
    }

    private void show() {
        int token = ++loadToken;
        content.removeAllViews();
        Page.status(this, null);
        if (tab == TAB_LOCAL) showLocal();
        else if (tab == TAB_RADIO) load(token, query.trim().isEmpty() ? MusicCatalog.radioTopUrl() : MusicCatalog.radioSearchUrl(query), true);
        else load(token, query.trim().isEmpty() ? MusicCatalog.chartUrl() : MusicCatalog.songSearchUrl(query), false);
    }

    // ------------------------------------------------------------ my music (songs on the phone)

    private boolean hasPermission() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void showLocal() {
        if (!hasPermission()) {
            TextView t = Ui.text(this, getString(R.string.music_local_permission), 15, Color.WHITE, false);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 12));
            content.addView(t);
            View b = Ui.button(this, R.string.music_allow, v -> requestPermissions(new String[] {
                    Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION));
            content.addView(b);
            return;
        }
        if (local == null) {
            Page.status(this, getString(R.string.loading));
            worker.execute(() -> {
                List<MusicCatalog.Track> l = loadLocal();
                runOnUiThread(() -> {
                    local = l;
                    if (tab == TAB_LOCAL) show();
                });
            });
            return;
        }
        if (local.isEmpty()) {
            TextView t = Ui.text(this, getString(R.string.music_local_empty), 15, getColor(R.color.text_muted), false);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), 0);
            content.addView(t);
            return;
        }
        List<MusicCatalog.Track> shown = new ArrayList<>();
        for (MusicCatalog.Track t : local) if (Search.matches(query, t.title, t.artist, t.album)) shown.add(t);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(pill("▶ " + getString(R.string.music_play_all), v -> music.play(shown, 0)));
        actions.addView(pill("🔀 " + getString(R.string.music_shuffle), v -> {
            List<MusicCatalog.Track> mixed = new ArrayList<>(shown);
            Collections.shuffle(mixed);
            music.play(mixed, 0);
        }));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(this, 10);
        content.addView(actions, al);
        TextView count = Ui.text(this, getResources().getQuantityString(R.plurals.music_local_count, shown.size(), shown.size()), 13,
                getColor(R.color.text_muted), false);
        count.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, 0);
        content.addView(count);
        for (int i = 0; i < shown.size() && i < 500; i++) content.addView(row(shown, i));
        if (shown.isEmpty()) none();
    }

    private List<MusicCatalog.Track> loadLocal() {
        List<MusicCatalog.Track> out = new ArrayList<>();
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID};
        try (Cursor c = getContentResolver().query(base, cols, MediaStore.Audio.Media.IS_MUSIC + "!=0", null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE")) {
            if (c == null) return out;
            while (c.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(base, c.getLong(0));
                String artist = c.getString(2);
                MusicCatalog.Track t = new MusicCatalog.Track(MusicCatalog.LOCAL, c.getString(1),
                        artist == null || artist.equals("<unknown>") ? "" : artist, uri.toString());
                t.album = c.getString(3) == null ? "" : c.getString(3);
                t.durationMs = c.getLong(4);
                t.art = Music.albumArt(c.getLong(5)).toString();
                out.add(t);
            }
        } catch (RuntimeException e) {
            // no access or no media storage
        }
        return out;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code == PERMISSION && tab == TAB_LOCAL) show();
    }

    // ------------------------------------------------------------ radio and songs from the internet

    private void load(int token, String url, boolean radio) {
        Page.status(this, getString(R.string.loading));
        worker.execute(() -> {
            List<MusicCatalog.Track> list;
            String err = null;
            try {
                String json = Download.text(url);
                list = radio ? MusicCatalog.parseRadio(json) : MusicCatalog.parseDeezer(json);
            } catch (IOException e) {
                list = new ArrayList<>();
                err = getString(R.string.music_offline);
            }
            List<MusicCatalog.Track> l = list;
            String e = err;
            runOnUiThread(() -> {
                if (token != loadToken || isDestroyed()) return;
                Page.status(this, e);
                content.removeAllViews();
                boolean empty = query.trim().isEmpty();
                TextView head = Ui.text(this, getString(radio ? (empty ? R.string.music_radio_top : R.string.music_radio_found)
                        : (empty ? R.string.music_charts : R.string.music_songs_found)), 17, Color.WHITE, true);
                head.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, Ui.dp(this, 2));
                content.addView(head);
                TextView note = Ui.text(this, getString(radio ? R.string.music_radio_note : R.string.music_songs_note), 12,
                        getColor(R.color.text_muted), false);
                note.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 4));
                content.addView(note);
                for (int i = 0; i < l.size(); i++) content.addView(row(l, i));
                if (l.isEmpty() && e == null) none();
            });
        });
    }

    private void none() {
        TextView t = Ui.text(this, getString(R.string.search_none), 15, getColor(R.color.text_muted), false);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, 0);
        content.addView(t);
    }

    private View row(List<MusicCatalog.Track> list, int index) {
        MusicCatalog.Track t = list.get(index);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 10);
        r.setPadding(p, p, Ui.dp(this, 12), p);
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ab = new GradientDrawable();
        ab.setCornerRadius(Ui.dp(this, 10));
        ab.setColor(Color.HSVToColor(new float[] {Math.abs(t.title.hashCode()) % 360, 0.45f, 0.55f}));
        art.setBackground(ab);
        art.setClipToOutline(true);
        art.setImageResource(t.live() ? R.drawable.ic_radio : R.drawable.ic_music);
        int ip = Ui.dp(this, 12);
        art.setPadding(ip, ip, ip, ip);
        r.addView(art, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        loadArt(art, t);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, t.title, 15.5f, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        String sub = t.live() ? (t.artist.isEmpty() ? getString(R.string.music_live) : "LIVE · " + t.artist)
                : join(t.artist, t.durationMs > 0 && MusicCatalog.LOCAL.equals(t.kind) ? MusicCatalog.time(t.durationMs) : "");
        TextView s = Ui.text(this, sub, 13, getColor(R.color.text_muted), false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(s);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        r.addView(texts, tl);
        if (MusicCatalog.PREVIEW.equals(t.kind)) {
            TextView more = Ui.text(this, "⋮", 22, Color.WHITE, true);
            more.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
            more.setOnClickListener(v -> fullSong(t));
            r.addView(more);
        }
        r.setOnClickListener(v -> {
            // a station alone; songs: the list from here on (the next one follows by itself)
            if (t.live()) music.play(Collections.singletonList(t), 0);
            else music.play(list, index);
        });
        r.setOnLongClickListener(v -> {
            if (MusicCatalog.PREVIEW.equals(t.kind)) fullSong(t);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);
        return r;
    }

    private void loadArt(ImageView iv, MusicCatalog.Track t) {
        if (t.art.startsWith("https://")) {
            ImageLoader.load(iv, t.art, null, () -> iv.setPadding(0, 0, 0, 0));
            return;
        }
        if (!MusicCatalog.LOCAL.equals(t.kind)) return;
        Bitmap cached = covers.get(t.url);
        if (cached != null) {
            iv.setPadding(0, 0, 0, 0);
            iv.setImageBitmap(cached);
            return;
        }
        iv.setTag(t.url);
        worker.execute(() -> {
            Bitmap b = Music.bitmap(this, t, 160);
            if (b == null) return;
            covers.put(t.url, b);
            runOnUiThread(() -> {
                if (!t.url.equals(iv.getTag())) return;
                iv.setPadding(0, 0, 0, 0);
                iv.setImageBitmap(b);
            });
        });
    }

    /** The preview is 30 seconds: the whole song in Deezer, Spotify or YouTube Music. */
    private void fullSong(MusicCatalog.Track t) {
        List<String> names = new ArrayList<>(), urls = new ArrayList<>();
        if (!t.link.isEmpty()) {
            names.add("Deezer");
            urls.add(t.link);
        }
        names.add("Spotify");
        urls.add(MusicCatalog.spotifySearch(t));
        names.add("YouTube Music");
        urls.add(MusicCatalog.youtubeSearch(t));
        new AlertDialog.Builder(this).setTitle(getString(R.string.music_full_song, t.title))
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls.get(w))));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private TextView pill(String text, View.OnClickListener click) {
        TextView t = Ui.text(this, text, 14, Color.WHITE, true);
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------ the player at the bottom

    private void addPlayer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(HomeWidgets.glass(this, 0xB14CD8));  // glass design
        box.setElevation(Ui.dp(this, 6));
        // tap: the big player (cover, time bar, big buttons)
        box.setOnClickListener(v -> startActivity(new Intent(this, NowPlayingActivity.class)));
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        pCover = new ImageView(this);
        pCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable cb = new GradientDrawable();
        cb.setCornerRadius(Ui.dp(this, 12));
        cb.setColor(0x33FFFFFF);
        pCover.setBackground(cb);
        pCover.setClipToOutline(true);
        top.addView(pCover, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        pTitle = Ui.text(this, "", 15.5f, Color.WHITE, true);
        pTitle.setSingleLine(true);
        pTitle.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(pTitle);
        pSub = Ui.text(this, "", 12.5f, 0xCCFFFFFF, false);
        pSub.setSingleLine(true);
        pSub.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(pSub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        top.addView(texts, tl);
        top.addView(control(R.drawable.ic_skip_prev, R.string.music_previous, v -> music.previous()));
        pToggle = control(R.drawable.ic_play_s, R.string.music_play, v -> music.toggle());
        top.addView(pToggle);
        top.addView(control(R.drawable.ic_skip_next, R.string.music_next, v -> music.next()));
        box.addView(top);
        pSeek = new SeekBar(this);
        pSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) pSub.setText(MusicCatalog.time(p) + " / " + MusicCatalog.time(s.getMax()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                music.seekTo(s.getProgress());
            }
        });
        box.addView(pSeek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        // where the sound comes out; tap: Bluetooth settings (connect headphones or a speaker)
        pOutput = Ui.text(this, "", 13, 0xFF9DB8FF, true);
        pOutput.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, Ui.dp(this, 2));
        pOutput.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        });
        box.addView(pOutput);
        player = box;
        ViewGroup column = (ViewGroup) findViewById(R.id.scroller).getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = getResources().getDimensionPixelSize(R.dimen.side);
        lp.setMargins(side, Ui.dp(this, 6), side, Ui.dp(this, 10));
        column.addView(box, lp);
    }

    private ImageButton control(int icon, int label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        lp.leftMargin = Ui.dp(this, 4);
        b.setLayoutParams(lp);
        return b;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updatePosition();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    public void musicChanged() {
        MusicCatalog.Track t = music.current();
        player.setVisibility(t == null ? View.GONE : View.VISIBLE);
        showOutput();
        if (t == null) return;
        String now = music.nowPlaying();
        pTitle.setText(!now.isEmpty() ? now : t.title);
        Bitmap c = music.cover();
        if (c != null) {
            pCover.setPadding(0, 0, 0, 0);
            pCover.setImageBitmap(c);
        } else {
            int ip = Ui.dp(this, 14);
            pCover.setPadding(ip, ip, ip, ip);
            pCover.setImageResource(t.live() ? R.drawable.ic_radio : R.drawable.ic_music);
        }
        pToggle.setImageResource(music.playing() ? R.drawable.ic_pause : R.drawable.ic_play_s);
        pToggle.setContentDescription(getString(music.playing() ? R.string.music_pause : R.string.music_play));
        pSeek.setVisibility(t.live() ? View.GONE : View.VISIBLE);
        updatePosition();
    }

    private void updatePosition() {
        MusicCatalog.Track t = music.current();
        if (t == null || seeking) return;
        String now = music.nowPlaying();
        String state = !music.error().isEmpty() ? music.error() : music.loading() ? getString(R.string.music_loading) : "";
        if (t.live()) {
            pSub.setText(!state.isEmpty() ? state : "● LIVE · " + (now.isEmpty() ? t.artist.isEmpty() ? getString(R.string.music_live) : t.artist : t.title));
            return;
        }
        long d = music.duration(), pos = music.position();
        pSeek.setMax((int) Math.max(1, d));
        pSeek.setProgress((int) pos);
        String time = MusicCatalog.time(pos) + " / " + MusicCatalog.time(d);
        String who = join(t.artist, MusicCatalog.PREVIEW.equals(t.kind) ? getString(R.string.music_preview) : "");
        pSub.setText(!state.isEmpty() ? state : join(time, who));
    }

    private static String join(String a, String b) {
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "  ·  " + b;
    }

    private void showOutput() {
        String name = null;
        AudioManager am = getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = d.getType();
            boolean bt = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31
                    && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER));
            boolean wired = type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET;
            if (bt) {
                name = "🎧 " + getString(R.string.music_output, String.valueOf(d.getProductName()));
                break;
            }
            if (wired && name == null) name = "🎧 " + getString(R.string.music_output, getString(R.string.music_headphones));
        }
        pOutput.setText(name != null ? name + "  ·  " + getString(R.string.music_bluetooth)
                : "🔊 " + getString(R.string.music_output, getString(R.string.music_speaker)) + "  ·  " + getString(R.string.music_bluetooth_connect));
    }
}
