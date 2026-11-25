package com.example.tunnel;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;

/** Shared visual primitives for the native Vexis screens. */
public final class VexisUi {
    // Colours are refreshed before each activity builds its views.
    public static int INK, TEXT, MUTED, ACCENT, DEEP, PALE, LINE, DANGER, OUTLINE, WHITE;
    public static int BACKGROUND, SURFACE;
    public static int ON_ACCENT;
    public static final String THEME_SYSTEM = "system", THEME_LIGHT = "light", THEME_DARK = "dark";
    private static final String THEME_KEY = "theme_mode";
    static { setPalette(false); }

    public static String getThemeMode(Context context) {
        String mode = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString(THEME_KEY, THEME_SYSTEM);
        return THEME_LIGHT.equals(mode) || THEME_DARK.equals(mode) ? mode : THEME_SYSTEM;
    }

    public static void setThemeMode(Context context, String mode) {
        String value = THEME_LIGHT.equals(mode) || THEME_DARK.equals(mode) ? mode : THEME_SYSTEM;
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString(THEME_KEY, value).apply();
    }

    public static boolean isDark(Context context) {
        String mode = getThemeMode(context);
        if (THEME_DARK.equals(mode)) return true;
        if (THEME_LIGHT.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Call before super.onCreate so native dialogs and window chrome match the palette. */
    public static void applyTheme(Activity activity) {
        boolean dark = isDark(activity);
        setPalette(dark);
        activity.setTheme(dark ? R.style.Theme_Vexis_Dark : R.style.Theme_Vexis);
    }

    private static void setPalette(boolean dark) {
        INK = dark ? 0xfff3edf9 : Color.rgb(22, 19, 32);
        TEXT = dark ? 0xffede5f4 : Color.rgb(32, 26, 45);
        MUTED = dark ? 0xffbdb0ca : Color.rgb(97, 91, 112);
        ACCENT = dark ? 0xffbd83f1 : Color.rgb(104, 24, 184);
        DEEP = dark ? 0xffd1a5f7 : Color.rgb(82, 14, 156);
        PALE = dark ? 0xff352441 : Color.rgb(241, 232, 250);
        LINE = dark ? 0xff44394c : Color.rgb(232, 227, 238);
        DANGER = dark ? 0xffff8295 : Color.rgb(162, 43, 67);
        OUTLINE = dark ? 0xff765d87 : Color.rgb(204, 183, 222);
        BACKGROUND = dark ? 0xff17121d : Color.WHITE;
        SURFACE = dark ? 0xff241c2b : Color.WHITE;
        WHITE = SURFACE;
        ON_ACCENT = dark ? 0xff201128 : Color.WHITE;
    }
    public static final int SOLID = 0, QUIET = 1, OUTLINE_BUTTON = 2, DESTRUCTIVE = 3;

    private VexisUi() { }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    public static android.graphics.drawable.Drawable buttonBackground(Context context, int kind) {
        int fill = kind == QUIET ? PALE : kind == OUTLINE_BUTTON ? Color.TRANSPARENT
                : kind == DESTRUCTIVE ? DANGER : ACCENT;
        GradientDrawable shape = rounded(fill, 10, context);
        if (kind == OUTLINE_BUTTON) shape.setStroke(dp(context, 1), ACCENT);
        return new RippleDrawable(ColorStateList.valueOf(0x33734C94), shape, null);
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setMinimumHeight(dp(context, 1));
        return view;
    }

    static Switch switchControl(Context context) {
        return new VexisSwitch(context);
    }

    static void setSwitchAvailable(Switch control, boolean available, boolean animate) {
        if (control instanceof VexisSwitch) ((VexisSwitch) control).setAvailable(available, animate);
    }

    static void shadeModal(Activity activity, Window window) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View decor = window.getDecorView();
        if (decor.getTag(R.id.vexis_modal_shade_hook) != null) return;
        decor.setTag(R.id.vexis_modal_shade_hook, Boolean.TRUE);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams params = window.getAttributes();
        params.y = -dp(activity, 8);
        window.setAttributes(params);

        DialogShade shade = (DialogShade) content.getTag(R.id.vexis_modal_shade);
        if (shade == null) {
            shade = new DialogShade(content);
            content.setTag(R.id.vexis_modal_shade, shade);
        }
        DialogShade activeShade = shade;
        activeShade.count++;
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                view.setTag(R.id.vexis_modal_shade_hook, null);
                if (--activeShade.count == 0) activeShade.remove();
            }
        });
    }

    private static final class DialogShade {
        private final View host;
        private final ColorDrawable fill = new ColorDrawable(0x61231b37);
        private final View.OnLayoutChangeListener layout =
                (view, l, t, r, b, ol, ot, or, ob) -> fill.setBounds(0, 0, r - l, b - t);
        private int count;

        DialogShade(View host) {
            this.host = host;
            fill.setBounds(0, 0, host.getWidth(), host.getHeight());
            host.getOverlay().add(fill);
            host.addOnLayoutChangeListener(layout);
        }

        void remove() {
            host.getOverlay().remove(fill);
            host.removeOnLayoutChangeListener(layout);
            host.setTag(R.id.vexis_modal_shade, null);
        }
    }

    private static final class VexisSwitch extends Switch {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int trackWidth;
        private final int trackHeight;
        private final int radius;
        private int activeColor = ACCENT;
        private VexisMotion.FrameAnimation colorAnimation;
        private VexisMotion.FrameAnimation thumbAnimation;
        private float thumbPosition;

        VexisSwitch(Context context) {
            super(context);
            trackWidth = dp(context, 38);
            trackHeight = dp(context, 22);
            radius = dp(context, 8);
            int inset = dp(context, 3);
            GradientDrawable rail = new GradientDrawable() {
                @Override public boolean getPadding(Rect padding) {
                    padding.set(inset, 0, inset, 0);
                    return true;
                }
            };
            rail.setColor(Color.TRANSPARENT);
            rail.setSize(trackWidth, trackHeight);
            GradientDrawable thumb = rounded(Color.TRANSPARENT, 8, context);
            thumb.setSize(radius * 2, radius * 2);
            setThumbTintList(null);
            setTrackTintList(null);
            setThumbDrawable(thumb);
            setTrackDrawable(rail);
            setSwitchMinWidth(trackWidth);
            setSwitchPadding(dp(context, 8));
            setShowText(false);
            setSplitTrack(false);
        }

        void setAvailable(boolean available, boolean animate) {
            int target = available ? ACCENT : 0xffbcb6c4;
            if (colorAnimation != null) colorAnimation.cancel();
            if (!animate || !isShown()) { activeColor = target; invalidate(); return; }
            int start = activeColor;
            colorAnimation = new VexisMotion.FrameAnimation(this, 200, fraction -> {
                int a = Math.round(Color.alpha(start) + (Color.alpha(target) - Color.alpha(start)) * fraction);
                int r = Math.round(Color.red(start) + (Color.red(target) - Color.red(start)) * fraction);
                int g = Math.round(Color.green(start) + (Color.green(target) - Color.green(start)) * fraction);
                int b = Math.round(Color.blue(start) + (Color.blue(target) - Color.blue(start)) * fraction);
                activeColor = Color.argb(a, r, g, b);
                invalidate();
            }, () -> colorAnimation = null);
            colorAnimation.start();
        }

        @Override public void setChecked(boolean checked) {
            boolean changed = isChecked() != checked;
            super.setChecked(checked);
            if (!changed) return;
            if (thumbAnimation != null) thumbAnimation.cancel();
            float target = checked ? 1f : 0f;
            if (!isAttachedToWindow() || !isShown()) { thumbPosition = target; invalidate(); return; }
            float start = thumbPosition;
            thumbAnimation = new VexisMotion.FrameAnimation(this, 180, fraction -> {
                thumbPosition = start + (target - start) * fraction;
                invalidate();
            }, () -> { thumbAnimation = null; });
            thumbAnimation.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? getPaddingLeft() : getWidth() - getPaddingRight() - trackWidth;
            float right = left + trackWidth;
            float y = (getPaddingTop() + getHeight() - getPaddingBottom()) * .5f;
            float half = trackHeight * .5f;
            paint.setColor(isChecked() ? activeColor : 0xffd8d2df);
            paint.setAlpha(isEnabled() ? 255 : 150);
            canvas.drawRoundRect(left, y - half, right, y + half, half, half, paint);
            float position = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1f - thumbPosition : thumbPosition;
            float x = left + half + (trackWidth - 2f * half) * position;
            paint.setColor(WHITE);
            canvas.drawCircle(Math.max(left + half, Math.min(x, right - half)), y, radius, paint);
        }
    }

    public static TextView toolbar(Context context, String title) {
        TextView view = text(context, title, 21, INK, true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(context, 63));
        return view;
    }
}
