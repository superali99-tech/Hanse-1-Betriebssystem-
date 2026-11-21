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
        nova();
        chat();
        chatMedia();
        mail();
        family();
        weather();
        search();
        history();
        storeMeta();
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

    private static void search() {
        check(Search.matches("", "x") && Search.matches("  ", (String) null), "search: empty query matches all");
        check(Search.matches("muller", "Anna Müller") && Search.matches("mueller", "Anna Müller"), "search: umlauts both ways");
        check(Search.matches("oeffi", "Öffi") && Search.matches("öffi", "Oeffi") == false, "search: ö -> oe, but oe is not ö");
        check(Search.matches("STRASSE", "Hauptstraße 1") && Search.matches("cafe", "Café"), "search: ß and accents");
        check(Search.matches("anna mail", "Anna", "anna@mail.de") && !Search.matches("anna post", "Anna", "anna@mail.de"),
                "search: every word must appear");
        check(Search.matches("whats", null, "WhatsApp"), "search: null texts are skipped");
    }

    private static void storeMeta() {
        java.util.Map<String, StoreMeta> m = StoreMeta.parseAll("{\"a.b\":{\"author\":\"X\",\"size\":2021432,\"minSdk\":26,"
                + "\"web\":\"http://plain.example\",\"source\":\"https://git.example/a\",\"shots\":[\"/a.b/de/phoneScreenshots/1.png\","
                + "\"/../evil.png\",\"https://other/x.png\",3],\"anti\":[\"Werbung\"]},\"bad\":5}");
        StoreMeta a = m.get("a.b");
        check(m.size() == 1 && a != null && a.author.equals("X") && a.size == 2021432 && a.minSdk == 26, "store meta fields");
        check(a != null && a.web.isEmpty() && a.source.equals("https://git.example/a"), "store meta: only https links");
        check(a != null && a.shots.equals(java.util.Arrays.asList("/a.b/de/phoneScreenshots/1.png")), "store meta: safe screenshot paths");
        check(StoreMeta.parseAll("kaputt").isEmpty() && StoreMeta.parseAll("[]").isEmpty(), "store meta broken");
    }

    private static void history() {
        java.util.List<History.Entry> h = History.parse("# comment\n== NovaOS 2 · heute\nNeu A|New A\nnur de\n\n== NovaOS 1\nAlt|Old\n");
        check(h.size() == 2 && h.get(0).title.equals("NovaOS 2 · heute") && h.get(1).title.equals("NovaOS 1"), "history titles");
        check(h.get(0).lines(true).equals(java.util.Arrays.asList("Neu A", "nur de"))
                && h.get(0).lines(false).equals(java.util.Arrays.asList("New A", "nur de")), "history lines");
        check(History.parse("Zeile ohne Titel|x\n").isEmpty() && History.parse(null).isEmpty(), "history without title");
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
        check(UpdateInfo.whatsNew("@apps: chat\nA|B\n", true).equals(java.util.Arrays.asList("A")), "whats new without @apps");
        check(UpdateInfo.changedApps("# x\n@apps: Chat, mail ,../x, store\nA|B").equals(java.util.Arrays.asList("chat", "mail", "store")),
                "changed apps from whatsnew");
        UpdateInfo ua = UpdateInfo.parse(ok + "apps=store,chat,store,b@d\n");
        check(ua != null && ua.apps.equals(java.util.Arrays.asList("store", "chat")), "update apps");
        check(u != null && u.apps.isEmpty(), "update without apps");
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
    private static java.util.Set<String> names(java.io.File dir) throws java.io.IOException {
        java.util.Set<String> out = new java.util.TreeSet<>();
        java.io.File[] files = dir.listFiles((d, n) -> n.startsWith("strings") && n.endsWith(".xml"));
        if (files == null) return out;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<(string|plurals) name=\"([a-z0-9_]+)\"(?![^>]*translatable=\"false\")");
        for (java.io.File f : files) {
            java.util.regex.Matcher m = p.matcher(new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            while (m.find()) out.add(m.group(2));
        }
        return out;
    }

    private static void resources(String res) {
        try {
            java.util.Set<String> en = names(new java.io.File(res, "values")), de = names(new java.io.File(res, "values-de"));
            java.util.Set<String> missingDe = new java.util.TreeSet<>(en), missingEn = new java.util.TreeSet<>(de);
            missingDe.removeAll(de);
            missingEn.removeAll(en);
            check(missingDe.isEmpty(), "strings missing in German: " + missingDe);
            check(missingEn.isEmpty(), "strings missing in English: " + missingEn);
            check(en.size() > 300, "strings found: " + en.size());
        } catch (java.io.IOException e) {
            check(false, "strings: " + e);
        }
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
            java.util.Set<String> novaKeys = new java.util.HashSet<>(java.util.Arrays.asList("nova", "chat", "mail", "family", "store",
                    "camera", "gallery", "files", "notes", "settings", "music", "shop", "update"));
            for (String key : UpdateInfo.changedApps(news)) check(novaKeys.contains(key), "whatsnew.txt: unknown Nova app " + key);
            java.util.Map<String, StoreMeta> meta = StoreMeta.parseAll(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(res, "raw", "store_meta.json")), java.nio.charset.StandardCharsets.UTF_8));
            for (StoreApp app : apps) {
                if (!app.fromFdroid()) continue;
                StoreMeta m = meta.get(app.pkg);
                check(m != null && m.size > 0 && m.code > 0 && !m.version.isEmpty(), "store_meta.json: details of " + app.pkg);
            }
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
        check(Registry.releaseAsset("superali99-tech/Hanse-1-Betriebssystem-", "mobile-update", "update.properties").equals(
                "https://github.com/superali99-tech/Hanse-1-Betriebssystem-/releases/download/mobile-update/update.properties"),
                "release asset url");
        for (String[] bad : new String[][] {{"nobody", "t", "f"}, {"a/b", "t", "../x"}, {"a/b", "t", ".."}, {"a/b/c", "t", "f"},
                {"a/b", "t/x", "f"}, {"a/b", "t", "f?x"}}) {
            boolean failed = false;
            try {
                Registry.releaseAsset(bad[0], bad[1], bad[2]);
            } catch (IllegalArgumentException e) {
                failed = true;
            }
            check(failed, "release asset rejects " + String.join(" ", bad));
        }
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

    private static String nova(String spoken) {
        return NovaBrain.understand(spoken, true, java.util.Arrays.asList("Das Erste", "ZDF", "ZDFneo", "NDR Hamburg", "arte", "phoenix"), 0).toString();
    }

    private static void expect(String spoken, String expected) {
        String got = nova(spoken);
        check(got.equals(expected), "nova: \"" + spoken + "\" -> " + got + " (expected " + expected + ")");
    }

    /** Nova assistant: phrases in German and English, without cloud AI. */
    private static void nova() {
        expect("Hey Nova, wie spät ist es?", "TIME()");
        expect("Wie viel Uhr ist es", "TIME()");
        expect("what time is it", "TIME()");
        expect("Welches Datum haben wir heute", "DATE()");
        expect("Stell einen Timer auf 5 Minuten", "TIMER( 300s)");
        expect("Timer fünf Minuten", "TIMER( 300s)");
        expect("Timer 1 Stunde 30 Minuten", "TIMER( 5400s)");
        expect("Timer für eine halbe Stunde", "TIMER( 1800s)");
        expect("set a timer for twenty five seconds", "TIMER( 25s)");
        expect("Timer für eine Minute", "TIMER( 60s)");
        expect("Stelle einen Wecker auf 7 Uhr", "ALARM( 7:0)");
        expect("Weck mich um 6:45", "ALARM( 6:45)");
        expect("Wecker um halb sieben", "ALARM( 6:30)");
        expect("Wecker 7 Uhr 30", "ALARM( 7:30)");
        expect("Stell den Wecker auf 8", "ALARM( 8:0)");
        expect("wake me at 7 am", "ALARM( 7:0)");
        expect("set an alarm for 6:30 pm", "ALARM( 18:30)");
        expect("Wecker stellen", "ALARM()");
        expect("Taschenlampe an", "TORCH()");
        check(NovaBrain.understand("Taschenlampe an", true).level == 1, "torch on");
        check(NovaBrain.understand("Mach das Licht aus", true).level == 0, "torch off");
        check(NovaBrain.understand("turn off the flashlight", false).level == 0, "torch off en");
        expect("Öffne Kamera", "OPEN_APP(Kamera)");
        expect("Hey Nova starte die Galerie", "OPEN_APP(Galerie)");
        expect("Nova Store öffnen", "OPEN_APP(Nova Store)");
        expect("open spotify", "OPEN_APP(spotify)");
        expect("Zeig mir meine Fotos", "OPEN_APP(Fotos)");
        expect("Öffne die WLAN Einstellungen", "SETTINGS(wifi)");
        expect("Bluetooth öffnen", "SETTINGS(bluetooth)");
        expect("Einstellungen", "SETTINGS(settings)");
        expect("Ruf Mama an", "CALL(Mama)");
        expect("Rufe meinen Bruder an", "CALL(Bruder)");
        expect("call John", "CALL(John)");
        expect("Papa anrufen", "CALL(Papa)");
        expect("Schreib Mama eine Nachricht dass ich später komme", "MESSAGE(Mama|ich später komme)");
        expect("Schreibe eine Nachricht an Lisa", "MESSAGE(Lisa)");
        expect("send a message to Tom saying hello", "MESSAGE(Tom|hello)");
        expect("Notiere Brot und Milch kaufen", "NOTE(Brot und Milch kaufen)");
        expect("Merke dir: Auto steht in der Tiefgarage", "NOTE(Auto steht in der Tiefgarage)");
        expect("Wie ist das Wetter?", "WEATHER()");
        expect("Wie wird das Wetter in Hamburg", "WEATHER(Hamburg)");
        expect("Wetter in München heute", "WEATHER(München)");
        expect("what's the weather in London", "WEATHER(London)");
        expect("Spiele ZDF", "TV(ZDF)");
        expect("Schalte auf ARD", "TV(Das Erste)");
        expect("Spiel ZDFneo", "TV(ZDFneo)");
        expect("Spiele NDR", "TV(NDR Hamburg)");
        expect("Fernsehen", "TV()");
        expect("Suche nach Pizza in der Nähe", "SEARCH(Pizza in der Nähe)");
        expect("Wer ist Albert Einstein", "SEARCH(Wer ist Albert Einstein)");
        expect("Lauter", "VOLUME()");
        check(NovaBrain.understand("Leiser bitte", true).level == -1, "volume down");
        check(NovaBrain.understand("Ton aus", true).level == 0, "mute");
        expect("Mach ein Foto", "PHOTO()");
        expect("Was kannst du?", "HELP()");
        expect("Danke", "TALK()");
        expect("Erzähl mir einen Witz", "TALK()");
        expect("Blablubb Quatsch", "UNKNOWN(Blablubb Quatsch)");
        // calculator
        String[][] sums = {{"Was ist 5 mal 7", "35"}, {"Rechne 12 plus 30", "42"}, {"wie viel ist 100 geteilt durch 8", "12,5"},
                {"was ist zwanzig minus drei", "17"}, {"15 Prozent von 80", "12"}, {"Wurzel aus 144", "12"},
                {"2 hoch 10", "1024"}, {"what is 6 times 7", "42"}, {"3 + 4 * 2", "11"}, {"(3 + 4) * 2", "14"},
                {"was ist 3,5 plus 1,25", "4,75"}};
        for (String[] t : sums) {
            NovaBrain.Command r = NovaBrain.understand(t[0], true);
            check(r.kind == NovaBrain.Kind.CALC && NovaBrain.format(r.value, true).equals(t[1]),
                    "calc \"" + t[0] + "\" -> " + r + " = " + NovaBrain.format(r.value, true));
        }
        check(NovaBrain.understand("Ruf 0151-1234 an", true).kind == NovaBrain.Kind.CALL, "phone number is no sum");
        check(NovaBrain.hasWakeWord("Hey Nova, wie spät ist es") && NovaBrain.hasWakeWord("hallo nova")
                && !NovaBrain.hasWakeWord("Nova Store öffnen") && !NovaBrain.hasWakeWord("supernova"), "wake word");
        check(NovaBrain.clean("Hey Nova, kannst du bitte die Taschenlampe einschalten?").equals("die taschenlampe einschalten"), "clean");
        check(NovaBrain.understand("", true).kind == NovaBrain.Kind.TALK, "empty -> listening");
        check(NovaBrain.understand("Hey Nova, bitte notiere: Termin beim Zahnarzt am Montag", true).text.equals("Termin beim Zahnarzt am Montag"),
                "note keeps spelling " + NovaBrain.understand("Hey Nova, bitte notiere: Termin beim Zahnarzt am Montag", true).text);
    }

    /** Nova Chat: Matrix /sync answers, contacts, Matrix IDs, JSON writing. */
    private static long at(int y, int mo, int d, int h, int mi) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Berlin"));
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    private static void family() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Europe/Berlin");
        // the family code
        String code = FamilyCrypto.newCode();
        check(code.matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}"), "code format " + code);
        check(FamilyCrypto.normalize(code.toLowerCase().replace("-", " ")).equals(code), "code normalize");
        check(FamilyCrypto.normalize("K7QM-2XRA-9PLD-W4E0") == null && FamilyCrypto.normalize("K7QM") == null
                && FamilyCrypto.normalize(null) == null && FamilyCrypto.normalize("K7QM-2XRA-9PLD-W4EO") == null, "bad codes");
        check(!FamilyCrypto.newCode().equals(FamilyCrypto.newCode()), "codes differ");
        String topic = FamilyCrypto.topic("K7QM-2XRA-9PLD-W4EH");
        check(topic.equals(FamilyCrypto.topic("K7QM-2XRA-9PLD-W4EH")) && topic.matches("novaos-[0-9a-f]{32}")
                && !topic.equals(FamilyCrypto.topic("K7QM-2XRA-9PLD-W4EJ")), "topic " + topic);
        byte[] key = FamilyCrypto.key("K7QM-2XRA-9PLD-W4EH");
        check(key.length == 32 && java.util.Arrays.equals(key, FamilyCrypto.key("K7QM-2XRA-9PLD-W4EH")), "key");
        String sealed = FamilyCrypto.seal(key, topic, "{\"type\":\"policy\",\"ä\":1}");
        check(sealed.startsWith("n1.") && !sealed.contains("policy"), "sealed hides content");
        check("{\"type\":\"policy\",\"ä\":1}".equals(FamilyCrypto.open(key, topic, sealed)), "open");
        check(!sealed.equals(FamilyCrypto.seal(key, topic, "{\"type\":\"policy\",\"ä\":1}")), "random iv");
        check(FamilyCrypto.open(FamilyCrypto.key("K7QM-2XRA-9PLD-W4EJ"), topic, sealed) == null, "other family cannot read");
        check(FamilyCrypto.open(key, "novaos-other", sealed) == null, "other topic refused");
        char[] t = sealed.toCharArray();
        t[10] = t[10] == 'A' ? 'B' : 'A';
        check(FamilyCrypto.open(key, topic, new String(t)) == null, "changed message refused");
        check(FamilyCrypto.open(key, topic, "hallo") == null && FamilyCrypto.open(key, topic, null) == null, "garbage refused");
        check(FamilyCrypto.pinHash("1234", "s1").equals(FamilyCrypto.pinHash("1234", "s1")) && !FamilyCrypto.pinHash("1234", "s1").equals(FamilyCrypto.pinHash("1234", "s2"))
                && !FamilyCrypto.pinHash("1234", "s1").equals(FamilyCrypto.pinHash("1235", "s1")), "pin hash");

        // locked now / until
        FamilyPolicy p = new FamilyPolicy();
        long now = at(2026, 11, 23, 16, 0);  // Monday 16:00
        check(!p.evaluate(now, tz, 0, 0).locked, "free by default");
        p.lockUntil = -1;
        FamilyPolicy.State s = p.evaluate(now, tz, 0, 0);
        check(s.locked && s.reason.equals("lock") && s.until == -1, "locked until parents");
        p.lockUntil = at(2026, 11, 24, 7, 0);
        s = p.evaluate(now, tz, 0, 0);
        check(s.locked && s.until == at(2026, 11, 24, 7, 0), "locked until 7:00");
        check(!p.evaluate(at(2026, 11, 24, 7, 0), tz, 0, 0).locked, "free at 7:00");
        check(!p.evaluate(now, tz, 0, now + 1000).locked, "parents' PIN on the phone");
        p.freeUntil = now + 60000;
        check(!p.evaluate(now, tz, 0, 0).locked, "unlocked by parents");
        p.freeUntil = 0;
        p.lockUntil = 0;
        // bedtime 21:00-07:00 every day
        p.bedtime = true;
        p.bedFrom = 21 * 60;
        p.bedTo = 7 * 60;
        s = p.evaluate(at(2026, 11, 23, 22, 30), tz, 0, 0);
        check(s.locked && s.reason.equals("bedtime") && s.until == at(2026, 11, 24, 7, 0), "bedtime evening " + s);
        check(p.evaluate(at(2026, 11, 24, 6, 59), tz, 0, 0).locked, "bedtime morning");
        check(!p.evaluate(at(2026, 11, 24, 7, 0), tz, 0, 0).locked && !p.evaluate(at(2026, 11, 23, 20, 59), tz, 0, 0).locked, "bedtime edges");
        p.freeUntil = at(2026, 11, 23, 23, 0);
        check(!p.evaluate(at(2026, 11, 23, 22, 30), tz, 0, 0).locked, "free beats bedtime");
        p.freeUntil = 0;
        // school nights: Sunday to Thursday evenings
        p.days = 0x4F;
        check(!p.evaluate(at(2026, 11, 27, 22, 0), tz, 0, 0).locked, "Friday night free");
        check(!p.evaluate(at(2026, 11, 28, 6, 0), tz, 0, 0).locked, "Saturday morning free");
        check(p.evaluate(at(2026, 11, 29, 22, 0), tz, 0, 0).locked, "Sunday night locked");
        check(p.evaluate(at(2026, 11, 30, 6, 0), tz, 0, 0).locked, "Monday morning locked");
        check(p.evaluate(at(2026, 11, 26, 23, 0), tz, 0, 0).locked && !p.evaluate(at(2026, 11, 27, 23, 0), tz, 0, 0).locked, "Thursday vs Friday");
        // midday bedtime on Mondays only
        p.days = 0x01;
        p.bedFrom = 13 * 60;
        p.bedTo = 15 * 60;
        check(p.evaluate(at(2026, 11, 23, 14, 0), tz, 0, 0).locked && !p.evaluate(at(2026, 11, 24, 14, 0), tz, 0, 0).locked, "midday Monday only");
        p.bedtime = false;
        // screen time
        p.limitMinutes = 60;
        check(!p.evaluate(now, tz, 59, 0).locked, "under the limit");
        s = p.evaluate(now, tz, 60, 0);
        check(s.locked && s.reason.equals("limit") && s.until == at(2026, 11, 24, 0, 0), "limit reached " + s);
        p.bonusDay = FamilyPolicy.day(now, tz);
        p.bonusMinutes = 30;
        check(!p.evaluate(now, tz, 60, 0).locked && p.evaluate(now, tz, 90, 0).locked, "more time today");
        check(p.evaluate(at(2026, 11, 24, 16, 0), tz, 60, 0).locked, "bonus only today");
        // JSON
        p.blocked.add("com.example.game");
        p.callable.add(new String[] {"Mama", "+49 170 1234567"});
        p.message = "Schlafenszeit 💤";
        p.version = 42;
        FamilyPolicy q = FamilyPolicy.fromJson(MiniJson.parse(MiniJson.write(p.toJson())));
        check(q.version == 42 && q.limitMinutes == 60 && q.bonusMinutes == 30 && q.blocked.equals(p.blocked) && q.callable.get(0)[0].equals("Mama")
                && q.message.equals("Schlafenszeit 💤") && q.days == 0x01 && q.bedFrom == 13 * 60, "policy json");
        check(FamilyPolicy.fromJson("x").version == 0 && FamilyPolicy.fromJson(MiniJson.parse("{\"bedFrom\":99999,\"days\":255}")).bedFrom == 1439,
                "policy json garbage");
        check(FamilyPolicy.parseTime("21:30") == 21 * 60 + 30 && FamilyPolicy.parseTime("7 Uhr") == 420 && FamilyPolicy.parseTime("25:00") == -1
                && FamilyPolicy.time(420).equals("7:00"), "times");
        // relay
        FamilyRelay.Message rm = FamilyRelay.parse("{\"id\":\"abc\",\"time\":1795197267,\"event\":\"message\",\"message\":\"n1.xyz\"}");
        check(rm != null && rm.id.equals("abc") && rm.text.equals("n1.xyz") && rm.time == 1795197267L, "relay message");
        check(FamilyRelay.parse("{\"event\":\"keepalive\"}") == null && FamilyRelay.parse("kaputt") == null, "relay other events");
        check(FamilyRelay.url("https://ntfy.sh/", topic, "/json").equals("https://ntfy.sh/" + topic + "/json"), "relay url");
        boolean refused = false;
        try {
            FamilyRelay.url("http://ntfy.sh", topic, "");
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "relay only https");
    }

    private static void mail() {
        // headers, encoded words, addresses, dates
        check(Mime.decodeWords("=?UTF-8?Q?Gr=C3=BC=C3=9Fe_aus?= =?UTF-8?B?SGFtYnVyZw==?=").equals("Grüße ausHamburg"), "encoded words join");
        check(Mime.decodeWords("=?iso-8859-1?q?M=FCller?=").equals("Müller") && Mime.decodeWords("plain").equals("plain"), "latin1 word");
        java.util.List<Mime.Address> ad = Mime.addresses("\"Müller, Max\" <max@y.de>, Lisa <lisa@x.de>, z@w.de, Freunde: a@b.de, c@d.de;, kaputt");
        check(ad.size() == 5 && ad.get(0).name.equals("Müller, Max") && ad.get(1).email.equals("lisa@x.de") && ad.get(2).name.isEmpty()
                && ad.get(3).email.equals("a@b.de"), "addresses " + ad);
        check(Mime.date("Fri, 20 Nov 2026 18:22:05 +0100 (CET)") == 1795195325000L, "date " + Mime.date("Fri, 20 Nov 2026 18:22:05 +0100 (CET)"));
        check(Mime.date("20 Nov 2026 17:22:05 GMT") == 1795195325000L && Mime.date("kaputt") == 0 && Mime.date(null) == 0, "date variants");
        check(Mime.headers("Subject: a\r\n b\r\nFrom: x@y\r\n").get("subject").equals("a b"), "unfolding");
        check(Mime.headers("Subject: Gr\u00c3\u00bc\u00c3\u009fe\n").get("subject").equals("Grüße"), "raw utf-8 header");
        // a real multipart mail: text, html, attachment with RFC 2231 name, quoted-printable
        String raw = "From: =?UTF-8?Q?J=C3=B6rg?= <joerg@x.de>\r\nTo: a@b.de\r\nSubject: Test\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"XX\"\r\n\r\npreamble\r\n--XX\r\n"
                + "Content-Type: multipart/alternative; boundary=YY\r\n\r\n--YY\r\nContent-Type: text/plain; charset=utf-8\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n\r\nSch=C3=B6ne Gr=C3=BC=\r\n=C3=9Fe\r\n--YY\r\n"
                + "Content-Type: text/html; charset=windows-1252\r\n\r\n<p>Sch\u00f6ne &amp; <b>Gr\u00fc\u00dfe</b></p>\r\n--YY--\r\n"
                + "--XX\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename*=UTF-8''Rechnung%20M%C3%A4rz.pdf\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\nJVBERi0xLjQK\r\n--XX--\r\n";
        Mime.Message m = Mime.parse(raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        check(m.sender().name.equals("Jörg") && m.subject.equals("Test"), "parsed header");
        check(m.text.equals("Schöne Grüße"), "qp text: [" + m.text + "]");
        check(m.html.contains("Grüße") && Mime.htmlToText(m.html).equals("Schöne & Grüße"), "html " + Mime.htmlToText(m.html));
        check(m.attachments.size() == 1 && m.attachments.get(0).name.equals("Rechnung März.pdf")
                && new String(m.attachments.get(0).data).startsWith("%PDF-1.4"), "attachment rfc2231");
        // building: round trip through the parser
        Mime.Outgoing o = new Mime.Outgoing();
        o.from = new Mime.Address("Alice Müller", "alice@nova.test");
        o.to.add(new Mime.Address("Bob", "bob@x.de"));
        o.cc.add(new Mime.Address("Kim, Lee", "kim@x.de"));
        o.subject = "Grüße aus Hamburg ☀️ – eine ziemlich lange Betreffzeile mit Umlauten äöü";
        o.text = "Zeile 1\n.\nZeile mit Leerzeichen am Ende \nÄÖÜ €";
        Mime.Attachment at = new Mime.Attachment();
        at.name = "Übersicht.txt";
        at.type = "text/plain";
        at.data = "Inhalt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        o.attachments.add(at);
        Mime.Message back = Mime.parse(Mime.build(o));
        check(back.subject.equals(o.subject), "subject round trip: " + back.subject);
        check(back.text.equals(o.text), "text round trip: [" + back.text + "]");
        check(back.sender().name.equals("Alice Müller") && back.cc.get(0).name.equals("Kim, Lee"), "names round trip");
        check(back.attachments.get(0).name.equals("Übersicht.txt") && new String(back.attachments.get(0).data).equals("Inhalt"), "attachment round trip");
        check(Mime.qpEncode("x".repeat(200)).split("\r\n")[0].length() <= 76, "qp line length");
        check(new String(Mime.base64Decode(Mime.base64("Hallo Welt äöü".getBytes(java.nio.charset.StandardCharsets.UTF_8), 76)),
                java.nio.charset.StandardCharsets.UTF_8).equals("Hallo Welt äöü"), "base64 round trip");
        check(Mime.replySubject("AW: x").equals("AW: x") && Mime.replySubject("x").equals("Re: x") && Mime.forwardSubject("WG: y").equals("WG: y"),
                "reply/forward subject");
        check(Mime.validEmail("a.b+c@x-y.de") && !Mime.validEmail("a@b") && !Mime.validEmail("a b@c.de"), "valid email");
        check(Mime.preview("> zitat\nHallo\n\nWelt\n--\nSignatur", 50).equals("Hallo Welt"), "preview");
        check(Smtp.dotStuff(".a\nb\n.c".getBytes()).length == ("..a\r\nb\r\n..c\r\n").length(), "dot stuffing");
        // IMAP parsing
        java.util.List<Object> l = ImapParser.parse("* 3 FETCH (UID 17 FLAGS (\\Seen) BODY[HEADER.FIELDS (FROM SUBJECT)] {12}\r\nSubject: x\r\n)"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        java.util.List<?> f = (java.util.List<?>) l.get(3);
        check(f.get(0).equals("UID") && f.get(1).equals("17") && f.get(4).equals("BODY[HEADER.FIELDS (FROM SUBJECT)]")
                && ImapParser.string(f.get(5)).equals("Subject: x\r\n"), "imap fetch parse " + f);
        check(ImapParser.parse("* LIST (\\HasNoChildren) \"/\" NIL".getBytes()).get(4) == null, "imap NIL");
        check(Imap.literalSize("* 1 FETCH (BODY[] {123}") == 123 && Imap.literalSize("* OK") == -1, "literal size");
        check(Utf7.decode("Entw&APw-rfe").equals("Entwürfe") && Utf7.encode("Entwürfe").equals("Entw&APw-rfe")
                && Utf7.decode("A&-B").equals("A&B") && Utf7.decode(Utf7.encode("日本語 & Ümläut")).equals("日本語 & Ümläut"), "modified utf-7");
        check(Imap.guessRole("Gesendete Objekte").equals("sent") && Imap.guessRole("INBOX/Papierkorb").equals("trash")
                && Imap.guessRole("Spamverdacht").equals("junk") && Imap.guessRole("Urlaub").isEmpty(), "folder roles");
        check(Imap.internalDate("17-Jul-1996 02:44:25 -0700") == 837596665000L, "internal date");
        // finding the servers
        MailServers.Settings gmx = MailServers.known("Max@GMX.de");
        check(gmx != null && gmx.imapHost.equals("imap.gmx.net") && gmx.smtpPort == 465 && gmx.user.equals("Max@GMX.de"), "known gmx");
        check(MailServers.known("a@web.de").smtpSecurity.equals("starttls") && MailServers.known("a@unbekannt.example") == null, "known web.de");
        String xml = "<clientConfig version=\"1.1\"><emailProvider id=\"x\"><incomingServer type=\"pop3\"><hostname>pop.x.de</hostname>"
                + "<port>995</port><socketType>SSL</socketType></incomingServer><incomingServer type=\"imap\"><hostname>imap.x.de</hostname>"
                + "<port>143</port><socketType>plain</socketType><username>%EMAILADDRESS%</username></incomingServer>"
                + "<incomingServer type=\"imap\"><hostname>imap.x.de</hostname><port>143</port><socketType>STARTTLS</socketType>"
                + "<username>%EMAILLOCALPART%</username></incomingServer><outgoingServer type=\"smtp\"><hostname>smtp.x.de</hostname>"
                + "<port>587</port><socketType>STARTTLS</socketType></outgoingServer></emailProvider></clientConfig>";
        MailServers.Settings ac = MailServers.parseAutoconfig(xml, "max@x.de");
        check(ac != null && ac.imapSecurity.equals("starttls") && ac.imapPort == 143 && ac.user.equals("max") && ac.smtpHost.equals("smtp.x.de"),
                "autoconfig (plain refused): " + ac);
        check(MailServers.parseAutoconfig("<html>", "a@b.de") == null, "autoconfig garbage");
        check(MailServers.autoconfigUrls("a@meine-firma.de").get(0).equals("https://autoconfig.thunderbird.net/v1.1/meine-firma.de")
                && MailServers.autoconfigUrls("a@b/../c").isEmpty(), "autoconfig urls");
        check(MailServers.guess("a@firma.de").imapHost.equals("imap.firma.de") && MailServers.guess("kaputt") == null, "guess");
    }

    private static void chatMedia() {
        String sync = "{\"next_batch\":\"s9\",\"rooms\":{\"join\":{\"!r:x.org\":{\"timeline\":{\"events\":["
                + "{\"type\":\"m.room.message\",\"event_id\":\"$v\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":5,\"content\":"
                + "{\"msgtype\":\"m.audio\",\"body\":\"Voice message\",\"url\":\"mxc://x.org/AbC_1-2\",\"info\":{\"mimetype\":\"audio/ogg\",\"size\":999,\"duration\":4200},"
                + "\"org.matrix.msc1767.audio\":{\"duration\":4200,\"waveform\":[0,512,2000,-3]},\"org.matrix.msc3245.voice\":{}}},"
                + "{\"type\":\"m.room.message\",\"event_id\":\"$i\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":6,\"content\":"
                + "{\"msgtype\":\"m.image\",\"body\":\"a.jpg\",\"url\":\"mxc://x.org/img\",\"info\":{\"mimetype\":\"image/jpeg\",\"w\":800,\"h\":600}}},"
                + "{\"type\":\"m.room.message\",\"event_id\":\"$e\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":7,\"content\":"
                + "{\"msgtype\":\"m.text\",\"body\":\"* neu\",\"m.new_content\":{\"msgtype\":\"m.text\",\"body\":\"neu\"},"
                + "\"m.relates_to\":{\"rel_type\":\"m.replace\",\"event_id\":\"$old\"}}},"
                + "{\"type\":\"m.room.message\",\"event_id\":\"$q\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":8,\"content\":"
                + "{\"msgtype\":\"m.text\",\"body\":\"> <@me:x.org> Kommst du?\\n\\nJa, gleich!\"}},"
                + "{\"type\":\"m.room.redaction\",\"event_id\":\"$red\",\"sender\":\"@lisa:x.org\",\"redacts\":\"$gone\",\"content\":{}},"
                + "{\"type\":\"m.sticker\",\"event_id\":\"$s\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":9,\"content\":"
                + "{\"body\":\"Sticker\",\"url\":\"mxc://x.org/st\",\"info\":{\"mimetype\":\"image/png\"}}},"
                + "{\"type\":\"m.room.message\",\"event_id\":\"$bad\",\"sender\":\"@lisa:x.org\",\"origin_server_ts\":10,\"content\":"
                + "{\"msgtype\":\"m.file\",\"body\":\"x.pdf\",\"url\":\"https://evil.example/x\"}}"
                + "]}}}}}";
        MatrixSync.Result r = MatrixSync.parse(sync, "@me:x.org");
        java.util.List<MatrixSync.Message> ms = r.rooms.get(0).messages;
        check(ms.size() == 6, "media messages: " + ms.size());
        MatrixSync.Message v = ms.get(0);
        check(v.kind.equals("audio") && v.voice && v.duration == 4200 && v.size == 999 && v.url.equals("mxc://x.org/AbC_1-2")
                && v.mime.equals("audio/ogg"), "voice message");
        check(v.waveform.equals(java.util.Arrays.asList(0, 512, 1024, 0)), "waveform clamped: " + v.waveform);
        check(ms.get(1).kind.equals("image") && ms.get(1).w == 800 && ms.get(1).h == 600, "image size");
        check(ms.get(2).replaces.equals("$old") && ms.get(2).body.equals("neu"), "edit replaces");
        check(ms.get(3).body.equals("Ja, gleich!"), "reply fallback stripped: " + ms.get(3).body);
        check(r.rooms.get(0).redacted.equals(java.util.Arrays.asList("$gone")), "redaction");
        check(ms.get(4).kind.equals("image") && ms.get(4).url.equals("mxc://x.org/st"), "sticker as image");
        check(ms.get(5).url.isEmpty() && ms.get(5).kind.equals("file"), "non-mxc url ignored");
        MatrixSync.Message copy = new MatrixSync.Message();
        MatrixSync.readMedia(copy, MatrixSync.mediaJson(v));
        check(copy.url.equals(v.url) && copy.voice && copy.duration == 4200 && copy.waveform.equals(v.waveform)
                && copy.mime.equals("audio/ogg") && copy.size == 999, "media json round trip");
        check(MatrixSync.mediaJson(new MatrixSync.Message()).isEmpty(), "no media no json");
        MatrixSync.readMedia(copy, "{broken");
        check(MatrixSync.mxc("mxc://matrix.org/AbC123")[1].equals("AbC123") && MatrixSync.mxc("mxc://localhost:8448/x")[0].equals("localhost:8448"), "mxc");
        check(MatrixSync.mxc("mxc://x.org/../etc") == null && MatrixSync.mxc("https://x.org/a") == null && MatrixSync.mxc("mxc://x.org/") == null
                && MatrixSync.mxc(null) == null && MatrixSync.mxc("mxc://x.org/a?b") == null, "bad mxc");
        check(MatrixSync.stripReply("> zitat\nohne leerzeile").equals("> zitat\nohne leerzeile")
                && MatrixSync.stripReply("> a\nb\n\nc").equals("> a\nb\n\nc") && MatrixSync.stripReply("x").equals("x"), "reply fallback kept");

        // emojis
        check(Emoji.onlyEmoji("😀") && Emoji.onlyEmoji("👍🏽") && Emoji.onlyEmoji("❤️ 😂") && Emoji.onlyEmoji("🇩🇪")
                && Emoji.onlyEmoji("👨‍👩‍👧") && Emoji.onlyEmoji("😀😀😀😀😀😀"), "only emoji");
        check(!Emoji.onlyEmoji("hallo 😀") && !Emoji.onlyEmoji("") && !Emoji.onlyEmoji(null) && !Emoji.onlyEmoji("123")
                && !Emoji.onlyEmoji("😀😀😀😀😀😀😀"), "not only emoji");
        int count = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] cat : Emoji.LIST) for (String e : cat) {
            count++;
            check(Emoji.onlyEmoji(e), "emoji in list is an emoji: " + e);
            seen.add(e);
        }
        check(Emoji.LIST.length == Emoji.TABS.length && count > 700, "emoji list " + count);
        check(Emoji.remember(java.util.Arrays.asList("a", "b", "c"), "b", 3).equals(java.util.Arrays.asList("b", "a", "c"))
                && Emoji.remember(java.util.Arrays.asList("a", "b", "c"), "d", 3).equals(java.util.Arrays.asList("d", "a", "b")), "recent emojis");

        // quick answers
        check(QuickReplies.to("Wie geht's dir?", "text", true).get(0).startsWith("Gut, danke"), "quick: how are you");
        check(QuickReplies.to("Kommst du morgen?", "text", true).get(0).startsWith("Ja"), "quick: question");
        check(QuickReplies.to("Danke dir!", "text", true).get(0).startsWith("Gern"), "quick: thanks");
        check(QuickReplies.to("Hallo Lisa", "text", true).get(0).startsWith("Hallo"), "quick: hello");
        check(QuickReplies.to("Gute Nacht", "text", true).get(0).startsWith("Gute Nacht"), "quick: night");
        check(QuickReplies.to("bild.jpg", "image", true).get(0).startsWith("Schön"), "quick: photo");
        check(QuickReplies.to("Wo bist du", "text", true).get(0).startsWith("Bin gleich"), "quick: where");
        check(QuickReplies.to("how are you?", "text", false).get(0).startsWith("Good"), "quick english");
        check(QuickReplies.to(null, null, true).size() == 4 && QuickReplies.to("Okay", "text", true).get(0).equals("👍"), "quick default");

        // Nova dictation
        check(Dictation.clean("hallo lisa komma wie geht es dir fragezeichen", true).equals("Hallo lisa, wie geht es dir?"),
                "dictation: " + Dictation.clean("hallo lisa komma wie geht es dir fragezeichen", true));
        check(Dictation.clean("ich komme gleich punkt bis später daumen hoch", true).equals("Ich komme gleich. Bis später 👍"),
                "dictation 2: " + Dictation.clean("ich komme gleich punkt bis später daumen hoch", true));
        check(Dictation.clean("erste zeile neue zeile zweite zeile", true).equals("Erste zeile\nZweite zeile"), "dictation newline: "
                + Dictation.clean("erste zeile neue zeile zweite zeile", true));
        check(Dictation.clean("see you soon period thumbs up", false).equals("See you soon. 👍"), "dictation english: "
                + Dictation.clean("see you soon period thumbs up", false));
        check(Dictation.clean("  ", true).isEmpty() && Dictation.clean(null, true).isEmpty(), "dictation empty");
        check(Dictation.isYes("Ja") && Dictation.isYes("ja bitte senden") && Dictation.isYes("Okay") && Dictation.isYes("yes"), "yes");
        check(!Dictation.isYes("nein") && !Dictation.isYes("nicht senden") && Dictation.isNo("Nein danke") && Dictation.isNo("abbrechen")
                && !Dictation.isNo("ja"), "no");
        check(Dictation.isAgain("nochmal") && Dictation.isRead("lies vor") && Dictation.isRead("vorlesen") && !Dictation.isRead("hallo"), "again/read");

        // voice message wave
        java.util.List<Integer> amps = new java.util.ArrayList<>();
        for (int i = 0; i < 300; i++) amps.add(i % 10 == 0 ? 32767 : 100);
        java.util.List<Integer> wave = Waveform.of(amps, 64);
        check(wave.size() == 64 && wave.get(0) == 1024, "wave size/peak " + wave.size());
        check(Waveform.of(java.util.Arrays.asList(0, 32767), 64).equals(java.util.Arrays.asList(0, 1024)), "short wave");
        check(Waveform.of(null, 64).isEmpty() && Waveform.at(wave, 0, 10) == wave.get(0) && Waveform.at(null, 3, 10) == 0, "wave edges");
        check(Waveform.time(65000).equals("1:05") && Waveform.time(0).equals("0:00") && Waveform.time(-5).equals("0:00"), "wave time");

        // Nova: read messages, write over Nova Chat
        check(NovaBrain.understand("Lies meine Nachrichten vor", true).kind == NovaBrain.Kind.READ_MESSAGES, "read messages");
        check(NovaBrain.understand("Hey Nova, habe ich neue Nachrichten?", true).kind == NovaBrain.Kind.READ_MESSAGES, "new messages?");
        check(NovaBrain.understand("read my messages", false).kind == NovaBrain.Kind.READ_MESSAGES, "read messages en");
        check(NovaBrain.understand("Schreib Lisa eine Nachricht: Ich komme später", true).kind == NovaBrain.Kind.MESSAGE, "message still message");
        NovaBrain.Command chatMsg = NovaBrain.understand("Schreibe eine Nachricht an Lisa im Chat: Bin gleich da", true);
        check(chatMsg.kind == NovaBrain.Kind.MESSAGE && NovaBrain.viaChat(chatMsg.text)[0].equals("Lisa")
                && NovaBrain.viaChat(chatMsg.text)[1].equals("chat") && chatMsg.body.equals("Bin gleich da"), "message via chat: " + chatMsg);
        NovaBrain.Command colon = NovaBrain.understand("Schreib Bob im Chat: Alles klar, bis gleich", true);
        check(colon.kind == NovaBrain.Kind.MESSAGE && NovaBrain.viaChat(colon.text)[0].equals("Bob")
                && NovaBrain.viaChat(colon.text)[1].equals("chat") && colon.body.equals("Alles klar bis gleich"), "schreib X: " + colon);
        NovaBrain.Command dass = NovaBrain.understand("Sag Mama im Chat dass ich später komme", true);
        check(dass.kind == NovaBrain.Kind.MESSAGE && NovaBrain.viaChat(dass.text)[0].equals("Mama") && dass.body.equals("ich später komme"),
                "sag X im chat dass: " + dass);
        check(NovaBrain.understand("Sag Lisa: Ich komme", true).kind == NovaBrain.Kind.MESSAGE, "sag X:");
        check(NovaBrain.understand("Schreib auf: Milch kaufen", true).kind == NovaBrain.Kind.NOTE, "schreib auf stays note");
        check(NovaBrain.understand("tell Tom: see you", false).kind == NovaBrain.Kind.MESSAGE, "tell X:");
        check(NovaBrain.understand("Lies meine E-Mails vor", true).kind == NovaBrain.Kind.READ_MAIL, "read mail");
        check(NovaBrain.understand("Habe ich neue Mails?", true).kind == NovaBrain.Kind.READ_MAIL, "new mails?");
        check(NovaBrain.understand("check my email", false).kind == NovaBrain.Kind.READ_MAIL, "check email");
        NovaBrain.Command em = NovaBrain.understand("Schreib eine E-Mail an Lisa Meier: Hallo Lisa, bis morgen", true);
        check(em.kind == NovaBrain.Kind.MAIL && em.text.equals("Lisa Meier") && em.body.equals("Hallo Lisa bis morgen"), "mail to: " + em);
        NovaBrain.Command em2 = NovaBrain.understand("E-Mail an lisa@gmx.de", true);
        check(em2.kind == NovaBrain.Kind.MAIL && NovaBrain.spokenEmail(em2.text).equals("lisa@gmx.de"), "mail to address: " + em2);
        check(NovaBrain.understand("send an email to Tom saying see you", false).text.equals("Tom"), "send email en");
        check(NovaBrain.understand("Neue E-Mail", true).kind == NovaBrain.Kind.MAIL, "new mail");
        check("lisa.meier@gmx.de".equals(NovaBrain.spokenEmail("Lisa Punkt Meier at GMX Punkt de")), "spoken email");
        check(NovaBrain.spokenEmail("Lisa") == null && NovaBrain.spokenEmail(null) == null, "not an email");
        check(NovaBrain.understand("Öffne Nova Mail", true).kind == NovaBrain.Kind.OPEN_APP, "open mail app");
        check(NovaBrain.viaChat("Mama")[1].isEmpty() && NovaBrain.viaChat("Mama über Nova Chat")[0].equals("Mama"), "via chat");
    }

    private static void chat() {
        String w = MiniJson.write(MiniJson.obj("a", "x\"y\\z\n", "n", 3, "d", 2.5, "l", new Object[] {1, true, null}, "o", MiniJson.obj()));
        check(w.equals("{\"a\":\"x\\\"y\\\\z\\n\",\"n\":3,\"d\":2.5,\"l\":[1,true,null],\"o\":{}}"), "json write " + w);
        check(MiniJson.get(MiniJson.parse(w), "a").equals("x\"y\\z\n"), "json round trip");
        String sync = "{\"next_batch\":\"s72\",\"account_data\":{\"events\":["
                + "{\"type\":\"m.direct\",\"content\":{\"@lisa:matrix.org\":[\"!dm:matrix.org\"]}},"
                + "{\"type\":\"org.novaos.contacts\",\"content\":{\"contacts\":[{\"name\":\"Lisa\",\"id\":\"@lisa:matrix.org\"},{\"name\":\"bad\",\"id\":\"lisa\"}]}}]},"
                + "\"rooms\":{\"join\":{\"!dm:matrix.org\":{\"unread_notifications\":{\"notification_count\":2},"
                + "\"summary\":{\"m.heroes\":[\"@lisa:matrix.org\"]},"
                + "\"state\":{\"events\":[{\"type\":\"m.room.member\",\"state_key\":\"@lisa:matrix.org\",\"content\":{\"membership\":\"join\",\"displayname\":\"Lisa M\"}}]},"
                + "\"timeline\":{\"events\":["
                + "{\"type\":\"m.room.message\",\"event_id\":\"$1\",\"sender\":\"@lisa:matrix.org\",\"origin_server_ts\":1000,\"content\":{\"msgtype\":\"m.text\",\"body\":\"Hallo!\"}},"
                + "{\"type\":\"m.room.message\",\"event_id\":\"$2\",\"sender\":\"@me:matrix.org\",\"origin_server_ts\":2000,\"content\":{\"msgtype\":\"m.image\",\"body\":\"bild.jpg\"}},"
                + "{\"type\":\"m.room.encrypted\",\"event_id\":\"$3\",\"sender\":\"@lisa:matrix.org\",\"origin_server_ts\":3000,\"content\":{}},"
                + "{\"type\":\"m.call.invite\",\"event_id\":\"$4\",\"sender\":\"@lisa:matrix.org\",\"origin_server_ts\":4000,\"content\":{\"call_id\":\"c1\",\"offer\":{\"type\":\"offer\",\"sdp\":\"v=0\"}}},"
                + "{\"type\":\"m.reaction\",\"event_id\":\"$5\",\"sender\":\"@lisa:matrix.org\",\"origin_server_ts\":5000,\"content\":{}}]}},"
                + "\"!group:matrix.org\":{\"state\":{\"events\":[{\"type\":\"m.room.name\",\"content\":{\"name\":\"Familie\"}},{\"type\":\"m.room.encryption\",\"content\":{}}]},\"timeline\":{\"events\":[]}}},"
                + "\"invite\":{\"!inv:matrix.org\":{\"invite_state\":{\"events\":["
                + "{\"type\":\"m.room.member\",\"state_key\":\"@tom:matrix.org\",\"sender\":\"@tom:matrix.org\",\"content\":{\"membership\":\"join\",\"displayname\":\"Tom\"}},"
                + "{\"type\":\"m.room.member\",\"state_key\":\"@me:matrix.org\",\"sender\":\"@tom:matrix.org\",\"content\":{\"membership\":\"invite\",\"is_direct\":true}}]}}},"
                + "\"leave\":{\"!old:matrix.org\":{}}}}";
        MatrixSync.Result r = MatrixSync.parse(sync, "@me:matrix.org");
        check(r.nextBatch.equals("s72") && r.rooms.size() == 3 && r.left.equals(java.util.Collections.singletonList("!old:matrix.org")), "sync rooms");
        MatrixSync.Room dm = r.rooms.get(0);
        check(dm.id.equals("!dm:matrix.org") && dm.name.equals("Lisa M") && dm.unread == 2 && dm.directUser.equals("@lisa:matrix.org"), "dm room " + dm.name);
        check(dm.messages.size() == 4 && dm.messages.get(0).body.equals("Hallo!") && dm.messages.get(0).senderName.equals("Lisa M")
                && dm.messages.get(1).kind.equals("image") && dm.messages.get(2).kind.equals("encrypted") && dm.messages.get(3).kind.equals("call"),
                "messages " + dm.messages.size());
        check(r.calls.size() == 1 && r.calls.get(0).type.equals("m.call.invite") && "c1".equals(r.calls.get(0).content.get("call_id")), "call event");
        MatrixSync.Room group = r.rooms.get(1);
        check(group.name.equals("Familie") && group.encrypted && group.unread == -1, "group room");
        MatrixSync.Room inv = r.rooms.get(2);
        check(inv.invite && inv.inviter.equals("@tom:matrix.org") && inv.name.equals("Tom") && inv.directUser.equals("@tom:matrix.org"), "invite " + inv.name);
        check(r.contacts != null && r.contacts.size() == 1 && r.contacts.get(0).name.equals("Lisa"), "contacts from account data");
        check(MiniJson.write(MatrixSync.contactsContent(r.contacts)).equals("{\"contacts\":[{\"name\":\"Lisa\",\"id\":\"@lisa:matrix.org\"}]}"), "contacts content");
        check(MatrixSync.parse("{\"next_batch\":\"x\"}", "@me:a.b").rooms.isEmpty(), "empty sync");
        check("@lisa:matrix.org".equals(MatrixSync.userId("Lisa", "matrix.org")) && "@lisa:matrix.org".equals(MatrixSync.userId("@lisa", "matrix.org"))
                && "@tom:example.com".equals(MatrixSync.userId("tom:example.com", "matrix.org")) && MatrixSync.userId("a b", "matrix.org") == null
                && MatrixSync.userId("", "matrix.org") == null, "user ids");
        check(MatrixSync.server("@lisa:matrix.org").equals("matrix.org") && MatrixSync.localpart("@lisa:matrix.org").equals("lisa"), "id parts");
    }

    /** Weather (Open-Meteo): answers and sentences. */
    private static void weather() {
        Weather.Place p = Weather.place("{\"results\":[{\"name\":\"Hamburg\",\"latitude\":53.55,\"longitude\":10.0}]}");
        check(p != null && p.name.equals("Hamburg") && Math.abs(p.lat - 53.55) < 1e-9, "geocode");
        check(Weather.place("{}") == null && Weather.place("{\"results\":[]}") == null && Weather.place("x") == null, "geocode empty");
        Weather.Now n = Weather.now("{\"current\":{\"temperature_2m\":12.4,\"weather_code\":2,\"wind_speed_10m\":10.1},"
                + "\"daily\":{\"temperature_2m_max\":[14.6],\"temperature_2m_min\":[7.8]}}");
        check(n != null && n.code == 2, "forecast");
        check(Weather.sentence("Hamburg", n, true).equals("In Hamburg sind es gerade 12 Grad, teilweise bewölkt. Heute 8 bis 15 Grad."),
                "sentence " + Weather.sentence("Hamburg", n, true));
        check(Weather.sentence("", n, false).startsWith("It's 12 degrees, partly cloudy."), "sentence en");
        check(Weather.geocodeUrl("Bad Homburg", true).endsWith("&name=Bad%20Homburg") || Weather.geocodeUrl("Bad Homburg", true).endsWith("&name=Bad+Homburg"),
                "geocode url");
        check(Weather.forecastUrl(53.55, 10).contains("latitude=53.5500&longitude=10.0000"), "forecast url");
        check(Weather.describe(0, true).equals("sonnig und klar") && Weather.describe(95, false).equals("thunderstorms")
                && Weather.symbol(71).equals("❄"), "codes");
    }
}
