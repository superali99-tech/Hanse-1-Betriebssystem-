package org.novaos.tv;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All channels the TV receives itself (antenna, cable, satellite) - including
 * private channels such as RTL, ProSieben, Sat.1 or VOX. The list comes from the
 * Android TV channel database; a channel opens in the TV's own live TV app.
 */
public class TunerActivity extends Activity {
    static final String PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";
    private static final int REQUEST = 7;
    private static final int MAX_TILES = 600;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private FlowLayout grid;
    private TextView status;
    private FrameLayout openTile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_page);
        ((ImageView) findViewById(R.id.page_icon)).setImageResource(R.drawable.ic_antenna);
        ((TextView) findViewById(R.id.page_title)).setText(R.string.tuner_title);
        ((TextView) findViewById(R.id.page_subtitle)).setText(R.string.tuner_subtitle);
        ((TextView) findViewById(R.id.page_hint)).setText(R.string.tuner_hint);
        status = findViewById(R.id.page_status);

        grid = new FlowLayout(this);
        int side = getResources().getDimensionPixelSize(R.dimen.margin_side);
        grid.setPadding(side, Tiles.dp(this, 18), side, Tiles.dp(this, 12));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        ((LinearLayout) findViewById(R.id.content)).addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        openTile = Tiles.action(this, R.drawable.ic_antenna, R.string.tuner_open_app, true);
        openTile.setOnClickListener(v -> {
            if (!Actions.liveTv(this)) status.setText(R.string.tuner_no_tv);
        });
        grid.addView(openTile);
        openTile.requestFocus();

        boolean database = true;
        try {
            getPackageManager().getPermissionInfo(PERMISSION, 0);
        } catch (PackageManager.NameNotFoundException e) {
            database = false;  // no Android TV channel database (no tuner, e.g. streaming box)
        }
        if (!database) {
            status.setText(Actions.liveTvIntent(this) == null ? R.string.tuner_no_tv : R.string.tuner_none);
        } else if (checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            load();
        } else {
            status.setText(R.string.tuner_asking);
            requestPermissions(new String[] {PERMISSION}, REQUEST);
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            load();
        } else {
            status.setText(Actions.liveTvIntent(this) == null ? R.string.tuner_no_tv : R.string.tuner_denied);
        }
    }

    private void load() {
        status.setText(R.string.loading);
        worker.execute(() -> {
            List<TunerChannel> list = query();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                show(list);
            });
        });
    }

    /** TV channels (no radio) from the system's channel database; empty if there is none. */
    private List<TunerChannel> query() {
        List<TunerChannel> out = new ArrayList<>();
        String[] cols = {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_SERVICE_TYPE};
        try (Cursor c = getContentResolver().query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String type = c.getString(3);
                if (TvContract.Channels.SERVICE_TYPE_AUDIO.equals(type)) continue;
                out.add(new TunerChannel(c.getLong(0), c.getString(1), c.getString(2)));
            }
        } catch (RuntimeException e) {
            // no TV channel database (e.g. streaming box) or access refused
        }
        Collections.sort(out, TunerChannel.BY_NUMBER);
        return out;
    }

    private void show(List<TunerChannel> list) {
        while (grid.getChildCount() > 1) grid.removeViewAt(1);
        if (list.isEmpty()) {
            status.setText(Actions.liveTvIntent(this) == null ? R.string.tuner_no_tv : R.string.tuner_none);
            return;
        }
        status.setText(getString(R.string.tuner_count, list.size()));
        for (int i = 0; i < list.size() && i < MAX_TILES; i++) {
            TunerChannel ch = list.get(i);
            int number = 0;
            try {
                number = Integer.parseInt(ch.number);
            } catch (NumberFormatException ignored) {
                // e.g. "1-2": shown in the name instead
            }
            String name = number > 0 || ch.number.isEmpty() ? ch.name : ch.number + "  " + ch.name;
            FrameLayout t = Tiles.channel(this, number, name, 0xFF2A3157);
            t.setOnClickListener(v -> Actions.tunerChannel(this, ch));
            grid.addView(t);
        }
        View first = grid.getChildAt(1);
        if (first != null) first.requestFocus();
    }
}
