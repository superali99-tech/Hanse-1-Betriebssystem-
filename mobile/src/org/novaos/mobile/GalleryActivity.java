package org.novaos.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** Nova Galerie: all photos and videos of the phone, newest first; tap = full screen. */
public class GalleryActivity extends Activity {
    private static final int PERMISSION = 3;
    static List<Media.Item> current = new ArrayList<>();  // shared with the viewer

    private GridView grid;
    private List<Media.Item> items = new ArrayList<>();

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return items.get(i).id;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            FrameLayout cell = convert instanceof FrameLayout ? (FrameLayout) convert : cell();
            int size = grid.getColumnWidth();
            cell.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size));
            Media.Item it = items.get(i);
            Media.thumbnail((ImageView) cell.getChildAt(0), it);
            cell.getChildAt(1).setVisibility(it.video ? View.VISIBLE : View.GONE);
            cell.setContentDescription(it.name);
            return cell;
        }
    };

    private FrameLayout cell() {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFF161A33);
        f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView play = new ImageView(this);
        play.setImageResource(R.drawable.ic_play);
        f.addView(play, new FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32), Gravity.CENTER));
        return f;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Page.setup(this, R.string.app_gallery, "");
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 110));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.dp(this, 3));
        grid.setVerticalSpacing(Ui.dp(this, 3));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            current = items;
            startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_POS, pos));
        });
        // the grid scrolls itself: it replaces the page's scroll view
        View scroller = findViewById(R.id.scroller);
        ViewGroup parent = (ViewGroup) scroller.getParent();
        int at = parent.indexOfChild(scroller);
        parent.removeView(scroller);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        gl.topMargin = Ui.dp(this, 10);
        parent.addView(grid, at, gl);
        if (!Media.allowed(this)) Media.ask(this, PERMISSION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Media.allowed(this)) {
            showNoAccess();
            return;
        }
        View grant = findViewById(R.id.grant);
        if (grant != null) ((ViewGroup) grant.getParent()).removeView(grant);
        new Thread(() -> {
            List<Media.Item> list = Media.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                items = list;
                adapter.notifyDataSetChanged();
                Page.status(this, list.isEmpty() ? getString(R.string.gallery_empty) : null);
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == PERMISSION) onResume();
    }

    private void showNoAccess() {
        Page.status(this, getString(R.string.gallery_permission));
        if (findViewById(R.id.grant) != null) return;
        Button b = Ui.button(this, R.string.grant, v -> Media.ask(this, PERMISSION));
        b.setId(R.id.grant);
        ViewGroup parent = (ViewGroup) grid.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 20);
        lp.topMargin = Ui.dp(this, 10);
        parent.addView(b, parent.indexOfChild(grid), lp);
    }
}
