package org.novaos.mobile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the mail servers of an e-mail address for Nova Mail: known providers (GMX, WEB.DE,
 * Gmail, T-Online, ...), the Thunderbird server list (autoconfig XML) or a guess.
 * Android-independent, tested.
 */
public final class MailServers {
    private MailServers() {}

    /** Server settings of one account. security: "ssl" (TLS from the start) or "starttls". */
    public static final class Settings {
        public String imapHost = "", imapSecurity = "ssl", smtpHost = "", smtpSecurity = "ssl", user = "";
        public int imapPort = 993, smtpPort = 465;
        public String provider = "";   // key of the known provider, e.g. "gmail"
        public String source = "";     // known, autoconfig, guess, manual

        @Override
        public String toString() {
            return imapHost + ":" + imapPort + "/" + imapSecurity + " " + smtpHost + ":" + smtpPort + "/" + smtpSecurity + " " + user;
        }
    }

    /** Provider shown in the setup of Nova Mail, with what the user must know. */
    public static final class Provider {
        public final String key, name, imap, smtp, help;
        public final int imapPort, smtpPort;
        public final boolean smtpStarttls;
        public final String[] domains;

        Provider(String key, String name, String imap, int imapPort, String smtp, int smtpPort, boolean starttls, String help, String... domains) {
            this.key = key;
            this.name = name;
            this.imap = imap;
            this.imapPort = imapPort;
            this.smtp = smtp;
            this.smtpPort = smtpPort;
            this.smtpStarttls = starttls;
            this.help = help;
            this.domains = domains;
        }
    }

    /** help: page where the user creates the app password or switches on IMAP ("" = none needed). */
    public static final Provider[] PROVIDERS = {
        new Provider("gmx", "GMX", "imap.gmx.net", 993, "mail.gmx.net", 465, false,
                "https://hilfe.gmx.net/pop-imap/einschalten.html", "gmx.de", "gmx.net", "gmx.at", "gmx.ch", "gmx.eu", "gmx.info", "gmx.org"),
        new Provider("webde", "WEB.DE", "imap.web.de", 993, "smtp.web.de", 587, true,
                "https://hilfe.web.de/pop-imap/einschalten.html", "web.de"),
        new Provider("gmail", "Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 465, false,
                "https://myaccount.google.com/apppasswords", "gmail.com", "googlemail.com"),
        new Provider("tonline", "T-Online", "secureimap.t-online.de", 993, "securesmtp.t-online.de", 465, false,
                "https://www.telekom.de/hilfe/apps-dienste/e-mail/e-mail-programme/passwort", "t-online.de", "magenta.de"),
        new Provider("outlook", "Outlook / Hotmail", "outlook.office365.com", 993, "smtp-mail.outlook.com", 587, true,
                "https://support.microsoft.com/office/c5a8e3b5-5c1a-4d5b-9b1f-8e2d1a9a9a9a", "outlook.com", "outlook.de", "hotmail.com",
                "hotmail.de", "live.com", "live.de", "msn.com"),
        new Provider("yahoo", "Yahoo", "imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 465, false,
                "https://login.yahoo.com/account/security/app-passwords", "yahoo.com", "yahoo.de", "ymail.com", "rocketmail.com"),
        new Provider("icloud", "iCloud", "imap.mail.me.com", 993, "smtp.mail.me.com", 587, true,
                "https://account.apple.com/account/manage", "icloud.com", "me.com", "mac.com"),
        new Provider("freenet", "freenet", "mx.freenet.de", 993, "mx.freenet.de", 587, true, "", "freenet.de"),
        new Provider("vodafone", "Vodafone", "imap.vodafonemail.de", 993, "smtp.vodafonemail.de", 587, true, "",
                "vodafone.de", "vodafonemail.de", "arcor.de"),
        new Provider("aol", "AOL", "imap.aol.com", 993, "smtp.aol.com", 465, false,
                "https://login.aol.com/account/security/app-passwords", "aol.com", "aol.de"),
        new Provider("mailbox", "mailbox.org", "imap.mailbox.org", 993, "smtp.mailbox.org", 465, false, "", "mailbox.org"),
        new Provider("posteo", "Posteo", "posteo.de", 993, "posteo.de", 465, false, "", "posteo.de", "posteo.net", "posteo.eu"),
        new Provider("ionos", "IONOS (1&1)", "imap.ionos.de", 993, "smtp.ionos.de", 465, false, "", "online.de", "onlinehome.de"),
    };

