package org.novaos.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The map of Nova Karte: OpenStreetMap tiles (loaded over https, kept on the phone),
 * moving with one finger, zooming with two fingers or a double tap, and on top the route,
 * the destination and the own position with its direction.
 */
final class MapView extends View {
    interface Listener {
        /** The user moved the map (the map stops following the position). */
        void moved();

        void longPress(Geo.Point p);
    }

    static final double MIN_ZOOM = 3, MAX_ZOOM = 19;
    private static final String TILES = "https://tile.openstreetmap.org/";
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 6, 64L << 20)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static boolean pruned;

    private final Set<String> loading = Collections.synchronizedSet(new HashSet<>());
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final float tile;          // shown size of a 256-pixel tile (bigger on sharp screens)
    private final float density;
    private double lat = 51.16, lon = 10.45, zoom = 6;
    private Listener listener;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scale;
    private final OverScroller scroller;
    private double flingX, flingY;

    private List<Geo.Point> route = Collections.emptyList();
    private double[] routeCumulative = new double[0];
    private double passed;              // metres of the route already driven
    private Geo.Point destination, position;
    private float heading = Float.NaN, accuracy;
    private boolean navigating;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    MapView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        tile = Math.max(256f, 256f * density / 1.5f);
        scroller = new OverScroller(c);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                moveBy(dx, dy);
                if (listener != null) listener.moved();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                flingX = 0;
                flingY = 0;
                scroller.fling(0, 0, (int) -vx, (int) -vy, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomAround(e.getX(), e.getY(), 1);
                if (listener != null) listener.moved();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (listener != null) listener.longPress(pointAt(e.getX(), e.getY()));
            }
        });
        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAround(d.getFocusX(), d.getFocusY(), Math.log(d.getScaleFactor()) / Math.log(2));
                if (listener != null) listener.moved();
                return true;
            }
        });
        if (!pruned) {
            pruned = true;
            File dir = new File(c.getCacheDir(), "tiles");
            LOADER.execute(() -> prune(dir));
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    // ------------------------------------------------------------ position of the map

    double zoom() {
        return zoom;
    }

    Geo.Point center() {
        return new Geo.Point(lat, lon);
    }

    void show(Geo.Point p, double z) {
        lat = p.lat;
        lon = p.lon;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        invalidate();
    }

    /** Shows all points (route, or start and destination) with some margin. */
    void fit(List<Geo.Point> pts, float topPx, float bottomPx) {
        if (pts.isEmpty() || getWidth() == 0) return;
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (Geo.Point q : pts) {
            minLat = Math.min(minLat, q.lat);
            maxLat = Math.max(maxLat, q.lat);
            minLon = Math.min(minLon, q.lon);
            maxLon = Math.max(maxLon, q.lon);
        }
        float w = getWidth() - 2 * 32 * density, h = getHeight() - topPx - bottomPx - 2 * 24 * density;
        double z = MAX_ZOOM;
        while (z > MIN_ZOOM) {
            double dx = Geo.x(maxLon, z, tile) - Geo.x(minLon, z, tile), dy = Geo.y(minLat, z, tile) - Geo.y(maxLat, z, tile);
            if (dx <= w && dy <= h) break;
            z -= 0.25;
        }
        zoom = Math.min(17, z);
        // the middle of the free area (between the top card and the bottom card)
        double cx = (Geo.x(minLon, zoom, tile) + Geo.x(maxLon, zoom, tile)) / 2;
        double cy = (Geo.y(minLat, zoom, tile) + Geo.y(maxLat, zoom, tile)) / 2 - (topPx - bottomPx) / 2;
        lon = Geo.lon(cx, zoom, tile);
        lat = Geo.lat(cy, zoom, tile);
        invalidate();
    }

    private void moveBy(double dx, double dy) {
        double cx = Geo.x(lon, zoom, tile) + dx, cy = Geo.y(lat, zoom, tile) + dy;
        lon = Geo.lon(cx, zoom, tile);
        lat = Math.max(-85, Math.min(85, Geo.lat(cy, zoom, tile)));
        invalidate();
    }

    private void zoomAround(float fx, float fy, double by) {
        Geo.Point before = pointAt(fx, fy);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + by));
        // keep the point under the fingers where it is
        double px = Geo.x(before.lon, zoom, tile), py = Geo.y(before.lat, zoom, tile);
        double cx = px - (fx - getWidth() / 2.0), cy = py - (fy - getHeight() / 2.0);
        lon = Geo.lon(cx, zoom, tile);
        lat = Geo.lat(cy, zoom, tile);
        invalidate();
    }

    void zoomBy(double by) {
        zoomAround(getWidth() / 2f, getHeight() / 2f, by);
    }

    Geo.Point pointAt(float sx, float sy) {
        double cx = Geo.x(lon, zoom, tile) + sx - getWidth() / 2.0, cy = Geo.y(lat, zoom, tile) + sy - getHeight() / 2.0;
        return new Geo.Point(Geo.lat(cy, zoom, tile), Geo.lon(cx, zoom, tile));
    }

    private float sx(double plon, double cx) {
        return (float) (Geo.x(plon, zoom, tile) - cx + getWidth() / 2.0);
    }

    private float sy(double plat, double cy) {
        return (float) (Geo.y(plat, zoom, tile) - cy + getHeight() / 2.0);
    }

    // ------------------------------------------------------------ what is drawn on top

    void setRoute(List<Geo.Point> pts, double[] cumulative) {
        route = pts == null ? Collections.emptyList() : pts;
        routeCumulative = cumulative == null ? new double[0] : cumulative;
        passed = 0;
        invalidate();
    }

    void setPassed(double metres) {
        passed = metres;
        invalidate();
    }

    void setDestination(Geo.Point d) {
        destination = d;
        invalidate();
    }

    void setPosition(Geo.Point pos, float bearing, float acc) {
        position = pos;
        heading = bearing;
        accuracy = acc;
        invalidate();
    }

    void setNavigating(boolean on) {
        navigating = on;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scale.onTouchEvent(e);
        if (!scale.isInProgress()) gestures.onTouchEvent(e);
        return true;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            double x = scroller.getCurrX(), y = scroller.getCurrY();
            moveBy(x - flingX, y - flingY);
            flingX = x;
            flingY = y;
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFFF2EFE9);
        int z = (int) Math.max(0, Math.min(MAX_ZOOM, Math.round(zoom)));
        double factor = Math.pow(2, zoom - z), size = tile * factor;
        double cx = Geo.x(lon, z, tile) * factor, cy = Geo.y(lat, z, tile) * factor;
        double left = cx - getWidth() / 2.0, top = cy - getHeight() / 2.0;
        int n = 1 << z;
        int x0 = (int) Math.floor(left / size), x1 = (int) Math.floor((left + getWidth()) / size);
        int y0 = (int) Math.max(0, Math.floor(top / size)), y1 = (int) Math.min(n - 1, Math.floor((top + getHeight()) / size));
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                int wx = ((tx % n) + n) % n;
                dst.set((float) (tx * size - left), (float) (ty * size - top), (float) ((tx + 1) * size - left), (float) ((ty + 1) * size - top));
                Bitmap b = tile(z, wx, ty);
                if (b != null) {
                    c.drawBitmap(b, null, dst, p);
                } else {
                    // meanwhile the bigger tile one level up, enlarged
                    Bitmap up = z > 0 ? MEMORY.get((z - 1) + "/" + (wx / 2) + "/" + (ty / 2)) : null;
                    if (up != null) {
                        int half = up.getWidth() / 2;
                        src.set((wx % 2) * half, (ty % 2) * half, (wx % 2) * half + half, (ty % 2) * half + half);
                        c.drawBitmap(up, src, dst, p);
                    }
                }
            }
        }
        double wcx = Geo.x(lon, zoom, tile), wcy = Geo.y(lat, zoom, tile);
        drawRoute(c, wcx, wcy);
        if (destination != null) drawPin(c, sx(destination.lon, wcx), sy(destination.lat, wcy));
        if (position != null) drawPosition(c, sx(position.lon, wcx), sy(position.lat, wcy));
        // OpenStreetMap asks for this
        p.setTextSize(11 * density);
        p.setColor(0xCCFFFFFF);
        String credit = "© OpenStreetMap-Mitwirkende";
        float tw = p.measureText(credit);
        c.drawRect(getWidth() - tw - 10 * density, getHeight() - 18 * density, getWidth(), getHeight(), p);
        p.setColor(0xFF333333);
        c.drawText(credit, getWidth() - tw - 5 * density, getHeight() - 5 * density, p);
    }

    private void drawRoute(Canvas c, double wcx, double wcy) {
        if (route.size() < 2) return;
        Path all = new Path(), done = new Path();
        boolean doneStarted = false;
        Geo.Point split = passed > 0 && routeCumulative.length == route.size() ? Geo.at(route, routeCumulative, passed) : null;
        for (int i = 0; i < route.size(); i++) {
            Geo.Point q = route.get(i);
            float x = sx(q.lon, wcx), y = sy(q.lat, wcy);
            if (i == 0) all.moveTo(x, y);
            else all.lineTo(x, y);
            if (split != null && routeCumulative[i] <= passed) {
                if (!doneStarted) {
                    done.moveTo(x, y);
                    doneStarted = true;
                } else {
                    done.lineTo(x, y);
                }
            }
        }
        if (split != null && doneStarted) done.lineTo(sx(split.lon, wcx), sy(split.lat, wcy));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF0B57D0);
        p.setStrokeWidth(10 * density);
        c.drawPath(all, p);
        p.setColor(0xFF4C8DF6);
        p.setStrokeWidth(6.5f * density);
        c.drawPath(all, p);
        if (doneStarted) {
            p.setColor(0xFF9AA0A6);
            p.setStrokeWidth(6.5f * density);
            c.drawPath(done, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawPin(Canvas c, float x, float y) {
        float r = 11 * density;
        Path pin = new Path();
        pin.moveTo(x, y);
        pin.cubicTo(x - r * 0.3f, y - r * 1.1f, x - r * 1.4f, y - r * 1.6f, x - r * 1.0f, y - r * 2.6f);
        pin.arcTo(new RectF(x - r, y - r * 3.6f, x + r, y - r * 1.6f), 160, 220);
        pin.cubicTo(x + r * 1.4f, y - r * 1.6f, x + r * 0.3f, y - r * 1.1f, x, y);
        p.setColor(0xFFEA4335);
        c.drawPath(pin, p);
        p.setColor(0xFFB31412);
        c.drawCircle(x, y - r * 2.6f, r * 0.38f, p);
    }

    private void drawPosition(Canvas c, float x, float y) {
        float metresPerPixel = (float) (156543.03 * Math.cos(Math.toRadians(lat)) / Math.pow(2, zoom) * 256 / tile);
        if (accuracy > 0 && !navigating) {
            p.setColor(0x334285F4);
            c.drawCircle(x, y, Math.max(12 * density, accuracy / metresPerPixel), p);
        }
        if (!Float.isNaN(heading)) {
            // direction: a light cone, or in navigation an arrow
            c.save();
            c.rotate(heading, x, y);
            Path cone = new Path();
            if (navigating) {
                float r = 14 * density;
                cone.moveTo(x, y - r * 1.3f);
                cone.lineTo(x + r, y + r);
                cone.lineTo(x, y + r * 0.45f);
                cone.lineTo(x - r, y + r);
                cone.close();
                p.setColor(Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * density);
                c.drawPath(cone, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF1A73E8);
                c.drawPath(cone, p);
                c.restore();
                return;
            }
            float r = 34 * density;
            cone.moveTo(x, y);
            cone.lineTo(x - r * 0.55f, y - r);
            cone.quadTo(x, y - r * 1.15f, x + r * 0.55f, y - r);
            cone.close();
            p.setColor(0x554285F4);
            c.drawPath(cone, p);
            c.restore();
        }
        p.setColor(Color.WHITE);
        c.drawCircle(x, y, 10 * density, p);
        p.setColor(0xFF1A73E8);
        c.drawCircle(x, y, 7 * density, p);
    }

    // ------------------------------------------------------------ tiles: memory, file, internet

    private Bitmap tile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        Bitmap b = MEMORY.get(key);
        if (b != null || !loading.add(key)) return b;
        File f = new File(new File(getContext().getCacheDir(), "tiles"), z + "_" + x + "_" + y + ".png");
        LOADER.execute(() -> {
            try {
                Bitmap bmp = null;
                if (f.isFile() && System.currentTimeMillis() - f.lastModified() < 30L * 24 * 3600 * 1000) bmp = BitmapFactory.decodeFile(f.getPath());
                if (bmp == null) bmp = download(key, f);
                if (bmp != null) {
                    MEMORY.put(key, bmp);
                    ui.post(this::invalidate);
                }
            } finally {
                loading.remove(key);
            }
        });
        return null;
    }

    private static Bitmap download(String key, File f) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(TILES + key + ".png").openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            // the tile servers of OpenStreetMap ask every app to say who it is
            con.setRequestProperty("User-Agent", "NovaOS-Mobile (https://github.com/superali99-tech/Hanse-1-Betriebssystem-)");
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                byte[] data = b.toByteArray();
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bmp != null) {
                    //noinspection ResultOfMethodCallIgnored
                    f.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(data);
                    }
                }
                return bmp;
            } finally {
                con.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** At most 4000 tiles on the phone (about 60 MB); the oldest go first. */
    private static void prune(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 4000) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - 4000; i++) //noinspection ResultOfMethodCallIgnored
            files[i].delete();
    }
}
