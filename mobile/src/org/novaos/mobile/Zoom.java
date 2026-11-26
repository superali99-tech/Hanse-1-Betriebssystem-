package org.novaos.mobile;

/**
 * Zooming in photos, videos and the camera, and the colours of the photo editor -
 * the arithmetic, Android-independent and tested.
 */
final class Zoom {
    static final float MAX_VIEW = 6f;  // photos and videos: up to 6 times

    private Zoom() {}

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** How far a picture zoomed by scale may be moved without showing the black edge (each side). */
    static float maxShift(float scale, float size) {
        return maxShift(scale, size, size);
    }

    /** The same for a picture of size content shown centred in a view of size view (a letterboxed photo). */
    static float maxShift(float scale, float content, float view) {
        return Math.max(0, (scale * content - view) / 2);
    }

    /** Double tap: zoomed in -> back to the whole picture, otherwise 2.5 times. */
    static float doubleTap(float current) {
        return current > 1.05f ? 1f : 2.5f;
    }

    /** "2,3×" / "2.3×" (whole numbers without decimals: "2×"). */
    static String label(float zoom, boolean german) {
        float r = Math.round(zoom * 10) / 10f;
        String s = r == Math.round(r) ? String.valueOf(Math.round(r)) : String.format(java.util.Locale.ROOT, "%.1f", r);
        return (german ? s.replace('.', ',') : s) + "×";
    }

    /** The camera's crop for a digital zoom: the middle 1/zoom of the sensor (left, top, right, bottom). */
    static int[] crop(int width, int height, float zoom) {
        float z = Math.max(1f, zoom);
        int w = Math.round(width / z), h = Math.round(height / z);
        int l = (width - w) / 2, t = (height - h) / 2;
        return new int[] {l, t, l + w, t + h};
    }

    // ------------------------------------------------------------ photo editor colours (4×5 colour matrices)

    static float[] identity() {
        return new float[] {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
    }

    /** a then b (b applied to the result of a). */
    static float[] concat(float[] a, float[] b) {
        float[] out = new float[20];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                float v = c == 4 ? b[r * 5 + 4] : 0;
                for (int k = 0; k < 4; k++) v += b[r * 5 + k] * (c == 4 ? a[k * 5 + 4] : a[k * 5 + c]);
                out[r * 5 + c] = v;
            }
        }
        return out;
    }

    /** brightness -1..1 (adds up to ±100 on 0..255). */
    static float[] brightness(float v) {
        float[] m = identity();
        m[4] = m[9] = m[14] = v * 100;
        return m;
    }

    /** contrast -1..1 (scale around the middle grey). */
    static float[] contrast(float v) {
        float s = 1 + v, off = 128 * (1 - s);
        return new float[] {s, 0, 0, 0, off, 0, s, 0, 0, off, 0, 0, s, 0, off, 0, 0, 0, 1, 0};
    }

    /** saturation -1..1 (-1 = black and white). */
    static float[] saturation(float v) {
        float s = Math.max(0, 1 + v);
        float rw = 0.213f * (1 - s), gw = 0.715f * (1 - s), bw = 0.072f * (1 - s);
        return new float[] {rw + s, gw, bw, 0, 0, rw, gw + s, bw, 0, 0, rw, gw, bw + s, 0, 0, 0, 0, 0, 1, 0};
    }

    /** warmth -1..1 (more red/less blue, or the other way). */
    static float[] warmth(float v) {
        float[] m = identity();
        m[4] = v * 30;
        m[14] = -v * 30;
        return m;
    }

    /** The filters: 0 original, 1 black and white, 2 sepia, 3 vivid, 4 warm, 5 cool, 6 dramatic. */
    static float[] filter(int f) {
        switch (f) {
            case 1:
                return saturation(-1);
            case 2:
                return concat(saturation(-1), new float[] {1.07f, 0, 0, 0, 20, 0, 0.95f, 0, 0, 8, 0, 0, 0.82f, 0, -10, 0, 0, 0, 1, 0});
            case 3:
                return concat(saturation(0.45f), contrast(0.12f));
            case 4:
                return warmth(0.6f);
            case 5:
                return warmth(-0.6f);
            case 6:
                return concat(contrast(0.35f), saturation(-0.25f));
            default:
                return identity();
        }
    }

    /** One colour (0..255 each) through a matrix - for the tests and the preview of a pixel. */
    static int[] apply(float[] m, int r, int g, int b) {
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            float v = m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b + m[i * 5 + 4];
            out[i] = Math.round(clamp(v, 0, 255));
        }
        return out;
    }
}
