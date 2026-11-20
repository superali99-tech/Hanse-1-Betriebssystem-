package org.novaos.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** NovaOS Chat on the phone: account, chats and messages (copy of the Matrix server's data). */
final class ChatDb extends SQLiteOpenHelper {
    static final class RoomRow {
        String id, name, lastBody, lastKind, lastSender, inviter, directUser;
        long lastTs;
        int unread;
        boolean invite, encrypted;
    }

    private static ChatDb instance;

    static synchronized ChatDb get(Context c) {
        if (instance == null) instance = new ChatDb(c.getApplicationContext());
        return instance;
    }

    private ChatDb(Context c) {
        super(c, "chat.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rooms (id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', last_body TEXT NOT NULL DEFAULT '', "
                + "last_kind TEXT NOT NULL DEFAULT '', last_sender TEXT NOT NULL DEFAULT '', last_ts INTEGER NOT NULL DEFAULT 0, "
                + "unread INTEGER NOT NULL DEFAULT 0, invite INTEGER NOT NULL DEFAULT 0, inviter TEXT NOT NULL DEFAULT '', "
                + "encrypted INTEGER NOT NULL DEFAULT 0, direct_user TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE messages (event_id TEXT PRIMARY KEY, room_id TEXT NOT NULL, sender TEXT NOT NULL, "
                + "sender_name TEXT NOT NULL DEFAULT '', body TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL, ts INTEGER NOT NULL, "
                + "media TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX messages_room ON messages (room_id, ts)");
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 2: photos, voice messages and files (media details as JSON)
        if (oldV < 2) db.execSQL("ALTER TABLE messages ADD COLUMN media TEXT NOT NULL DEFAULT ''");
    }

    // ------------------------------------------------------------ account (in the app's private preferences)

    static SharedPreferences account(Context c) {
        return c.getSharedPreferences("chat", Context.MODE_PRIVATE);
    }

    static boolean loggedIn(Context c) {
        return !account(c).getString("token", "").isEmpty();
    }

    static String me(Context c) {
        return account(c).getString("user", "");
    }

    static MatrixApi api(Context c) {
        return new MatrixApi(account(c).getString("base", "https://matrix-client.matrix.org"), account(c).getString("token", null));
    }

    static void saveLogin(Context c, String base, Map<?, ?> login) {
        account(c).edit().putString("base", base).putString("user", String.valueOf(login.get("user_id")))
                .putString("token", String.valueOf(login.get("access_token")))
                .putString("device", String.valueOf(login.get("device_id"))).apply();
    }

    void logout(Context c) {
        account(c).edit().clear().apply();
        SQLiteDatabase db = getWritableDatabase();
        db.delete("rooms", null, null);
        db.delete("messages", null, null);
        db.delete("kv", null, null);
    }

    // ------------------------------------------------------------ key/value

    String get(String k) {
        try (Cursor c = getReadableDatabase().query("kv", new String[] {"v"}, "k=?", new String[] {k}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    void put(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    List<MatrixSync.Contact> contacts() {
        String json = get("contacts");
        if (json == null) return new ArrayList<>();
        try {
            return MatrixSync.contacts(MiniJson.parse(json));
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    void setContacts(List<MatrixSync.Contact> contacts) {
        put("contacts", MiniJson.write(MatrixSync.contactsContent(contacts)));
    }

    // ------------------------------------------------------------ rooms + messages

    /** New messages of others in this answer (for notifications). */
    List<MatrixSync.Message> apply(MatrixSync.Result r, String me) {
        List<MatrixSync.Message> fresh = new ArrayList<>();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : r.left) {
                db.delete("rooms", "id=?", new String[] {id});
                db.delete("messages", "room_id=?", new String[] {id});
            }
            for (MatrixSync.Room room : r.rooms) {
                for (String gone : room.redacted) db.delete("messages", "event_id=?", new String[] {gone});
                ContentValues cv = new ContentValues();
                cv.put("id", room.id);
                db.insertWithOnConflict("rooms", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                ContentValues up = new ContentValues();
                if (!room.name.isEmpty()) up.put("name", room.name);
                if (room.unread >= 0) up.put("unread", room.unread);
                up.put("invite", room.invite ? 1 : 0);
                if (!room.inviter.isEmpty()) up.put("inviter", room.inviter);
                if (room.encrypted) up.put("encrypted", 1);
                if (!room.directUser.isEmpty()) up.put("direct_user", room.directUser);
                for (MatrixSync.Message m : room.messages) {
                    if (!m.replaces.isEmpty()) {  // edited message: new text, no new message
                        ContentValues ev = new ContentValues();
                        ev.put("body", m.body == null ? "" : m.body);
                        ev.put("kind", m.kind);
                        ev.put("media", MatrixSync.mediaJson(m));
                        db.update("messages", ev, "event_id=?", new String[] {m.replaces});
                        continue;
                    }
                    ContentValues mv = new ContentValues();
                    mv.put("event_id", m.eventId);
                    mv.put("room_id", m.roomId);
                    mv.put("sender", m.sender);
                    mv.put("sender_name", m.senderName == null ? "" : m.senderName);
                    mv.put("body", m.body == null ? "" : m.body);
                    mv.put("kind", m.kind);
                    mv.put("ts", m.ts);
                    mv.put("media", MatrixSync.mediaJson(m));
                    long row = db.insertWithOnConflict("messages", null, mv, SQLiteDatabase.CONFLICT_IGNORE);
                    if (row != -1 && !m.sender.equals(me) && !"call".equals(m.kind)) fresh.add(m);
                    up.put("last_body", m.body == null ? "" : m.body);
                    up.put("last_kind", m.kind);
                    up.put("last_sender", m.sender);
                    up.put("last_ts", m.ts);
                }
                db.update("rooms", up, "id=?", new String[] {room.id});
            }
            if (r.direct != null) {
                for (Map.Entry<String, List<String>> d : r.direct.entrySet()) {
                    for (String roomId : d.getValue()) {
                        ContentValues dv = new ContentValues();
                        dv.put("direct_user", d.getKey());
                        db.update("rooms", dv, "id=?", new String[] {roomId});
                    }
                }
            }
            if (r.contacts != null) {
                ContentValues cv = new ContentValues();
                cv.put("k", "contacts");
                cv.put("v", MiniJson.write(MatrixSync.contactsContent(r.contacts)));
                db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return fresh;
    }

    void addMessage(MatrixSync.Message m) {
        MatrixSync.Result r = new MatrixSync.Result();
        MatrixSync.Room room = new MatrixSync.Room();
        room.id = m.roomId;
        room.messages.add(m);
        r.rooms.add(room);
        apply(r, m.sender);
    }

    void setUnread(String roomId, int unread) {
        ContentValues cv = new ContentValues();
        cv.put("unread", unread);
        getWritableDatabase().update("rooms", cv, "id=?", new String[] {roomId});
    }

    void removeMessage(String eventId) {
        getWritableDatabase().delete("messages", "event_id=?", new String[] {eventId});
    }

    /** Messages of others since the last one read (for "read aloud"), oldest first. */
    List<MatrixSync.Message> unreadOf(String roomId, int unread, String me) {
        List<MatrixSync.Message> all = messages(roomId), out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < Math.max(1, unread); i--) {
            if (!all.get(i).sender.equals(me)) out.add(0, all.get(i));
        }
        return out;
    }

    void removeRoom(String roomId) {
        getWritableDatabase().delete("rooms", "id=?", new String[] {roomId});
        getWritableDatabase().delete("messages", "room_id=?", new String[] {roomId});
    }

    /** Invitations first, then the newest chats. */
    List<RoomRow> rooms() {
        List<RoomRow> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("rooms", null, null, null, null, null, "invite DESC, last_ts DESC")) {
            while (c.moveToNext()) out.add(row(c));
        }
        for (RoomRow r : out) named(r);
        return out;
    }

    RoomRow room(String id) {
        RoomRow r;
        try (Cursor c = getReadableDatabase().query("rooms", null, "id=?", new String[] {id}, null, null, null)) {
            r = c.moveToFirst() ? row(c) : null;
        }
        if (r != null) named(r);
        return r;
    }

    RoomRow directRoom(String userId) {
        try (Cursor c = getReadableDatabase().query("rooms", null, "direct_user=? AND invite=0", new String[] {userId},
                null, null, "last_ts DESC")) {
            return c.moveToFirst() ? row(c) : null;
        }
    }

    private static RoomRow row(Cursor c) {
        RoomRow r = new RoomRow();
        r.id = c.getString(c.getColumnIndexOrThrow("id"));
        r.name = c.getString(c.getColumnIndexOrThrow("name"));
        r.lastBody = c.getString(c.getColumnIndexOrThrow("last_body"));
        r.lastKind = c.getString(c.getColumnIndexOrThrow("last_kind"));
        r.lastSender = c.getString(c.getColumnIndexOrThrow("last_sender"));
        r.lastTs = c.getLong(c.getColumnIndexOrThrow("last_ts"));
        r.unread = c.getInt(c.getColumnIndexOrThrow("unread"));
        r.invite = c.getInt(c.getColumnIndexOrThrow("invite")) == 1;
        r.inviter = c.getString(c.getColumnIndexOrThrow("inviter"));
        r.encrypted = c.getInt(c.getColumnIndexOrThrow("encrypted")) == 1;
        r.directUser = c.getString(c.getColumnIndexOrThrow("direct_user"));
        if (r.name.isEmpty()) r.name = r.directUser.isEmpty() ? r.id : MatrixSync.localpart(r.directUser);
        return r;
    }

    /** Direct chats are called like the saved contact. */
    private void named(RoomRow r) {
        if (r.directUser.isEmpty()) return;
        for (MatrixSync.Contact c : contacts()) {
            if (c.id.equals(r.directUser)) {
                r.name = c.name;
                return;
            }
        }
    }

    /** The last messages of a chat, oldest first. */
    List<MatrixSync.Message> messages(String roomId) {
        List<MatrixSync.Message> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM (SELECT * FROM messages WHERE room_id=? ORDER BY ts DESC LIMIT 300) "
                + "ORDER BY ts ASC", new String[] {roomId})) {
            while (c.moveToNext()) {
                MatrixSync.Message m = new MatrixSync.Message();
                m.eventId = c.getString(c.getColumnIndexOrThrow("event_id"));
                m.roomId = roomId;
                m.sender = c.getString(c.getColumnIndexOrThrow("sender"));
                m.senderName = c.getString(c.getColumnIndexOrThrow("sender_name"));
                m.body = c.getString(c.getColumnIndexOrThrow("body"));
                m.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                m.ts = c.getLong(c.getColumnIndexOrThrow("ts"));
                MatrixSync.readMedia(m, c.getString(c.getColumnIndexOrThrow("media")));
                out.add(m);
            }
        }
        return out;
    }
}