    public static String domain(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    public static Provider provider(String key) {
        for (Provider p : PROVIDERS) if (p.key.equals(key)) return p;
        return null;
    }

    public static Provider providerFor(String email) {
        String d = domain(email);
        for (Provider p : PROVIDERS) for (String x : p.domains) if (x.equals(d)) return p;
        return null;
    }

    /** Settings of a known provider (login name = the whole address). */
    public static Settings known(String email) {
        Provider p = providerFor(email);
        if (p == null) return null;
        return of(p, email);
    }

    public static Settings of(Provider p, String email) {
        Settings s = new Settings();
        s.imapHost = p.imap;
        s.imapPort = p.imapPort;
        s.smtpHost = p.smtp;
        s.smtpPort = p.smtpPort;
        s.smtpSecurity = p.smtpStarttls ? "starttls" : "ssl";
        s.user = email.trim();
        s.provider = p.key;
        s.source = "known";
        return s;
    }

    /** Addresses of the Thunderbird autoconfig server list for this domain (only the domain is sent). */
    public static List<String> autoconfigUrls(String email) {
        String d = domain(email);
        List<String> out = new ArrayList<>();
        if (!d.matches("[a-z0-9.\\-]+\\.[a-z]{2,}")) return out;
        out.add("https://autoconfig.thunderbird.net/v1.1/" + d);
        out.add("https://autoconfig." + d + "/mail/config-v1.1.xml");
        return out;
    }

    /** Reads an autoconfig XML (clientConfig): the first IMAP and SMTP server with TLS. */
    public static Settings parseAutoconfig(String xml, String email) {
        if (xml == null || !xml.contains("<clientConfig")) return null;
        Settings s = new Settings();
        boolean imap = false, smtp = false;
        Matcher in = Pattern.compile("(?s)<incomingServer\\s+type=\"imap\"\\s*>(.*?)</incomingServer>").matcher(xml);
        while (in.find() && !imap) {
            Map<String, String> v = fields(in.group(1));
            String sec = security(v.get("socketType"));
            if (sec == null || !v.containsKey("hostname")) continue;
            s.imapHost = v.get("hostname");
            s.imapPort = port(v.get("port"), 993);
            s.imapSecurity = sec;
            s.user = username(v.get("username"), email);
            imap = true;
        }
        Matcher out = Pattern.compile("(?s)<outgoingServer\\s+type=\"smtp\"\\s*>(.*?)</outgoingServer>").matcher(xml);
        while (out.find() && !smtp) {
            Map<String, String> v = fields(out.group(1));
            String sec = security(v.get("socketType"));
            if (sec == null || !v.containsKey("hostname")) continue;
            s.smtpHost = v.get("hostname");
            s.smtpPort = port(v.get("port"), 465);
            s.smtpSecurity = sec;
            smtp = true;
        }
        if (!imap || !smtp || !validHost(s.imapHost) || !validHost(s.smtpHost)) return null;
        s.source = "autoconfig";
        return s;
    }

    private static Map<String, String> fields(String block) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<(hostname|port|socketType|username)>\\s*([^<]*?)\\s*</\\1>").matcher(block);
        while (m.find()) if (!out.containsKey(m.group(1))) out.put(m.group(1), m.group(2));
        return out;
    }

    /** Only encrypted connections: SSL/TLS or STARTTLS (plain is refused). */
    private static String security(String socketType) {
        if ("SSL".equalsIgnoreCase(socketType) || "TLS".equalsIgnoreCase(socketType)) return "ssl";
        if ("STARTTLS".equalsIgnoreCase(socketType)) return "starttls";
        return null;
    }

    private static int port(String s, int fallback) {
        try {
            int p = Integer.parseInt(s.trim());
            return p > 0 && p < 65536 ? p : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String username(String template, String email) {
        String e = email.trim();
        String local = e.contains("@") ? e.substring(0, e.lastIndexOf('@')) : e;
        if (template == null || template.isEmpty()) return e;
        return template.replace("%EMAILADDRESS%", e).replace("%EMAILLOCALPART%", local).replace("%EMAILDOMAIN%", domain(e));
    }

    public static boolean validHost(String h) {
        return h != null && h.matches("[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,62})(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,62}))*") && h.length() <= 253;
    }

    /** Last try: imap.domain / smtp.domain (TLS). */
    public static Settings guess(String email) {
        String d = domain(email);
        if (!validHost(d)) return null;
        Settings s = new Settings();
        s.imapHost = "imap." + d;
        s.smtpHost = "smtp." + d;
        s.smtpPort = 587;
        s.smtpSecurity = "starttls";
        s.user = email.trim();
        s.source = "guess";
        return s;
    }
}
