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
        files();
        registry();
        if (args.length > 0) resources(args[0]);
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

    private static void files() {
        check(FileNames.valid("Urlaub 2026") && FileNames.valid("a.txt") && FileNames.valid("  x  "), "valid names");
        check(!FileNames.valid("") && !FileNames.valid("   ") && !FileNames.valid(null), "empty name");
        check(!FileNames.valid(".") && !FileNames.valid("..") && !FileNames.valid("a/b") && !FileNames.valid("a\0b"),
                "no paths");
        check(!FileNames.valid(new String(new char[256]).replace('\0', 'x')), "max 255 characters");
        check(FileNames.inside("/storage/emulated/0", "/storage/emulated/0/DCIM/a.jpg"), "inside");
        check(FileNames.inside("/storage/emulated/0", "/storage/emulated/0"), "root itself");
        check(!FileNames.inside("/storage/emulated/0", "/storage/emulated/01/x")
                && !FileNames.inside("/storage/emulated/0", "/data/data/x"), "outside");
        check(FileNames.extension("Foto.JPG").equals("jpg") && FileNames.extension(".bashrc").equals("")
                && FileNames.extension("a.") .equals("") && FileNames.extension("x.tar.gz").equals("gz"), "extension");
        java.io.File tmp = new java.io.File(System.getProperty("java.io.tmpdir"), "novaos-files-test-" + System.nanoTime());
        java.io.File dir = new java.io.File(tmp, "zeta");
        java.io.File a = new java.io.File(tmp, "Alpha.txt");
        java.io.File b = new java.io.File(tmp, "beta.txt");
        try {
            check(dir.mkdirs() && a.createNewFile() && b.createNewFile(), "test files");
            java.util.List<java.io.File> l = new java.util.ArrayList<>(java.util.Arrays.asList(b, a, dir));
            l.sort(FileNames.ORDER);
            check(l.get(0).equals(dir) && l.get(1).equals(a) && l.get(2).equals(b), "folders first, then by name");
        } catch (java.io.IOException e) {
            check(false, "test files: " + e);
        } finally {
            a.delete();
            b.delete();
            dir.delete();
            tmp.delete();
        }
    }

    /** The real Nova Store catalogue and "what's new" file. */
    private static void resources(String res) {
        try {
            String store = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(res, "raw", "store.txt")),
                    java.nio.charset.StandardCharsets.UTF_8);
            int lines = 0;
            for (String line : store.split("\n")) if (!line.trim().isEmpty() && !line.startsWith("#")) lines++;
            java.util.List<StoreApp> apps = StoreApp.parse(store);
            check(apps.size() == lines && lines >= 20, "store.txt: every line valid (" + apps.size() + "/" + lines + ")");
            java.util.Set<String> pkgs = new java.util.HashSet<>();
            java.util.Set<String> cats = new java.util.HashSet<>(java.util.Arrays.asList("kommunikation", "unterwegs",
                    "multimedia", "sicherheit", "werkzeuge", "unterhaltung"));
            int fdroid = 0;
            for (StoreApp app : apps) {
                check(pkgs.add(app.pkg), "store.txt: " + app.pkg + " only once");
                check(cats.contains(app.category), "store.txt: category of " + app.pkg);
                check(!app.textDe.isEmpty() && !app.textEn.isEmpty(), "store.txt: texts of " + app.pkg);
                if (app.fromFdroid()) fdroid++;
            }
            check(fdroid >= 10, "store.txt: F-Droid apps " + fdroid);
            check(StoreApp.sections(apps).keySet().iterator().next().equals("empfohlen"), "store.txt: recommended first");
            String news = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(res, "raw", "whatsnew.txt")),
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<String> de = UpdateInfo.whatsNew(news, true), en = UpdateInfo.whatsNew(news, false);
            check(!de.isEmpty() && de.size() == en.size() && de.size() <= 8, "whatsnew.txt: " + de.size() + " points");
        } catch (java.io.IOException e) {
            check(false, "resources: " + e);
        }
    }

    /** Software update from the container registry: JSON, token, manifest, URLs. */
    private static void registry() {
        Object j = MiniJson.parse("{\"a\": [1, 2.5, -3e2, true, false, null, \"x\\\"y\\u00e4\\n\"], \"b\": {\"c\": {}}, \"d\": []}");
        java.util.List<?> a = (java.util.List<?>) MiniJson.get(j, "a");
        check(a.size() == 7 && a.get(1).equals(2.5) && a.get(2).equals(-300.0) && a.get(3).equals(true)
                && a.get(5) == null && a.get(6).equals("x\"yä\n"), "json values");
        check(MiniJson.get(j, "b", "c") instanceof java.util.Map && MiniJson.get(j, "b", "x") == null
                && MiniJson.get(j, "a", "x") == null, "json path");
        for (String bad : new String[] {"", "{", "{\"a\" 1}", "[1,]x", "\"abc", "{\"a\":tru}", "{} {}"}) {
            boolean failed = false;
            try {
                MiniJson.parse(bad);
            } catch (IllegalArgumentException e) {
                failed = true;
            }
            check(failed, "json rejects " + bad);
        }
        check("abc".equals(Registry.token("{\"token\":\"abc\"}")) && "x".equals(Registry.token("{\"access_token\":\"x\"}"))
                && Registry.token("{\"errors\":[{\"code\":\"DENIED\"}]}") == null && Registry.token("<html>") == null,
                "token");
        String apk = "sha256:" + "a1".repeat(32), props = "sha256:" + "b2".repeat(32);
        String manifest = "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\","
                + "\"config\":{\"mediaType\":\"application/vnd.oci.empty.v1+json\",\"digest\":\"sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a\",\"size\":2},"
                + "\"layers\":[{\"mediaType\":\"application/vnd.android.package-archive\",\"digest\":\"" + apk + "\",\"size\":2511976,"
                + "\"annotations\":{\"org.opencontainers.image.title\":\"novaos-mobile.apk\"}},"
                + "{\"annotations\":{\"org.opencontainers.image.title\":\"update.properties\"},\"digest\":\"" + props + "\",\"size\":900}],"
                + "\"annotations\":{\"org.opencontainers.image.source\":\"https://github.com/x/y\"}}";
        check(apk.equals(Registry.digest(manifest, "novaos-mobile.apk")) && props.equals(Registry.digest(manifest, "update.properties")),
                "manifest digests (any key order)");
        check(Registry.digest(manifest, "other.apk") == null && Registry.digest("{}", "x") == null
                && Registry.digest("nonsense", "x") == null
                && Registry.digest(manifest.replace(apk, "sha256:1234"), "novaos-mobile.apk") == null, "manifest checks");
        check(Registry.hex(apk).equals("a1".repeat(32)), "hex");
        Registry r = new Registry("ghcr.io", "superali99-tech/novaos-mobile");
        check(r.tokenUrl().equals("https://ghcr.io/token?scope=repository:superali99-tech/novaos-mobile:pull&service=ghcr.io")
                && r.manifestUrl("latest").equals("https://ghcr.io/v2/superali99-tech/novaos-mobile/manifests/latest")
                && r.blobUrl(apk).equals("https://ghcr.io/v2/superali99-tech/novaos-mobile/blobs/" + apk), "registry urls");
        check(new Registry("10.0.2.2:8443", "a/b").tokenUrl().endsWith("&service=10.0.2.2"), "registry with port");
        for (String[] bad : new String[][] {{"ghcr.io/x", "a/b"}, {"ghcr.io", "nobody"}, {"ghcr.io", "A/B"}, {"ghcr.io", "a/b?x"}}) {
            boolean failed = false;
            try {
                new Registry(bad[0], bad[1]);
            } catch (IllegalArgumentException e) {
                failed = true;
            }
            check(failed, "registry rejects " + bad[0] + " " + bad[1]);
        }
    }
}
