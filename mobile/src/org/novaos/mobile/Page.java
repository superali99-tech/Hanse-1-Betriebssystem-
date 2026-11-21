package org.novaos.mobile;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Common setup of the pages with back button, title and subtitle. */
final class Page {
    private Page() {}

    static LinearLayout setup(Activity a, int title, String subtitle) {
        Ui.edgeToEdge(a);
        a.setContentView(R.layout.activity_page);
        ((TextView) a.findViewById(R.id.page_title)).setText(title);
        ((TextView) a.findViewById(R.id.page_subtitle)).setText(subtitle);
        a.findViewById(R.id.back).setOnClickListener(v -> a.finish());
        return a.findViewById(R.id.content);
    }

    /**
     * Search field under the title (stays while the list below is rebuilt). The callback
     * gets the text on every change; the ✕ empties it.
     */
    static android.widget.EditText search(Activity a, int hint, java.util.function.Consumer<String> changed) {
        LinearLayout box = new LinearLayout(a);
        box.setGravity(android.view.Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.pill_bg);
        box.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 4), 0);
        android.widget.ImageView icon = new android.widget.ImageView(a);
        icon.setImageResource(R.drawable.ic_search_s);
        icon.setAlpha(0.75f);
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        android.widget.EditText edit = new android.widget.EditText(a);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setBackground(null);
        edit.setTextColor(a.getColor(R.color.text));
        edit.setHintTextColor(a.getColor(R.color.text_muted));
        edit.setTextSize(16);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        edit.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
        box.addView(edit, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
        android.widget.ImageButton clear = new android.widget.ImageButton(a);
        clear.setImageResource(R.drawable.ic_close);
        clear.setBackground(null);
        clear.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(a, 12);
        clear.setPadding(p, p, p, p);
        clear.setContentDescription(a.getString(R.string.search_clear));
        clear.setVisibility(android.view.View.GONE);
        clear.setOnClickListener(v -> edit.setText(""));
        box.addView(clear, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                clear.setVisibility(s.length() == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
                changed.accept(s.toString());
            }
        });
        edit.setOnEditorActionListener((v, action, ev) -> {
            a.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        // between the title row and the status line / list
        android.view.ViewGroup column = (android.view.ViewGroup) a.findViewById(R.id.page_status).getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = a.getResources().getDimensionPixelSize(R.dimen.side);
        lp.setMargins(side, Ui.dp(a, 12), side, Ui.dp(a, 2));
        column.addView(box, column.indexOfChild(a.findViewById(R.id.page_status)), lp);
        return edit;
    }

    static void status(Activity a, CharSequence text) {
        TextView t = a.findViewById(R.id.page_status);
        t.setText(text);
        t.setVisibility(text == null || text.length() == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
    }
}
