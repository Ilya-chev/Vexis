package com.example.tunnel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

final class VexisCrystalView extends View {
    enum State {
        IDLE(11.5f, .55f, .63f),
        CONNECTING(2.25f, .92f, 1f),
        CONNECTED(5.8f, 1f, 1f),
        RECOVERING(1.55f, .79f, .94f),
        WAITING(15.5f, .43f, .46f),
        ERROR(13f, .48f, .46f);

        final float lapSeconds;
        final float crystalAlpha;
        final float orbAlpha;

        State(float lapSeconds, float crystalAlpha, float orbAlpha) {
            this.lapSeconds = lapSeconds;
            this.crystalAlpha = crystalAlpha;
            this.orbAlpha = orbAlpha;
        }
    }

    private static final float FULL_CIRCLE = (float) (2 * Math.PI);
    private static final float WIDTH = 160f;
    private static final float HEIGHT = WIDTH * 2f / 3f;
    private static final float ORBIT_X = 78f;
    private static final float ORBIT_Y = 54.6f;
    private static final float ORBIT_RX = 46f;
    private static final float ORBIT_RY = 9.9f;
    private static final float ORB_RADIUS = 4f;
    private static final float[] CORE_STOPS = {0f, .22f, .55f, .87f, 1f};

    private final Bitmap crystal;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrixColorFilter waitingTint = mutedCrystal();
    private final RectF crystalBounds = new RectF(0, 0, WIDTH, HEIGHT);
    private final RectF orbitBounds = new RectF(ORBIT_X - ORBIT_RX, ORBIT_Y - ORBIT_RY,
            ORBIT_X + ORBIT_RX, ORBIT_Y + ORBIT_RY);
    private final RadialGradient purpleCore = crystalCore(0xffedd5ff, 0xffb35bef, 0xff6225a9, 0xffb56ff1);
    private final RadialGradient pinkCore = crystalCore(0xffffdbfa, 0xfff071d5, 0xff8f2fb1, 0xffdf88f6);
    private final RadialGradient blueCore = crystalCore(0xffdce2ff, 0xff838cf7, 0xff4635b9, 0xffb59cff);
    private final Shader purpleGlow = light(12f, 0xaaae6bf8);
    private final Shader pinkGlow = light(12f, 0xaaf88cf3);
    private final Shader blueGlow = light(12f, 0xaa9faeff);
    private final Shader glint = light(12f, 0xffeedcff);

    private State state = State.IDLE;
    private float phase = 2.1f;
    private float sceneTime;
    private float speed = FULL_CIRCLE / state.lapSeconds;
    private float crystalAlpha = state.crystalAlpha;
    private float orbAlpha = state.orbAlpha;
    private float arrival;
    private long lastFrame;
    private boolean visible;
    private boolean motionEnabled;

    VexisCrystalView(Context context) {
        super(context);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        crystal = BitmapFactory.decodeResource(getResources(), R.drawable.crystal_hero, options);
        setContentDescription(getContext().getString(R.string.secondary_81));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setState(State next) {
        if (state == next) return;
        state = next;
        if (next == State.CONNECTED && visible && motionEnabled) arrival = 1f;
        if (!visible || !motionEnabled) {
            crystalAlpha = next.crystalAlpha;
            orbAlpha = next.orbAlpha;
            speed = FULL_CIRCLE / next.lapSeconds;
        }
        invalidate();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        motionEnabled = isVisible;
        lastFrame = 0;
        if (!motionEnabled) {
            crystalAlpha = state.crystalAlpha;
            orbAlpha = state.orbAlpha;
            arrival = 0;
        }
        if (isVisible) invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        visible = false;
        lastFrame = 0;
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        visible = getWindowVisibility() == VISIBLE && isShown();
        motionEnabled = visible;
        lastFrame = 0;
        if (visible) invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (crystal == null || getWidth() == 0 || getHeight() == 0) return;
        if (visible && motionEnabled) advanceFrame();
        float scale = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT) * 1.1f;
        canvas.save();
        canvas.translate((getWidth() - WIDTH * scale) * .5f, (getHeight() - HEIGHT * scale) * .5f);
        canvas.scale(scale, scale);
        float floatStrength = .32f + orbAlpha * .68f;
        float drift = (float) Math.sin(sceneTime * 1.45f);
        canvas.translate(0f, drift * 2f * floatStrength);
        canvas.rotate(drift * 2.15f * floatStrength, 80f, 54f);

        float depth = (float) Math.sin(phase);
        drawOrbit(canvas, false, depth);
        paint.setShader(null);
        paint.setAlpha(Math.round(255f * crystalAlpha));
        paint.setColorFilter(state == State.WAITING ? waitingTint : null);
        canvas.drawBitmap(crystal, null, crystalBounds, paint);
        paint.setColorFilter(null);
        drawFacetLights(canvas);
        drawOrbit(canvas, true, depth);
        canvas.restore();
        if (visible && motionEnabled) postInvalidateOnAnimation();
    }

