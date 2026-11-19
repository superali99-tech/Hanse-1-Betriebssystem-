package org.novaos.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;

import org.novaos.tv.AppFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** All apps: search (umlaut-tolerant, like NovaOS TV) and a grid; hold an app for options. */
public class AppsActivity extends Activity {
    static final String EXTRA_SEARCH = "search";

    private EditText query;
    private GridView grid;
    private TextView count;
    private List<App> all = new ArrayList<>();
    private List<App> shown = new ArrayList<>();
    private GestureDetector gestures;

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            App a = shown.get(i);
            View cell = Ui.appCell(AppsActivity.this, a, true);
            cell.setOnClickListener(v -> {
                Launch.app(AppsActivity.this, a, v);
                finish();
            });
            cell.setOnLongClickListener(v -> {
                AppMenu.show(AppsActivity.this, a, () -> { });
                return true;
            });
            return cell;
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_apps);
        query = findViewById(R.id.query);
        grid = findViewById(R.id.grid);
        count = findViewById(R.id.count);
        grid.setAdapter(adapter);
        all = Apps.cached();
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
            if (!shown.isEmpty()) {
                Launch.app(this, shown.get(0), null);  // "Go" starts the best match
                finish();
            }
            return true;
        });
        // swipe down at the top of the list closes the app overview
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 != null && e2.getY() - e1.getY() > Ui.dp(AppsActivity.this, 80) && vy > 1000
                        && !grid.canScrollVertically(-1)) {
                    finish();
                    return true;
                }
                return false;
            }
        });
        filter();
        if (all.isEmpty()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<App> list = Apps.load(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    all = list;
                    filter();
                });
            });
        }
        if (getIntent().getBooleanExtra(EXTRA_SEARCH, false)) {
            query.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures.onTouchEvent(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onPause() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (im != null) im.hideSoftInputFromWindow(query.getWindowToken(), 0);
        super.onPause();
    }

    private void filter() {
        shown = AppFilter.filter(all, a -> a.label, query.getText().toString());
        adapter.notifyDataSetChanged();
        count.setText(shown.isEmpty() ? getString(R.string.no_results) : getString(R.string.apps_count, shown.size()));
    }
}
