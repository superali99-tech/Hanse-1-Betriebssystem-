package org.novaos.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Nova Notizen: notes in a small SQLite database, only on this device. */
final class NotesDb extends SQLiteOpenHelper {
    static final class Note {
        long id;
        String title = "", text = "";
        long changed;
    }

    NotesDb(Context c) {
        super(c, "notes.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', "
                + "text TEXT NOT NULL DEFAULT '', changed INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // version 1 only
    }

    List<Note> all() {
        List<Note> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("notes", null, null, null, null, null, "changed DESC")) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    Note get(long id) {
        try (Cursor c = getReadableDatabase().query("notes", null, "_id=?", new String[] {String.valueOf(id)},
                null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    /** Saves the note (new: id 0); empty notes are deleted. Returns the id. */
    long save(Note n) {
        if (n.title.trim().isEmpty() && n.text.trim().isEmpty()) {
            if (n.id > 0) delete(n.id);
            return 0;
        }
        ContentValues v = new ContentValues();
        v.put("title", n.title);
        v.put("text", n.text);
        v.put("changed", System.currentTimeMillis());
        if (n.id > 0) {
            getWritableDatabase().update("notes", v, "_id=?", new String[] {String.valueOf(n.id)});
            return n.id;
        }
        return n.id = getWritableDatabase().insert("notes", null, v);
    }

    void delete(long id) {
        getWritableDatabase().delete("notes", "_id=?", new String[] {String.valueOf(id)});
    }

    private static Note read(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.text = c.getString(c.getColumnIndexOrThrow("text"));
        n.changed = c.getLong(c.getColumnIndexOrThrow("changed"));
        return n;
    }
}
