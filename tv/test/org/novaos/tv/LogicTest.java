package org.novaos.tv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for the Android-independent logic (run by tv/build.sh, no test framework needed). */
public final class LogicTest {
    private static int failed = 0, passed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++; else { failed++; System.out.println("FAIL: " + what); }
    }

    public static void main(String[] args) throws IOException {
        // favorites list
        List<String> k = KeyList.decode("a/.A\n\nb/.B\na/.A\n");
        check(k.equals(Arrays.asList("a/.A", "b/.B")), "decode drops empty lines and duplicates");
        check(KeyList.encode(k).equals("a/.A\nb/.B"), "encode");
        check(KeyList.decode(null).isEmpty() && KeyList.decode("").isEmpty(), "decode empty");
        check(KeyList.toggle(k, "c/.C") && k.size() == 3, "toggle adds");
        check(!KeyList.toggle(k, "a/.A") && k.equals(Arrays.asList("b/.B", "c/.C")), "toggle removes");
        check(KeyList.move(k, "c/.C", -1) && k.get(0).equals("c/.C"), "move left");
        check(!KeyList.move(k, "c/.C", -1), "move left at start does nothing");
        check(!KeyList.move(k, "x", 1), "move unknown key");
        check(KeyList.retain(k, Arrays.asList("b/.B")).equals(Arrays.asList("b/.B")), "retain installed apps");

        // search
        check(AppFilter.normalize(" Größe ").equals("grosse"), "normalize umlauts/ß");
        check(AppFilter.score("ARD Mediathek", "ard") > AppFilter.score("Bard", "ard"), "prefix beats substring");
        check(AppFilter.score("ZDFmediathek", "mediathek") > 0, "substring match");
        check(AppFilter.score("Prime Video", "video") == 60, "word prefix");
        check(AppFilter.score("YouTube", "netflix") == 0, "no match");
        check(AppFilter.score("Amazon Prime Video", "apv") == 30, "initials");
        List<String> apps = new ArrayList<>(Arrays.asList("Bard", "ARD Mediathek", "Arte", "YouTube"));
        List<String> res = AppFilter.filter(apps, s -> s, "ar");
        check(res.equals(Arrays.asList("ARD Mediathek", "Arte", "Bard")), "filter order: " + res);
        check(AppFilter.filter(apps, s -> s, "").size() == 4, "empty query lists all");

        channels(args.length > 0 ? new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8) : null);
        tuner();
        feed();
        titles();
        rows();

        System.out.println("LogicTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ TV channels (internet streams)
    private static void channels(String shipped) {
        List<Channel> l = Channel.parse("#EXTM3U\n# comment\n"
                + "#EXTINF:-1 tvg-chno=\"7\" group-title=\"Dritte\" nova-color=\"#1B4F9C\",NDR Hamburg\n"
                + "https://example.org/ndr.m3u8\n"
                + "#EXTINF:-1,Ohne Nummer\r\nhttps://example.org/x.m3u8\n"
                + "#EXTINF:-1 tvg-chno=\"9\",Unsicher\nhttp://example.org/plain.m3u8\n"
                + "#EXTINF:-1 tvg-name=\"a,b\" tvg-chno=\"3\",Name, mit Komma\nhttps://example.org/c.m3u8\n");
        check(l.size() == 3, "m3u: 3 https channels, plain http dropped: " + l.size());
        check(l.get(0).number == 7 && l.get(0).name.equals("NDR Hamburg") && l.get(0).group.equals("Dritte")
                && l.get(0).color == 0xFF1B4F9C, "m3u attributes");
        check(l.get(1).number == 2 && l.get(1).color == Channel.DEFAULT_COLOR, "m3u defaults (position, colour)");
        check(l.get(2).name.equals("Name, mit Komma") && l.get(2).number == 3, "m3u: comma inside attribute");
        check(Channel.parseColor("red") == Channel.DEFAULT_COLOR, "bad colour");
        check(Channel.step(30, 29, 1) == 0 && Channel.step(30, 0, -1) == 29 && Channel.step(30, 5, 1) == 6, "zapping wraps");
        check(Channel.indexOfNumber(l, 3) == 2 && Channel.indexOfNumber(l, 99) == -1, "find by number");

        if (shipped == null) return;
        List<Channel> all = Channel.parse(shipped);
        check(all.size() >= 25, "shipped channel list: " + all.size() + " channels");
        Set<Integer> numbers = new HashSet<>();
        Set<String> urls = new HashSet<>();
        for (Channel c : all) {
            check(numbers.add(c.number), "unique channel number " + c.number);
            check(urls.add(c.url), "unique stream " + c.name);
            check(c.color != Channel.DEFAULT_COLOR, "colour for " + c.name);
            check(c.url.contains(".m3u8"), "HLS stream for " + c.name);
        }
        check(all.get(0).name.equals("Das Erste") && all.get(1).name.equals("ZDF"), "1 = Das Erste, 2 = ZDF");
    }

    private static void tuner() {
        List<TunerChannel> l = new ArrayList<>(Arrays.asList(
                new TunerChannel(1, "10", "zehn"), new TunerChannel(2, "2", "zwei"),
                new TunerChannel(3, "", "ohne"), new TunerChannel(4, "1-2", "eins-zwei"),
                new TunerChannel(5, "1", "eins"), new TunerChannel(6, "002", "zwei b")));
        Collections.sort(l, TunerChannel.BY_NUMBER);
        StringBuilder sb = new StringBuilder();
        for (TunerChannel c : l) sb.append(c.number).append(' ');
        check(sb.toString().equals("1 1-2 2 002 10  "), "tuner channel order: " + sb);
    }

    // ------------------------------------------------------------ Nova Filme
    private static final Catalog.Source FILM = Catalog.SOURCES.get(0);
    private static final Catalog.Source SERIES = new Catalog.Source("UCx", "ZDFneo", Catalog.Category.SERIEN);
    private static final Catalog.Source KIDS = new Catalog.Source("UCy", "TOGGO", Catalog.Category.KINDER);

    private static void feed() {
        String xml = "<?xml version=\"1.0\"?><feed><title>Videos</title>"
                + "<entry><id>yt:video:abcdefghijk</id><yt:videoId>abcdefghijk</yt:videoId>"
                + "<title>Tom &amp; Jerry &quot;Film&quot; &#228;&#x1F600;</title><published>2026-11-18T10:00:00+00:00</published></entry>"
                + "<entry><yt:videoId>bad</yt:videoId><title>kaputt</title></entry>"
                + "<entry><yt:videoId>ABCDEFGHIJ-</yt:videoId><title>Zweiter</title></entry></feed>";
        List<Video> v = Video.parseFeed(xml, FILM);
        check(v.size() == 2, "feed: 2 valid entries");
        check(v.get(0).title.equals("Tom & Jerry \"Film\" \u00e4\uD83D\uDE00"), "feed: entities " + v.get(0).title);
        check(v.get(0).published.startsWith("2026-11-18") && v.get(1).published.isEmpty(), "feed: date");
        check(v.get(0).watchUrl().equals("https://www.youtube.com/watch?v=abcdefghijk"), "watch url");
        check(Video.parseFeed(null, FILM).isEmpty() && Video.parseFeed("<html>", FILM).isEmpty(), "feed: no data");
        check(Video.unescape("a &unknown; b & c").equals("a &unknown; b & c"), "unknown entity kept");
        check(FILM.feedUrl().endsWith("playlist_id=UULFGkHFrwE8KEuOHPVsIOskAg"), "feed url without Shorts");
        Set<String> ids = new HashSet<>();
        for (Catalog.Source s : Catalog.SOURCES) {
            check(s.channelId.matches("UC[0-9A-Za-z_-]{22}"), "channel id " + s.name);
            check(ids.add(s.channelId), "unique source " + s.name);
        }
    }

    private static void title(String in, String source, String want) {
        String got = Catalog.cleanTitle(in, source);
        check(got.equals(want), "title: '" + in + "' -> '" + got + "', expected '" + want + "'");
    }

    private static void titles() {
        title("Kung Fu Rookie (ACTION KOMÖDIE ganzer Film Deutsch, Action Filme Deutsch komplett, Komödien 2026)",
                "Netzkino", "Kung Fu Rookie");
        title("Zauber einer finnischen Weihnacht (WEIHNACHTSFILM in voller Länge, Weihanchtsfilme Deutsch komplett)",
                "Netzkino", "Zauber einer finnischen Weihnacht");
        title("Red Sky - Grandioser Actionfilm mit spektakulären Luftkämpfen - Ganzer Film bei Moviedome",
                "Moviedome", "Red Sky");
        title("Unglaublich! Ein starbesetzter Spielfilm mit viel Spannung! (Film: Die Hollywood-Verschwörung)",
                "Moviedome", "Die Hollywood-Verschwörung");
        title("Magisch! Dieser wundervolle Weihnachtsfilm ist ein Geschenk (Ganzer Film: Frohe Weihnachten)",
                "Moviedome", "Frohe Weihnachten");
        title("Wild Kat | Der Verrat | Ganze Folge | TOGGO", "TOGGO", "Wild Kat · Der Verrat");
        title("Ninjago Deutsch | Eine gefrorene Narbe | Aufstieg der Drachen | TOGGO", "TOGGO",
                "Ninjago · Eine gefrorene Narbe");
        title("Die Ameisen übernehmen die Macht! \uD83D\uDC1C \uD83D\uDC1D Die Biene Maja | Ganze Folgen | Zeichentrickserie für Kinder",
                "Die Biene Maja", "Die Ameisen übernehmen die Macht! Die Biene Maja");
        title("Der zerstörte Frieden | Es war einmal in Gaza (2/2) | Doku HD | ARTE", "ARTE",
                "Der zerstörte Frieden · Es war einmal in Gaza (2/2)");
        title("Der grausamste Konzern der Geschichte | Terra X", "Terra X History", "Der grausamste Konzern der Geschichte");
        title("Tell - Im Kampf gegen Lord Xax (EPISODE 7: Kalems Geist, ganze Folge, komplette Serie auf Deutsch)",
                "Netzkino", "Tell - Im Kampf gegen Lord Xax · Folge 7: Kalems Geist");
        title("Take the Money and Run – True-Crime-Serie | Die komplette 1. Folge #neoriginal", "ZDFneo",
                "Take the Money and Run – True-Crime-Serie · Die komplette 1. Folge");
        title("Uruguay: Schadet uns Mercosur? | ARTE Reportage", "ARTE", "Uruguay: Schadet uns Mercosur?");
        title("Streetball · #5 · Der Game Plan | Go for It!", "KiKA", "Streetball · #5 · Der Game Plan · Go for It!");
        title("Der zerstörte Frieden |\u00a0 Es war einmal", "ARTE", "Der zerstörte Frieden · Es war einmal");
        title("\uD83D\uDE00", "X", "\uD83D\uDE00");
    }

    private static Video vid(String id, String title, String date, Catalog.Source s) {
        return new Video(id, title, date, s);
    }

    private static void rows() {
        check(Catalog.isEpisode("Tell (EPISODE 7: Kalems Geist)") && Catalog.isEpisode("Staffel 1 Folge 1")
                && Catalog.isEpisode("Boarders S01E02") && !Catalog.isEpisode("Kung Fu Rookie (ganzer Film)")
                && !Catalog.isEpisode("Folgenreich"), "episode detection");
        List<Video> films = Arrays.asList(
                vid("f0000000001", "Film A (ganzer Film)", "2026-11-10", FILM),
                vid("f0000000002", "Film B (ganzer Film)", "2026-11-12", FILM),
                vid("f0000000003", "Serie X (EPISODE 1, ganze Folge)", "2026-11-11", FILM),
                vid("f0000000004", "Film C | Trailer", "2026-11-13", FILM));
        List<Video> series = Arrays.asList(
                vid("s0000000001", "Boarders – Staffel 1 Folge 1", "2026-11-01", SERIES),
                vid("s0000000002", "Sketch History – Trailer", "2026-11-14", SERIES),
                vid("s0000000003", "Interview mit X", "2026-11-14", SERIES));
        List<Video> kids1 = new ArrayList<>(), kids2 = new ArrayList<>();
        for (int i = 0; i < 30; i++) kids1.add(vid(String.format("k1%09d", i), "A " + i, String.format("2026-10-%02d", 30 - i), KIDS));
        for (int i = 0; i < 30; i++) kids2.add(vid(String.format("k2%09d", i), "B " + i, String.format("2026-09-%02d", 30 - i), KIDS));
        kids2.add(vid(kids1.get(0).id, "A 0 again", "2026-01-01", KIDS));  // duplicate
        Map<Catalog.Category, List<Video>> r = Catalog.rows(Arrays.asList(films, series, kids1, kids2));
        List<Video> f = r.get(Catalog.Category.FILME);
        check(f.size() == 2 && f.get(0).id.equals("f0000000002"), "films: newest first, trailer and episode removed");
        List<Video> s = r.get(Catalog.Category.SERIEN);
        check(s.size() == 2 && s.get(0).id.equals("f0000000003") && s.get(1).id.equals("s0000000001"),
                "series: episodes of film channels + episodes of series channels only");
        List<Video> k = r.get(Catalog.Category.KINDER);
        check(k.size() == Catalog.MAX_PER_ROW, "row limit");
        check(k.get(0).title.equals("A 0") && k.get(1).title.equals("B 0") && k.get(2).title.equals("A 1"),
                "sources take turns");
        Set<String> seen = new HashSet<>();
        for (Video v : k) check(seen.add(v.id), "no duplicate " + v.id);
        check(r.get(Catalog.Category.DOKUS).isEmpty(), "empty row");
    }
}
