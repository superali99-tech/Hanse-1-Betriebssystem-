package org.novaos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Nova Filme: free complete films, series, documentaries and children's
 * programmes from official YouTube channels. The lists come from the channels'
 * public feeds; a video plays inside the app in the official embedded YouTube
 * player (VideoActivity, with YouTube's ads, so the rights holders are paid).
 */
public class NovaFilmeActivity extends Activity {
    static final String EXTRA_CATEGORY = "category";
    private static final long MAX_AGE_MS = 3 * 60 * 60 * 1000L;

    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private LinearLayout content;
    private TextView status;
    private Catalog.Category focusCategory;

    static void start(Activity a, Catalog.Category category) {
        Intent i = new Intent(a, NovaFilmeActivity.class);
        if (category != null) i.putExtra(EXTRA_CATEGORY, category.name());
        a.startActivity(i);
    }

    static int label(Catalog.Category c) {
        switch (c) {
            case FILME: return R.string.cat_filme;
            case SERIEN: return R.string.cat_serien;
            case DOKUS: return R.string.cat_dokus;
            default: return R.string.cat_kinder;
        }
    }

    static int icon(Catalog.Category c) {
        switch (c) {
            case FILME: return R.drawable.ic_movie;
            case SERIEN: return R.drawable.ic_series;
            case DOKUS: return R.drawable.ic_docs;
            default: return R.drawable.ic_kids;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_page);
        ((ImageView) findViewById(R.id.page_icon)).setImageResource(R.drawable.ic_play);
        ((TextView) findViewById(R.id.page_title)).setText(R.string.nova_filme);
        ((TextView) findViewById(R.id.page_subtitle)).setText(R.string.filme_subtitle);
        ((TextView) findViewById(R.id.page_hint)).setText(R.string.filme_hint);
        status = findViewById(R.id.page_status);
        content = findViewById(R.id.content);
        String cat = getIntent().getStringExtra(EXTRA_CATEGORY);
        try {
            focusCategory = cat != null ? Catalog.Category.valueOf(cat) : null;
        } catch (IllegalArgumentException e) {
            focusCategory = null;
        }
        LinearLayout row = Rows.add(this, content, getString(R.string.loading));
        FrameLayout wait = Tiles.info(this, R.string.filme_loading);
        row.addView(wait);
        load(false);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Loads all feeds in parallel (cache first, then internet) and builds the rows. */
    private void load(boolean force) {
        status.setText(R.string.loading);
        new Thread(() -> {
            List<Future<Net.Feed>> jobs = new ArrayList<>();
            for (Catalog.Source s : Catalog.SOURCES) {
                jobs.add(worker.submit(() -> Net.feed(getApplicationContext(), s, force ? 0 : MAX_AGE_MS)));
            }
            List<List<Video>> feeds = new ArrayList<>();
            int ok = 0, stale = 0;
            for (int i = 0; i < jobs.size(); i++) {
                Net.Feed f;
                try {
                    f = jobs.get(i).get();
                } catch (Exception e) {
                    f = null;
                }
                if (f == null) continue;
                if (f.fresh) ok++; else stale++;
                feeds.add(Video.parseFeed(f.xml, Catalog.SOURCES.get(i)));
            }
            Map<Catalog.Category, List<Video>> rows = Catalog.rows(feeds);
            keepEmbeddable(rows);
            final int fresh = ok, old = stale;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                show(rows, fresh, old);
            });
        }, "nova-filme").start();
    }

    /** Removes videos whose owners do not allow the embedded player (checked in parallel). */
    private void keepEmbeddable(Map<Catalog.Category, List<Video>> rows) {
        List<Video> all = new ArrayList<>();
        for (List<Video> l : rows.values()) all.addAll(l);
        List<Future<Boolean>> checks = new ArrayList<>();
        for (Video v : all) checks.add(worker.submit(() -> Net.embeddable(getApplicationContext(), v)));
        java.util.Set<String> blocked = new java.util.HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            try {
                if (!checks.get(i).get()) blocked.add(all.get(i).id);
            } catch (Exception ignored) {
                // keep the video
            }
        }
        for (List<Video> l : rows.values()) {
            for (java.util.Iterator<Video> it = l.iterator(); it.hasNext(); ) {
                if (blocked.contains(it.next().id)) it.remove();
            }
        }
        Net.saveEmbed(getApplicationContext());
    }

    private void show(Map<Catalog.Category, List<Video>> rows, int fresh, int stale) {
        content.removeAllViews();
        int total = 0;
        View focus = null;
        for (Catalog.Category c : Catalog.Category.values()) {
            List<Video> videos = rows.get(c);
            if (videos == null || videos.isEmpty()) continue;
            total += videos.size();
            LinearLayout row = Rows.add(this, content, getString(label(c)) + "  ·  " + videos.size());
            for (Video v : videos) {
                FrameLayout t = Tiles.video(this, v);
                t.setOnClickListener(x -> VideoActivity.start(this, v));  // plays inside NovaOS TV
                row.addView(t);
            }
            if (focus == null || c == focusCategory) focus = Rows.firstFocusable(row);
        }
        if (total == 0) {
            LinearLayout row = Rows.add(this, content, getString(R.string.nova_filme));
            FrameLayout retry = Tiles.action(this, R.drawable.ic_network, R.string.filme_retry, true);
            retry.setOnClickListener(v -> load(true));
            row.addView(retry);
            FrameLayout info = Tiles.info(this, R.string.filme_failed);
            row.addView(info);
            focus = retry;
            status.setText("");
        } else if (fresh == 0 && stale > 0) {
            status.setText(R.string.filme_offline);
        } else {
            status.setText(getString(R.string.filme_count, total));
        }
        Rows.lockEdges(content);
        if (focus != null) {
            View f = focus;
            f.requestFocus();
            // bring the chosen row into view (rows further down when opened via a category tile)
            content.post(() -> {
                View scroller = findViewById(R.id.scroller);
                int[] a = new int[2], b = new int[2];
                f.getLocationInWindow(a);
                scroller.getLocationInWindow(b);
                scroller.scrollBy(0, a[1] - b[1] - Tiles.dp(this, 40));
            });
        }
    }
}
