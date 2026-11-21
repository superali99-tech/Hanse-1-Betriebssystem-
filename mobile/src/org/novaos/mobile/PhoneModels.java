package org.novaos.mobile;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The phones of the Nova Phone Shop: Nova 1, Nova 2 and their Pro, Max and Lite models.
 * They are concepts made for NovaOS (pictures drawn by PhoneRender) - not real products.
 * Prices up to 1,199 euros. Android-independent, tested.
 */
public final class PhoneModels {
    /** Camera layout of the back, drawn by PhoneRender. */
    public static final int CAM_SINGLE = 1, CAM_DUAL = 2, CAM_TRIPLE = 3, CAM_BAR = 4;

    public static final class Colour {
        public final String de, en;
        public final int body, frame;

        Colour(String de, String en, int body, int frame) {
            this.de = de;
            this.en = en;
            this.body = body;
            this.frame = frame;
        }

        public String name(boolean german) {
            return german ? de : en;
        }
    }

    public static final class Model {
        public final String id, name, badgeDe, badgeEn, taglineDe, taglineEn;
        public final int basePrice;      // euros, smallest storage
        public final int[] storage;      // GB
        public final int[] storageExtra; // euros on top of the base price
        public final List<Colour> colours;
        public final int camera;
        public final float screenInches;
        public final String chip;
        public final List<String[]> highlights = new ArrayList<>();  // {icon, de, en}
        public final List<String[]> specs = new ArrayList<>();       // {label de, label en, value de, value en}
        public final String line;        // "pro", "standard", "lite"

        Model(String id, String name, String line, int basePrice, int[] storage, int[] storageExtra, int camera, float inches,
              String chip, String badgeDe, String badgeEn, String taglineDe, String taglineEn, Colour... colours) {
            this.id = id;
            this.name = name;
            this.line = line;
            this.basePrice = basePrice;
            this.storage = storage;
            this.storageExtra = storageExtra;
            this.camera = camera;
            this.screenInches = inches;
            this.chip = chip;
            this.badgeDe = badgeDe;
            this.badgeEn = badgeEn;
            this.taglineDe = taglineDe;
            this.taglineEn = taglineEn;
            this.colours = Collections.unmodifiableList(Arrays.asList(colours));
        }

        public int price(int storageIndex) {
            int i = Math.max(0, Math.min(storageIndex, storage.length - 1));
            return basePrice + storageExtra[i];
        }

        public String tagline(boolean german) {
            return german ? taglineDe : taglineEn;
        }

        public String badge(boolean german) {
            return german ? badgeDe : badgeEn;
        }

        Model hl(String icon, String de, String en) {
            highlights.add(new String[] {icon, de, en});
            return this;
        }

        Model spec(String labelDe, String labelEn, String de, String en) {
            specs.add(new String[] {labelDe, labelEn, de, en});
            return this;
        }
    }

    private PhoneModels() {}

    private static Colour c(String de, String en, int body, int frame) {
        return new Colour(de, en, body, frame);
    }

    public static final List<Model> ALL;

