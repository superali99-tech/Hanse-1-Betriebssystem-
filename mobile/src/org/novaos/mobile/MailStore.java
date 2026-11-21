package org.novaos.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nova Mail on the phone: the accounts (password encrypted with Android's keystore) and a
 * copy of the mail lists (SQLite). The mails themselves stay on the mail server.
 */
final class MailStore extends SQLiteOpenHelper {
    static final class Account {
        String id = "", email = "", name = "", provider = "";
        MailServers.Settings s = new MailServers.Settings();
        String secret = "";

        String password() {
            return Secret.decrypt(secret);
        }

        Mime.Address address() {
            return new Mime.Address(name, email);
        }
    }

    static final class Folder {
        String account, name, role;
        long uidValidity;
    }

    static final class Mail {
        String account, folder, fromName = "", fromAddr = "", to = "", cc = "", subject = "", preview = "", messageId = "",
                refs = "", replyTo = "";
        long uid, date, size;
        boolean seen, flagged, answered, attachment, fetched;

        String sender() {
            return fromName.isEmpty() ? fromAddr : fromName;
        }
    }

    private static MailStore instance;

    static synchronized MailStore get(Context c) {
        if (instance == null) instance = new MailStore(c.getApplicationContext());
        return instance;
    }

    private MailStore(Context c) {
        super(c, "mail.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE folders (account TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL DEFAULT '', "
                + "uidvalidity INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (account, name))");
        db.execSQL("CREATE TABLE mails (account TEXT NOT NULL, folder TEXT NOT NULL, uid INTEGER NOT NULL, "
                + "from_name TEXT NOT NULL DEFAULT '', from_addr TEXT NOT NULL DEFAULT '', to_list TEXT NOT NULL DEFAULT '', "
                + "cc_list TEXT NOT NULL DEFAULT '', reply_to TEXT NOT NULL DEFAULT '', subject TEXT NOT NULL DEFAULT '', "
                + "preview TEXT NOT NULL DEFAULT '', message_id TEXT NOT NULL DEFAULT '', refs TEXT NOT NULL DEFAULT '', "
                + "date INTEGER NOT NULL DEFAULT 0, size INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL DEFAULT 0, "
                + "flagged INTEGER NOT NULL DEFAULT 0, answered INTEGER NOT NULL DEFAULT 0, attachment INTEGER NOT NULL DEFAULT 0, "
                + "fetched INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (account, folder, uid))");
        db.execSQL("CREATE INDEX mails_date ON mails (account, folder, date)");
        db.execSQL("CREATE TABLE addresses (email TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', used INTEGER NOT NULL DEFAULT 0)");
        createContacts(db);
    }

    private static void createContacts(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE contacts (email TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', "
                + "note TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) createContacts(db);  // 0.1.1.59: address book
    }

    // ------------------------------------------------------------ accounts

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mail", Context.MODE_PRIVATE);
    }

    static List<Account> accounts(Context c) {
        List<Account> out = new ArrayList<>();
        try {
            Object list = MiniJson.parse(prefs(c).getString("accounts", "[]"));
            if (list instanceof List) {
                for (Object o : (List<?>) list) {
                    Account a = new Account();
                    a.id = str(o, "id");
                    a.email = str(o, "email");
                    a.name = str(o, "name");
                    a.provider = str(o, "provider");
                    a.secret = str(o, "secret");
                    a.s.imapHost = str(o, "imapHost");
                    a.s.imapPort = (int) num(o, "imapPort", 993);
                    a.s.imapSecurity = str(o, "imapSecurity");
                    a.s.smtpHost = str(o, "smtpHost");
                    a.s.smtpPort = (int) num(o, "smtpPort", 465);
                    a.s.smtpSecurity = str(o, "smtpSecurity");
                    a.s.user = str(o, "user");
                    a.s.provider = a.provider;
                    if (!a.id.isEmpty()) out.add(a);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // broken: no accounts
        }
        return out;
    }

    private static String str(Object o, String k) {
        Object v = MiniJson.get(o, k);
        return v instanceof String ? (String) v : "";
    }

    private static long num(Object o, String k, long fallback) {
        Object v = MiniJson.get(o, k);
        return v instanceof Double ? ((Double) v).longValue() : fallback;
    }

    static void saveAccounts(Context c, List<Account> list) {
        List<Object> out = new ArrayList<>();
        for (Account a : list) {
            out.add(MiniJson.obj("id", a.id, "email", a.email, "name", a.name, "provider", a.provider, "secret", a.secret,
                    "imapHost", a.s.imapHost, "imapPort", a.s.imapPort, "imapSecurity", a.s.imapSecurity,
                    "smtpHost", a.s.smtpHost, "smtpPort", a.s.smtpPort, "smtpSecurity", a.s.smtpSecurity, "user", a.s.user));
        }
        prefs(c).edit().putString("accounts", MiniJson.write(out)).apply();
    }

    static Account account(Context c, String id) {
        for (Account a : accounts(c)) if (a.id.equals(id)) return a;
        return null;
    }

    /** The account shown in Nova Mail (the last chosen, else the first). */
    static Account current(Context c) {
        List<Account> all = accounts(c);
        String id = prefs(c).getString("current", "");
        for (Account a : all) if (a.id.equals(id)) return a;
        return all.isEmpty() ? null : all.get(0);
    }

    static void setCurrent(Context c, String id) {
        prefs(c).edit().putString("current", id).apply();
    }

    static void addAccount(Context c, Account a, String password) {
        a.secret = Secret.encrypt(password);
        List<Account> all = accounts(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).email.equalsIgnoreCase(a.email)) all.remove(i);
        if (a.id.isEmpty()) a.id = Mime.randomId().substring(0, 12);
        all.add(a);
        saveAccounts(c, all);
        setCurrent(c, a.id);
    }

    void removeAccount(Context c, String id) {
        List<Account> all = accounts(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        saveAccounts(c, all);
        SQLiteDatabase db = getWritableDatabase();
        db.delete("mails", "account=?", new String[] {id});
        db.delete("folders", "account=?", new String[] {id});
        prefs(c).edit().remove("last_uid_" + id).apply();
    }

    // ------------------------------------------------------------ folders

    void saveFolders(String account, List<Imap.Folder> folders) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<String> names = new ArrayList<>();
            for (Imap.Folder f : folders) {
                names.add(f.name);
                ContentValues cv = new ContentValues();
                cv.put("account", account);
                cv.put("name", f.name);
                db.insertWithOnConflict("folders", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                ContentValues up = new ContentValues();
                up.put("role", f.role);
                db.update("folders", up, "account=? AND name=?", new String[] {account, f.name});
            }
            for (Folder f : folders(account)) {
                if (!names.contains(f.name)) {
                    db.delete("folders", "account=? AND name=?", new String[] {account, f.name});
                    db.delete("mails", "account=? AND folder=?", new String[] {account, f.name});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Folder> folders(String account) {
        List<Folder> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("folders", null, "account=?", new String[] {account}, null, null, "name")) {
            while (c.moveToNext()) {
                Folder f = new Folder();
                f.account = account;
                f.name = c.getString(c.getColumnIndexOrThrow("name"));
                f.role = c.getString(c.getColumnIndexOrThrow("role"));
                f.uidValidity = c.getLong(c.getColumnIndexOrThrow("uidvalidity"));
                out.add(f);
            }
        }
        return out;
    }

    /** Folder with this role ("inbox", "sent", "trash", ...), or null. */
    String folder(String account, String role) {
        if (role.equals("inbox")) return "INBOX";
        for (Folder f : folders(account)) if (f.role.equals(role)) return f.name;
        return null;
    }

    /** A new UIDVALIDITY means the server renumbered the folder: forget the old copy. */
    void checkValidity(String account, String folder, long validity) {
        long old = 0;
        try (Cursor c = getReadableDatabase().query("folders", new String[] {"uidvalidity"}, "account=? AND name=?",
                new String[] {account, folder}, null, null, null)) {
            if (c.moveToFirst()) old = c.getLong(0);
        }
        if (old != validity) {
            getWritableDatabase().delete("mails", "account=? AND folder=?", new String[] {account, folder});
            ContentValues cv = new ContentValues();
            cv.put("account", account);
            cv.put("name", folder);
            cv.put("uidvalidity", validity);
            getWritableDatabase().insertWithOnConflict("folders", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    // ------------------------------------------------------------ mails

    void saveHeaders(String account, String folder, List<Imap.Header> headers) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Imap.Header h : headers) {
                Map<String, String> hd = Mime.headers(h.headerText);
                List<Mime.Address> from = Mime.addresses(hd.get("from"));
                ContentValues cv = new ContentValues();
                cv.put("account", account);
                cv.put("folder", folder);
                cv.put("uid", h.uid);
                cv.put("from_name", from.isEmpty() ? "" : from.get(0).name);
                cv.put("from_addr", from.isEmpty() ? "" : from.get(0).email);
                cv.put("to_list", Mime.addressHeader(Mime.addresses(hd.get("to"))));
                cv.put("cc_list", Mime.addressHeader(Mime.addresses(hd.get("cc"))));
                cv.put("reply_to", Mime.addressHeader(Mime.addresses(hd.get("reply-to"))));
                cv.put("subject", Mime.decodeWords(hd.getOrDefault("subject", "")).trim());
                cv.put("message_id", hd.getOrDefault("message-id", ""));
                cv.put("refs", hd.getOrDefault("references", ""));
                long d = Mime.date(hd.get("date"));
                cv.put("date", d > 0 ? d : h.date);
                cv.put("size", h.size);
                cv.put("seen", h.seen ? 1 : 0);
                cv.put("flagged", h.flagged ? 1 : 0);
                cv.put("answered", h.answered ? 1 : 0);
                cv.put("attachment", h.attachment ? 1 : 0);
                db.insertWithOnConflict("mails", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                ContentValues flags = new ContentValues();
                flags.put("seen", h.seen ? 1 : 0);
                flags.put("flagged", h.flagged ? 1 : 0);
                flags.put("answered", h.answered ? 1 : 0);
                db.update("mails", flags, "account=? AND folder=? AND uid=?", new String[] {account, folder, String.valueOf(h.uid)});
                if (!from.isEmpty()) remember(db, from.get(0), false);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Removes the local copies of mails that are gone on the server (among the known UIDs). */
    void keepOnly(String account, String folder, List<Long> serverUids, long fromUid) {
        java.util.Set<Long> keep = new java.util.HashSet<>(serverUids);
        List<Long> gone = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("mails", new String[] {"uid"}, "account=? AND folder=? AND uid>=?",
                new String[] {account, folder, String.valueOf(fromUid)}, null, null, null)) {
            while (c.moveToNext()) if (!keep.contains(c.getLong(0))) gone.add(c.getLong(0));
        }
        for (Long u : gone) removeMail(account, folder, u);
    }

    void removeMail(String account, String folder, long uid) {
        getWritableDatabase().delete("mails", "account=? AND folder=? AND uid=?", new String[] {account, folder, String.valueOf(uid)});
    }

    void setPreview(String account, String folder, long uid, String preview, boolean attachment) {
        ContentValues cv = new ContentValues();
        cv.put("preview", preview);
        cv.put("fetched", 1);
        if (attachment) cv.put("attachment", 1);
        getWritableDatabase().update("mails", cv, "account=? AND folder=? AND uid=?", new String[] {account, folder, String.valueOf(uid)});
    }

    void setSeen(String account, String folder, long uid, boolean seen) {
        ContentValues cv = new ContentValues();
        cv.put("seen", seen ? 1 : 0);
        getWritableDatabase().update("mails", cv, "account=? AND folder=? AND uid=?", new String[] {account, folder, String.valueOf(uid)});
    }

    List<Mail> mails(String account, String folder, int limit) {
        return query("account=? AND folder=?", new String[] {account, folder}, limit);
    }

    List<Mail> unread(String account, String folder, int limit) {
        return query("account=? AND folder=? AND seen=0", new String[] {account, folder}, limit);
    }

    int unreadCount(String account, String folder) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM mails WHERE account=? AND folder=? AND seen=0",
                new String[] {account, folder})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    Mail mail(String account, String folder, long uid) {
        List<Mail> l = query("account=? AND folder=? AND uid=?", new String[] {account, folder, String.valueOf(uid)}, 1);
        return l.isEmpty() ? null : l.get(0);
    }

    List<Long> unfetched(String account, String folder, int limit, long maxSize) {
        List<Long> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("mails", new String[] {"uid"}, "account=? AND folder=? AND fetched=0 AND size<=?",
                new String[] {account, folder, String.valueOf(maxSize)}, null, null, "date DESC", String.valueOf(limit))) {
            while (c.moveToNext()) out.add(c.getLong(0));
        }
        return out;
    }

    private List<Mail> query(String where, String[] args, int limit) {
        List<Mail> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("mails", null, where, args, null, null, "date DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                Mail m = new Mail();
                m.account = c.getString(c.getColumnIndexOrThrow("account"));
                m.folder = c.getString(c.getColumnIndexOrThrow("folder"));
                m.uid = c.getLong(c.getColumnIndexOrThrow("uid"));
                m.fromName = c.getString(c.getColumnIndexOrThrow("from_name"));
                m.fromAddr = c.getString(c.getColumnIndexOrThrow("from_addr"));
                m.to = c.getString(c.getColumnIndexOrThrow("to_list"));
                m.cc = c.getString(c.getColumnIndexOrThrow("cc_list"));
                m.replyTo = c.getString(c.getColumnIndexOrThrow("reply_to"));
                m.subject = c.getString(c.getColumnIndexOrThrow("subject"));
                m.preview = c.getString(c.getColumnIndexOrThrow("preview"));
                m.messageId = c.getString(c.getColumnIndexOrThrow("message_id"));
                m.refs = c.getString(c.getColumnIndexOrThrow("refs"));
                m.date = c.getLong(c.getColumnIndexOrThrow("date"));
                m.size = c.getLong(c.getColumnIndexOrThrow("size"));
                m.seen = c.getInt(c.getColumnIndexOrThrow("seen")) == 1;
                m.flagged = c.getInt(c.getColumnIndexOrThrow("flagged")) == 1;
                m.answered = c.getInt(c.getColumnIndexOrThrow("answered")) == 1;
                m.attachment = c.getInt(c.getColumnIndexOrThrow("attachment")) == 1;
                m.fetched = c.getInt(c.getColumnIndexOrThrow("fetched")) == 1;
                out.add(m);
            }
        }
        return out;
    }

    long maxUid(String account, String folder) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(uid) FROM mails WHERE account=? AND folder=?", new String[] {account, folder})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    // ------------------------------------------------------------ addresses (suggestions when writing)

    private static void remember(SQLiteDatabase db, Mime.Address a, boolean used) {
        if (!Mime.validEmail(a.email)) return;
        ContentValues cv = new ContentValues();
        cv.put("email", a.email.toLowerCase(java.util.Locale.ROOT));
        cv.put("name", a.name);
        db.insertWithOnConflict("addresses", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (!a.name.isEmpty()) {
            ContentValues n = new ContentValues();
            n.put("name", a.name);
            db.update("addresses", n, "email=?", new String[] {a.email.toLowerCase(java.util.Locale.ROOT)});
        }
        if (used) db.execSQL("UPDATE addresses SET used=used+1 WHERE email=?", new Object[] {a.email.toLowerCase(java.util.Locale.ROOT)});
    }

    void used(List<Mime.Address> list) {
        for (Mime.Address a : list) remember(getWritableDatabase(), a, true);
    }

    /** Suggestions when writing: the saved contacts first (with their saved name), then addresses used before. */
    List<Mime.Address> addresses() {
        List<Mime.Address> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Contact k : contacts()) {
            out.add(new Mime.Address(k.name, k.email));
            seen.add(k.email);
        }
        try (Cursor c = getReadableDatabase().query("addresses", null, null, null, null, null, "used DESC, name", "500")) {
            while (c.moveToNext()) {
                String email = c.getString(c.getColumnIndexOrThrow("email"));
                if (seen.add(email)) out.add(new Mime.Address(c.getString(c.getColumnIndexOrThrow("name")), email));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ address book (saved contacts)

    static final class Contact {
        String name = "", email = "", phone = "", note = "";

        Contact() {}

        Contact(String name, String email) {
            this.name = name == null ? "" : name;
            this.email = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        }

        String display() {
            return name.isEmpty() ? email : name;
        }
    }

    List<Contact> contacts() {
        List<Contact> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("contacts", null, null, null, null, null, "name COLLATE NOCASE, email")) {
            while (c.moveToNext()) {
                Contact k = new Contact(c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("email")));
                k.phone = c.getString(c.getColumnIndexOrThrow("phone"));
                k.note = c.getString(c.getColumnIndexOrThrow("note"));
                out.add(k);
            }
        }
        return out;
    }

    Contact contact(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(java.util.Locale.ROOT);
        for (Contact k : contacts()) if (k.email.equals(e)) return k;
        return null;
    }

    /** Saves a contact; oldEmail (may be null) is replaced when the address was changed. */
    void saveContact(String oldEmail, Contact k) {
        SQLiteDatabase db = getWritableDatabase();
        if (oldEmail != null && !oldEmail.equalsIgnoreCase(k.email)) deleteContact(oldEmail);
        ContentValues cv = new ContentValues();
        cv.put("email", k.email.trim().toLowerCase(java.util.Locale.ROOT));
        cv.put("name", k.name.trim());
        cv.put("phone", k.phone.trim());
        cv.put("note", k.note.trim());
        db.insertWithOnConflict("contacts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteContact(String email) {
        getWritableDatabase().delete("contacts", "email=?", new String[] {email.trim().toLowerCase(java.util.Locale.ROOT)});
    }
}
