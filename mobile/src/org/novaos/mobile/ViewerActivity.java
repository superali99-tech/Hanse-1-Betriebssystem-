package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Collections;
import java.util.List;

/** Full-screen photo or video of Nova Galerie: swipe = next/previous, tap = bar with share and delete. */
public class ViewerActivity extends Activity {
    static final String EXTRA_POS = "pos";
    private static final int DELETE = 5;

    private List<Media.Item> items;
    private int pos;
    private FrameLayout stage;
    private LinearLayout bar;
    private TextView name;
    private GestureDetector gestures;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        items = GalleryActivity.current;
        pos = state != null ? state.getInt(EXTRA_POS) : getIntent().getIntExtra(EXTRA_POS, 0);
        if (items.isEmpty()) {
            finish();
            return;
        }
        Ui.immersive(this, true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xCC0B0E1F);
        int p = Ui.dp(this, 10);
        bar.setPadding(p, Ui.dp(this, 28), p, p);
        bar.addView(button(R.drawable.ic_back, R.string.back, v -> finish()));
        name = Ui.text(this, "", 15, Color.WHITE, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nl.leftMargin = Ui.dp(this, 10);
        bar.addView(name, nl);
        bar.addView(button(R.drawable.ic_share, R.string.share, v -> share()));
        bar.addView(button(R.drawable.ic_delete, R.string.delete, v -> confirmDelete()));
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        setContentView(root);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                bar.setVisibility(bar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < Math.abs(vy) || Math.abs(vx) < 700) return false;
                go(e2.getX() < e1.getX() ? 1 : -1);
                return true;
            }
        });
        show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_POS, pos);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean video = stage.getChildCount() > 0 && stage.getChildAt(0) instanceof VideoView;
        if (gestures.onTouchEvent(ev) && !video) return true;
        return super.dispatchTouchEvent(ev);
    }

    private View button(int icon, int label, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        lp.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private void go(int delta) {
        int next = pos + delta;
        if (next < 0 || next >= items.size()) return;
        pos = next;
        show();
    }

    private void show() {
        stage.removeAllViews();
        Media.Item it = items.get(pos);
        name.setText(it.name);
        if (it.video) {
            VideoView vv = new VideoView(this);
            MediaController mc = new MediaController(this);
            mc.setAnchorView(vv);
            vv.setMediaController(mc);
            vv.setVideoURI(it.uri());
            vv.setOnPreparedListener(mp -> vv.start());
            stage.addView(vv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            return;
        }
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Media.thumbnail(iv, it);  // preview first, then the full picture
        int max = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        Uri uri = it.uri();
        new Thread(() -> {
            Bitmap b = Media.picture(getApplicationContext(), uri, max);
            runOnUiThread(() -> {
                if (b != null && !isDestroyed() && iv.getParent() == stage) iv.setImageBitmap(b);
            });
        }).start();
    }

    private void share() {
        Media.Item it = items.get(pos);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(it.video ? "video/*" : "image/*")
                .putExtra(Intent.EXTRA_STREAM, it.uri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    private void confirmDelete() {
        Media.Item it = items.get(pos);
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_confirm, it.name))
                .setPositiveButton(R.string.delete, (d, w) -> delete(it))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void delete(Media.Item it) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // Android asks the user (other apps' photos may only be deleted with confirmation)
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(it.uri()));
                startIntentSenderForResult(pi.getIntentSender(), DELETE, null, 0, 0, 0);
                return;
            }
            getContentResolver().delete(it.uri(), null, null);
            removed();
        } catch (RecoverableSecurityException e) {
            try {
                startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), DELETE, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException ex) {
                Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (IntentSender.SendIntentException | RuntimeException e) {
            Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != DELETE || result != RESULT_OK) return;
        if (Build.VERSION.SDK_INT == 29) {
            try {
                getContentResolver().delete(items.get(pos).uri(), null, null);  // allowed now
            } catch (RuntimeException e) {
                Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        removed();
    }

    private void removed() {
        items.remove(pos);
        if (items.isEmpty()) {
            finish();
            return;
        }
        if (pos >= items.size()) pos = items.size() - 1;
        show();
    }
}