    static {
        List<Model> l = new ArrayList<>();
        l.add(new Model("nova2promax", "Nova 2 Pro Max", "pro", 1099, new int[] {256, 512}, new int[] {0, 100}, CAM_TRIPLE, 6.9f,
                "Nova N2 Pro", "Neu", "New",
                "Das Größte, Schnellste und Klügste, was Nova je gebaut hat.",
                "The biggest, fastest and smartest phone Nova has ever built.",
                c("Titan Natur", "Natural Titanium", 0xFFB9B2A6, 0xFFD8D2C6), c("Titan Schwarz", "Black Titanium", 0xFF2B2D31, 0xFF55585E),
                c("Polarlicht", "Aurora", 0xFF5B4BB7, 0xFF8E83E0))
                .hl("✦", "Nova KI-Assistent direkt auf dem Chip – antwortet auch ohne Internet", "Nova AI assistant on the chip – answers even offline")
                .hl("📷", "Dreifach-Kamera 200 MP mit 10-fach optischem Zoom", "Triple camera 200 MP with 10× optical zoom")
                .hl("⚡", "Höchstleistung: Nova N2 Pro mit 3-nm-Technik", "Top performance: Nova N2 Pro, 3 nm")
                .hl("🔋", "5.500 mAh – bis zu 2 Tage, 100 W in 25 Minuten voll", "5,500 mAh – up to 2 days, 100 W full in 25 minutes")
                .spec("Display", "Display", "6,9″ LTPO OLED, 1–144 Hz, 3.000 nits", "6.9″ LTPO OLED, 1–144 Hz, 3,000 nits")
                .spec("Chip", "Chip", "Nova N2 Pro (3 nm), 16 GB RAM", "Nova N2 Pro (3 nm), 16 GB RAM")
                .spec("Kamera", "Camera", "200 MP Haupt, 50 MP Ultraweit, 50 MP Tele (10×)", "200 MP main, 50 MP ultra-wide, 50 MP tele (10×)")
                .spec("Akku", "Battery", "5.500 mAh, 100 W Kabel, 50 W kabellos", "5,500 mAh, 100 W wired, 50 W wireless")
                .spec("Gehäuse", "Body", "Titan, Glas, IP68", "Titanium, glass, IP68")
                .spec("Updates", "Updates", "7 Jahre NovaOS-Updates", "7 years of NovaOS updates"));
        l.add(new Model("nova2pro", "Nova 2 Pro", "pro", 999, new int[] {128, 256, 512}, new int[] {0, 100, 200}, CAM_TRIPLE, 6.3f,
                "Nova N2 Pro", "Bestseller", "Bestseller",
                "Profi-Kamera und volle KI-Power – in handlicher Größe.",
                "Pro camera and full AI power – in a handy size.",
                c("Titan Blau", "Blue Titanium", 0xFF3E4F6B, 0xFF7C8DA8), c("Titan Weiß", "White Titanium", 0xFFE8E6E1, 0xFFF5F4F0),
                c("Titan Schwarz", "Black Titanium", 0xFF2B2D31, 0xFF55585E))
                .hl("✦", "Nova KI-Assistent: schreibt, übersetzt, fasst zusammen", "Nova AI assistant: writes, translates, summarises")
                .hl("📷", "Dreifach-Kamera 50 MP mit 5-fach Zoom", "Triple camera 50 MP with 5× zoom")
                .hl("⚡", "Nova N2 Pro – Hochleistung für Spiele und Videos", "Nova N2 Pro – high performance for games and videos")
                .spec("Display", "Display", "6,3″ LTPO OLED, 1–120 Hz", "6.3″ LTPO OLED, 1–120 Hz")
                .spec("Chip", "Chip", "Nova N2 Pro (3 nm), 12 GB RAM", "Nova N2 Pro (3 nm), 12 GB RAM")
                .spec("Kamera", "Camera", "50 MP Haupt, 48 MP Ultraweit, 12 MP Tele (5×)", "50 MP main, 48 MP ultra-wide, 12 MP tele (5×)")
                .spec("Akku", "Battery", "4.700 mAh, 65 W", "4,700 mAh, 65 W")
                .spec("Gehäuse", "Body", "Titan, Glas, IP68", "Titanium, glass, IP68")
                .spec("Updates", "Updates", "7 Jahre NovaOS-Updates", "7 years of NovaOS updates"));
        l.add(new Model("nova2", "Nova 2", "standard", 799, new int[] {128, 256}, new int[] {0, 100}, CAM_DUAL, 6.1f,
                "Nova N2", "Neu", "New",
                "Alles, was du brauchst. Mit Nova KI.",
                "Everything you need. With Nova AI.",
                c("Pink", "Pink", 0xFFF2B8C6, 0xFFF7D3DC), c("Mintgrün", "Mint", 0xFFB5E3D0, 0xFFD3F0E4),
                c("Mitternacht", "Midnight", 0xFF1F2433, 0xFF3A4157), c("Ultramarin", "Ultramarine", 0xFF4A62D8, 0xFF8295EE))
                .hl("✦", "Nova KI-Assistent eingebaut", "Nova AI assistant built in")
                .hl("📷", "48 MP Kamera mit Nachtmodus", "48 MP camera with night mode")
                .hl("🔋", "Akku für den ganzen Tag", "All-day battery")
                .spec("Display", "Display", "6,1″ OLED, 120 Hz", "6.1″ OLED, 120 Hz")
                .spec("Chip", "Chip", "Nova N2, 8 GB RAM", "Nova N2, 8 GB RAM")
                .spec("Kamera", "Camera", "48 MP Haupt, 12 MP Ultraweit", "48 MP main, 12 MP ultra-wide")
                .spec("Akku", "Battery", "4.300 mAh, 45 W", "4,300 mAh, 45 W")
                .spec("Gehäuse", "Body", "Aluminium, Glas, IP67", "Aluminium, glass, IP67")
                .spec("Updates", "Updates", "6 Jahre NovaOS-Updates", "6 years of NovaOS updates"));
        l.add(new Model("nova1pro", "Nova 1 Pro", "pro", 649, new int[] {256}, new int[] {0}, CAM_BAR, 6.7f,
                "Nova N1 Pro", "Preis gesenkt", "Price cut",
                "Der Vorjahres-Profi – jetzt günstiger.",
                "Last year's pro – now cheaper.",
                c("Graphit", "Graphite", 0xFF3A3B3F, 0xFF6A6C72), c("Gletscherblau", "Glacier", 0xFFA9C7DE, 0xFFCADDEC))
                .hl("📷", "Kamera-Leiste mit 50 MP und 3-fach Zoom", "Camera bar with 50 MP and 3× zoom")
                .hl("✦", "Nova KI-Assistent", "Nova AI assistant")
                .hl("🖥", "Großes 6,7″ Display", "Big 6.7″ display")
                .spec("Display", "Display", "6,7″ OLED, 120 Hz", "6.7″ OLED, 120 Hz")
                .spec("Chip", "Chip", "Nova N1 Pro, 12 GB RAM", "Nova N1 Pro, 12 GB RAM")
                .spec("Kamera", "Camera", "50 MP Haupt, 12 MP Ultraweit, 10 MP Tele (3×)", "50 MP main, 12 MP ultra-wide, 10 MP tele (3×)")
                .spec("Akku", "Battery", "5.000 mAh, 45 W", "5,000 mAh, 45 W")
                .spec("Updates", "Updates", "noch 5 Jahre NovaOS-Updates", "5 more years of NovaOS updates"));
        l.add(new Model("nova1", "Nova 1", "standard", 449, new int[] {128, 256}, new int[] {0, 60}, CAM_DUAL, 6.1f,
                "Nova N1", "", "",
                "Das erste Nova. Schnell, schön, fair im Preis.",
                "The first Nova. Fast, beautiful, fairly priced.",
                c("Lavendel", "Lavender", 0xFFC9B8F0, 0xFFDDD2F6), c("Schwarz", "Black", 0xFF26272B, 0xFF4A4C52),
                c("Weiß", "White", 0xFFEDEDEE, 0xFFF7F7F8))
                .hl("✦", "Nova Assistent für Fragen, Wecker und Nachrichten", "Nova assistant for questions, alarms and messages")
                .hl("📷", "50 MP Kamera", "50 MP camera")
                .hl("🔋", "4.500 mAh Akku", "4,500 mAh battery")
                .spec("Display", "Display", "6,1″ OLED, 90 Hz", "6.1″ OLED, 90 Hz")
                .spec("Chip", "Chip", "Nova N1, 8 GB RAM", "Nova N1, 8 GB RAM")
                .spec("Kamera", "Camera", "50 MP Haupt, 8 MP Ultraweit", "50 MP main, 8 MP ultra-wide")
                .spec("Akku", "Battery", "4.500 mAh, 33 W", "4,500 mAh, 33 W")
                .spec("Updates", "Updates", "5 Jahre NovaOS-Updates", "5 years of NovaOS updates"));
        l.add(new Model("novalite", "Nova Lite", "lite", 249, new int[] {64, 128}, new int[] {0, 30}, CAM_SINGLE, 6.5f,
                "Nova L1", "Günstig", "Budget",
                "NovaOS für alle – ideal als erstes Handy.",
                "NovaOS for everyone – ideal as a first phone.",
                c("Himmelblau", "Sky Blue", 0xFF8FC2F2, 0xFFB8D8F6), c("Koralle", "Coral", 0xFFF29C8F, 0xFFF6BEB4),
                c("Schwarz", "Black", 0xFF26272B, 0xFF4A4C52))
                .hl("👨‍👩‍👧", "Mit Nova Family – perfekt fürs Kinderhandy", "With Nova Family – perfect as a child's phone")
                .hl("🔋", "5.000 mAh – hält lange durch", "5,000 mAh – lasts long")
                .hl("💶", "Günstig, aber mit allen Nova-Apps", "Low price, with all Nova apps")
                .spec("Display", "Display", "6,5″ LCD, 90 Hz", "6.5″ LCD, 90 Hz")
                .spec("Chip", "Chip", "Nova L1, 4 GB RAM", "Nova L1, 4 GB RAM")
                .spec("Kamera", "Camera", "13 MP", "13 MP")
                .spec("Akku", "Battery", "5.000 mAh, 18 W", "5,000 mAh, 18 W")
                .spec("Updates", "Updates", "4 Jahre NovaOS-Updates", "4 years of NovaOS updates"));
        ALL = Collections.unmodifiableList(l);
    }

    public static Model get(String id) {
        for (Model m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    /** 1199 -> "1.199 €" (German) / "€1,199" (English). */
    public static String price(int euros, boolean german) {
        NumberFormat f = NumberFormat.getIntegerInstance(german ? Locale.GERMANY : Locale.UK);
        return german ? f.format(euros) + " €" : "€" + f.format(euros);
    }

    /** "ab 449 €": the smallest price of a model. */
    public static int from(Model m) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < m.storage.length; i++) min = Math.min(min, m.price(i));
        return min;
    }

    /** 1024 -> "1 TB", 256 -> "256 GB". */
    public static String storage(int gb) {
        return gb >= 1024 ? gb / 1024 + " TB" : gb + " GB";
    }
}
