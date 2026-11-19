package org.novaos.mobile;

/** Tests for the Android-independent logic of NovaOS Mobile (run by mobile/build.sh). */
public final class MobileTest {
    private static int failed = 0, passed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++; else { failed++; System.out.println("FAIL: " + what); }
    }

    public static void main(String[] args) {
        check(Greeting.of(5) == Greeting.MORNING && Greeting.of(10) == Greeting.MORNING, "morning");
        check(Greeting.of(11) == Greeting.DAY && Greeting.of(16) == Greeting.DAY, "day");
        check(Greeting.of(17) == Greeting.EVENING && Greeting.of(21) == Greeting.EVENING, "evening");
        check(Greeting.of(22) == Greeting.NIGHT && Greeting.of(0) == Greeting.NIGHT && Greeting.of(4) == Greeting.NIGHT, "night");
        check(Greeting.columns(320, 84, 4, 7) == 4, "small phone: 4 columns");
        check(Greeting.columns(800, 84, 4, 7) == 7, "tablet: at most 7");
        check(Greeting.columns(0, 84, 4, 7) == 4 && Greeting.columns(500, 0, 4, 7) == 4, "minimum");
        check(Greeting.columns(400, 160, 2, 6) == 2 && Greeting.columns(1000, 160, 2, 6) == 6, "channel columns");
        store();
        update();
        System.out.println("MobileTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void store() {
        java.util.List<StoreApp> l = StoreApp.parse("# comment\n"
                + "fdroid|de.schildbach.oeffi|Öffi|unterwegs|Fahrpläne|Timetables|*\n"
                + "play|com.whatsapp|WhatsApp|kommunikation|Nachrichten|Messages|\n"
                + "other|x.y|X|a|b|c|\n"
                + "fdroid|not a package|X|a|b|c|\n"
                + "fdroid|a.b|Kurz|werkzeuge|nur deutsch\n"
                + "play|org.example.app|Beispiel|unterwegs|Text|Text\n");
        check(l.size() == 3, "store: 3 valid apps, got " + l.size());
        check(l.get(0).fromFdroid() && l.get(0).recommended && l.get(0).text(true).equals("Fahrpläne"), "store fields");
        check(!l.get(1).fromFdroid() && l.get(1).playUrl().endsWith("id=com.whatsapp"), "play url");
        check(l.get(0).fdroidApk(140009).equals("https://f-droid.org/repo/de.schildbach.oeffi_140009.apk"), "apk url");
        java.util.Map<String, java.util.List<StoreApp>> sec = StoreApp.sections(l);
        check(new java.util.ArrayList<>(sec.keySet()).equals(java.util.Arrays.asList("empfohlen", "unterwegs", "kommunikation")),
                "sections: " + sec.keySet());
        check(sec.get("unterwegs").size() == 2, "category keeps recommended app too");
        String api = "{\"packageName\":\"x.y\",\"suggestedVersionCode\":140009,\"packages\":["
                + "{\"versionName\":\"14.0.9\",\"versionCode\":140009},{\"versionName\":\"14.0.8\",\"versionCode\":140008}]}";
        check(StoreApp.suggestedCode(api) == 140009 && StoreApp.versionName(api, 140009).equals("14.0.9"), "f-droid api");
        check(StoreApp.suggestedCode("{}") == -1 && StoreApp.suggestedCode(null) == -1, "f-droid api broken");
    }

    private static void update() {
        String ok = "versionCode=10100031\nversionName=0.1.1.31\napk=novaos-mobile.apk\n"
                + "sha256=" + "ab".repeat(32) + "\nsize=2413216\nnote.de.1=Neu: Store\nnote.de.2=Kamera\nnote.en.1=New: store\n";
        UpdateInfo u = UpdateInfo.parse(ok);
        check(u != null && u.versionCode == 10100031 && u.versionName.equals("0.1.1.31"), "update parse");
        check(u != null && u.notes(true).size() == 2 && u.notes(false).get(0).equals("New: store"), "update notes");
        check(u != null && u.newerThan(10100030) && !u.newerThan(10100031), "newer");
        check(UpdateInfo.parse(ok.replace("novaos-mobile.apk", "../evil.apk")) == null, "apk name checked");
        check(UpdateInfo.parse(ok.replace("ab", "zz")) == null, "sha256 checked");
        check(UpdateInfo.parse("<html>404</html>") == null && UpdateInfo.parse(null) == null, "not an update file");
        check(UpdateInfo.whatsNew("# x\nA|B\nnur de\n", false).equals(java.util.Arrays.asList("B", "nur de")), "whats new");
        check(UpdateInfo.size(2413216, true).equals("2,3 MB") && UpdateInfo.size(2048, false).equals("2 KB"), "size");
    }
}
