package org.novaos.mobile;

import android.content.Context;

import org.novaos.tv.Channel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The internet TV channels (res/raw/sender.m3u, shared with NovaOS TV). */
final class Channels {
    private Channels() {}

    private static List<Channel> list;

    static synchronized List<Channel> get(Context c) {
        if (list == null) {
            List<Channel> l = new ArrayList<>();
            try (InputStream in = c.getResources().openRawResource(R.raw.sender)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                l = Channel.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // empty list
            }
            list = Collections.unmodifiableList(l);
        }
        return list;
    }
}
