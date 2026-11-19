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

    static void status(Activity a, CharSequence text) {
        TextView t = a.findViewById(R.id.page_status);
        t.setText(text);
        t.setVisibility(text == null || text.length() == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
    }
}
