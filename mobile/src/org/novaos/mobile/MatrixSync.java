package org.novaos.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the answer of Matrix /sync for NovaOS Chat: rooms with names, new messages,
 * invitations, unread counts, direct chats and the contacts saved in the account
 * ("org.novaos.contacts"). Android-independent, tested.
 */
public final class MatrixSync {
    public static final String CONTACTS_TYPE = "org.novaos.contacts";

    public static final class Message {
        public String roomId, eventId, sender, senderName, body, kind;  // kind: text, image, file, encrypted, call, notice
        public long ts;
    }

    /** A call signal (m.call.invite/answer/candidates/hangup) for the call screen. */
    public static final class CallEvent {
        public String roomId, sender, type;
        public Map<?, ?> content;
        public long ts;
    }

    public static final class Room {
        public String id, name = "", inviter = "", directUser = "";
        public boolean invite, encrypted;
        public int unread = -1;  // -1 = unknown
        public final List<Message> messages = new ArrayList<>();
        public final Map<String, String> names = new LinkedHashMap<>();
        final List<String> heroes = new ArrayList<>();
    }

    public static final class Contact {
        public final String name, id;

        public Contact(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    public static final class Result {
        public String nextBatch = "";
        public final List<Room> rooms = new ArrayList<>();
        public final List<String> left = new ArrayList<>();
        public final List<CallEvent> calls = new ArrayList<>();
        public Map<String, List<String>> direct;   // null = unchanged
        public List<Contact> contacts;              // null = unchanged
    }

    private MatrixSync() {}

    public static Result parse(String json, String me) {
        Object root = MiniJson.parse(json);
        Result r = new Result();
        Object nb = MiniJson.get(root, "next_batch");
        if (nb instanceof String) r.nextBatch = (String) nb;
        for (Object e : list(MiniJson.get(root, "account_data", "events"))) {
            Object type = MiniJson.get(e, "type"), content = MiniJson.get(e, "content");
            if ("m.direct".equals(type) && content instanceof Map) {
                r.direct = new LinkedHashMap<>();
                for (Map.Entry<?, ?> d : ((Map<?, ?>) content).entrySet()) {
                    List<String> ids = new ArrayList<>();
                    for (Object id : list(d.getValue())) if (id instanceof String) ids.add((String) id);
                    r.direct.put(String.valueOf(d.getKey()), ids);
                }
            } else if (CONTACTS_TYPE.equals(type)) {
                r.contacts = contacts(content);
            }
        }
        Object join = MiniJson.get(root, "rooms", "join");
        if (join instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) join).entrySet()) r.rooms.add(joined(String.valueOf(e.getKey()), e.getValue(), me, r));
        }
        Object invite = MiniJson.get(root, "rooms", "invite");
        if (invite instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) invite).entrySet()) r.rooms.add(invited(String.valueOf(e.getKey()), e.getValue(), me));
        }
        Object leave = MiniJson.get(root, "rooms", "leave");
        if (leave instanceof Map) for (Object id : ((Map<?, ?>) leave).keySet()) r.left.add(String.valueOf(id));
        if (r.direct != null) {
            for (Room room : r.rooms) {
                for (Map.Entry<String, List<String>> d : r.direct.entrySet()) {
                    if (d.getValue().contains(room.id)) room.directUser = d.getKey();
                }
            }
        }
        return r;
    }

    /** Contacts of the account data content {"contacts":[{"name":..,"id":..}]}. */
    public static List<Contact> contacts(Object content) {
        List<Contact> out = new ArrayList<>();
        for (Object c : list(MiniJson.get(content, "contacts"))) {
            Object n = MiniJson.get(c, "name"), id = MiniJson.get(c, "id");
            if (id instanceof String && validUserId((String) id)) {
                out.add(new Contact(n instanceof String && !((String) n).isEmpty() ? (String) n : (String) id, (String) id));
            }
        }
        return out;
    }

    public static Map<String, Object> contactsContent(List<Contact> contacts) {
        List<Object> l = new ArrayList<>();
        for (Contact c : contacts) l.add(MiniJson.obj("name", c.name, "id", c.id));
        return MiniJson.obj("contacts", l);
    }

    /** "@name:server.tld" */
    public static boolean validUserId(String id) {
        return id != null && id.matches("@[a-z0-9._=\\-/+]+:[A-Za-z0-9.\\-]+(:[0-9]{1,5})?");
    }

    /** What the user typed ("lisa", "@lisa", "lisa:matrix.org") as a Matrix ID on the own server. */
    public static String userId(String typed, String myServer) {
        String t = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return null;
        if (!t.startsWith("@")) t = "@" + t;
        if (!t.contains(":")) t = t + ":" + myServer;
        return validUserId(t) ? t : null;
    }

    /** "@lisa:matrix.org" -> "matrix.org" */
    public static String server(String userId) {
        int i = userId == null ? -1 : userId.indexOf(':');
        return i < 0 ? "" : userId.substring(i + 1);
    }

    /** "@lisa:matrix.org" -> "lisa" */
    public static String localpart(String userId) {
        if (userId == null) return "";
        int i = userId.indexOf(':');
        return (i > 0 ? userId.substring(1, i) : userId.replace("@", ""));
    }

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : new ArrayList<>();
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }

    private static Room joined(String id, Object data, String me, Result r) {
        Room room = new Room();
        room.id = id;
        Object n = MiniJson.get(data, "unread_notifications", "notification_count");
        if (n instanceof Double) room.unread = ((Double) n).intValue();
        for (Object h : list(MiniJson.get(data, "summary", "m.heroes"))) if (h instanceof String) room.heroes.add((String) h);
        for (Object e : list(MiniJson.get(data, "state", "events"))) state(room, e);
        for (Object e : list(MiniJson.get(data, "timeline", "events"))) {
            state(room, e);
            String type = str(MiniJson.get(e, "type"));
            if (type.startsWith("m.call.")) {
                CallEvent c = new CallEvent();
                c.roomId = id;
                c.sender = str(MiniJson.get(e, "sender"));
                c.type = type;
                Object content = MiniJson.get(e, "content");
                c.content = content instanceof Map ? (Map<?, ?>) content : new LinkedHashMap<>();
                Object ts = MiniJson.get(e, "origin_server_ts");
                c.ts = ts instanceof Double ? ((Double) ts).longValue() : 0;
                r.calls.add(c);
            }
            Message m = message(id, e);
            if (m != null) room.messages.add(m);
        }
        for (Message m : room.messages) {
            String name = room.names.get(m.sender);
            m.senderName = name != null && !name.isEmpty() ? name : localpart(m.sender);
        }
        room.name = name(room, me);
        return room;
    }

    private static void state(Room room, Object e) {
        String type = str(MiniJson.get(e, "type"));
        if (type.equals("m.room.name")) {
            room.name = str(MiniJson.get(e, "content", "name"));
        } else if (type.equals("m.room.member")) {
            String user = str(MiniJson.get(e, "state_key"));
            String membership = str(MiniJson.get(e, "content", "membership"));
            String dn = str(MiniJson.get(e, "content", "displayname"));
            if (!user.isEmpty() && (membership.equals("join") || membership.equals("invite"))) {
                room.names.put(user, dn.isEmpty() ? localpart(user) : dn);
            } else if (!user.isEmpty() && !room.names.containsKey(user)) {
                room.names.put(user, dn.isEmpty() ? localpart(user) : dn);
            }
        } else if (type.equals("m.room.encryption")) {
            room.encrypted = true;
        }
    }

    private static Message message(String roomId, Object e) {
        String type = str(MiniJson.get(e, "type"));
        Message m = new Message();
        m.roomId = roomId;
        m.eventId = str(MiniJson.get(e, "event_id"));
        m.sender = str(MiniJson.get(e, "sender"));
        Object ts = MiniJson.get(e, "origin_server_ts");
        m.ts = ts instanceof Double ? ((Double) ts).longValue() : 0;
        if (m.eventId.isEmpty()) return null;
        if (type.equals("m.room.message")) {
            String msgtype = str(MiniJson.get(e, "content", "msgtype"));
            m.body = str(MiniJson.get(e, "content", "body"));
            switch (msgtype) {
                case "m.image": m.kind = "image"; break;
                case "m.video": case "m.audio": case "m.file": m.kind = "file"; break;
                case "m.notice": m.kind = "notice"; break;
                default: m.kind = "text"; break;
            }
            if (m.body.isEmpty() && MiniJson.get(e, "unsigned", "redacted_because") == null) return null;
            if (m.body.isEmpty()) return null;
            return m;
        }
        if (type.equals("m.room.encrypted")) {
            m.kind = "encrypted";
            m.body = "";
            return m;
        }
        if (type.equals("m.call.invite") || type.equals("m.call.hangup")) {
            m.kind = "call";
            m.body = type.equals("m.call.invite") ? "invite" : "hangup";
            return m;
        }
        return null;
    }

    /** Room name like Element: own name, else the other people. */
    private static String name(Room room, String me) {
        if (!room.name.isEmpty()) return room.name;
        List<String> others = new ArrayList<>();
        List<String> ids = room.heroes.isEmpty() ? new ArrayList<>(room.names.keySet()) : room.heroes;
        for (String u : ids) {
            if (u.equals(me)) continue;
            String n = room.names.get(u);
            others.add(n != null && !n.isEmpty() ? n : localpart(u));
            if (others.size() == 3) break;
        }
        return others.isEmpty() ? "" : String.join(", ", others);
    }

    private static Room invited(String id, Object data, String me) {
        Room room = new Room();
        room.id = id;
        room.invite = true;
        String inviter = "";
        for (Object e : list(MiniJson.get(data, "invite_state", "events"))) {
            state(room, e);
            if ("m.room.member".equals(MiniJson.get(e, "type")) && me.equals(MiniJson.get(e, "state_key"))) {
                inviter = str(MiniJson.get(e, "sender"));
                if (Boolean.TRUE.equals(MiniJson.get(e, "content", "is_direct"))) room.directUser = inviter;
            }
        }
        room.inviter = inviter;
        String in = room.names.get(inviter);
        room.name = !room.name.isEmpty() ? room.name : in != null && !in.isEmpty() ? in : localpart(inviter);
        return room;
    }
}
