package com.example.tunnel;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Network probes run off the UI thread and never include server configuration. */
public final class DiagnosticsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView physicalResult, vpnResult, dnsResult;
    private Button run;
    private DetailUi.Icon internetCheck, dnsCheck, vpnCheck;
    private volatile boolean closed;
    private String ownProfile;
    private final BroadcastReceiver vpnState = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (TunnelService.CONNECTED.equals(intent.getStringExtra("state"))) {
                ownProfile = intent.getStringExtra(TunnelService.PROFILE);
                if (ownProfile != null) { vpnResult.setText("Vexis · " + SubscriptionInfo.withoutFlagEmoji(SubscriptionInfo.name(ownProfile))); vpnCheck.setVisibility(View.VISIBLE); }
            }
        }
    };

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppLocale.context(base)); }

    @Override public void onCreate(Bundle state) {
        VexisUi.applyTheme(this);
        super.onCreate(state);
        LinearLayout root = DetailUi.root(this, getString(R.string.secondary_63));
        root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad = DetailUi.dp(this, 16); body.setPadding(pad, 0, pad, 0); root.addView(body);
        physicalResult = row(body, getString(R.string.secondary_64), "globe");
        dnsResult = row(body, "DNS", "dns");
        vpnResult = row(body, "VPN", "shield");
        run = new Button(this); run.setText(getString(R.string.secondary_66)); run.setTextSize(16); run.setTextColor(DetailUi.ACCENT);
        run.setAllCaps(false); run.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable disabled = new GradientDrawable(); disabled.setColor(VexisUi.PALE); disabled.setCornerRadius(DetailUi.dp(this, 10));
        states.addState(new int[]{-android.R.attr.state_enabled}, disabled); states.addState(new int[]{}, DetailUi.outline(this)); run.setBackground(states);
        run.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{VexisUi.MUTED, DetailUi.ACCENT}));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, DetailUi.dp(this, 43)); buttonParams.topMargin = DetailUi.dp(this, 12);
        body.addView(run, buttonParams); run.setOnClickListener(v -> check());
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(vpnState, new IntentFilter(TunnelService.STATE), TunnelService.STATE_PERMISSION, null, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(vpnState, new IntentFilter(TunnelService.STATE), TunnelService.STATE_PERMISSION, null);
        check();
    }

    private TextView row(LinearLayout body, String title, String icon) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new DetailUi.Icon(this, icon, DetailUi.ACCENT), new LinearLayout.LayoutParams(DetailUi.dp(this, 24), DetailUi.dp(this, 32)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1); labelParams.setMarginStart(DetailUi.dp(this, 12));
        row.addView(labels, labelParams); labels.addView(DetailUi.text(this, title, 14, DetailUi.TEXT));
        TextView value = DetailUi.text(this, getString(R.string.secondary_65), 12, DetailUi.SECONDARY); labels.addView(value);
        DetailUi.Icon check = new DetailUi.Icon(this, "check", 0xff16804b); check.setVisibility(View.INVISIBLE);
        row.addView(check, new LinearLayout.LayoutParams(DetailUi.dp(this, 24), DetailUi.dp(this, 32)));
        if (icon.equals("globe")) internetCheck = check; else if (icon.equals("dns")) dnsCheck = check; else vpnCheck = check;
        row.setMinimumHeight(DetailUi.dp(this, icon.equals("globe") ? 63 : 54)); row.setPadding(0, DetailUi.dp(this, 8), 0, DetailUi.dp(this, 8));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2)); DetailUi.divider(this, body); return value;
    }

    private void check() {
        run.setEnabled(false);
        internetCheck.setVisibility(View.INVISIBLE); dnsCheck.setVisibility(View.INVISIBLE); vpnCheck.setVisibility(View.INVISIBLE);
        physicalResult.setText(getString(R.string.secondary_67));
        vpnResult.setText(getString(R.string.secondary_67));
        dnsResult.setText(getString(R.string.secondary_67));
        ownProfile = null;
        ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if (am != null) for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            String name = info.service.getClassName();
            Class<?> type = name.equals(TunnelService.class.getName()) ? TunnelService.class : name.equals(AwgTunnelService.class.getName()) ? AwgTunnelService.class : null;
            if (type != null) try { startService(new Intent(this, type).setAction(TunnelService.QUERY)); } catch (RuntimeException ignored) { }
        }
        worker.execute(() -> {
            String internet = getString(R.string.secondary_70), dns = getString(R.string.secondary_70), vpn = getString(R.string.secondary_68);
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Network active = cm.getActiveNetwork();
                NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
                if (active == null || caps == null) { internet = getString(R.string.secondary_72); dns = getString(R.string.secondary_72); }
                else {
                    boolean reachable = probe(active, "https://www.google.com/generate_204") || probe(active, "https://cp.cloudflare.com/generate_204");
                    internet = reachable ? getString(R.string.secondary_74) : caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? getString(R.string.secondary_73) : getString(R.string.secondary_69);
                    boolean resolved = false;
                    for (String host : new String[]{"www.google.com", "one.one.one.one"}) try { if (active.getAllByName(host).length > 0) { resolved = true; break; } } catch (Exception ignored) { }
                    dns = resolved ? getString(R.string.secondary_76) : getString(R.string.secondary_69);
                }
                Network vpnNetwork = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? active : null;
                if (vpnNetwork == null) for (Network n : cm.getAllNetworks()) { NetworkCapabilities nc = cm.getNetworkCapabilities(n); if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpnNetwork = n; break; } }
                vpn = vpnNetwork == null ? getString(R.string.secondary_71) : getString(R.string.secondary_75);
                if (vpnNetwork != null && Build.VERSION.SDK_INT >= 30) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(vpnNetwork);
                    int uid = nc == null ? -1 : nc.getOwnerUid();
                    String[] packages = uid < 0 ? null : getPackageManager().getPackagesForUid(uid);
                    if (packages != null && packages.length > 0) {
                        String app = packages[0];
                        try { app = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app, 0)).toString(); } catch (Exception ignored) { }
                        vpn = app;
                    }
                }
            } catch (RuntimeException ignored) { }
            final String i = internet, d = dns, v = vpn;
            if (!closed) runOnUiThread(() -> {
                if (closed) return;
                physicalResult.setText(capitalize(i));
                internetCheck.setVisibility(i.equals(getString(R.string.secondary_74)) ? View.VISIBLE : View.INVISIBLE);
                dnsResult.setText(capitalize(d));
                dnsCheck.setVisibility(d.equals(getString(R.string.secondary_76)) ? View.VISIBLE : View.INVISIBLE);
                vpnResult.setText(ownProfile == null ? capitalize(v) : "Vexis · " + SubscriptionInfo.withoutFlagEmoji(SubscriptionInfo.name(ownProfile)));
                vpnCheck.setVisibility(ownProfile != null || (!v.equals(getString(R.string.secondary_71)) && !v.equals(getString(R.string.secondary_68))) ? View.VISIBLE : View.INVISIBLE);
                run.setEnabled(true);
            });
        });
    }

    private static String capitalize(String value) { return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(java.util.Locale.getDefault()) + value.substring(1); }

    private boolean probe(Network network, String endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) network.openConnection(new URL(endpoint), Proxy.NO_PROXY);
            connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            return connection.getResponseCode() == 204;
        } catch (Exception ignored) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    @Override protected void onDestroy() { closed = true; unregisterReceiver(vpnState); worker.shutdownNow(); super.onDestroy(); }
}
