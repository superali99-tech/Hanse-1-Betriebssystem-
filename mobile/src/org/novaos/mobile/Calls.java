package org.novaos.mobile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;

/** Calls in Nova Chat: buttons in a chat, incoming calls (ringing notification). */
final class Calls {
    static final int NOTIFICATION = 7700;
    private static final String CHANNEL = "calls";

    private Calls() {}

    /** Voice and video call buttons in the header of a chat. */
    static void addButtons(Activity room, String roomId) {
        ViewGroup header = (ViewGroup) room.findViewById(R.id.page_title).getParent().getParent();
        header.addView(button(room, R.drawable.ic_call, R.string.call_voice, roomId, false));
        header.addView(button(room, R.drawable.ic_video, R.string.call_video, roomId, true));
    }

    private static ImageButton button(Activity a, int icon, int label, String roomId, boolean video) {
        ImageButton b = new ImageButton(a);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(a, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(a.getString(label));
        b.setOnClickListener(v -> {
            ChatDb.RoomRow r = ChatDb.get(a).room(roomId);
            if (r == null || r.directUser.isEmpty()) {
                Toast.makeText(a, R.string.call_direct_only, Toast.LENGTH_LONG).show();
                return;
            }
            a.startActivity(new Intent(a, CallActivity.class).putExtra(CallActivity.EXTRA_ROOM, roomId)
                    .putExtra(CallActivity.EXTRA_VIDEO, video).putExtra(CallActivity.EXTRA_NAME, r.name));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
        lp.leftMargin = Ui.dp(a, 8);
        b.setLayoutParams(lp);
        return b;
    }

    /** Someone calls: ringing over the lock screen, or a notification with Accept / Decline. */
    static void incoming(Context c, MatrixSync.CallEvent e) {
        Object callId = e.content.get("call_id");
        Object sdp = MiniJson.get(e.content, "offer", "sdp");
        if (!(callId instanceof String) || !(sdp instanceof String)) return;
        boolean video = ((String) sdp).contains("m=video");
        ChatDb.RoomRow room = ChatDb.get(c).room(e.roomId);
        String name = room != null && !room.name.isEmpty() ? room.name : MatrixSync.localpart(e.sender);
        Object party = e.content.get("party_id");
        Intent open = new Intent(c, CallActivity.class).putExtra(CallActivity.EXTRA_ROOM, e.roomId)
                .putExtra(CallActivity.EXTRA_INCOMING, true).putExtra(CallActivity.EXTRA_CALL_ID, (String) callId)
                .putExtra(CallActivity.EXTRA_OFFER, (String) sdp).putExtra(CallActivity.EXTRA_VIDEO, video)
                .putExtra(CallActivity.EXTRA_NAME, name).putExtra(CallActivity.EXTRA_PARTY, party instanceof String ? (String) party : null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.call_channel), NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent full = PendingIntent.getActivity(c, 11, open, flags);
            PendingIntent accept = PendingIntent.getActivity(c, 12, new Intent(open).putExtra(CallActivity.EXTRA_ACCEPT, true), flags);
            PendingIntent decline = PendingIntent.getBroadcast(c, 13, new Intent(c, Decline.class)
                    .putExtra(CallActivity.EXTRA_ROOM, e.roomId).putExtra(CallActivity.EXTRA_CALL_ID, (String) callId), flags);
            @SuppressWarnings("deprecation")
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_call).setColor(c.getColor(R.color.nova_violet))
                    .setContentTitle(name).setContentText(c.getString(video ? R.string.call_incoming_video : R.string.call_incoming))
                    .setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_MAX).setOngoing(true)
                    .setAutoCancel(true).setContentIntent(full).setFullScreenIntent(full, true)
                    .addAction(new Notification.Action.Builder(null, c.getString(R.string.call_decline), decline).build())
                    .addAction(new Notification.Action.Builder(null, c.getString(R.string.call_accept), accept).build());
            if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(60_000);
            nm.notify(NOTIFICATION, b.build());
        }
        try {
            c.startActivity(open);  // allowed while NovaOS is in front; otherwise the notification rings
        } catch (RuntimeException ignored) {
            // notification only
        }
    }

    static void cancelRinging(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION);
    }

    /** "Ablehnen" in the notification: tells the caller without opening anything. */
    public static class Decline extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            cancelRinging(c);
            String room = i.getStringExtra(CallActivity.EXTRA_ROOM), callId = i.getStringExtra(CallActivity.EXTRA_CALL_ID);
            if (room == null || callId == null || !ChatDb.loggedIn(c)) return;
            PendingResult pending = goAsync();
            MatrixApi api = ChatDb.api(c);
            new Thread(() -> {
                try {
                    api.sendEvent(room, "m.call.reject", MiniJson.obj("call_id", callId, "party_id", "nova" + Long.toHexString(System.nanoTime()),
                            "version", "1"));
                } catch (IOException ignored) {
                    // the caller times out
                }
                pending.finish();
            }).start();
        }
    }
}
