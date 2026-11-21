package org.novaos.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Writing a note; saved automatically when leaving (empty notes are not kept). */
public class NoteEditActivity extends Activity {
    private NotesDb db;
    private NotesDb.Note note;
    private EditText title, text;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = Page.setup(this, R.string.app_notes, "");
        db = new NotesDb(this);
        long id = state != null ? state.getLong("id", 0) : getIntent().getLongExtra("id", 0);
        note = id > 0 ? db.get(id) : null;
        if (note == null) note = new NotesDb.Note();
        title = field(R.string.notes_title_hint, 22, true);
        title.setSingleLine(true);  // Enter jumps to the text
        title.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        title.setText(note.title);
        text = field(R.string.notes_text_hint, 17, false);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        text.setSingleLine(false);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setMinLines(10);
        text.setText(note.text);
        content.addView(title);
        content.addView(text);
        if (note.id == 0) {
            title.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private EditText field(int hint, float sp, boolean bold) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(sp);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(getColor(R.color.text_muted));
        e.setBackground(null);
        if (bold) e.setTypeface(Fonts.bold(this));
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    @Override
    protected void onPause() {
        note.title = title.getText().toString();
        note.text = text.getText().toString();
        db.save(note);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("id", note.id);
    }
}
