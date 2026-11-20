package org.novaos.mobile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Nova Mail against a real IMAP server (Dovecot) and the SMTP test server: sending over TLS
 * and STARTTLS, folders, list, whole message with umlauts and attachment, flags, moving,
 * saving sent mails, wrong password, wrong host name. Run by tests/mail/run.sh.
 */
public final class MailIntegration {
    private static int failed, passed;

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        // 1. alice -> bob over TLS (465 style), with umlauts and an attachment
        Mime.Outgoing o = new Mime.Outgoing();
        o.from = new Mime.Address("Alice Müller", "alice@nova.test");
        o.to.add(new Mime.Address("Bob", "bob@nova.test"));
        o.subject = "Grüße aus Hamburg ☀️";
        o.text = "Hallo Bob,\n\nwie geht's? Hier ist das Foto.\n.\nEine Zeile mit Punkt.\n\nViele Grüße\nAlice";
        Mime.Attachment a = new Mime.Attachment();
        a.name = "Strand Übersicht.txt";
        a.type = "text/plain";
        a.data = "Sonne, Sand und Meer 🌊".getBytes(StandardCharsets.UTF_8);
        o.attachments.add(a);
        byte[] raw = Mime.build(o);
        Smtp.send(host, 3465, "ssl", "alice@nova.test", "nova-mail-alice", "alice@nova.test",
                Arrays.asList("bob@nova.test"), raw, 15000);
        check(true, "smtp tls");
        // 2. bob -> alice over STARTTLS (587 style)
        Mime.Outgoing o2 = new Mime.Outgoing();
        o2.from = new Mime.Address("", "bob@nova.test");
        o2.to.add(new Mime.Address("", "alice@nova.test"));
        o2.subject = "Antwort";
        o2.text = "Danke!";
        Smtp.send(host, 3587, "starttls", "bob@nova.test", "nova-mail-bob", "bob@nova.test",
                Arrays.asList("alice@nova.test"), Mime.build(o2), 15000);
        check(true, "smtp starttls");
        // wrong password
        try {
            Smtp.send(host, 3465, "ssl", "bob@nova.test", "falsch", "bob@nova.test", Arrays.asList("alice@nova.test"), Mime.build(o2), 15000);
            check(false, "smtp wrong password accepted");
        } catch (Smtp.SmtpException e) {
            check(e.auth(), "smtp wrong password -> auth error " + e.code);
        }
        Thread.sleep(1000);

        // 3. bob reads over IMAPS
        try (Imap imap = Imap.connect(host, 3993, "ssl", 15000)) {
            imap.login("bob@nova.test", "nova-mail-bob");
            List<Imap.Folder> folders = imap.folders();
            String roles = "";
            for (Imap.Folder f : folders) roles += f.role + ":" + f.name + " ";
            check(roles.contains("inbox:INBOX") && roles.contains("sent:Sent") && roles.contains("trash:Trash")
                    && roles.contains("drafts:Drafts") && roles.contains("junk:Junk"), "folders " + roles);
            Imap.Selected sel = imap.select("INBOX", false);
            check(sel.exists >= 1 && sel.uidValidity > 0 && sel.uidNext > 1, "select " + sel.exists + " " + sel.uidValidity);
            List<Long> uids = imap.search("ALL");
            check(!uids.isEmpty(), "search");
            List<Imap.Header> hs = imap.headers(uids);
            Imap.Header h = hs.get(hs.size() - 1);
            java.util.Map<String, String> hdr = Mime.headers(h.headerText);
            check(Mime.decodeWords(hdr.get("subject")).equals("Grüße aus Hamburg ☀️"), "header subject " + hdr.get("subject"));
            check(!h.seen && h.attachment && h.size > 300 && h.date > 0, "header flags/size/date " + h.seen + h.attachment + h.size);
            Mime.Message m = Mime.parse(imap.message(h.uid));
            check(m.subject.equals("Grüße aus Hamburg ☀️") && m.sender().name.equals("Alice Müller"), "parsed subject/sender " + m.subject);
            check(m.text.contains("wie geht's?") && m.text.contains("\n.\nEine Zeile mit Punkt.") && m.text.contains("Viele Grüße"),
                    "body with dot line: " + m.text);
            check(m.attachments.size() == 1 && m.attachments.get(0).name.equals("Strand Übersicht.txt")
                    && new String(m.attachments.get(0).data, StandardCharsets.UTF_8).equals("Sonne, Sand und Meer 🌊"), "attachment");
            check(imap.search("UNSEEN").contains(h.uid), "still unseen after reading (PEEK)");
            imap.flag(h.uid, "\\Seen", true);
            check(!imap.search("UNSEEN").contains(h.uid), "seen flag");
            long before = imap.select("Sent", true).exists;
            imap.append("Sent", raw, true);
            Imap.Selected sent = imap.select("Sent", true);
            check(sent.exists == before + 1, "append to Sent " + sent.exists);
            long trashBefore = imap.select("Trash", true).exists;
            imap.select("INBOX", false);
            imap.move(h.uid, "Trash");
            check(!imap.search("ALL").contains(h.uid), "moved out of inbox");
            check(imap.select("Trash", false).exists == trashBefore + 1, "in Trash");
            List<Long> inTrash = imap.search("ALL");
            imap.delete(inTrash.get(inTrash.size() - 1));
            check(imap.select("Trash", true).exists == trashBefore, "deleted for good");
        }
        // 4. alice over STARTTLS
        try (Imap imap = Imap.connect(host, 3143, "starttls", 15000)) {
            imap.login("alice@nova.test", "nova-mail-alice");
            imap.select("INBOX", true);
            List<Imap.Header> hs = imap.headers(imap.search("ALL"));
            check(!hs.isEmpty() && Mime.headers(hs.get(hs.size() - 1).headerText).get("subject").equals("Antwort"), "starttls imap");
        }
        // 5. wrong password
        try (Imap imap = Imap.connect(host, 3993, "ssl", 15000)) {
            imap.login("alice@nova.test", "falsch");
            check(false, "imap wrong password accepted");
        } catch (Imap.ImapException e) {
            check(e.auth, "imap wrong password -> auth error");
        }
        // 6. certificate for another name: refused before the password is sent
        try (Imap imap = Imap.connect("localhost", 3993, "ssl", 15000)) {
            check(false, "wrong host name accepted");
        } catch (java.io.IOException e) {
            check(e instanceof javax.net.ssl.SSLException || String.valueOf(e).contains("SSL"), "wrong host name refused: " + e);
        }
        System.out.println("MailIntegration: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
