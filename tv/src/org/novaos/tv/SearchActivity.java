package org.novaos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.Executors;

/** Find an app by name (TV on-screen keyboard or voice input of the keyboard). */
public class SearchActivity extends Activity {
    private EditText query;
    private FlowLayout results;
    private TextView count;
    private List<AppEntry> apps;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        query = findViewById(R.id.query);
        results = findViewById(R.id.results);
        count = findViewById(R.id.count);
        apps = AppRepository.cached();
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                show();
            }
        });
        query.setOnEditorActionListener((v, actionId, ev) -> {
            if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            return true;
        });
        show();
        if (apps.isEmpty()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<AppEntry> list = AppRepository.load(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    apps = list;
                    show();
                });
            });
        }
        query.requestFocus();
    }

    private void show() {
        List<AppEntry> found = AppFilter.filter(apps, e -> e.label, query.getText().toString());
        results.removeAllViews();
        for (AppEntry e : found) {
            FrameLayout t = Tiles.app(this, e);
            t.setOnClickListener(v -> Actions.launch(this, e));
            results.addView(t);
        }
        count.setText(found.isEmpty() ? getString(R.string.no_results) : getString(R.string.apps_count, found.size()));
    }
}
