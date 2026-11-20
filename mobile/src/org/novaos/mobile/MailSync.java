package org.novaos.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova Mail and the mail servers: finding the servers of an address, checking the login,
 * loading the mail lists and mails, sending, and the notification for new mails (every 15
 * minutes in the background). Everything here blocks: call it from a background thread.
 */
final class MailSync {
    static final int TIMEOUT = 25000;
    static final int PAGE = 80;  // newest mails per folder on the phone

    private MailSync() {}

    static {
        Imap.verifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier();
    }

    /** Server settings of an address: known provider, Thunderbird's list, then a guess. */
    static MailServers.Settings find(String email) {
        MailServers.Settings s = MailServers.known(email);
        if (s != null) return s;
        for (String url : MailServers.autoconfigUrls(email)) {
            try {
                MailServers.Settings a = MailServers.parseAutoconfig(Download.text(url), email);
                if (a != null) return a;
            } catch (IOException | RuntimeException ignored) {
                // next source
            }
        }
        return MailServers.guess(email);
    }

    /** Logs in to the mail server (IMAP) and the sending server (SMTP), throws on failure. */
    static void test(MailStore.Account a, String password) throws IOException {
        try (Imap imap = Imap.connect(a.s.imapHost, a.s.imapPort, a.s.imapSecurity, TIMEOUT)) {
            imap.login(a.s.user, password);
        }
        Smtp.check(a.s.smtpHost, a.s.smtpPort, a.s.smtpSecurity, a.s.user, password, TIMEOUT);
    }

    static Imap open(MailStore.Account a) throws IOException {
        Imap imap = Imap.connect(a.s.imapHost, a.s.imapPort, a.s.imapSecurity, TIMEOUT);
        try {
            imap.login(a.s.user, a.password());
        } catch (IOException e) {
            imap.close();
            throw e;
        }
        return imap;
    }

    /** Folders, then the newest mails of the folder with their flags; previews of the newest. */
    static void refresh(Context c, MailStore.Account a, String folder) throws IOException {
        MailStore db = MailStore.get(c);
        try (Imap imap = open(a)) {
            db.saveFolders(a.id, imap.folders());
            Imap.Selected sel = imap.select(folder, false);
            db.checkValidity(a.id, folder, sel.uidValidity);
            List<Long> all = imap.search("ALL");
            List<Long> newest = all.size() > PAGE ? new ArrayList<>(all.subList(all.size() - PAGE, all.size())) : all;
            // headers of new mails, flags of known ones (one request for all)
            db.saveHeaders(a.id, folder, imap.headers(newest));
            db.keepOnly(a.id, folder, all, newest.isEmpty() ? Long.MAX_VALUE : newest.get(0));
            // previews of the newest mails that are not too big
            for (Long uid : db.unfetched(a.id, folder, 12, 400 * 1024)) {
                Mime.Message m = load(c, imap, a, folder, uid);
                db.setPreview(a.id, folder, uid, Mime.preview(m.plain(), 160), hasFiles(m));
            }
            if ("INBOX".equals(folder) && !newest.isEmpty()) {
                MailStore.prefs(c).edit().putLong("last_uid_" + a.id, Math.max(lastUid(c, a), newest.get(newest.size() - 1))).apply();
            }
        }
    }

    static boolean hasFiles(Mime.Message m) {
        for (Mime.Attachment x : m.attachments) if (!x.inline) return true;
        return false;
    }

    private static long lastUid(Context c, MailStore.Account a) {
        return MailStore.prefs(c).getLong("last_uid_" + a.id, 0);
    }

    // ------------------------------------------------------------ one mail

    private static File rawFile(Context c, MailStore.Account a, String folder, long uid) {
        File dir = new File(c.getCacheDir(), "mail-raw");
        if (!dir.isDirectory() && !dir.mkdirs()) dir = c.getCacheDir();
        long validity = 0;
        for (MailStore.Folder f : MailStore.get(c).folders(a.id)) if (f.name.equals(folder)) validity = f.uidValidity;
        return new File(dir, hash(a.id + "|" + folder + "|" + validity + "|" + uid) + ".eml");
    }

    /** The whole mail (from the phone's copy if it was loaded before). */
    static Mime.Message message(Context c, MailStore.Account a, String folder, long uid) throws IOException {
        File f = rawFile(c, a, folder, uid);
        if (f.isFile() && f.length() > 0) return Mime.parse(read(f));
        try (Imap imap = open(a)) {
            imap.select(folder, true);
            return load(c, imap, a, folder, uid);
        }
    }

