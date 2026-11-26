package org.novaos.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Nova Kamera: photo camera with preview, shutter, flash (auto/on/off), front/back camera
 * and the last photo as a button to the gallery. Photos are saved in DCIM/NovaOS.
 */
public class CameraActivity extends Activity {
    private static final int PERMISSION = 6;
    private static final int PREVIEW = 0, WAIT_FOCUS = 1, WAIT_PRECAPTURE = 2, WAIT_EXPOSURE = 3, TAKING = 4;
    private static final int FLASH_AUTO = 0, FLASH_ON = 1, FLASH_OFF = 2;

    private TextureView preview;
    private View shutterFlash;
    private ImageButton flashButton, switchButton, shutter;
    private ImageView last;
    private TextView message;
    private Button grant;
    private final List<View> rotating = new ArrayList<>();

    private HandlerThread thread;
    private Handler background;
    private final Object lock = new Object();
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private CaptureRequest.Builder request;
    private CameraCharacteristics chars;
    // zoom: two fingers on the picture or the buttons (0,5× … 5×, what this camera can)
    private volatile float zoom = 1f;
    private float zoomMin = 1f, zoomMax = 1f;
    private boolean zoomRatio;  // Android 11+: the camera's zoom ratio (also wide-angle below 1×)
    private android.graphics.Rect sensor;
    private LinearLayout zoomRow;
    private TextView zoomLabel;
    private Size previewSize;
    private String[] ids = new String[0];
    private int which;  // index into ids
    private boolean hasFlash, hasFocus;
    private int flash = FLASH_AUTO;
    private int state = PREVIEW;
    private int deviceOrientation;
    private OrientationEventListener orientation;
    private MediaActionSound sound;
    private Uri lastUri;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this, true);
        if (state != null) {
            which = state.getInt("camera");
            flash = state.getInt("flash", FLASH_AUTO);
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new TextureView(this);
        root.addView(preview, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        shutterFlash = new View(this);
        shutterFlash.setBackgroundColor(Color.BLACK);
        shutterFlash.setAlpha(0);
        root.addView(shutterFlash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 14);
        top.setPadding(p, Ui.dp(this, 8), p, p);
        top.addView(round(R.drawable.ic_back, R.string.back, 46, v -> finish()));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        flashButton = round(R.drawable.ic_flash_auto, R.string.camera_flash, 46, v -> {
            flash = (flash + 1) % 3;
            showFlash();
            background.post(this::repeatPreview);
        });
        top.addView(flashButton);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(0x66000000);
        bottom.setPadding(Ui.dp(this, 30), Ui.dp(this, 22), Ui.dp(this, 30), Ui.dp(this, 26));
        last = new ImageView(this);
        last.setBackgroundResource(R.drawable.thumb_bg);
        last.setClipToOutline(true);
        last.setScaleType(ImageView.ScaleType.CENTER_CROP);
        last.setContentDescription(getString(R.string.app_gallery));
        last.setOnClickListener(v -> openLast());
        rotating.add(last);
        bottom.addView(last, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        shutter = new ImageButton(this);
        shutter.setBackgroundResource(R.drawable.shutter_bg);
        shutter.setContentDescription(getString(R.string.camera_shutter));
        shutter.setOnClickListener(v -> takePicture());
        bottom.addView(shutter, new LinearLayout.LayoutParams(Ui.dp(this, 78), Ui.dp(this, 78)));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        switchButton = round(R.drawable.ic_camera_switch, R.string.camera_switch, 54, v -> {
            if (ids.length < 2) return;
            which = (which + 1) % ids.length;
            closeCamera();
            openCamera();
        });
        bottom.addView(switchButton);
        root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // zoom: buttons above the shutter, the current zoom while pinching
        zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        zl.bottomMargin = Ui.dp(this, 140);
        root.addView(zoomRow, zl);
        zoomLabel = Ui.text(this, "", 22, Color.WHITE, true);
        zoomLabel.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        zoomLabel.setVisibility(View.GONE);
        root.addView(zoomLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        android.view.ScaleGestureDetector pinch = new android.view.ScaleGestureDetector(this,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(android.view.ScaleGestureDetector d) {
                        setZoom(zoom * d.getScaleFactor());
                        return true;
                    }

                    @Override
                    public void onScaleEnd(android.view.ScaleGestureDetector d) {
                        zoomLabel.postDelayed(() -> zoomLabel.setVisibility(View.GONE), 800);
                    }
                });
        root.setOnTouchListener((v, e) -> {
            pinch.onTouchEvent(e);
            return true;
        });

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        message = Ui.text(this, "", 16, Color.WHITE, false);
        message.setGravity(Gravity.CENTER);
        info.addView(message);
        grant = Ui.button(this, R.string.grant, v -> requestPermissions(new String[] {Manifest.permission.CAMERA}, PERMISSION));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(this, 14);
        info.addView(grant, gl);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        info.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        root.addView(info, il);
        setContentView(root);
        rotating.add(flashButton);
        rotating.add(switchButton);
        // controls stay clear of the status and navigation bar (also when they appear)
        int topPad = top.getPaddingTop(), bottomPad = bottom.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            top.setPadding(top.getPaddingLeft(), topPad + insets.getSystemWindowInsetTop(), top.getPaddingRight(),
                    top.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(),
                    bottomPad + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::layoutPreview);
        });

        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
                transform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        orientation = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int o) {
                if (o == ORIENTATION_UNKNOWN) return;
                int snapped = ((o + 45) / 90 * 90) % 360;
                if (snapped == deviceOrientation) return;
                deviceOrientation = snapped;
                // icons turn with the phone, like in the One UI camera
                float target = -(snapped == 270 ? -90 : snapped);
                for (View r : rotating) r.animate().rotation(target).setDuration(250).start();
            }
        };
        sound = new MediaActionSound();
        sound.load(MediaActionSound.SHUTTER_CLICK);
    }

    private ImageButton round(int icon, int label, int sizeDp, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.round_bg);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(this, sizeDp > 50 ? 14 : 11);
        b.setPadding(p, p, p, p);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        return b;
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) Ui.immersive(this, true);  // full screen again after dialogs
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("camera", which);
        out.putInt("flash", flash);
    }

    @Override
    protected void onResume() {
        super.onResume();
        thread = new HandlerThread("NovaKamera");
        thread.start();
        background = new Handler(thread.getLooper());
        if (orientation.canDetectOrientation()) orientation.enable();
        showLast();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            info(getString(R.string.camera_permission), true);
            requestPermissions(new String[] {Manifest.permission.CAMERA}, PERMISSION);
            return;
        }
        if (preview.isAvailable()) openCamera();
    }

    @Override
    protected void onPause() {
        orientation.disable();
        closeCamera();
        thread.quitSafely();
        thread = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (sound != null) sound.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && preview.isAvailable()) {
            openCamera();
        }
    }

    private void info(String text, boolean withGrant) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        grant.setVisibility(withGrant ? View.VISIBLE : View.GONE);
        shutter.setEnabled(text == null);
    }

    // ---- camera

    private void openCamera() {
        if (thread == null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            ids = cameras(cm);
            if (ids.length == 0) {
                info(getString(R.string.camera_none), false);
                switchButton.setVisibility(View.INVISIBLE);
                flashButton.setVisibility(View.INVISIBLE);
                return;
            }
            if (which >= ids.length) which = 0;
            chars = cm.getCameraCharacteristics(ids[which]);
            readZoom();
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new IllegalStateException("no stream configuration");
            Size photo = pickPhoto(map.getOutputSizes(ImageFormat.JPEG));
            previewSize = pickPreview(map.getOutputSizes(SurfaceTexture.class), photo);
            Boolean f = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            hasFlash = f != null && f;
            Float minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            hasFocus = minFocus != null && minFocus > 0 && supports(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            flashButton.setVisibility(hasFlash ? View.VISIBLE : View.INVISIBLE);
            switchButton.setVisibility(ids.length > 1 ? View.VISIBLE : View.INVISIBLE);
            showFlash();
            layoutPreview();
            info(null, false);
            reader = ImageReader.newInstance(photo.getWidth(), photo.getHeight(), ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(this::saveImage, background);
            cm.openCamera(ids[which], new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice d) {
                    synchronized (lock) {
                        device = d;
                    }
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice d) {
                    d.close();
                    synchronized (lock) {
                        if (device == d) device = null;
                    }
                }

                @Override
                public void onError(CameraDevice d, int error) {
                    d.close();
                    synchronized (lock) {
                        if (device == d) device = null;
                    }
                    failed("device " + error);
                }
            }, background);
        } catch (CameraAccessException | RuntimeException e) {
            failed(e.getMessage());
        }
    }

    /** Back camera first, then the front camera (other lenses are reached by the phone's own app). */
    private static String[] cameras(CameraManager cm) throws CameraAccessException {
        String back = null, front = null;
        for (String id : cm.getCameraIdList()) {
            Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing == null) continue;
            if (facing == CameraCharacteristics.LENS_FACING_BACK && back == null) back = id;
            else if (facing == CameraCharacteristics.LENS_FACING_FRONT && front == null) front = id;
        }
        List<String> out = new ArrayList<>();
        if (back != null) out.add(back);
        if (front != null) out.add(front);
        if (out.isEmpty()) out.addAll(Arrays.asList(cm.getCameraIdList()));  // external camera
        return out.toArray(new String[0]);
    }

    private boolean supports(CameraCharacteristics.Key<int[]> key, int mode) {
        int[] modes = chars.get(key);
        if (modes == null) return false;
        for (int m : modes) if (m == mode) return true;
        return false;
    }

    /** Largest 4:3 picture (the full sensor on almost all phones). */
    private static Size pickPhoto(Size[] sizes) {
        Size best = null, any = null;
        for (Size s : sizes) {
            long area = (long) s.getWidth() * s.getHeight();
            if (any == null || area > (long) any.getWidth() * any.getHeight()) any = s;
            if (s.getWidth() * 3 == s.getHeight() * 4 && (best == null || area > (long) best.getWidth() * best.getHeight())) {
                best = s;
            }
        }
        return best != null ? best : any;
    }

    /** Preview with the photo's aspect ratio, at most 1920 × 1440. */
    private static Size pickPreview(Size[] sizes, Size photo) {
        Size best = null, fallback = null;
        for (Size s : sizes) {
            if (s.getWidth() > 1920 || s.getHeight() > 1440) continue;
            long area = (long) s.getWidth() * s.getHeight();
            boolean same = (long) s.getWidth() * photo.getHeight() == (long) s.getHeight() * photo.getWidth();
            if (same && (best == null || area > (long) best.getWidth() * best.getHeight())) best = s;
            if (fallback == null || area > (long) fallback.getWidth() * fallback.getHeight()) fallback = s;
        }
        return best != null ? best : fallback != null ? fallback : sizes[0];
    }

    private void startPreview() {
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(st);
            CameraDevice d;
            synchronized (lock) {
                d = device;
            }
            if (d == null) return;
            request = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            d.createCaptureSession(Arrays.asList(surface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession s) {
                    synchronized (lock) {
                        if (device == null) return;
                        session = s;
                    }
                    state = PREVIEW;
                    repeatPreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    failed("session");
                }
            }, background);
        } catch (CameraAccessException | RuntimeException e) {
            failed(e.getMessage());
        }
    }

    /** What this camera can zoom: Android 11+ the zoom ratio (wide-angle too), before a crop of the sensor. */
    private void readZoom() {
        zoom = 1f;
        zoomMin = 1f;
        zoomMax = 1f;
        zoomRatio = false;
        sensor = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (Build.VERSION.SDK_INT >= 30) {
            android.util.Range<Float> r = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (r != null && r.getUpper() > 1f) {
                zoomRatio = true;
                zoomMin = r.getLower();
                zoomMax = r.getUpper();
            }
        }
        if (!zoomRatio) {
            Float max = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            zoomMax = max == null || sensor == null ? 1f : max;
        }
        zoomMax = Math.min(zoomMax, 30f);
        runOnUiThread(this::showZoomButtons);
    }

    private void showZoomButtons() {
        zoomRow.removeAllViews();
        if (zoomMax <= 1.01f) return;
        java.util.List<Float> steps = new java.util.ArrayList<>();
        if (zoomMin < 0.95f) steps.add(Math.max(zoomMin, 0.5f));
        steps.add(1f);
        if (zoomMax >= 2f) steps.add(2f);
        if (zoomMax >= 5f) steps.add(5f);
        boolean german = java.util.Locale.getDefault().getLanguage().equals("de");
        for (float st : steps) {
            boolean on = Math.abs(zoom - st) < 0.05f;
            TextView b = Ui.text(this, Zoom.label(st, german), 13, on ? 0xFFFFD36B : Color.WHITE, true);
            b.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(0x80000000);
            b.setBackground(g);
            b.setOnClickListener(v -> setZoom(st));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
            lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            zoomRow.addView(b, lp);
        }
    }

    private void setZoom(float z) {
        if (zoomMax <= 1.01f) return;
        zoom = Zoom.clamp(z, zoomMin, zoomMax);
        zoomLabel.setText(Zoom.label(zoom, java.util.Locale.getDefault().getLanguage().equals("de")));
        zoomLabel.setVisibility(View.VISIBLE);
        zoomLabel.removeCallbacks(hideZoomLabel);
        zoomLabel.postDelayed(hideZoomLabel, 1200);
        showZoomButtons();
        background.post(this::repeatPreview);
    }

    private final Runnable hideZoomLabel = () -> zoomLabel.setVisibility(View.GONE);

    private void setModes(CaptureRequest.Builder b) {
        if (zoomRatio && Build.VERSION.SDK_INT >= 30) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
        } else if (sensor != null && zoomMax > 1.01f) {
            int[] c = Zoom.crop(sensor.width(), sensor.height(), zoom);
            b.set(CaptureRequest.SCALER_CROP_REGION, new android.graphics.Rect(sensor.left + c[0], sensor.top + c[1], sensor.left + c[2], sensor.top + c[3]));
        }
        if (hasFocus) b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        if (!hasFlash || flash == FLASH_OFF) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, flash == FLASH_ON
                    ? CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH : CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private void repeatPreview() {
        CameraCaptureSession s;
        synchronized (lock) {
            s = session;
        }
        if (s == null || request == null) return;
        try {
            setModes(request);
            request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            s.setRepeatingRequest(request.build(), progress, background);
        } catch (CameraAccessException | RuntimeException e) {
            failed(e.getMessage());
        }
    }

    private void closeCamera() {
        synchronized (lock) {
            if (session != null) session.close();
            session = null;
            if (device != null) device.close();
            device = null;
            if (reader != null) reader.close();
            reader = null;
        }
    }

    private void failed(String why) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            info(getString(R.string.camera_error, why == null ? "?" : why), false);
        });
    }

    // ---- preview size and rotation

    private void layoutPreview() {
        if (previewSize == null) return;
        View root = (View) preview.getParent();
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        // portrait screen: the (landscape) camera picture is shown upright, e.g. 3:4
        boolean portrait = h >= w;
        float ratio = portrait ? (float) previewSize.getHeight() / previewSize.getWidth()
                : (float) previewSize.getWidth() / previewSize.getHeight();
        int vw = w, vh = Math.round(w / ratio);
        if (vh > h) {
            vh = h;
            vw = Math.round(h * ratio);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) preview.getLayoutParams();
        if (lp.width != vw || lp.height != vh) {
            lp.width = vw;
            lp.height = vh;
            preview.setLayoutParams(lp);
        }
        transform();
    }

    @SuppressWarnings("deprecation")
    private void transform() {
        if (previewSize == null) return;
        int vw = preview.getWidth(), vh = preview.getHeight();
        if (vw == 0 || vh == 0) return;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix m = new Matrix();
        RectF view = new RectF(0, 0, vw, vh);
        RectF buffer = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float cx = view.centerX(), cy = view.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            buffer.offset(cx - buffer.centerX(), cy - buffer.centerY());
            m.setRectToRect(view, buffer, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) vh / previewSize.getHeight(), (float) vw / previewSize.getWidth());
            m.postScale(scale, scale, cx, cy);
            m.postRotate(90 * (rotation - 2), cx, cy);
        } else if (rotation == Surface.ROTATION_180) {
            m.postRotate(180, cx, cy);
        }
        preview.setTransform(m);
    }

    private int jpegOrientation() {
        Integer sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        int o = deviceOrientation;
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) o = -o;
        return ((sensor == null ? 0 : sensor) + o + 360) % 360;
    }

    // ---- taking a photo: focus, exposure (flash), capture

    private final Runnable giveUp = () -> {
        if (state == WAIT_FOCUS || state == WAIT_PRECAPTURE || state == WAIT_EXPOSURE) capture();
    };

    private void takePicture() {
        if (background == null || state != PREVIEW) return;
        shutter.setEnabled(false);
        background.post(() -> {
            CameraCaptureSession s;
            synchronized (lock) {
                s = session;
            }
            if (s == null) {
                runOnUiThread(() -> shutter.setEnabled(true));
                return;
            }
            background.postDelayed(giveUp, 2500);  // some cameras never report their state
            try {
                if (hasFocus) {
                    state = WAIT_FOCUS;
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                    s.capture(request.build(), progress, background);
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                } else {
                    precapture();
                }
            } catch (CameraAccessException | RuntimeException e) {
                capture();
            }
        });
    }

    private void precapture() {
        CameraCaptureSession s;
        synchronized (lock) {
            s = session;
        }
        if (s == null) return;
        if (!hasFlash || flash == FLASH_OFF) {
            capture();
            return;
        }
        try {
            state = WAIT_PRECAPTURE;
            request.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            s.capture(request.build(), progress, background);
            request.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        } catch (CameraAccessException | RuntimeException e) {
            capture();
        }
    }

    private final CameraCaptureSession.CaptureCallback progress = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult r) {
            Integer af = r.get(CaptureResult.CONTROL_AF_STATE);
            Integer ae = r.get(CaptureResult.CONTROL_AE_STATE);
            switch (state) {
                case WAIT_FOCUS:
                    if (af == null || af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED && flash != FLASH_ON) capture();
                        else precapture();
                    }
                    break;
                case WAIT_PRECAPTURE:
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                            || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                            || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        state = WAIT_EXPOSURE;
                    }
                    break;
                case WAIT_EXPOSURE:
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) capture();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession s, CaptureRequest r, CaptureResult partial) {
            process(partial);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
            process(result);
        }
    };

    private void capture() {
        if (state == TAKING) return;
        state = TAKING;
        background.removeCallbacks(giveUp);
        CameraCaptureSession s;
        CameraDevice d;
        synchronized (lock) {
            s = session;
            d = device;
        }
        if (s == null || d == null || reader == null) {
            state = PREVIEW;
            return;
        }
        try {
            CaptureRequest.Builder b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(reader.getSurface());
            setModes(b);
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            s.stopRepeating();
            runOnUiThread(() -> {
                sound.play(MediaActionSound.SHUTTER_CLICK);
                shutterFlash.setAlpha(1);
                shutterFlash.animate().alpha(0).setDuration(220).start();
            });
            s.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession cs, CaptureRequest r, TotalCaptureResult t) {
                    unlock();
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession cs, CaptureRequest r,
                        android.hardware.camera2.CaptureFailure f) {
                    unlock();
                    failed("capture " + f.getReason());
                }
            }, background);
        } catch (CameraAccessException | RuntimeException e) {
            unlock();
            failed(e.getMessage());
        }
    }

    private void unlock() {
        CameraCaptureSession s;
        synchronized (lock) {
            s = session;
        }
        try {
            if (s != null && hasFocus) {
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                s.capture(request.build(), null, background);
            }
        } catch (CameraAccessException | RuntimeException ignored) {
            // preview restarts below
        }
        state = PREVIEW;
        repeatPreview();
        runOnUiThread(() -> shutter.setEnabled(true));
    }

    // ---- saving

    private void saveImage(ImageReader r) {
        byte[] jpeg;
        try (Image img = r.acquireNextImage()) {
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            jpeg = new byte[buf.remaining()];
            buf.get(jpeg);
        } catch (RuntimeException e) {
            return;
        }
        String name = "NovaOS_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".jpg";
        try {
            Uri uri = Build.VERSION.SDK_INT >= 29 ? saveQ(name, jpeg) : saveFile(name, jpeg);
            Bitmap thumb = thumbnail(jpeg);
            runOnUiThread(() -> {
                lastUri = uri;
                if (thumb != null) last.setImageBitmap(thumb);
                Toast.makeText(this, R.string.camera_saved, Toast.LENGTH_SHORT).show();
            });
        } catch (IOException | RuntimeException e) {
            failed(e.getMessage());
        }
    }

    private Uri saveQ(String name, byte[] jpeg) throws IOException {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/NovaOS");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("MediaStore");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("MediaStore");
            out.write(jpeg);
        } catch (IOException e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, v, null, null);
        return uri;
    }

    /** Android 7 – 9: write the file and let the media scanner add it to the gallery. */
    @SuppressWarnings("deprecation")
    private Uri saveFile(String name, byte[] jpeg) throws IOException {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION + 1));
            throw new IOException(getString(R.string.files_permission));
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "NovaOS");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(dir.getPath());
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(jpeg);
        }
        MediaScannerConnection.scanFile(getApplicationContext(), new String[] {f.getPath()}, new String[] {"image/jpeg"},
                (path, uri) -> runOnUiThread(() -> lastUri = uri));
        return null;
    }

    private static Bitmap thumbnail(byte[] jpeg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        int sample = 1;
        while (o.outWidth / sample > 400) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, d);
        if (b == null) return null;
        int rot = 0;
        try {
            android.media.ExifInterface exif = new android.media.ExifInterface(new ByteArrayInputStream(jpeg));
            switch (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90: rot = 90; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: rot = 180; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: rot = 270; break;
                default: break;
            }
        } catch (IOException ignored) {
            // no rotation information
        }
        if (rot == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(rot);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /** Newest photo of the phone as the gallery button (needs the gallery permission). */
    private void showLast() {
        if (!Media.allowed(this)) return;
        new Thread(() -> {
            List<Media.Item> all = Media.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed() || all.isEmpty()) return;
                Media.thumbnail(last, all.get(0));
                lastUri = all.get(0).uri();
            });
        }).start();
    }

    private void openLast() {
        if (!Media.allowed(this)) {
            startActivity(new Intent(this, GalleryActivity.class));
            return;
        }
        new Thread(() -> {
            List<Media.Item> all = Media.load(getApplicationContext());
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (all.isEmpty()) {
                    startActivity(new Intent(this, GalleryActivity.class));
                    return;
                }
                GalleryActivity.current = all;
                startActivity(new Intent(this, ViewerActivity.class).putExtra(ViewerActivity.EXTRA_POS, 0));
            });
        }).start();
    }

    private void showFlash() {
        flashButton.setImageResource(flash == FLASH_ON ? R.drawable.ic_flash_on
                : flash == FLASH_OFF ? R.drawable.ic_flash_off : R.drawable.ic_flash_auto);
    }
}
