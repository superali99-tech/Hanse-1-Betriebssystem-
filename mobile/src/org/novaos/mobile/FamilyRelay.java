package org.novaos.mobile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The relay between the family's phones: ntfy (ntfy.sh, free and without an account, or an
 * own ntfy server). It only carries encrypted text for a few hours; the phones keep the state.
 * No Android classes.
 */
final class FamilyRelay {
    static final class Message {
        String id = "", text = "";
        long time;  // seconds
    }

    interface Receiver {
        void received(Message m);
    }

    private FamilyRelay() {}

    static String url(String base, String topic, String suffix) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (!b.startsWith("https://")) throw new IllegalArgumentException("only https");
        if (!topic.matches("[A-Za-z0-9_\\-]{1,64}")) throw new IllegalArgumentException("bad topic");
        return b + "/" + topic + suffix;
    }

    static void publish(String base, String topic, String text) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(base, topic, "")).openConnection();
        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", "NovaOS-Family");
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("Cache", "yes");
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(data);
            }
            int code = c.getResponseCode();
            drain(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw new IOException("relay HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    /** Messages since (a message id, "all" or e.g. "12h"). */
    static List<Message> poll(String base, String topic, String since) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(base, topic, "/json?poll=1&since=" + since)).openConnection();
        List<Message> out = new ArrayList<>();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "NovaOS-Family");
            if (c.getResponseCode() >= 400) throw new IOException("relay HTTP " + c.getResponseCode());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Message m = parse(line);
                    if (m != null) out.add(m);
                }
            }
        } finally {
            c.disconnect();
        }
        return out;
    }

    /** Waits for new messages (open connection) until the relay or the network closes it. */
    static void stream(String base, String topic, String since, Receiver receiver) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url(base, topic, "/json" + (since.isEmpty() ? "" : "?since=" + since))).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(120000);  // the relay sends a keepalive every ~45 s
            c.setRequestProperty("User-Agent", "NovaOS-Family");
            if (c.getResponseCode() >= 400) throw new IOException("relay HTTP " + c.getResponseCode());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Message m = parse(line);
                    if (m != null) receiver.received(m);
                }
            }
        } finally {
            c.disconnect();
        }
    }

    /** One line of the relay's JSON stream (only "message" events). */
    static Message parse(String line) {
        try {
            Object o = MiniJson.parse(line);
            if (!"message".equals(MiniJson.get(o, "event"))) return null;
            Message m = new Message();
            Object id = MiniJson.get(o, "id"), t = MiniJson.get(o, "time"), msg = MiniJson.get(o, "message");
            m.id = id instanceof String ? (String) id : "";
            m.time = t instanceof Double ? ((Double) t).longValue() : 0;
            m.text = msg instanceof String ? (String) msg : "";
            return m;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void drain(InputStream in) throws IOException {
        if (in == null) return;
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0) if (b.size() < 65536) b.write(buf, 0, n);
        }
    }
}
