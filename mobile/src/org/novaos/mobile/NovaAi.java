package org.novaos.mobile;

/**
 * Nova's models and the daily limit (Nova AI). Honest: Nova runs without a cloud AI - the
 * models differ in what they really do: Mini only commands (offline), Nova 5 also knowledge
 * from Wikipedia, Pro 5 longer answers with the source and no limit (Nova Pro 5 subscription,
 * free while NovaOS is being tested), Vision 5 is not there yet. Android-independent, tested.
 */
final class NovaAi {
    enum Model {
        MINI("mini", 0, false, true),
        NOVA5("nova5", 2, false, true),
        PRO5("pro5", 6, true, true),
        VISION5("vision5", 0, true, false);

        final String key;
        final int sentences;   // how much of a Wikipedia article Nova reads out (0 = no knowledge questions)
        final boolean pro;     // needs Nova Pro 5
        final boolean ready;   // can be chosen already

        Model(String key, int sentences, boolean pro, boolean ready) {
            this.key = key;
            this.sentences = sentences;
            this.pro = pro;
            this.ready = ready;
        }

        static Model of(String key) {
            for (Model m : values()) if (m.key.equals(key)) return m;
            return NOVA5;
        }
    }

    /** Questions a day with Nova 5 without the subscription. */
    static final int FREE_PER_DAY = 30;

    private NovaAi() {}

    /** The model that really answers: Pro 5 without the subscription (or one not ready) falls back to Nova 5. */
    static Model effective(Model chosen, boolean pro) {
        if (!chosen.ready || (chosen.pro && !pro)) return Model.NOVA5;
        return chosen;
    }

    /** Whether one more question is allowed today (Mini and the subscription: always). */
    static boolean allowed(Model m, boolean pro, int usedToday) {
        return m == Model.MINI || pro || usedToday < FREE_PER_DAY;
    }

    /** The count of today: a new day starts at 0. day = e.g. "2026-11-21". */
    static int usedToday(String storedDay, int storedCount, String today) {
        return today.equals(storedDay) ? storedCount : 0;
    }

    static int left(boolean pro, int usedToday) {
        return pro ? Integer.MAX_VALUE : Math.max(0, FREE_PER_DAY - usedToday);
    }
}
