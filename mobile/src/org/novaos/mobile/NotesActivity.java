package org.novaos.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.novaos.tv.AppFilter;

import java.util.List;

/** Nova Notizen: list with search; + writes a new note; hold a note to delete it. */
public class NotesActivity extends Activity {
    private LinearLayout content;
    private EditText search;
    private NotesDb db;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = Page.setup(this, R.string.app_notes, "");
        db = new NotesDb(this);
        search = new EditText(this);
        search.setHint(R.string.notes_search);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(getColor(R.color.text_muted));
        search.setBackgroundResource(R.drawable.pill_bg);
        search.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                show();
            }
        });
        // floating "+" button
        Ui.fab(this, R.drawable.ic_plus, R.string.notes_new, v -> startActivity(new Intent(this, NoteEditActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private void show() {
        content.removeAllViews();
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        sl.topMargin = Ui.dp(this, 10);
        sl.bottomMargin = Ui.dp(this, 4);
        if (search.getParent() != null) ((ViewGroup) search.getParent()).removeView(search);
        content.addView(search, sl);
        List<NotesDb.Note> notes = AppFilter.filter(db.all(), n -> n.title + " " + n.text, search.getText().toString());
        if (notes.isEmpty() && search.length() == 0) {
            TextView t = Ui.text(this, getString(R.string.notes_empty), 15, getColor(R.color.text_muted), false);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), 0, 0);
            content.addView(t);
        }
        for (NotesDb.Note n : notes) content.addView(card(n));
    }

    private View card(NotesDb.Note n) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        String title = n.title.trim().isEmpty() ? getString(R.string.notes_untitled) : n.title.trim();
        c.addView(Ui.text(this, title, 16, Color.WHITE, true));
        if (!n.text.trim().isEmpty()) {
            TextView t = Ui.text(this, n.text.trim(), 14, 0xFFDDE1F5, false);
            t.setMaxLines(3);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, Ui.dp(this, 4), 0, 0);
            c.addView(t);
        }
        TextView d = Ui.text(this, System.currentTimeMillis() - n.changed < DateUtils.MINUTE_IN_MILLIS
                ? getString(R.string.notes_just_now) : DateUtils.getRelativeTimeSpanString(n.changed), 12, getColor(R.color.text_muted), false);
        d.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(d);
        c.setClickable(true);
        c.setOnClickListener(v -> startActivity(new Intent(this, NoteEditActivity.class).putExtra("id", n.id)));
        c.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_confirm, title))
                    .setPositiveButton(R.string.delete, (dlg, w) -> {
                        db.delete(n.id);
                        show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        c.setLayoutParams(lp);
        return c;
    }
}
