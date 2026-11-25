package com.example.tunnel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class RoutingActivity extends Activity {
    private boolean appRoutingChanged;
    private boolean rulesChanged;
    private final ArrayList<Switch> ruleSwitches=new ArrayList<>();
    static final String APP_MODE = "app_routing_mode";
    static final String APP_LIST = "app_routing_packages";
    static final String ALL = "all";
    static final String BYPASS = "bypass";
    static final String ONLY = "only";
    private static final String[] MODES = {ALL, BYPASS, ONLY};
    private String[] MODE_LABELS;
    private static final String[] ROUTES = {"proxy", "direct", "block"};
    private String[] ROUTE_LABELS;
    private static final String[] STRATEGIES = {"AsIs", "IPIfNonMatch", "IPOnDemand"};
    private SharedPreferences prefs;
    private int INK, TEXT, SECONDARY, ACCENT, PALE, LINE;
    private LinearLayout body;
    private LinearLayout toolbar;
    private JSONObject profile;
    private final ArrayList<ApplicationInfo> apps = new ArrayList<>();
    private Set<String> selected;
    private String mode;
    private LinearLayout appList;
    private final java.util.Map<String,String> appNames=new java.util.HashMap<>();
    private final java.util.Map<String,android.graphics.drawable.Drawable> appIcons=new java.util.HashMap<>();
    private EditText appSearch;
    private boolean appsLoaded;
    private boolean hideSystem = true;

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppLocale.context(base)); }

    @Override public void onCreate(Bundle state) {
        VexisUi.applyTheme(this);
        super.onCreate(state);
        MODE_LABELS = new String[]{getString(R.string.secondary_9), getString(R.string.secondary_12), getString(R.string.secondary_56)};
        ROUTE_LABELS = new String[]{getString(R.string.secondary_60), getString(R.string.secondary_32), getString(R.string.secondary_3)};
        INK = VexisUi.INK; TEXT = VexisUi.TEXT; SECONDARY = VexisUi.MUTED;
        ACCENT = VexisUi.ACCENT; PALE = VexisUi.PALE; LINE = VexisUi.LINE;
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean perApp = getIntent().getBooleanExtra("per_app", false);
        getWindow().setStatusBarColor(VexisUi.BACKGROUND);
        getWindow().setNavigationBarColor(VexisUi.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(VexisUi.isDark(this) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        LinearLayout root = column();
        root.setBackgroundColor(VexisUi.BACKGROUND);
        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPaddingRelative(dp(8), 0, dp(16), 0);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));
        View rule = new View(this); rule.setBackgroundColor(LINE);
        root.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = column();
        body.setPadding(dp(17), dp(8), dp(17), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
        if (perApp) showApps();
        else showRules();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String title, int size) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        return view;
    }

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color); result.setCornerRadius(dp(radius));
        if (stroke != 0) result.setStroke(dp(1), stroke);
        return result;
    }

    private ColorStateList tint() {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{ACCENT, 0xffbdb5c8});
    }

    private void styleSwitch(Switch view) {
        view.setTextColor(TEXT); view.setTextSize(14);
        view.setPadding(dp(2), dp(12), dp(2), dp(12));
    }

    private void styleInput(EditText view) {
        view.setTextColor(TEXT); view.setHintTextColor(SECONDARY); view.setTextSize(14);
        view.setPadding(dp(11), dp(8), dp(11), dp(8));
        view.setMinHeight(dp(43));
        view.setBackground(shape(VexisUi.SURFACE, VexisUi.OUTLINE, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(8)); view.setLayoutParams(lp);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(LINE);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private Button actionButton(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(15); button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(ACCENT); button.setAllCaps(false); button.setElevation(0);
        button.setBackground(shape(VexisUi.SURFACE, ACCENT, 10));
        button.setMinHeight(dp(44)); button.setOnClickListener(view -> action.run());
        return button;
    }

    private void button(LinearLayout parent, String title, Runnable action) {
        Button button = actionButton(title, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(10), 0, dp(10)); parent.addView(button, lp);
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().getDecorView().setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(shape(VexisUi.SURFACE, 0, 22));
            VexisUi.shadeModal(this,dialog.getWindow());
        }
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button b = dialog.getButton(id); if (b != null) {
                boolean deleting = getString(R.string.secondary_57).contentEquals(b.getText());
                b.setTextColor(id == AlertDialog.BUTTON_POSITIVE ? VexisUi.ON_ACCENT : deleting ? VexisUi.DANGER : ACCENT);
                b.setAllCaps(false); b.setTypeface(null, Typeface.BOLD); b.setElevation(0);
                b.setStateListAnimator(null);
                b.setBackground(VexisUi.buttonBackground(this,id == AlertDialog.BUTTON_POSITIVE
                        ? deleting ? VexisUi.DESTRUCTIVE : VexisUi.SOLID : VexisUi.QUIET));
                b.setPadding(dp(14), 0, dp(14), 0);
            }
        }
        int titleId = getResources().getIdentifier("alertTitle","id","android");
        View titleView = titleId == 0 ? null : dialog.findViewById(titleId);
        if (titleView instanceof TextView) {
            TextView title=(TextView)titleView;
            title.setTextSize(21);
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        }
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(40), -2);
    }

    private class VexisDialog extends AlertDialog.Builder {
        VexisDialog() { super(RoutingActivity.this); }
        @Override public AlertDialog show() { AlertDialog dialog = super.show(); styleDialog(dialog); return dialog; }
    }

    private void toast(String message) {
        AppToast.show(this, message);
    }

    private void title(String name) {
        toolbar.removeAllViews();
        View back = new View(this) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                android.graphics.Paint paint = new android.graphics.Paint(3);
                paint.setColor(INK); paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2)); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                float x = getWidth() / 2f, y = getHeight() / 2f;
                canvas.drawLine(x + dp(7), y, x - dp(7), y, paint);
                canvas.drawLine(x - dp(7), y, x, y - dp(7), paint);
                canvas.drawLine(x - dp(7), y, x, y + dp(7), paint);
            }
        };
        back.setContentDescription(getString(R.string.secondary_28));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(56)));
        TextView heading = text(name, 21); heading.setTextColor(INK);
        heading.setTypeface(null, Typeface.BOLD); heading.setPaddingRelative(dp(4), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void saveMessage() {
        rulesChanged=true;
    }

    @Override public void finish() {
        if ((appRoutingChanged || rulesChanged) && TunnelService.isActive())
            AppToast.show(this, getString(R.string.secondary_47));
        super.finish();
    }

    private void showApps() {
        title(getString(R.string.secondary_27));
        TextView explanation=text(getString(R.string.secondary_11),13);
        explanation.setTextColor(SECONDARY);explanation.setPadding(0,dp(8),0,dp(10));body.addView(explanation);
        mode = prefs.getString(APP_MODE, ALL);
        selected = new HashSet<>(prefs.getStringSet(APP_LIST, new HashSet<>()));
        RadioGroup modes = new RadioGroup(this);
        modes.setDividerDrawable(new android.graphics.drawable.ColorDrawable(LINE){
            @Override public int getIntrinsicHeight() { return dp(1); }
        });
        modes.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        for (int i = 0; i < MODES.length; i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(i + 1);
            choice.setText(MODE_LABELS[i]); choice.setTextColor(TEXT); choice.setTextSize(14);
            choice.setButtonTintList(tint()); choice.setPadding(dp(8), dp(12), dp(8), dp(12));
            choice.setMinHeight(dp(54));
            choice.setBackground(shape(MODES[i].equals(mode) ? PALE : VexisUi.SURFACE, 0, 10));
            modes.addView(choice,new RadioGroup.LayoutParams(-1, -2));
            if (MODES[i].equals(mode)) modes.check(choice.getId());
        }
        body.addView(modes);
        LinearLayout picker = column();
        EditText search = new EditText(this);
        search.setSingleLine();
        search.setHint(getString(R.string.secondary_49));
        styleInput(search);
        search.setTextSize(14);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.vexis_ic_search,0,0,0);
        search.setCompoundDrawablePadding(dp(8));
        appSearch=search;
        LinearLayout.LayoutParams searchSpace=new LinearLayout.LayoutParams(-1, -2);
        searchSpace.topMargin=dp(10);
        picker.addView(search,searchSpace);
        Switch systemFilter = VexisUi.switchControl(this);
        systemFilter.setText(getString(R.string.secondary_53)); styleSwitch(systemFilter);
        systemFilter.setChecked(true);
        systemFilter.setOnCheckedChangeListener((view, checked) -> {
            hideSystem = checked;
            renderApps(search.getText().toString());
        });
        picker.addView(systemFilter);
        appList = column();
        picker.addView(appList);
        body.addView(picker);
        picker.setVisibility(ALL.equals(mode) ? View.GONE : View.VISIBLE);
        modes.setOnCheckedChangeListener((group, checked) -> {
            mode = MODES[checked - 1];
            for (int i = 0; i < modes.getChildCount(); i++)
                modes.getChildAt(i).setBackground(shape(i == checked - 1 ? PALE : VexisUi.SURFACE, 0, 10));
            prefs.edit().putString(APP_MODE, mode).apply();
            appRoutingChanged = true;
            picker.setVisibility(ALL.equals(mode) ? View.GONE : View.VISIBLE);
            if (!ALL.equals(mode)) renderApps(search.getText().toString());
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        appList.addView(text(getString(R.string.secondary_21),15));
        new Thread(()->{
            ArrayList<ApplicationInfo> loaded=new ArrayList<>();
            java.util.Map<String,String> names=new java.util.HashMap<>();
            java.util.Map<String,android.graphics.drawable.Drawable> icons=new java.util.HashMap<>();
            android.content.pm.PackageManager manager=getPackageManager();
            for(ApplicationInfo app:manager.getInstalledApplications(0)){
                if(getPackageName().equals(app.packageName)||!app.enabled)continue;
                loaded.add(app);
                names.put(app.packageName,app.loadLabel(manager).toString());
                icons.put(app.packageName,app.loadIcon(manager));
            }
            loaded.sort((a,b)->{
                int comparison=names.get(a.packageName).compareToIgnoreCase(names.get(b.packageName));
                return comparison!=0?comparison:a.packageName.compareTo(b.packageName);
            });
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                apps.addAll(loaded);appNames.putAll(names);appIcons.putAll(icons);appsLoaded=true;
                if(!ALL.equals(mode))renderApps(appSearch.getText().toString());
            });
        },"load-apps").start();
    }

    private String appName(ApplicationInfo app) {
        return appNames.get(app.packageName);
    }

    private void renderApps(String search) {
        appList.removeAllViews();
        if (!appsLoaded) {
            appList.addView(text(getString(R.string.secondary_21), 15));
            return;
        }
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (ApplicationInfo app : apps) {
            if(hideSystem && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0)continue;
            String name = appName(app);
            if (!name.toLowerCase(Locale.ROOT).contains(query)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(appIcons.get(app.packageName));
            icon.setContentDescription(null);
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(26), dp(26));
            iconSize.setMarginEnd(dp(12));
            row.addView(icon, iconSize);
            TextView label = text(name, 14);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox checkbox = new CheckBox(this);
            checkbox.setContentDescription(name);
            checkbox.setChecked(selected.contains(app.packageName));
            checkbox.setOnCheckedChangeListener((view, checked) -> {
                if (checked) selected.add(app.packageName);
                else selected.remove(app.packageName);
                prefs.edit().putStringSet(APP_LIST, new HashSet<>(selected)).apply();
                appRoutingChanged = true;
            });
            row.setMinimumHeight(dp(54));
            checkbox.setButtonDrawable(R.drawable.vexis_main_check_selector);
            checkbox.setButtonTintList(null);
            row.addView(checkbox);
            row.setOnClickListener(v -> checkbox.setChecked(!checkbox.isChecked()));
            appList.addView(row); divider(appList);
        }
        if (appList.getChildCount() == 0) appList.addView(text(getString(R.string.secondary_43), 15));
    }

    private void showRules() {
        body.removeAllViews();
        ruleSwitches.clear();
        title(getString(R.string.secondary_51));
        TextView subtitle = text(getString(R.string.secondary_50), 13); subtitle.setTextColor(SECONDARY);
        subtitle.setPadding(0, dp(10), 0, dp(16)); body.addView(subtitle);
        Switch global = VexisUi.switchControl(this);
        global.setText(getString(R.string.secondary_25)); styleSwitch(global);
        global.setChecked(RoutingPresets.enabled(prefs));
        global.setOnCheckedChangeListener((view, checked) -> {
            RoutingPresets.setEnabled(prefs, checked); saveMessage();
            for(Switch toggle:ruleSwitches)VexisUi.setSwitchAvailable(toggle,checked,true);
        });
        body.addView(global); divider(body);
        button(body, getString(R.string.secondary_16), this::showAddRules);
        try {
            JSONArray sets = RoutingPresets.load(prefs);
            if (sets.length() == 0) body.addView(text(getString(R.string.secondary_42), 15));
            for (int i = 0; i < sets.length(); i++) {
                JSONObject item = sets.getJSONObject(i);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(56));
                View shield = new View(this) {
                    @Override protected void onDraw(android.graphics.Canvas canvas) {
                        super.onDraw(canvas); android.graphics.Paint paint = new android.graphics.Paint(3);
                        paint.setColor(ACCENT); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
                        android.graphics.Path path = new android.graphics.Path();
                        path.moveTo(dp(12), dp(3)); path.lineTo(dp(20), dp(6)); path.lineTo(dp(20), dp(13));
                        path.quadTo(dp(20), dp(19), dp(12), dp(22)); path.quadTo(dp(4), dp(19), dp(4), dp(13));
                        path.lineTo(dp(4), dp(6)); path.close(); canvas.drawPath(path, paint);
                        canvas.drawLine(dp(8), dp(12), dp(11), dp(15), paint); canvas.drawLine(dp(11), dp(15), dp(16), dp(10), paint);
                    }
                };
                row.addView(shield, new LinearLayout.LayoutParams(dp(28), dp(28)));
                TextView name = text(RoutingPresets.name(item), 14);
                name.setPadding(dp(8), dp(12), dp(8), dp(12));
                name.setOnClickListener(v -> showPresetDetails(item));
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                Switch toggle = VexisUi.switchControl(this);
                ruleSwitches.add(toggle);
                VexisUi.setSwitchAvailable(toggle,global.isChecked(),false);
                toggle.setContentDescription(getString(R.string.secondary_7) + " " + RoutingPresets.name(item));
                toggle.setChecked(item.optBoolean("enabled", true));
                toggle.setOnCheckedChangeListener((view, checked) -> {
                    try { item.put("enabled", checked); RoutingPresets.save(prefs, sets); saveMessage(); }
                    catch (JSONException e) { toast(getString(R.string.secondary_38)); }
                });
                styleSwitch(toggle); row.addView(toggle);
                TextView arrow = text(getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? "‹" : "›", 25); arrow.setTextColor(SECONDARY);
                arrow.setOnClickListener(v -> showPresetDetails(item)); row.addView(arrow);
                row.setOnClickListener(v -> showPresetDetails(item));
                body.addView(row); divider(body);

            }
        } catch (JSONException e) { toast(getString(R.string.secondary_37)); }
    }

    private void showAddRules() {
        LinearLayout options=column();
        options.setPadding(dp(18),dp(5),dp(18),dp(15));
        AlertDialog dialog=new VexisDialog().setTitle(getString(R.string.secondary_17)).setView(options).create();
        String[] names={getString(R.string.secondary_54),getString(R.string.secondary_23),getString(R.string.secondary_52)};
        int[] icons={R.drawable.vexis_ic_add,R.drawable.vexis_main_paste,R.drawable.vexis_main_qr};
        for(int i=0;i<names.length;i++){
            final int action=i;
            if(i>0)divider(options);
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(46));
            ImageView icon=new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(ACCENT);
            LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(19),dp(19));
            space.setMarginEnd(dp(10));
            row.addView(icon,space);
            TextView name=VexisUi.text(this,names[i],14,TEXT,false);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(view->{dialog.dismiss();addRuleFrom(action);});
            options.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        dialog.show();
        styleDialog(dialog);
        VexisMotion.enter(options,dp(5));
    }

    private void addRuleFrom(int action) {
        if(action==0)createPreset(null);
        else if(action==1){
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard==null||!clipboard.hasPrimaryClip()||clipboard.getPrimaryClip().getItemCount()==0){
                toast(getString(R.string.secondary_6));
                return;
            }
            CharSequence value=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            importPreset(value==null?"":value.toString());
        }else{
            startActivityForResult(new Intent(this,QrScannerActivity.class),101);
        }
    }

    private void showPresetDetails(JSONObject item) {
        JSONObject data;
        try { data = RoutingPresets.displayData(item); }
        catch (JSONException e) { toast(getString(R.string.secondary_37)); return; }
        LinearLayout details = column(); details.setPadding(dp(20), 0, dp(20), dp(8));
        if (data != null) {
            detailStatus(details, getString(R.string.secondary_14), data.optBoolean("GlobalProxy", true));
            detailStatus(details, getString(R.string.secondary_45), data.optBoolean("bypassLan", false) || RoutingPresets.isBuiltIn(item));
            String[] keys = {"DirectSites", "DirectIp", "ProxySites", "ProxyIp", "BlockSites", "BlockIp"};
            String[] titles = {getString(R.string.secondary_34), getString(R.string.secondary_33), getString(R.string.secondary_2), getString(R.string.secondary_1), getString(R.string.secondary_5), getString(R.string.secondary_4)};
            for (int i = 0; i < keys.length; i++) {
                JSONArray values = data.optJSONArray(keys[i]); if (values == null || values.length() == 0) continue;
                TextView section = text(titles[i], 14); section.setTextColor(ACCENT); section.setTypeface(null, Typeface.BOLD);
                section.setBackground(shape(PALE, 0, 8));
                LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
                sectionParams.setMargins(0, dp(12), 0, dp(4)); details.addView(section, sectionParams);
                for (int j = 0; j < values.length(); j++) details.addView(text(values.optString(j), 15));
            }
        } else details.addView(text(item.optJSONObject("legacy") == null ? getString(R.string.secondary_41) : item.optJSONObject("legacy").toString(), 15));
        ScrollView detailsScroll = new ScrollView(this){
            @Override protected void onMeasure(int width,int height) {
                int limit=Math.min(View.MeasureSpec.getSize(height),dp(315));
                if(limit<=0)limit=dp(315);
                super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
            }
        };
        detailsScroll.addView(details);
        LinearLayout frame=column();
        frame.addView(detailsScroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footSpace=new LinearLayout.LayoutParams(-1,-2);
        footSpace.setMargins(dp(20),dp(10),dp(20),dp(17));
        frame.addView(footer,footSpace);
        AlertDialog dialog = new VexisDialog()
                .setTitle(RoutingPresets.name(item)).setView(frame).create();
        if (!RoutingPresets.isBuiltIn(item)) {
            addPresetAction(footer,getString(R.string.secondary_57),VexisUi.QUIET,()->{
                dialog.dismiss();
                confirmPresetRemoval(item);
            });
            if(item.optBoolean("editable",false))addPresetAction(footer,getString(R.string.secondary_24),VexisUi.QUIET,()->{
                dialog.dismiss();
                createPreset(item);
            });
        }
        addPresetAction(footer,getString(R.string.secondary_22),VexisUi.SOLID,dialog::dismiss);
        dialog.show();
        styleDialog(dialog);
    }

    private void addPresetAction(LinearLayout footer,String label,int kind,Runnable action) {
        Button button=new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        button.setStateListAnimator(null);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4),0,dp(4),0);
        button.setTextColor(kind==VexisUi.SOLID?VexisUi.ON_ACCENT:getString(R.string.secondary_57).equals(label)?VexisUi.DANGER:VexisUi.DEEP);
        button.setBackground(getString(R.string.secondary_57).equals(label)?shape(VexisUi.isDark(this) ? 0xff392129 : 0xfffdf0f3,0,10):VexisUi.buttonBackground(this,kind));
        button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(getString(R.string.secondary_22).equals(label)?82:77),dp(44));
        if(footer.getChildCount()>0)space.setMarginStart(dp(6));
        footer.addView(button,space);
    }

    private void confirmPresetRemoval(JSONObject item) {
        new VexisDialog().setTitle(getString(R.string.secondary_58)).setMessage(RoutingPresets.name(item))
                .setNegativeButton(getString(R.string.secondary_46), null).setPositiveButton(getString(R.string.secondary_57), (confirm, choice) -> {
                    try {
                        JSONArray sets=RoutingPresets.load(prefs);
                        for(int i=0;i<sets.length();i++){
                            JSONObject candidate=sets.getJSONObject(i);
                            if(!RoutingPresets.isBuiltIn(candidate)&&RoutingPresets.name(candidate).equals(RoutingPresets.name(item))){
                                sets.remove(i);RoutingPresets.save(prefs,sets);showRules();saveMessage();break;
                            }
                        }
                    }catch(JSONException e){toast(getString(R.string.secondary_40));}
                }).show();
    }

    private void detailStatus(LinearLayout parent, String label, boolean enabled) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(48));
        ImageView glyph=new ImageView(this);
        glyph.setImageResource(label.equals(getString(R.string.secondary_45))?R.drawable.vexis_main_apps:R.drawable.vexis_ic_globe);
        glyph.setColorFilter(ACCENT);
        LinearLayout.LayoutParams iconSpace=new LinearLayout.LayoutParams(dp(18),dp(18));
        iconSpace.setMarginEnd(dp(10));row.addView(glyph,iconSpace);
        TextView name = text(label, 14); name.setPadding(0, 0, 0, 0); row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(enabled ? getString(R.string.secondary_8) : getString(R.string.secondary_13), 12); value.setTextColor(SECONDARY);
        row.addView(value); parent.addView(row); divider(parent);
    }

    private void createPreset(JSONObject existing) {
        toast(getString(R.string.secondary_19));
        new Thread(() -> {
            try {
                String[] sites = RoutingPresets.bundledGeoNames(getApplicationContext(), "v2ray-geosite.dat");
                String[] ips = RoutingPresets.bundledGeoNames(getApplicationContext(), "v2ray-geoip.dat");
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showPresetEditor(existing, sites, ips); });
            } catch (java.io.IOException e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) toast(getString(R.string.secondary_36)); });
            }
        }, "load-routing-geo").start();
    }

    private void showPresetEditor(JSONObject existing, String[] sites, String[] ips) {
        LinearLayout form = column();
        form.setPadding(dp(16), 0, dp(16), 0);
        EditText name = new EditText(this);
        TextView nameLabel = text(getString(R.string.secondary_29), 13); nameLabel.setTextColor(SECONDARY); nameLabel.setPadding(0, dp(4), 0, 0); form.addView(nameLabel);
        name.setSingleLine(); name.setHint(getString(R.string.secondary_29)); styleInput(name); form.addView(name);
        JSONObject oldData = existing == null ? null : existing.optJSONObject("data");
        if (oldData != null) name.setText(oldData.optString("Name"));
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        Switch global = VexisUi.switchControl(this);
        global.setText(getString(R.string.secondary_10)); styleSwitch(global); global.setChecked(oldData == null || oldData.optBoolean("GlobalProxy", true)); form.addView(global); divider(form);
        Switch lan = VexisUi.switchControl(this);
        lan.setText(getString(R.string.secondary_26)); styleSwitch(lan); lan.setChecked(oldData == null || oldData.optBoolean("bypassLan", false)); form.addView(lan); divider(form);
        final boolean[][] selectedSites = new boolean[3][sites.length];
        final boolean[][] selectedIps = new boolean[3][ips.length];
        final EditText[] domainInputs = new EditText[3];
        final EditText[] ipInputs = new EditText[3];
        final AlertDialog[] editor = new AlertDialog[1];
        String[] fields = {"Proxy", "Direct", "Block"};
        for (int action = 0; action < 3; action++) {
            final int index = action;
            domainInputs[action] = new EditText(this);
            domainInputs[action].setHint(getString(R.string.secondary_18)); styleInput(domainInputs[action]);
            ipInputs[action] = new EditText(this);
            ipInputs[action].setHint("IP / CIDR"); styleInput(ipInputs[action]);
            if (oldData != null) {
                try {
                    restoreValues(oldData.optJSONArray(fields[action] + "Sites"), sites, selectedSites[action], domainInputs[action], "geosite:");
                    restoreValues(oldData.optJSONArray(fields[action] + "Ip"), ips, selectedIps[action], ipInputs[action], "geoip:");
                } catch (JSONException e) { toast(getString(R.string.secondary_37)); return; }
                if (action == 1 && oldData.optBoolean("bypassLan", false)) {
                    for (int j = 0; j < sites.length; j++) if ("private".equals(sites[j])) selectedSites[action][j] = false;
                    for (int j = 0; j < ips.length; j++) if ("private".equals(ips[j])) selectedIps[action][j] = false;
                }
            }
            domainInputs[action].setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32768)});
            ipInputs[action].setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32768)});
            TextView section = text(ROUTE_LABELS[action], 15); section.setTextColor(ACCENT);
            section.setTypeface(null, Typeface.BOLD); section.setBackground(shape(PALE, 0, 8));
            LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
            sectionParams.setMargins(0, dp(8), 0, dp(6)); form.addView(section, sectionParams);
            LinearLayout geoRow = new LinearLayout(this);
            Button siteButton = actionButton("GeoSite", () -> chooseGeo(editor[0],"GeoSite · " + ROUTE_LABELS[index], sites, selectedSites[index]));
            Button ipButton = actionButton("GeoIP", () -> chooseGeo(editor[0],"GeoIP · " + ROUTE_LABELS[index], ips, selectedIps[index]));
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(44), 1); half.setMarginEnd(dp(4));
            geoRow.addView(siteButton, half);
            LinearLayout.LayoutParams other = new LinearLayout.LayoutParams(0, dp(44), 1); other.setMarginStart(dp(4));
            geoRow.addView(ipButton, other); form.addView(geoRow);
            form.addView(domainInputs[action]); form.addView(ipInputs[action]);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new VexisDialog().setTitle(getString(R.string.secondary_44)).setView(scroll)
                .setNegativeButton(getString(R.string.secondary_46), null).setPositiveButton(getString(R.string.secondary_55), null).create();
        editor[0]=dialog;
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError(getString(R.string.secondary_59)); return; }
            try {
                JSONArray sets = RoutingPresets.load(prefs);
                for (int i = 0; i < sets.length(); i++) {
                    if ((existing == null || !RoutingPresets.name(existing).equals(RoutingPresets.name(sets.getJSONObject(i)))) && title.equalsIgnoreCase(RoutingPresets.name(sets.getJSONObject(i)))) {
                        name.setError(getString(R.string.secondary_31)); return;
                    }
                }
                JSONObject item = RoutingPresets.create(title, global.isChecked(), lan.isChecked(),
                        geoValues(sites, selectedSites[1], "geosite:", domainInputs[1]), geoValues(ips, selectedIps[1], "geoip:", ipInputs[1]),
                        geoValues(sites, selectedSites[0], "geosite:", domainInputs[0]), geoValues(ips, selectedIps[0], "geoip:", ipInputs[0]),
                        geoValues(sites, selectedSites[2], "geosite:", domainInputs[2]), geoValues(ips, selectedIps[2], "geoip:", ipInputs[2]));
                if (existing != null) {
                    item.put("enabled", existing.optBoolean("enabled", true));
                    for (int i = 0; i < sets.length(); i++) if (RoutingPresets.name(existing).equals(RoutingPresets.name(sets.getJSONObject(i)))) { sets.put(i, item); item = null; break; }
                }
                if (item != null) sets.put(item); RoutingPresets.save(prefs, sets); dialog.dismiss(); showRules(); saveMessage();
            } catch (JSONException e) { toast(e.getMessage() == null ? getString(R.string.secondary_39) : e.getMessage()); }
        }));
        dialog.show(); styleDialog(dialog);
        if(dialog.getWindow()!=null){
            int width=dialog.getWindow().getAttributes().width;
            int height=Math.min(dp(650),getResources().getDisplayMetrics().heightPixels-dp(48));
            dialog.getWindow().setLayout(width,height);
        }
    }

    private void chooseGeo(AlertDialog editor,String title, String[] names, boolean[] selected) {
        boolean[] draft = selected.clone();
        LinearLayout picker = column();
        EditText search = new EditText(this);
        search.setSingleLine(); search.setHint(getString(R.string.secondary_48)); styleInput(search); picker.setPadding(dp(16), 0, dp(16), 0); picker.addView(search);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.vexis_ic_search,0,0,0);
        search.setCompoundDrawablePadding(dp(8));
        ListView list = new ListView(this){
            @Override protected void onMeasure(int width,int height){
                int bound=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED?dp(315):
                    Math.min(dp(315),View.MeasureSpec.getSize(height));
                super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(bound,View.MeasureSpec.AT_MOST));
            }
        };
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE); list.setDividerHeight(dp(1)); list.setDivider(new android.graphics.drawable.ColorDrawable(LINE));
        picker.addView(list, new LinearLayout.LayoutParams(-1, -2));
        ArrayList<Integer> visible = new ArrayList<>();
        Runnable filter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            visible.clear();
            ArrayList<String> labels = new ArrayList<>();
            for(boolean checked:new boolean[]{true,false})
                for (int i = 0; i < names.length; i++) if (draft[i]==checked&&names[i].toLowerCase(Locale.ROOT).contains(query)) {
                    visible.add(i); labels.add(names[i]);
                }
            list.clearChoices();
            list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, labels) {
                @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                    row.setTextColor(TEXT); row.setTextSize(14);
                    row.setCheckMarkDrawable(R.drawable.vexis_main_check_selector);
                    row.setCheckMarkTintList(null);
                    row.setMinHeight(dp(48)); return row;
                }
            });
            for (int i = 0; i < visible.size(); i++) list.setItemChecked(i, draft[visible.get(i)]);
        };
        list.setOnItemClickListener((parent, view, position, id) -> draft[visible.get(position)] = list.isItemChecked(position));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        filter.run();
        AlertDialog pickerDialog=new VexisDialog().setTitle(title).setView(picker)
                .setNegativeButton(getString(R.string.secondary_46), null)
                .setPositiveButton(getString(R.string.secondary_15), (dialog, which) -> System.arraycopy(draft, 0, selected, 0, draft.length)).create();
        if(editor!=null){
            editor.hide();
            pickerDialog.setOnDismissListener(v->{if(!isFinishing()&&!isDestroyed())editor.show();});
        }
        pickerDialog.show();
        styleDialog(pickerDialog);
    }

    private void restoreValues(JSONArray values, String[] names, boolean[] selected, EditText input, String prefix) throws JSONException {
        if (values == null) return;
        ArrayList<String> manual = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String value = values.getString(i);
            boolean found = false;
            if (value.startsWith(prefix)) for (int j = 0; j < names.length; j++) {
                if (value.substring(prefix.length()).equalsIgnoreCase(names[j])) { selected[j] = true; found = true; break; }
            }
            if (!found) manual.add(value);
        }
        input.setText(android.text.TextUtils.join("\n", manual));
    }

    private JSONArray geoValues(String[] names, boolean[] selected, String prefix, EditText input) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < names.length; i++) if (selected[i]) result.put(prefix + names[i]);
        String raw = input.getText().toString();
        // Commas can be part of a regexp, so a regexp occupies a whole line.
        for (String line : raw.split("[\r\n]+")) {
            String[] values = line.trim().startsWith("regexp:") ? new String[]{line} : line.split(",");
            for (String value : values) {
                value = value.trim();
                if (value.isEmpty()) continue;
                RoutingPresets.validateValue(value, "geoip:".equals(prefix));
                result.put(value);
            }
        }
        return result;
    }

    private void importPreset(String link) {
        AppToast.show(this, getString(R.string.secondary_20));
        new Thread(() -> {
            try {
                JSONObject imported = RoutingPresets.importLink(getApplicationContext(), link);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        JSONArray sets = RoutingPresets.load(prefs);
                        for (int i = sets.length() - 1; i >= 0; i--) {
                            JSONObject current = sets.getJSONObject(i);
                            if (RoutingPresets.name(current).equalsIgnoreCase(RoutingPresets.name(imported))) {
                                if (RoutingPresets.isBuiltIn(current)) { toast(getString(R.string.secondary_30)); return; }
                                sets.remove(i);
                            }
                        }
                        sets.put(imported);
                        RoutingPresets.save(prefs, sets);
                        showRules();
                        saveMessage();
                    } catch (JSONException e) { toast(getString(R.string.secondary_39)); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) toast(e.getMessage() == null ? getString(R.string.secondary_35) : e.getMessage()); });
            }
        }).start();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 101 && result == RESULT_OK && data != null) {
            String link = data.getStringExtra("SCAN_RESULT");
            if (link != null) importPreset(link);
        }
    }
}

