package com.example.tunnel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import java.util.WeakHashMap;

final class VexisMotion {
    private static final PathInterpolator EASE_OUT = new PathInterpolator(.23f, 1f, .32f, 1f);

    private VexisMotion() {}

    interface FrameUpdate { void update(float fraction); }

    /** Drives app-owned motion from frame time, independently of system animator settings. */
    static final class FrameAnimation implements Runnable, View.OnAttachStateChangeListener {
        private final View view;
        private final long duration;
        private final FrameUpdate update;
        private final Runnable end;
        private long start;
        private boolean running;

        FrameAnimation(View view, long duration, FrameUpdate update, Runnable end) {
            this.view = view;
            this.duration = duration;
            this.update = update;
            this.end = end;
        }

        void start() {
            running = true;
            start = SystemClock.uptimeMillis();
            view.addOnAttachStateChangeListener(this);
            update.update(0f);
            view.postOnAnimation(this);
        }

        void cancel() {
            if (!running) return;
            running = false;
            view.removeCallbacks(this);
            view.removeOnAttachStateChangeListener(this);
            if (entrances.get(view) == this) entrances.remove(view);
        }

        @Override public void run() {
            if (!running) return;
            float fraction = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) duration);
            update.update(EASE_OUT.getInterpolation(fraction));
            if (fraction < 1f) view.postOnAnimation(this);
            else {
                cancel();
                if (end != null) end.run();
            }
        }

        @Override public void onViewAttachedToWindow(View v) { }
        @Override public void onViewDetachedFromWindow(View v) { cancel(); }
    }

    private static final WeakHashMap<View, FrameAnimation> entrances = new WeakHashMap<>();

    static void enter(View view, float offset) {
        FrameAnimation previous = entrances.remove(view);
        if (previous != null) previous.cancel();
        if (!view.isShown()) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        FrameAnimation animation = new FrameAnimation(view, 180, fraction -> {
            view.setAlpha(.42f + .58f * fraction);
            view.setTranslationY(offset * (1f - fraction));
        }, () -> entrances.remove(view));
        entrances.put(view, animation);
        animation.start();
    }

    static final class Section extends android.widget.LinearLayout {
        private FrameAnimation transition;
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean branchGuide;

        Section(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        void setBranchGuide(boolean enabled) {
            branchGuide = enabled;
            setWillNotDraw(!enabled);
            guide.setStrokeWidth(VexisUi.dp(getContext(), 1));
            guide.setColor(0xffb479d6);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!branchGuide || getChildCount() == 0) return;
            float start = VexisUi.dp(getContext(), 16);
            float end = VexisUi.dp(getContext(), 41);
            float last = 0;
            for (int i = 0; i < getChildCount(); i += 2) {
                View row = getChildAt(i);
                last = row.getTop() + row.getHeight() * .5f;
                canvas.drawLine(start, last, end, last, guide);
            }
            canvas.drawLine(start, 0, start, last, guide);
        }

        void setExpanded(boolean expanded) {
            if (transition != null) {
                transition.cancel();
                transition = null;
            }
            View parent = (View) getParent();
            int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            if (width <= 0) {
                finish(expanded);
                return;
            }
            int start = getVisibility() == GONE ? 0 : getHeight();
            int target = 0;
            if (expanded) {
                measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                target = getMeasuredHeight();
            }
            if (start == target) {
                finish(expanded);
                return;
            }
            setVisibility(VISIBLE);
            applyHeight(start);
            final int targetHeight = target;
            transition = new FrameAnimation(this, 180,
                    fraction -> applyHeight(Math.round(start + (targetHeight - start) * fraction)),
                    () -> { transition = null; finish(expanded); });
            transition.start();
        }

        private void applyHeight(int height) {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.height = height;
            setLayoutParams(params);
        }

        private void finish(boolean expanded) {
            setVisibility(expanded ? VISIBLE : GONE);
            applyHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        @Override protected void onDetachedFromWindow() {
            if (transition != null) {
                transition.cancel();
                transition = null;
            }
            super.onDetachedFromWindow();
        }
    }

    static final class ConnectedDot extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float intensity = 1f;
        private int color = VexisUi.ACCENT;
        private boolean pulsating;
        private boolean visible;

        ConnectedDot(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            this.visible = visible;
            syncPulse();
        }

        void setStyle(int color, boolean pulsating) {
            if (this.color == color && this.pulsating == pulsating) return;
            this.color = color;
            this.pulsating = pulsating;
            syncPulse();
            invalidate();
        }

        private void syncPulse() {
            if (visible && pulsating) {
                postInvalidateOnAnimation();
            } else {
                stopPulse();
            }
        }

        @Override protected void onDetachedFromWindow() {
            visible = false;
            stopPulse();
            super.onDetachedFromWindow();
        }

        private void stopPulse() {
            intensity = 1f;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (visible && pulsating) {
                double phase = android.os.SystemClock.uptimeMillis() * Math.PI / 1050.0;
                intensity = (float) (.775 + .225 * Math.sin(phase));
                postInvalidateOnAnimation();
            }
            float radius = Math.min(getWidth(), getHeight()) * .22f;
            float centerX = getWidth() * .5f;
            float centerY = getHeight() * .5f;
            paint.setColor(color);
            if (pulsating) {
                paint.setAlpha(Math.round(36 * intensity));
                canvas.drawCircle(centerX, centerY, radius * 1.8f, paint);
            }
            paint.setAlpha(Math.round(255 * intensity));
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }
}
