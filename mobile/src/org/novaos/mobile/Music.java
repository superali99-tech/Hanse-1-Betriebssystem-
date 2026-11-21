package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.metadata.icy.IcyInfo;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The music player of Nova Musik (one for the whole app, on the main thread): a queue of
 * tracks, ExoPlayer (handles audio focus and pauses when headphones/Bluetooth disconnect),
 * a MediaSession (lock screen, headset and Bluetooth buttons, car displays) and the
 * notification with ⏮ ⏯ ⏭ while MusicService keeps playing in the background.
 */
final class Music {
    interface Listener {
        void musicChanged();
    }

    static final String CHANNEL = "music";
    static final int NOTIFICATION = 7800;
    private static Music instance;

    private final Context app;
    private final ExoPlayer player;
    private final MediaSession session;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private final List<MusicCatalog.Track> queue = new ArrayList<>();
    private Bitmap cover;
    private String coverFor = "";
    private String nowPlaying = "";  // radio: the song the station is playing right now
    private String error = "";

    static synchronized Music get(Context c) {
        if (instance == null) instance = new Music(c.getApplicationContext());
        return instance;
    }

    private Music(Context c) {
        app = c;
        player = new ExoPlayer.Builder(c).setHandleAudioBecomingNoisy(true).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player p, Player.Events events) {
                changed();
            }

            @Override
            public void onMediaItemTransition(MediaItem item, int reason) {
                nowPlaying = "";
                error = "";
            }

