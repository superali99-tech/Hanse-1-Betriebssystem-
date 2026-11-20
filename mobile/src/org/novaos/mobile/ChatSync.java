package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps NovaOS Chat up to date: while NovaOS runs (as home screen it nearly always does)
 * it waits for news from the Matrix server (long polling); in addition a background job
 * looks every 15 minutes. New messages become notifications.
 */
final class ChatSync {
    interface Listener {
        void onChatChanged();
    }

    /** The chat that is open on the screen (no notification for it). */
    static volatile String openRoom;

    private static ChatSync instance;
    private final Context app;
    private final Object lock = new Object();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<CallListener> callListeners = new CopyOnWriteArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread loop;
    // call signals of the last minutes: the call screen picks up what came before it opened
    private final List<MatrixSync.CallEvent> recentCalls = new java.util.ArrayList<>();

    interface CallListener {
        void onCallEvent(MatrixSync.CallEvent e);
    }

    static synchronized ChatSync get(Context c) {
        if (instance == null) instance = new ChatSync(c.getApplicationContext());
        return instance;
    }

    private ChatSync(Context app) {
        this.app = app;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    void addCallListener(CallListener l) {
        callListeners.add(l);
    }

    void removeCallListener(CallListener l) {
        callListeners.remove(l);
    }

    synchronized void start() {
        if (running || !ChatDb.loggedIn(app)) return;
        running = true;
        loop = new Thread(this::run, "nova-chat-sync");
        loop.start();
        ChatJob.schedule(app);
    }

    synchronized void stop() {
        running = false;
        if (loop != null) loop.interrupt();
        loop = null;
    }

    private void run() {
        int backoff = 2;
        while (running) {
            try {
                once(30000);
                backoff = 2;
            } catch (MatrixApi.MatrixException e) {
                if (e.status == 401) {  // logged out elsewhere
                    running = false;
                    changed();
                    return;
                }
                sleep(backoff);
                backoff = Math.min(backoff * 2, 120);
            } catch (IOException | RuntimeException e) {
                sleep(backoff);
                backoff = Math.min(backoff * 2, 120);
            }
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** One round: news from the server into the phone, notifications for new messages. */
    void once(int timeoutMs) throws IOException {
        synchronized (lock) {
            if (!ChatDb.loggedIn(app)) return;
            ChatDb db = ChatDb.get(app);
            String since = db.get("since");
            String me = ChatDb.me(app);
            String json = ChatDb.api(app).sync(since, since == null ? 0 : timeoutMs);
            MatrixSync.Result r = MatrixSync.parse(json, me);
            List<MatrixSync.Message> fresh = db.apply(r, me);
            if (!r.nextBatch.isEmpty()) db.put("since", r.nextBatch);
            if (since != null) {
                notifyMessages(fresh);
                for (MatrixSync.Room room : r.rooms) if (room.invite && !room.inviter.isEmpty()) notifyInvite(room);
                long now = System.currentTimeMillis();
                synchronized (recentCalls) {
                    for (int i = recentCalls.size() - 1; i >= 0; i--) {
                        if (now - recentCalls.get(i).ts > 180_000) recentCalls.remove(i);
                    }
                    for (MatrixSync.CallEvent c : r.calls) if (!c.sender.equals(me)) recentCalls.add(c);
                }
                for (MatrixSync.CallEvent c : r.calls) {
                    if (c.sender.equals(me)) continue;
                    if (c.type.equals("m.call.invite") && now - c.ts < 60_000 && callListeners.isEmpty()) Calls.incoming(app, c);
                    for (CallListener l : callListeners) ui.post(() -> l.onCallEvent(c));
                }
            }
            if (since == null || !r.rooms.isEmpty() || !r.left.isEmpty() || r.contacts != null) changed();
        }
    }

    /** Call signals of this call that arrived before the call screen was open. */
    List<MatrixSync.CallEvent> recentCalls(String callId) {
        List<MatrixSync.CallEvent> out = new java.util.ArrayList<>();
        synchronized (recentCalls) {
            for (MatrixSync.CallEvent c : recentCalls) if (callId.equals(c.content.get("call_id"))) out.add(c);
        }
        return out;
    }

    void changed() {
        for (Listener l : listeners) ui.post(l::onChatChanged);
    }

    // ------------------------------------------------------------ notifications

    static final String CHANNEL = "chat";

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.chat_channel),
                        NotificationManager.IMPORTANCE_HIGH));
            }
        }
    }

    private boolean mayNotify() {
        return Build.VERSION.SDK_INT < 33
                || app.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    private void notifyMessages(List<MatrixSync.Message> fresh) {
        if (fresh.isEmpty() || !mayNotify()) return;
        channel(app);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        for (MatrixSync.Message m : fresh) {
            if (m.roomId.equals(openRoom)) continue;
            ChatDb.RoomRow room = ChatDb.get(app).room(m.roomId);
            String title = room != null ? room.name : m.senderName;
            boolean direct = room != null && !room.directUser.isEmpty();
            String text = (direct ? "" : m.senderName + ": ") + preview(app, m.kind, m.body);
            nm.notify(m.roomId.hashCode(), build(app, title, text, openRoomIntent(app, m.roomId, m.roomId.hashCode())));
        }
    }

    private void notifyInvite(MatrixSync.Room room) {
        if (!mayNotify()) return;
        channel(app);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        String who = room.names.containsKey(room.inviter) ? room.names.get(room.inviter) : MatrixSync.localpart(room.inviter);
        nm.notify(room.id.hashCode(), build(app, app.getString(R.string.chat_invite_title),
                app.getString(R.string.chat_invite_text, who), openRoomIntent(app, room.id, room.id.hashCode())));
    }

    static PendingIntent openRoomIntent(Context c, String roomId, int code) {
        return PendingIntent.getActivity(c, code, new Intent(c, RoomActivity.class).putExtra(RoomActivity.EXTRA_ROOM, roomId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    static Notification build(Context c, String title, String text, PendingIntent open) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        return b.setSmallIcon(R.drawable.ic_chat).setColor(c.getColor(R.color.nova_violet)).setContentTitle(title)
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open)
                .setAutoCancel(true).setShowWhen(true).setCategory(Notification.CATEGORY_MESSAGE)
                .setDefaults(Notification.DEFAULT_ALL).setPriority(Notification.PRIORITY_HIGH).build();
    }

    static String preview(Context c, String kind, String body) {
        switch (kind == null ? "" : kind) {
            case "image": return c.getString(R.string.chat_photo);
            case "file": return c.getString(R.string.chat_file) + " " + body;
            case "encrypted": return c.getString(R.string.chat_encrypted_short);
            case "call": return c.getString("invite".equals(body) ? R.string.chat_call_started : R.string.chat_call_ended);
            default: return body;
        }
    }

    static void cancel(Context c, String roomId) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(roomId.hashCode());
    }

    // ------------------------------------------------------------ background job

    /** Looks for new messages every 15 minutes, also when NovaOS is not running. */
    public static class ChatJob extends JobService {
        private static final int ID = 4712;

        static void schedule(Context c) {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(ID) != null) return;
            try {
                js.schedule(new JobInfo.Builder(ID, new ComponentName(c, ChatJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(15 * 60 * 1000L)
                        .setPersisted(true)
                        .build());
            } catch (RuntimeException ignored) {
                // the loop in NovaOS still works
            }
        }

        static void cancel(Context c) {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js != null) js.cancel(ID);
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            new Thread(() -> {
                try {
                    ChatSync.get(getApplicationContext()).once(0);
                } catch (IOException | RuntimeException ignored) {
                    // next time
                }
                jobFinished(params, false);
            }, "nova-chat-job").start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return true;
        }
    }
}