    private static Mime.Message load(Context c, Imap imap, MailStore.Account a, String folder, long uid) throws IOException {
        byte[] raw = imap.message(uid);
        File f = rawFile(c, a, folder, uid);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(raw);
        }
        ChatMedia.trim(f.getParentFile(), 150L * 1024 * 1024);
        return Mime.parse(raw);
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return b;
        }
    }

    static void setSeen(Context c, MailStore.Account a, String folder, long uid, boolean seen) throws IOException {
        MailStore.get(c).setSeen(a.id, folder, uid, seen);
        try (Imap imap = open(a)) {
            imap.select(folder, false);
            imap.flag(uid, "\\Seen", seen);
        }
    }

    /** Into the bin (in the bin or without one: deleted for good). */
    static void delete(Context c, MailStore.Account a, String folder, long uid) throws IOException {
        String trash = MailStore.get(c).folder(a.id, "trash");
        try (Imap imap = open(a)) {
            imap.select(folder, false);
            if (trash == null || trash.equals(folder)) imap.delete(uid);
            else imap.move(uid, trash);
        }
        MailStore.get(c).removeMail(a.id, folder, uid);
    }

    /** Sends the mail and keeps a copy in "Gesendet" (Gmail and Outlook do that themselves). */
    static void send(Context c, MailStore.Account a, Mime.Outgoing o) throws IOException {
        List<String> rcpt = new ArrayList<>();
        for (Mime.Address x : o.to) rcpt.add(x.email);
        for (Mime.Address x : o.cc) rcpt.add(x.email);
        for (Mime.Address x : o.bcc) rcpt.add(x.email);
        byte[] raw = Mime.build(o);
        Smtp.send(a.s.smtpHost, a.s.smtpPort, a.s.smtpSecurity, a.s.user, a.password(), a.email, rcpt, raw, TIMEOUT);
        List<Mime.Address> all = new ArrayList<>(o.to);
        all.addAll(o.cc);
        all.addAll(o.bcc);
        MailStore.get(c).used(all);
        if ("gmail".equals(a.provider) || "outlook".equals(a.provider)) return;
        try (Imap imap = open(a)) {
            String sent = MailStore.get(c).folder(a.id, "sent");
            if (sent == null) {
                for (Imap.Folder f : imap.folders()) if (f.role.equals("sent")) sent = f.name;
            }
            if (sent != null) imap.append(sent, raw, true);
        } catch (IOException ignored) {
            // the mail is sent; only the copy is missing
        }
    }

    // ------------------------------------------------------------ new mails

    static final String CHANNEL = "mail";

    /** Looks for new mails in all inboxes and shows them as notifications. */
    static void checkNew(Context c) {
        for (MailStore.Account a : MailStore.accounts(c)) {
            try {
                long before = lastUid(c, a);
                refresh(c, a, "INBOX");
                if (before > 0) notifyNew(c, a, before);
            } catch (IOException | RuntimeException ignored) {
                // next time
            }
        }
        MailStore.prefs(c).edit().putLong("checked", System.currentTimeMillis()).apply();
    }

    static boolean due(Context c) {
        return !MailStore.accounts(c).isEmpty()
                && System.currentTimeMillis() - MailStore.prefs(c).getLong("checked", 0) > 10 * 60 * 1000L;
    }

    private static void notifyNew(Context c, MailStore.Account a, long after) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.mail_channel), NotificationManager.IMPORTANCE_DEFAULT));
        }
        List<MailStore.Mail> fresh = new ArrayList<>();
        for (MailStore.Mail m : MailStore.get(c).unread(a.id, "INBOX", 20)) if (m.uid > after) fresh.add(m);
        if (fresh.isEmpty()) return;
        MailStore.Mail first = fresh.get(0);
        String title = fresh.size() == 1 ? first.sender() : c.getResources().getQuantityString(R.plurals.mail_new, fresh.size(), fresh.size());
        StringBuilder text = new StringBuilder();
        for (MailStore.Mail m : fresh) {
            if (text.length() > 0) text.append('\n');
            text.append(fresh.size() == 1 ? m.subject : m.sender() + ": " + m.subject);
        }
        Intent open = new Intent(c, fresh.size() == 1 ? MailReadActivity.class : MailActivity.class)
                .putExtra(MailReadActivity.EXTRA_ACCOUNT, a.id).putExtra(MailReadActivity.EXTRA_FOLDER, "INBOX")
                .putExtra(MailReadActivity.EXTRA_UID, first.uid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, a.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        @SuppressWarnings("deprecation")
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_mail).setColor(c.getColor(R.color.nova_violet)).setContentTitle(title)
                .setContentText(text.toString().split("\n")[0]).setStyle(new Notification.BigTextStyle().bigText(text))
                .setSubText(a.email).setContentIntent(pi).setAutoCancel(true).setShowWhen(true)
                .setCategory(Notification.CATEGORY_EMAIL);
        nm.notify(("mail" + a.id).hashCode(), b.build());
    }

    static void cancelNotification(Context c, String accountId) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(("mail" + accountId).hashCode());
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Looks for new mails every 15 minutes, also when NovaOS is not open. */
    public static class MailJob extends JobService {
        private static final int ID = 4713;

        static void schedule(Context c) {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(ID) != null) return;
            try {
                js.schedule(new JobInfo.Builder(ID, new ComponentName(c, MailJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
            } catch (RuntimeException ignored) {
                // checked when NovaOS opens
            }
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            new Thread(() -> {
                checkNew(getApplicationContext());
                jobFinished(params, false);
            }, "nova-mail-job").start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return true;
        }
    }
}
