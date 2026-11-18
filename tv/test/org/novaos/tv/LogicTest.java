package org.novaos.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests for the Android-independent logic (run by tv/build.sh, no test framework needed). */
public final class LogicTest {
    private static int failed = 0, passed = 0;

    private static void check(boolean ok, String what) {
        if (ok) passed++; else { failed++; System.out.println("FAIL: " + what); }
    }

    public static void main(String[] args) {
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

        System.out.println("LogicTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