    private static ColorMatrixColorFilter mutedCrystal() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(.35f);
        return new ColorMatrixColorFilter(matrix);
    }

    private void advanceFrame() {
        long time = SystemClock.uptimeMillis();
        if (lastFrame != 0) {
            float elapsed = Math.min((time - lastFrame) * .001f, .064f);
            float blend = 1f - (float) Math.exp(-elapsed / .23f);
            speed += (FULL_CIRCLE / state.lapSeconds - speed) * blend;
            crystalAlpha += (state.crystalAlpha - crystalAlpha) * blend;
            orbAlpha += (state.orbAlpha - orbAlpha) * blend;
            phase = (phase + speed * elapsed) % FULL_CIRCLE;
            sceneTime = (sceneTime + elapsed) % 1000f;
            arrival = Math.max(0f, arrival - elapsed / .42f);
        }
        lastFrame = time;
    }

    private void drawOrbit(Canvas canvas, boolean front, float depth) {
        canvas.save();
        canvas.rotate(-9f, ORBIT_X, ORBIT_Y);
        paint.setShader(null);
        paint.setColor(front ? 0xffc59aff : 0xff9b78db);
        paint.setAlpha(Math.round((front ? 160f : 120f) * orbAlpha));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(front ? .85f : .7f);
        canvas.drawArc(orbitBounds, front ? 0f : 180f, 180f, false, paint);
        paint.setStyle(Paint.Style.FILL);

        if (front == (depth >= 0f)) {
            canvas.translate(ORBIT_X + ORBIT_RX * (float) Math.cos(phase),
                    ORBIT_Y + ORBIT_RY * depth);
            float perspective = .88f + depth * .12f;
            canvas.scale(perspective, perspective);
            float tone = (float) (Math.cos(phase + sceneTime * .42f) * .5 + .5);
            float pulse = .72f + .22f * (float) Math.sin(phase * 2f + sceneTime * 1.6f);
            drawLight(canvas, purpleGlow, 0f, 0f, 12f, orbAlpha * (pulse + arrival * .15f));
            drawLight(canvas, front ? pinkGlow : blueGlow, 0f, 0f, 12f, orbAlpha * tone * .64f);
            drawLight(canvas, purpleCore, 0f, 0f, ORB_RADIUS, orbAlpha);
            drawLight(canvas, front ? pinkCore : blueCore, 0f, 0f, ORB_RADIUS, orbAlpha * tone * .76f);
        }
        canvas.restore();
    }

    private void drawFacetLights(Canvas canvas) {
        float reflected = (float) Math.pow(Math.max(0, Math.sin(phase - .55f)), 5f);
        float strength = crystalAlpha * (.08f + reflected * .47f + arrival * .32f);
        drawLight(canvas, glint, 84f, 49f, 12f, strength);
        drawLight(canvas, glint, 42f, 20f, 12f, crystalAlpha * reflected * .32f);
        float rightGlint = (float) Math.pow(Math.max(0, Math.sin(phase + 2.1f)), 6f);
        drawLight(canvas, glint, 119f, 21f, 12f, crystalAlpha * rightGlint * .40f);
    }

    private void drawLight(Canvas canvas, Shader shader, float x, float y, float radius, float alpha) {
        canvas.save();
        canvas.translate(x, y);
        paint.setShader(shader);
        paint.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
        canvas.drawCircle(0, 0, radius, paint);
        canvas.restore();
    }

    private static RadialGradient crystalCore(int pale, int mid, int dark, int edge) {
        return new RadialGradient(-1.3f, -1.6f, 5.8f,
                new int[]{Color.WHITE, pale, mid, dark, edge}, CORE_STOPS, Shader.TileMode.CLAMP);
    }

    private static RadialGradient light(float radius, int color) {
        return new RadialGradient(0f, 0f, radius, new int[]{color, color & 0x00ffffff},
                null, Shader.TileMode.CLAMP);
    }
}