            @Override
            public void onMetadata(Metadata metadata) {
                for (int i = 0; i < metadata.length(); i++) {
                    Metadata.Entry e = metadata.get(i);
                    if (e instanceof IcyInfo && ((IcyInfo) e).title != null) nowPlaying = ((IcyInfo) e).title.trim();
                }
                changed();
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                error = app.getString(R.string.music_error);
                changed();
            }
        });
        session = new MediaSession(c, "NovaMusik");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onStop() {
                stop();
            }

            @Override
            public void onSeekTo(long pos) {
                player.seekTo(pos);
            }
        });
        session.setSessionActivity(PendingIntent.getActivity(c, 0, new Intent(c, MusicActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    // ------------------------------------------------------------ control

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Plays the list from position index (a radio station: just that one). */
    void play(List<MusicCatalog.Track> list, int index) {
        if (list.isEmpty()) return;
        queue.clear();
        queue.addAll(list);
        List<MediaItem> items = new ArrayList<>();
        for (MusicCatalog.Track t : list) items.add(new MediaItem.Builder().setUri(Uri.parse(t.url)).setMediaId(t.url).build());
        error = "";
        nowPlaying = "";
        player.setMediaItems(items, Math.max(0, Math.min(index, items.size() - 1)), 0);
        player.prepare();
        player.play();
        session.setActive(true);
        startService();
        changed();
    }

    void play() {
        if (queue.isEmpty()) return;
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0, 0);
        player.play();
        session.setActive(true);
        startService();
    }

    void pause() {
        player.pause();
    }

    void toggle() {
        if (player.isPlaying()) pause();
        else play();
    }

    void next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem();
    }

    void previous() {
        if (player.getCurrentPosition() > 4000 || !player.hasPreviousMediaItem()) player.seekTo(0);
        else player.seekToPreviousMediaItem();
    }

    void seekTo(long ms) {
        player.seekTo(ms);
    }

    void stop() {
        player.stop();
        player.clearMediaItems();
        queue.clear();
        session.setActive(false);
        app.stopService(new Intent(app, MusicService.class));
        changed();
    }

    // ------------------------------------------------------------ state

    MusicCatalog.Track current() {
        int i = player.getCurrentMediaItemIndex();
        return i >= 0 && i < queue.size() ? queue.get(i) : null;
    }

    boolean playing() {
        return player.isPlaying();
    }

    boolean loading() {
        return player.getPlaybackState() == Player.STATE_BUFFERING;
    }

    long position() {
        return Math.max(0, player.getCurrentPosition());
    }

    long duration() {
        long d = player.getDuration();
        return d == C.TIME_UNSET ? 0 : d;
    }

    String nowPlaying() {
        return nowPlaying;
    }

    String error() {
        return error;
    }

    Bitmap cover() {
        return cover;
    }

    MediaSession session() {
        return session;
    }

    private void startService() {
        Intent i = new Intent(app, MusicService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
        } catch (RuntimeException ignored) {
            // Android 12+: not allowed from the background - the music still plays while the app is open
        }
    }

    private void changed() {
        MusicCatalog.Track t = current();
        // lock screen, Bluetooth displays, car: title, artist, cover, position
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | (t != null && !t.live() ? PlaybackState.ACTION_SEEK_TO : 0);
        int state = player.isPlaying() ? PlaybackState.STATE_PLAYING : loading() ? PlaybackState.STATE_BUFFERING
                : t == null ? PlaybackState.STATE_STOPPED : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state, position(), player.isPlaying() ? 1f : 0f).build());
        if (t != null) {
            MediaMetadata.Builder md = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, !nowPlaying.isEmpty() ? nowPlaying : t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, !nowPlaying.isEmpty() ? t.title : t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, t.live() ? -1 : duration());
            if (cover != null && coverFor.equals(t.url)) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover);
            session.setMetadata(md.build());
            if (!coverFor.equals(t.url)) loadCover(t);
        }
        MusicService.update(app);
        for (Listener l : listeners) l.musicChanged();
    }

    private void loadCover(MusicCatalog.Track t) {
        coverFor = t.url;
        cover = null;
        worker.execute(() -> {
            Bitmap b = bitmap(app, t, 512);
            ui.post(() -> {
                if (!t.url.equals(coverFor)) return;
                cover = b;
                changed();
            });
        });
    }

    /** Cover of a track: the album art of a song on the phone, or the picture from the internet (https). */
    static Bitmap bitmap(Context c, MusicCatalog.Track t, int size) {
        try {
            if (MusicCatalog.LOCAL.equals(t.kind)) {
                if (Build.VERSION.SDK_INT >= 29) return c.getContentResolver().loadThumbnail(Uri.parse(t.url), new Size(size, size), null);
                if (t.art.isEmpty()) return null;
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(t.art))) {
                    return in == null ? null : BitmapFactory.decodeStream(in);
                }
            }
            if (!t.art.startsWith("https://")) return null;
            HttpURLConnection con = (HttpURLConnection) new URL(t.art).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            try (InputStream in = con.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static Uri albumArt(long albumId) {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
    }

    // ------------------------------------------------------------ notification

    Notification notification() {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, app.getString(R.string.app_music), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        MusicCatalog.Track t = current();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, CHANNEL) : new Notification.Builder(app);
        b.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(t == null ? app.getString(R.string.app_music) : !nowPlaying.isEmpty() ? nowPlaying : t.title)
                .setContentText(t == null ? "" : !nowPlaying.isEmpty() ? t.title : t.artist)
                .setContentIntent(session.getController().getSessionActivity())
                .setDeleteIntent(action("stop"))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(R.drawable.ic_skip_prev, app.getString(R.string.music_previous), action("prev")).build())
                .addAction(new Notification.Action.Builder(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_s,
                        app.getString(player.isPlaying() ? R.string.music_pause : R.string.music_play), action("toggle")).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_skip_next, app.getString(R.string.music_next), action("next")).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (cover != null) b.setLargeIcon(cover);
        return b.build();
    }

    private PendingIntent action(String what) {
        Intent i = new Intent(app, MusicService.class).setAction(MusicService.ACTION_PREFIX + what);
        return PendingIntent.getService(app, what.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** From the notification buttons (MusicService). */
    void command(String what) {
        switch (what) {
            case "prev": previous(); break;
            case "toggle": toggle(); break;
            case "next": next(); break;
            case "stop": stop(); break;
            default: break;
        }
    }
}
