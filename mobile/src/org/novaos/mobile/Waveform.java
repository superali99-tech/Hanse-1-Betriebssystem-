package org.novaos.mobile;

import java.util.ArrayList;
import java.util.List;

/** Wave picture of a voice message (levels 0..1024, as Element expects). Android-independent. */
public final class Waveform {
    private Waveform() {}

    /** Microphone levels (0..32767, every 100 ms) to n values 0..1024 (loudest of each part, softened). */
    public static List<Integer> of(List<Integer> amplitudes, int n) {
        List<Integer> out = new ArrayList<>();
        if (amplitudes == null || amplitudes.isEmpty() || n <= 0) return out;
        int size = amplitudes.size();
        int parts = Math.min(n, size);
        for (int p = 0; p < parts; p++) {
            int from = p * size / parts, to = Math.max(from + 1, (p + 1) * size / parts);
            int max = 0;
            for (int i = from; i < to; i++) max = Math.max(max, amplitudes.get(i));
            double v = Math.sqrt(Math.max(0, Math.min(32767, max)) / 32767.0);  // quiet voices stay visible
            out.add((int) Math.round(v * 1024));
        }
        return out;
    }

    /** The level for bar i of bars (the wave stretched or squeezed to the bubble). */
    public static int at(List<Integer> wave, int i, int bars) {
        if (wave == null || wave.isEmpty() || bars <= 0) return 0;
        int idx = (int) ((long) i * wave.size() / bars);
        return wave.get(Math.max(0, Math.min(wave.size() - 1, idx)));
    }

    /** 65000 ms -> "1:05" */
    public static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}
