package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.novaos.tv.Catalog;
import org.novaos.tv.ImageLoader;
import org.novaos.tv.Net;
import org.novaos.tv.Video;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Nova Filme on the phone: the same rows as on NovaOS TV (official YouTube channels,
 * only videos that may be embedded); a tap plays the video inside NovaOS.
 */
public class FilmeActivity extends Activity {
    private static final long MAX_AGE_MS = 3 * 60 * 60 * 1000L;
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.card_filme, getString(R.string.filme_sub));
        Page.status(this, getString(R.string.loading));
        load();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        new Thread(() -> {
            List<Future<Net.Feed>> jobs = new ArrayList<>();
            for (Catalog.Source s : Catalog.SOURCES) {
                jobs.add(worker.submit(() -> Net.feed(getApplicationContext(), s, MAX_AGE_MS)));
            }
            List<List<Video>> feeds = new ArrayList<>();
            int fresh = 0, stale = 0;
            for (int i = 0; i < jobs.size(); i++) {
                Net.Feed f;
                try {
                    f = jobs.get(i).get();
                } catch (Exception e) {
                    f = null;
                }
                if (f == null) continue;
                if (f.fresh) fresh++; else stale++;
                feeds.add(Video.parseFeed(f.xml, Catalog.SOURCES.get(i)));
            }
            Map<Catalog.Category, List<Video>> rows = Catalog.rows(feeds);
            keepEmbeddable(rows);
            final int ok = fresh, old = stale;
            runOnUiThread(() -> {
                if (!isDestroyed()) show(rows, ok, old);
            });
        }, "nova-filme").start();
    }

    private void keepEmbeddable(Map<Catalog.Category, List<Video>> rows) {
        List<Video> all = new ArrayList<>();
        for (List<Video> l : rows.values()) all.addAll(l);
        List<Future<Boolean>> checks = new ArrayList<>();
        for (Video v : all) checks.add(worker.submit(() -> Net.embeddable(getApplicationContext(), v)));
        Set<String> blocked = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            try {
                if (!checks.get(i).get()) blocked.add(all.get(i).id);
            } catch (Exception ignored) {
                // keep the video
            }
        }
        for (List<Video> l : rows.values()) {
            for (Iterator<Video> it = l.iterator(); it.hasNext(); ) if (blocked.contains(it.next().id)) it.remove();
        }
        Net.saveEmbed(getApplicationContext());
    }

    static int label(Catalog.Category c) {
        switch (c) {
            case FILME: return R.string.cat_filme;
            case SERIEN: return R.string.cat_serien;
            case DOKUS: return R.string.cat_dokus;
            default: return R.string.cat_kinder;
        }
    }

    private void show(Map<Catalog.Category, List<Video>> rows, int fresh, int stale) {
        content.removeAllViews();
        int total = 0;
        for (Catalog.Category c : Catalog.Category.values()) {
            List<Video> videos = rows.get(c);
            if (videos == null || videos.isEmpty()) continue;
            total += videos.size();
            TextView title = Ui.section(this, label(c));
            title.setText(getString(label(c)) + "  ·  " + videos.size());
            content.addView(title);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (Video v : videos) row.addView(card(v));
            hs.addView(row);
            content.addView(hs);
        }
        if (total == 0) {
            Page.status(this, getString(R.string.filme_failed));
        } else if (fresh == 0 && stale > 0) {
            Page.status(this, getString(R.string.filme_offline));
        } else {
            Page.status(this, null);
        }
    }

    /** Video card: preview picture (16:9), title and channel below. */
    private View card(Video v) {
        int w = Ui.dp(this, 232);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setClipToOutline(true);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFF161A33);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(iv, new LinearLayout.LayoutParams(w, w * 9 / 16));
        ImageLoader.load(iv, v.thumbnailUrl());
        String clean = Catalog.cleanTitle(v.title, v.source.name);
        TextView t = Ui.text(this, clean, 14, getColor(R.color.text), true);
        t.setMaxLines(2);
        t.setMinLines(2);  // all cards of a row get the same height
        t.setEllipsize(TextUtils.TruncateAt.END);
        int p = Ui.dp(this, 10);
        t.setPadding(p, p, p, 0);
        card.addView(t, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView s = Ui.text(this, v.source.name, 12, 0xFFC9D2FF, false);
        s.setPadding(p, Ui.dp(this, 2), p, p);
        card.addView(s);
        card.setClickable(true);
        card.setContentDescription(clean + ", " + v.source.name);
        card.setOnClickListener(x -> startActivity(new Intent(this, VideoActivity.class)
                .putExtra(VideoActivity.EXTRA_ID, v.id)
                .putExtra(VideoActivity.EXTRA_TITLE, clean)
                .putExtra(VideoActivity.EXTRA_SOURCE, v.source.name)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 12);
        card.setLayoutParams(lp);
        return card;
    }
}
