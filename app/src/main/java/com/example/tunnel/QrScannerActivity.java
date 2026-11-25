package com.example.tunnel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.graphics.Typeface;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class QrScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private final MultiFormatReader reader = new MultiFormatReader();
    private Camera camera;
    private SurfaceView surface;
    private View overlay;
    private TextView hint;
    private volatile boolean done;
    private int frameWidth;
    private int frameHeight;
    private int rotation;
    private boolean manualFocus;
    private final Runnable focusTask = new Runnable() {
        @Override public void run() {
            Camera current = camera;
            if (current == null || !manualFocus || done) return;
            try { current.autoFocus((success, source) -> {}); } catch (RuntimeException ignored) {}
            handler.postDelayed(this, 1800);
        }
    };

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppLocale.context(base)); }

    @Override public void onCreate(Bundle state) {
        VexisUi.applyTheme(this);
        super.onCreate(state);
        Map<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        reader.setHints(hints);
        LinearLayout root = DetailUi.root(this, getString(R.string.secondary_52));
        getWindow().setNavigationBarColor(0xff201a2d);
        getWindow().getDecorView().setSystemUiVisibility(VexisUi.isDark(this) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xff201a2d);
        surface = new SurfaceView(this);
        frame.addView(surface, new FrameLayout.LayoutParams(-1,-1));
        frame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                resizePreview(right - left, bottom - top));
        overlay = new FrameOverlay();
        frame.addView(overlay, new FrameLayout.LayoutParams(-1,-1));
        hint = new TextView(this);
        hint.setText(getString(R.string.secondary_77));
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(21);
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(100), Gravity.BOTTOM);
        frame.addView(hint, params);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
        surface.getHolder().addCallback(this);
        boolean granted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        surface.setVisibility(granted ? View.VISIBLE : View.INVISIBLE);
        overlay.setVisibility(granted ? View.VISIBLE : View.INVISIBLE); hint.setVisibility(granted ? View.VISIBLE : View.INVISIBLE);
        if (!granted) requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) { surface.setVisibility(View.VISIBLE); overlay.setVisibility(View.VISIBLE); hint.setVisibility(View.VISIBLE); open(); }
            else { AppToast.show(this, getString(R.string.secondary_79)); finish(); }
        }
    }

    private void open() {
        if (camera != null || done || !surface.getHolder().getSurface().isValid()
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            int cameraId = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId = i; break; }
            }
            if (cameraId < 0) throw new IllegalStateException(getString(R.string.secondary_80));
            Camera.getCameraInfo(cameraId, info);
            int display = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = display == Surface.ROTATION_90 ? 90 : display == Surface.ROTATION_180 ? 180 : display == Surface.ROTATION_270 ? 270 : 0;
            rotation = (info.orientation - degrees + 360) % 360;
            camera = Camera.open(cameraId);
            camera.setDisplayOrientation(rotation);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewFormat(ImageFormat.NV21);
            Camera.Size best = null;
            for (Camera.Size candidate : parameters.getSupportedPreviewSizes()) {
                long area = (long) candidate.width * candidate.height;
                if (area <= 1920L * 1080 && (best == null || area > (long) best.width * best.height)) best = candidate;
            }
            if (best != null) parameters.setPreviewSize(best.width, best.height);
            List<String> modes = parameters.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            else if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) { parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); manualFocus = true; }
            camera.setParameters(parameters);
            Camera.Size size = camera.getParameters().getPreviewSize();
            frameWidth = size.width;
            frameHeight = size.height;
            View parent = (View) surface.getParent();
            resizePreview(parent.getWidth(), parent.getHeight());
            camera.setPreviewDisplay(surface.getHolder());
            camera.startPreview();
            camera.setOneShotPreviewCallback(this);
            if (manualFocus) handler.post(focusTask);
        } catch (Exception e) {
            release();
            AppToast.show(this, getString(R.string.secondary_78));
            finish();
        }
    }

    private void resizePreview(int width, int height) {
        if (surface == null || frameWidth == 0 || frameHeight == 0 || width <= 0 || height <= 0) return;
        int imageWidth = rotation == 90 || rotation == 270 ? frameHeight : frameWidth;
        int imageHeight = rotation == 90 || rotation == 270 ? frameWidth : frameHeight;
        float scale = Math.max((float) width / imageWidth, (float) height / imageHeight);
        int previewWidth = Math.round(imageWidth * scale);
        int previewHeight = Math.round(imageHeight * scale);
        FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (current.width == previewWidth && current.height == previewHeight && current.gravity == Gravity.CENTER) return;
        surface.setLayoutParams(new FrameLayout.LayoutParams(previewWidth, previewHeight, Gravity.CENTER));
    }

    private void release() {
        handler.removeCallbacks(focusTask);
        Camera current = camera;
        camera = null;
        manualFocus = false;
        if (current != null) {
            try { current.setOneShotPreviewCallback(null); current.stopPreview(); } catch (RuntimeException ignored) {}
            current.release();
        }
    }

    @Override public void onPreviewFrame(byte[] data, Camera source) {
        if (done || data == null || source != camera) return;
        int width = frameWidth, height = frameHeight, frameRotation = rotation;
        decoder.execute(() -> {
            String text = null;
            try {
                int[] orientations = {frameRotation, (frameRotation + 90) % 360,
                        (frameRotation + 270) % 360, (frameRotation + 180) % 360};
                for (int orientation : orientations) {
                    try {
                        byte[] luminance = rotate(data, width, height, orientation);
                        int rotatedWidth = orientation == 90 || orientation == 270 ? height : width;
                        int rotatedHeight = orientation == 90 || orientation == 270 ? width : height;
                        PlanarYUVLuminanceSource image = new PlanarYUVLuminanceSource(
                                luminance, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false);
                        Result result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(image)));
                        text = result.getText();
                        break;
                    } catch (com.google.zxing.NotFoundException ignored) {
                        reader.reset();
                    }
                }
            } catch (Exception ignored) {
            } finally { reader.reset(); }
            String result = text;
            runOnUiThread(() -> {
                if (done || camera != source || isFinishing()) return;
                if (result != null && !result.isEmpty()) {
                    done = true;
                    setResult(RESULT_OK, new Intent().putExtra("SCAN_RESULT", result));
                    finish();
                } else {
                    try { source.setOneShotPreviewCallback(this); } catch (RuntimeException ignored) {}
                }
            });
        });
    }

    private static byte[] rotate(byte[] source, int width, int height, int rotation) {
        if (rotation == 0) return source;
        byte[] result = new byte[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = rotation == 90 ? x * height + height - 1 - y
                    : rotation == 180 ? (height - 1 - y) * width + width - 1 - x
                    : (width - 1 - x) * height + y;
            result[index] = source[y * width + x];
        }
        return result;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { open(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { release(); }
    @Override protected void onResume() { super.onResume(); if (surface != null) open(); }
    @Override protected void onPause() { release(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); decoder.shutdown(); super.onDestroy(); }

    private final class FrameOverlay extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path corner = new android.graphics.Path();
        FrameOverlay() { super(QrScannerActivity.this); setClickable(false); }
        @Override protected void onDraw(Canvas canvas) {
            float side = Math.min(getWidth() * .72f, getHeight() * .55f);
            float left = (getWidth() - side) / 2, top = (getHeight() - side) / 2 - dp(20);
            paint.setColor(0x88000000);paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0,0,getWidth(),top,paint);canvas.drawRect(0,top,left,top+side,paint);
            canvas.drawRect(left+side,top,getWidth(),top+side,paint);canvas.drawRect(0,top+side,getWidth(),getHeight(),paint);
            paint.setColor(0xffdfbbf3); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
            float right = left + side, bottom = top + side;
            drawCorner(canvas,left,top,1,1);
            drawCorner(canvas,right,top,-1,1);
            drawCorner(canvas,left,bottom,1,-1);
            drawCorner(canvas,right,bottom,-1,-1);
        }
        private void drawCorner(Canvas canvas,float x,float y,int horizontal,int vertical) {
            float arm = dp(38), radius = dp(7);
            corner.reset();
            corner.moveTo(x+horizontal*arm,y);
            corner.lineTo(x+horizontal*radius,y);
            corner.quadTo(x,y,x,y+vertical*radius);
            corner.lineTo(x,y+vertical*arm);
            canvas.drawPath(corner,paint);
        }
    }
}
