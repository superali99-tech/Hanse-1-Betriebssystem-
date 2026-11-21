package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Space;
import android.widget.TextView;

import org.novaos.tv.AppFilter;
import org.novaos.tv.KeyList;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * All apps, like the app screen of Samsung phones: pages side by side (swipe left/right),
 * dots below, "Suchen" at the bottom, ⋮ at the top. The order is the user's own - new apps
 * at the end - or alphabetical (⋮ → Sortieren). Swipe down closes it; hold an app for options.
 */
public class AppsActivity extends Activity {
    static final String EXTRA_SEARCH = "search";
    private static final String PREFS = "novaos_mobile", ORDER = "drawer_order", SORT = "drawer_sort";

    private Pager pager;
    private Pager.Dots dots;
    private GridView results;
    private EditText query;
    private TextView none;
    private List<App> all = new ArrayList<>();
    private List<App> ordered = new ArrayList<>();
    private List<App> found = new ArrayList<>();
    private final Map<String, Long> installed = new HashMap<>();
    private GestureDetector gestures;
    private int builtFor;  // pager size the pages were built for

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return found.size();
        }

        @Override
        public Object getItem(int i) {
            return found.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            return cell(found.get(i));
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        FrameLayout root = new FrameLayout(this);
        ImageView wall = new ImageView(this);
        wall.setImageResource(R.drawable.wallpaper_portrait);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View dim = new View(this);
        dim.setBackgroundColor(0xD614141A);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            col.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        // ⋮ at the top right
        FrameLayout top = new FrameLayout(this);
        ImageButton more = new ImageButton(this);
        more.setImageResource(R.drawable.ic_more);
        more.setBackground(null);
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, 13);
        more.setPadding(p, p, p, p);
        more.setContentDescription(getString(R.string.more_options));
        more.setOnClickListener(this::menu);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50));
        ml.gravity = Gravity.END;
        ml.rightMargin = Ui.dp(this, 8);
        top.addView(more, ml);
        col.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        // the pages, or the search results in their place
        FrameLayout middle = new FrameLayout(this);
        pager = new Pager(this);
        pager.setListener(page -> dots.set(pager.pages(), page));
        middle.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        results = new GridView(this);
        results.setNumColumns(GridView.AUTO_FIT);
        results.setColumnWidth(Ui.dp(this, 86));
        results.setVerticalSpacing(Ui.dp(this, 10));
        results.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        results.setSelector(new android.graphics.drawable.ColorDrawable(0));
        results.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        results.setAdapter(adapter);
        results.setVisibility(View.GONE);
        middle.addView(results, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        none = Ui.text(this, getString(R.string.no_results), 15, 0xB3FFFFFF, false);
        none.setGravity(Gravity.CENTER_HORIZONTAL);
        none.setPadding(0, Ui.dp(this, 40), 0, 0);
        none.setVisibility(View.GONE);
        middle.addView(none, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int size = (r - l) * 10000 + (b - t);
            if (size != builtFor && r > l && b > t) pager.post(this::buildPages);
        });

        dots = new Pager.Dots(this);
        col.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 26)));

        // "Suchen" at the bottom, with the microphone (Nova)
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(0xF0000000);
        sb.setCornerRadius(Ui.dp(this, 28));
        search.setBackground(sb);
        search.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 6), 0);
        ImageView lens = new ImageView(this);
        lens.setImageResource(R.drawable.ic_search);
        lens.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        search.addView(lens, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        query = new EditText(this);
        query.setHint(R.string.drawer_search);
        query.setSingleLine(true);
        query.setBackground(null);
        query.setTextColor(0xFFFFFFFF);
        query.setHintTextColor(0xFFBDBDC2);
        query.setTextSize(18);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        ql.leftMargin = Ui.dp(this, 12);
        search.addView(query, ql);
        ImageButton mic = new ImageButton(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setBackground(null);
        mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int mp = Ui.dp(this, 12);
        mic.setPadding(mp, mp, mp, mp);
        mic.setContentDescription(getString(R.string.nova_listen));
        mic.setOnClickListener(v -> startActivity(new Intent(this, NovaActivity.class).putExtra(NovaActivity.EXTRA_LISTEN, true)));
        search.addView(mic, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        int side = Math.max(Ui.dp(this, 24), (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 420)) / 2);
        sl.setMargins(side, Ui.dp(this, 6), side, Ui.dp(this, 14));
        col.addView(search, sl);
        setContentView(root);

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                filter();
            }
        });
        query.setOnEditorActionListener((v, id, ev) -> {
            if (!found.isEmpty() && query.length() > 0) {
                Launch.app(this, found.get(0), null);  // "Go" starts the best match
                finish();
            }
            return true;
        });
        // swipe down closes the app screen (not while searching in the results)
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2.getY() - e1.getY() < Ui.dp(AppsActivity.this, 80) || vy < 1000 || Math.abs(vy) < Math.abs(vx) * 1.5f) return false;
                if (results.getVisibility() == View.VISIBLE && results.canScrollVertically(-1)) return false;
                finish();
                return true;
            }
        });

        all = Apps.cached();
        load();
        if (getIntent().getBooleanExtra(EXTRA_SEARCH, false)) {
            query.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures.onTouchEvent(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (query.length() > 0) {
            query.setText("");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(query.getWindowToken(), 0);
        super.onPause();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean alphabetical() {
        return "az".equals(prefs().getString(SORT, "custom"));
    }

    /** The apps (and when each was installed: new apps come at the end) - off the main thread. */
    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<App> list = Apps.load(getApplicationContext());
            PackageManager pm = getPackageManager();
            Map<String, Long> times = new HashMap<>();
            for (App a : list) {
                if (times.containsKey(a.packageName())) continue;
                long t = 0;
                try {
                    t = pm.getPackageInfo(a.packageName(), 0).firstInstallTime;
                } catch (PackageManager.NameNotFoundException ignored) {
                    // just removed
                }
                times.put(a.packageName(), t);
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                all = list;
                installed.clear();
                installed.putAll(times);
                arrange();
            });
        });
    }

    /** The order of the pages: the own one (new apps at the end) or A-Z. */
    private void arrange() {
        List<App> byInstall = new ArrayList<>(all);  // all comes sorted by name
        byInstall.sort((a, b) -> Long.compare(time(a), time(b)));
        Map<String, App> byKey = new HashMap<>();
        List<String> present = new ArrayList<>();
        for (App a : byInstall) {
            byKey.put(a.key(), a);
            present.add(a.key());
        }
        List<String> order = DrawerOrder.arrange(KeyList.decode(prefs().getString(ORDER, "")), present);
        prefs().edit().putString(ORDER, KeyList.encode(order)).apply();
        ordered = new ArrayList<>();
        if (alphabetical()) {
            ordered.addAll(all);
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            ordered.sort((a, b) -> collator.compare(a.label, b.label));
        } else {
            for (String k : order) ordered.add(byKey.get(k));
        }
        builtFor = 0;
        buildPages();
        filter();
    }

    private long time(App a) {
        Long t = installed.get(a.packageName());
        return t == null ? 0 : t;
    }

    /** Pages of rows × columns, as many rows as fit. */
    private void buildPages() {
        int w = pager.getWidth(), h = pager.getHeight();
        if (w == 0 || h == 0) return;
        builtFor = w * 10000 + h;
        float density = getResources().getDisplayMetrics().density;
        int cols = Greeting.columns((int) (w / density) - 16, 86, 4, 7);
        int rows = DrawerOrder.rows(h / density, 104);
        int per = rows * cols, keep = pager.page();
        pager.removeAllViews();
        int pages = DrawerOrder.pages(ordered.size(), per);
        for (int pg = 0; pg < pages; pg++) {
            List<App> on = DrawerOrder.page(ordered, pg, per);
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            for (int r = 0; r < rows; r++) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                for (int c = 0; c < cols; c++) {
                    int i = r * cols + c;
                    View v = i < on.size() ? cell(on.get(i)) : new Space(this);
                    row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                }
                page.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            }
            pager.addView(page);
        }
        pager.setPage(Math.min(keep, pages - 1), false);
        dots.set(pager.pages(), pager.page());
    }

    private View cell(App a) {
        LinearLayout cell = Ui.appCell(this, a, true);
        cell.setOnClickListener(v -> {
            Launch.app(this, a, v);
            finish();
        });
        cell.setOnLongClickListener(v -> {
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            if (!alphabetical() && query.length() == 0) {
                labels.add(getString(R.string.drawer_forward));
                actions.add(() -> moveApp(a, -1));
                labels.add(getString(R.string.drawer_back));
                actions.add(() -> moveApp(a, 1));
            }
            AppMenu.show(this, a, () -> { }, labels, actions);
            return true;
        });
        return cell;
    }

    private void moveApp(App a, int by) {
        List<String> order = KeyList.decode(prefs().getString(ORDER, ""));
        if (DrawerOrder.move(order, a.key(), by)) {
            prefs().edit().putString(ORDER, KeyList.encode(order)).apply();
            arrange();
        }
    }

    private void filter() {
        String q = query.getText().toString();
        boolean searching = !q.trim().isEmpty();
        found = searching ? AppFilter.filter(all, a -> a.label, q) : new ArrayList<>();
        adapter.notifyDataSetChanged();
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        pager.setVisibility(searching ? View.INVISIBLE : View.VISIBLE);
        dots.setVisibility(searching || pager.pages() < 2 ? View.INVISIBLE : View.VISIBLE);
        none.setVisibility(searching && found.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** ⋮: sort, NovaOS settings, Nova Store. */
    private void menu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, R.string.drawer_sort);
        m.getMenu().add(0, 2, 1, R.string.app_store);
        m.getMenu().add(0, 3, 2, R.string.settings_title);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                String[] choices = {getString(R.string.drawer_sort_custom), getString(R.string.drawer_sort_az)};
                new AlertDialog.Builder(this).setTitle(R.string.drawer_sort)
                        .setSingleChoiceItems(choices, alphabetical() ? 1 : 0, (d, which) -> {
                            prefs().edit().putString(SORT, which == 1 ? "az" : "custom").apply();
                            pager.setPage(0, false);
                            arrange();
                            d.dismiss();
                        }).show();
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(this, StoreActivity.class));
            } else {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        m.show();
    }
}
