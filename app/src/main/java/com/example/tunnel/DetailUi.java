package com.example.tunnel;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared presentation for the detail screens. */
final class DetailUi {
    static int INK, TEXT, SECONDARY, ACCENT, LINE;
    static int dp(Activity a, int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    static LinearLayout root(Activity a, String title) {
        INK = VexisUi.INK; TEXT = VexisUi.TEXT; SECONDARY = VexisUi.MUTED; ACCENT = VexisUi.ACCENT; LINE = VexisUi.LINE;
        a.getWindow().setStatusBarColor(VexisUi.BACKGROUND);
        a.getWindow().setNavigationBarColor(VexisUi.BACKGROUND);
        int flags = VexisUi.isDark(a) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(VexisUi.BACKGROUND);
        root.post(() -> VexisMotion.enter(root, dp(a, 6)));
        LinearLayout bar = new LinearLayout(a); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPaddingRelative(dp(a, 8), 0, dp(a, 16), 0);
        Icon back = new Icon(a, "back", INK); back.setContentDescription(a.getString(R.string.secondary_28)); back.setOnClickListener(v -> a.finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 48)));
        TextView heading = VexisUi.text(a, title, 21, INK, true);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(bar, new LinearLayout.LayoutParams(-1, dp(a, 63)));
        View line = new View(a); line.setBackgroundColor(LINE); root.addView(line, new LinearLayout.LayoutParams(-1, dp(a, 1)));
        return root;
    }
    static TextView text(Activity a, String value, int size, int color) { TextView t = new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    static GradientDrawable outline(Activity a) { GradientDrawable d = new GradientDrawable(); d.setColor(VexisUi.SURFACE); d.setCornerRadius(dp(a, 10)); d.setStroke(dp(a, 1), ACCENT); return d; }
    static void divider(Activity a, LinearLayout parent) { View line = new View(a); line.setBackgroundColor(LINE); parent.addView(line, new LinearLayout.LayoutParams(-1, dp(a, 1))); }
    static final class Icon extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final String kind; final int color;
        Icon(Activity a, String k, int c) { super(a); kind=k; color=c; }
        @Override protected void onDraw(Canvas c) { super.onDraw(c); float s=Math.min(getWidth(),getHeight())/32f; c.save(); c.translate((getWidth()-32*s)/2,(getHeight()-32*s)/2); c.scale(s,s); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            if (kind.equals("back")) { if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) { c.translate(32, 0); c.scale(-1, 1); } c.drawLine(23,16,9,16,p); c.drawLine(9,16,16,9,p); c.drawLine(9,16,16,23,p); }
            else if (kind.equals("globe")) { c.drawCircle(16,16,11,p); c.drawOval(10,5,22,27,p); c.drawLine(5,16,27,16,p); c.drawArc(7,5,25,18,20,140,false,p); c.drawArc(7,14,25,27,200,140,false,p); }
            else if (kind.equals("dns")) { c.drawRoundRect(6,7,26,14,3,3,p); c.drawRoundRect(6,18,26,25,3,3,p); c.drawPoint(10,10.5f,p); c.drawPoint(10,21.5f,p); }
            else if (kind.equals("check")) { c.drawLine(7,16,13,22,p); c.drawLine(13,22,25,9,p); }
            else { path.moveTo(16,5); path.lineTo(25,9); path.lineTo(25,17); path.cubicTo(25,22,21,26,16,28); path.cubicTo(11,26,7,22,7,17); path.lineTo(7,9); path.close(); c.drawPath(path,p); c.drawLine(12,16,15,19,p); c.drawLine(15,19,20,13,p); }
            c.restore(); }
    }
}
