package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Nova Dateien: the phone's storage with shortcuts (Downloads, camera, pictures, ...),
 * open and share files with other apps, rename, delete, new folder.
 */
public class FilesActivity extends Activity {
    private static final int PERMISSION = 4;
    private final boolean german = Locale.getDefault().getLanguage().equals("de");
    private LinearLayout content;
    private File root, dir;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_files, "");
        root = Environment.getExternalStorageDirectory();
        String saved = state != null ? state.getString("dir") : null;
        dir = saved != null ? new File(saved) : root;
        ImageButton add = new ImageButton(this);
        add.setImageResource(R.drawable.ic_plus);
        add.setBackgroundResource(R.drawable.accent_round_bg);
        add.setContentDescription(getString(R.string.files_new_folder));
        int p = Ui.dp(this, 16);
        add.setPadding(p, p, p, p);
        add.setScaleType(ImageView.ScaleType.FIT_CENTER);
        add.setElevation(Ui.dp(this, 6));
        add.setOnClickListener(v -> newFolder());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60),
                Gravity.BOTTOM | Gravity.END);
        fl.rightMargin = Ui.dp(this, 22);
        fl.bottomMargin = Ui.dp(this, 40);
        ((FrameLayout) findViewById(android.R.id.content)).addView(add, fl);
        findViewById(R.id.back).setOnClickListener(v -> onBackPressed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("dir", dir.getPath());
    }

    @Override
    public void onBackPressed() {
        if (!dir.equals(root) && dir.getParentFile() != null && FileNames.inside(root.getPath(), dir.getParent())) {
            dir = dir.getParentFile();
            show();
        } else {
            super.onBackPressed();
        }
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void ask() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == PERMISSION) show();
    }

    private void show() {
        content.removeAllViews();
        if (!allowed()) {
            Page.status(this, getString(R.string.files_permission));
            Button b = new Button(this);
            b.setText(R.string.grant);
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setBackgroundResource(R.drawable.accent_card_bg);
            b.setOnClickListener(v -> ask());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 12);
            content.addView(b, lp);
            return;
        }
        String rel = dir.getPath().substring(Math.min(dir.getPath().length(), root.getPath().length()));
        ((TextView) findViewById(R.id.page_subtitle)).setText(getString(R.string.files_internal) + rel);
        if (dir.equals(root)) content.addView(shortcuts());
        File[] list = dir.listFiles();
        List<File> files = new ArrayList<>();
        if (list != null) for (File f : list) if (!f.getName().startsWith(".")) files.add(f);
        files.sort(FileNames.ORDER);
        Page.status(this, files.isEmpty() ? getString(R.string.files_empty) : null);
        for (File f : files) content.addView(row(f));
    }

    private View shortcuts() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        Object[][] places = {
            {Environment.DIRECTORY_DOWNLOADS, R.string.files_downloads},
            {Environment.DIRECTORY_DCIM, R.string.files_dcim},
            {Environment.DIRECTORY_PICTURES, R.string.files_pictures},
            {Environment.DIRECTORY_MOVIES, R.string.files_movies},
            {Environment.DIRECTORY_MUSIC, R.string.files_music},
            {Environment.DIRECTORY_DOCUMENTS, R.string.files_documents},
        };
        for (Object[] pl : places) {
            File target = new File(root, (String) pl[0]);
            TextView chip = Ui.text(this, getString((Integer) pl[1]), 14, Color.WHITE, true);
            chip.setBackgroundResource(R.drawable.pill_bg);
            chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            chip.setOnClickListener(v -> {
                if (target.isDirectory()) {
                    dir = target;
                    show();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            l.addView(chip, lp);
        }
        hs.addView(l);
        hs.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        return hs;
    }

    private View row(File f) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p, p);
        ImageView iv = new ImageView(this);
        iv.setImageResource(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, f.getName(), 15, Color.WHITE, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(n);
        String detail;
        if (f.isDirectory()) {
            String[] kids = f.list();
            detail = kids == null ? "" : getResources().getQuantityString(R.plurals.files_items, kids.length, kids.length);
        } else {
            detail = UpdateInfo.size(f.length(), german) + "  ·  "
                    + DateUtils.formatDateTime(this, f.lastModified(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }
        texts.addView(Ui.text(this, detail, 12, getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Ui.dp(this, 12);
        r.addView(texts, tl);
        r.setClickable(true);
        r.setOnClickListener(v -> {
            if (f.isDirectory()) {
                dir = f;
                show();
            } else {
                open(f);
            }
        });
        r.setOnLongClickListener(v -> {
            menu(f);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        r.setLayoutParams(lp);
        return r;
    }

    private void open(File f) {
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(NovaFileProvider.uri(f), NovaFileProvider.mime(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.files_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void share(File f) {
        Intent send = new Intent(Intent.ACTION_SEND).setType(NovaFileProvider.mime(f))
                .putExtra(Intent.EXTRA_STREAM, NovaFileProvider.uri(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    private void menu(File f) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (f.isFile()) {
            labels.add(getString(R.string.open));
            actions.add(() -> open(f));
            labels.add(getString(R.string.share));
            actions.add(() -> share(f));
        }
        labels.add(getString(R.string.rename));
        actions.add(() -> rename(f));
        labels.add(getString(R.string.delete));
        actions.add(() -> new AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_confirm, f.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (!deleteAll(f)) Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
                    scan(f);
                    show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
        new AlertDialog.Builder(this).setTitle(f.getName())
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).show();
    }

    private void rename(File f) {
        ask(getString(R.string.rename), f.getName(), name -> {
            File to = new File(f.getParentFile(), name.trim());
            if (to.exists() || !f.renameTo(to)) {
                Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
            } else {
                scan(f);
                scan(to);
            }
            show();
        });
    }

    private void newFolder() {
        if (!allowed()) return;
        ask(getString(R.string.files_new_folder), "", name -> {
            if (!new File(dir, name.trim()).mkdir()) Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
            show();
        });
    }

    private interface NameAction {
        void run(String name);
    }

    private void ask(String title, String start, NameAction action) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(start);
        e.setHint(R.string.files_name);
        e.setSelection(start.contains(".") && start.lastIndexOf('.') > 0 ? start.lastIndexOf('.') : start.length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = e.getText().toString();
                    if (FileNames.valid(name)) action.run(name);
                    else Toast.makeText(this, R.string.files_failed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static boolean deleteAll(File f) {
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        boolean ok = true;
        if (kids != null) for (File k : kids) ok &= deleteAll(k);
        return f.delete() && ok;
    }

    /** Tells the media library about the change (gallery, music apps). */
    private void scan(File f) {
        MediaScannerConnection.scanFile(getApplicationContext(), new String[] {f.getPath()}, null, null);
    }

    @SuppressWarnings("unused")
    private static List<File> sorted(File[] files) {
        List<File> l = new ArrayList<>(Arrays.asList(files));
        l.sort(FileNames.ORDER);
        return l;
    }
}
