package org.novaos.mobile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Matrix client-server API (matrix.org or any other server) for NovaOS Chat. */
final class MatrixApi {
    /** The server answered with an error, e.g. M_FORBIDDEN "Invalid password". */
    static final class MatrixException extends IOException {
        final int status;
        final String code;

        MatrixException(int status, String code, String message) {
            super(message == null || message.isEmpty() ? code + " (" + status + ")" : message);
            this.status = status;
            this.code = code;
        }
    }

    private static final String CLIENT = "/_matrix/client/v3";
    final String base;
    volatile String token;
    private static long txn = System.currentTimeMillis();

    MatrixApi(String base, String token) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.token = token;
    }

    /** Server address of "matrix.org" or "@lisa:matrix.org" (.well-known), e.g. https://matrix-client.matrix.org */
    static String discover(String serverOrUser) throws IOException {
        String domain = serverOrUser.trim();
        if (domain.startsWith("@") || domain.contains(":") && !domain.startsWith("http")) domain = MatrixSync.server(domain);
        if (domain.startsWith("http://") || domain.startsWith("https://")) return domain.replaceAll("/+$", "");
        try {
            String wk = request("GET", "https://" + domain + "/.well-known/matrix/client", null, null, 10000);
            Object url = MiniJson.get(MiniJson.parse(wk), "m.homeserver", "base_url");
            if (url instanceof String && ((String) url).startsWith("https://")) return ((String) url).replaceAll("/+$", "");
        } catch (IOException | IllegalArgumentException ignored) {
            // no .well-known: the domain itself
        }
        return "https://" + domain;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized String nextTxn() {
        return "nova" + (++txn);
    }

    // ---------------------------------------------------------------- account

    /** Login with user name (or Matrix ID) and password: {user_id, access_token, device_id}. */
    Map<?, ?> login(String user, String password) throws IOException {
        Map<String, Object> body = MiniJson.obj("type", "m.login.password",
                "identifier", MiniJson.obj("type", "m.id.user", "user", user),
                "password", password, "initial_device_display_name", "NovaOS Chat");
        return (Map<?, ?>) MiniJson.parse(call("POST", CLIENT + "/login", body, 20000));
    }

    /** Login with the token of the browser login (SSO). */
    Map<?, ?> loginToken(String loginToken) throws IOException {
        return (Map<?, ?>) MiniJson.parse(call("POST", CLIENT + "/login",
                MiniJson.obj("type", "m.login.token", "token", loginToken, "initial_device_display_name", "NovaOS Chat"), 20000));
    }

    String ssoUrl(String redirect) {
        return base + CLIENT + "/login/sso/redirect?redirectUrl=" + enc(redirect);
    }

    /** Whether the server offers password login. */
    boolean passwordLogin() {
        try {
            return call("GET", CLIENT + "/login", null, 10000).contains("m.login.password");
        } catch (IOException e) {
            return true;
        }
    }

    void logout() {
        try {
            call("POST", CLIENT + "/logout", MiniJson.obj(), 10000);
        } catch (IOException ignored) {
            // the token is forgotten anyway
        }
    }

    String displayName(String userId) throws IOException {
        Object n = MiniJson.get(MiniJson.parse(call("GET", CLIENT + "/profile/" + enc(userId), null, 10000)), "displayname");
        return n instanceof String ? (String) n : "";
    }

    // ---------------------------------------------------------------- sync + rooms

    private static final String FILTER = MiniJson.write(MiniJson.obj(
            "room", MiniJson.obj("timeline", MiniJson.obj("limit", 40), "state", MiniJson.obj("lazy_load_members", true)),
            "presence", MiniJson.obj("not_types", new Object[] {"*"})));

    /** Waits up to timeoutMs for news (long polling). */
    String sync(String since, int timeoutMs) throws IOException {
        String q = "?timeout=" + timeoutMs + "&filter=" + enc(FILTER) + (since == null || since.isEmpty() ? "" : "&since=" + enc(since));
        return call("GET", CLIENT + "/sync" + q, null, timeoutMs + 25000);
    }

    String sendText(String roomId, String text) throws IOException {
        return sendEvent(roomId, "m.room.message", MiniJson.obj("msgtype", "m.text", "body", text));
    }

    String sendEvent(String roomId, String type, Map<String, Object> content) throws IOException {
        Object id = MiniJson.get(MiniJson.parse(call("PUT", CLIENT + "/rooms/" + enc(roomId) + "/send/" + enc(type) + "/" + nextTxn(),
                content, 20000)), "event_id");
        return id instanceof String ? (String) id : "";
    }

    /** New direct chat with someone (not encrypted, so NovaOS Chat can read it). */
    String createDirect(String userId) throws IOException {
        Object id = MiniJson.get(MiniJson.parse(call("POST", CLIENT + "/createRoom",
                MiniJson.obj("preset", "trusted_private_chat", "is_direct", true, "invite", new Object[] {userId}), 20000)), "room_id");
        if (!(id instanceof String)) throw new IOException("no room");
        return (String) id;
    }

    void join(String roomId) throws IOException {
        call("POST", CLIENT + "/join/" + enc(roomId), MiniJson.obj(), 20000);
    }

    void leave(String roomId) throws IOException {
        call("POST", CLIENT + "/rooms/" + enc(roomId) + "/leave", MiniJson.obj(), 20000);
    }

    void read(String roomId, String eventId) {
        try {
            call("POST", CLIENT + "/rooms/" + enc(roomId) + "/read_markers",
                    MiniJson.obj("m.fully_read", eventId, "m.read", eventId), 10000);
        } catch (IOException ignored) {
            // only the unread counter
        }
    }

    // ---------------------------------------------------------------- account data (contacts, direct chats)

    String accountData(String userId, String type) throws IOException {
        try {
            return call("GET", CLIENT + "/user/" + enc(userId) + "/account_data/" + enc(type), null, 10000);
        } catch (MatrixException e) {
            if (e.status == 404) return "{}";
            throw e;
        }
    }

    void putAccountData(String userId, String type, Map<String, Object> content) throws IOException {
        call("PUT", CLIENT + "/user/" + enc(userId) + "/account_data/" + enc(type), content, 15000);
    }

    /** Remembers the room as direct chat with userId (m.direct, like Element). */
    void markDirect(String me, String userId, String roomId) throws IOException {
        Object current = MiniJson.parse(accountData(me, "m.direct"));
        Map<String, Object> content = MiniJson.obj();
        if (current instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) current).entrySet()) content.put(String.valueOf(e.getKey()), e.getValue());
        }
        List<Object> rooms = new ArrayList<>();
        Object old = content.get(userId);
        if (old instanceof List) rooms.addAll((List<?>) old);
        if (!rooms.contains(roomId)) rooms.add(roomId);
        content.put(userId, rooms);
        putAccountData(me, "m.direct", content);
    }

    /** Deletes a message (for everyone in the chat). */
    void redact(String roomId, String eventId) throws IOException {
        call("PUT", CLIENT + "/rooms/" + enc(roomId) + "/redact/" + enc(eventId) + "/" + nextTxn(), MiniJson.obj(), 20000);
    }

    // ---------------------------------------------------------------- media (photos, voice messages)

    private static final String MEDIA_V1 = "/_matrix/client/v1/media", MEDIA_V3 = "/_matrix/media/v3";

    /** Uploads a file (photo, voice message) and returns its mxc:// address. */
    String upload(byte[] data, String mime, String fileName) throws IOException {
        String text = send("POST", base + MEDIA_V3 + "/upload?filename=" + enc(fileName), data, mime, token, 180000);
        Object uri = MiniJson.get(MiniJson.parse(text), "content_uri");
        if (!(uri instanceof String) || MatrixSync.mxc((String) uri) == null) throw new IOException("upload failed");
        return (String) uri;
    }

    /**
     * Downloads a photo, voice message or file into target; thumbPx > 0 = small preview.
     * With login (matrix.org requires it since 2024), older servers without.
     */
    void download(String mxc, File target, int thumbPx) throws IOException {
        String[] m = MatrixSync.mxc(mxc);
        if (m == null) throw new IOException("bad media address");
        String path = (thumbPx > 0 ? "/thumbnail/" : "/download/") + m[0] + "/" + m[1]
                + (thumbPx > 0 ? "?width=" + thumbPx + "&height=" + thumbPx + "&method=scale" : "");
        try {
            fetch(base + MEDIA_V1 + path, token, target);
        } catch (MatrixException e) {
            if (e.status != 404 && e.status != 400 && e.status != 405 && e.status != 501) throw e;
            fetch(base + MEDIA_V3 + path, token, target);
        }
    }

    private static void fetch(String url, String token, File target) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        File part = new File(target.getPath() + ".part");
        try {
            con.setConnectTimeout(15000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "NovaOS-Chat");
            if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            int status = con.getResponseCode();
            if (status >= 400) {
                String text = read(con.getErrorStream());
                Object c = null;
                try {
                    c = MiniJson.get(MiniJson.parse(text), "errcode");
                } catch (IllegalArgumentException ignored) {
                    // not JSON
                }
                throw new MatrixException(status, c instanceof String ? (String) c : "HTTP", "");
            }
            long total = 0;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > 100L * 1024 * 1024) throw new IOException("too large");
                }
            }
            if (!part.renameTo(target)) throw new IOException("cannot save");
        } finally {
            con.disconnect();
            if (part.exists() && !part.delete()) part.deleteOnExit();
        }
    }

    /** TURN servers of the home server for calls ({uris, username, password}). */
    String turnServer() throws IOException {
        return call("GET", CLIENT + "/voip/turnServer", null, 10000);
    }

    // ---------------------------------------------------------------- HTTP

    private String call(String method, String path, Map<String, Object> body, int timeoutMs) throws IOException {
        return request(method, base + path, body == null ? null : MiniJson.write(body), token, timeoutMs);
    }

    private static String request(String method, String url, String json, String token, int timeoutMs) throws IOException {
        return send(method, url, json == null ? null : json.getBytes(StandardCharsets.UTF_8), "application/json", token, timeoutMs);
    }

    private static String send(String method, String url, byte[] data, String type, String token, int timeoutMs) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("only https");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod(method);
            con.setConnectTimeout(15000);
            con.setReadTimeout(timeoutMs);
            con.setRequestProperty("User-Agent", "NovaOS-Chat");
            con.setRequestProperty("Accept", "application/json");
            if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
            if (data != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", type);
                con.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = con.getOutputStream()) {
                    out.write(data);
                }
            }
            int status = con.getResponseCode();
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            String text = read(in);
            if (status >= 400) {
                String code = "", error = "";
                try {
                    Object j = MiniJson.parse(text);
                    Object c = MiniJson.get(j, "errcode"), e = MiniJson.get(j, "error");
                    code = c instanceof String ? (String) c : "";
                    error = e instanceof String ? (String) e : "";
                } catch (IllegalArgumentException ignored) {
                    // not JSON
                }
                throw new MatrixException(status, code.isEmpty() ? "HTTP" : code, error);
            }
            return text;
        } finally {
            con.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > 16 * 1024 * 1024) throw new IOException("too large");
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
