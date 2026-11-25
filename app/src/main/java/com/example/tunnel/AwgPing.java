package com.example.tunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Checks AWG through a temporary Android VPN, only while VPN is idle. */
final class AwgPing {
    static final long BUSY = -2;
    private static final Semaphore SLOT = new Semaphore(1, true);

    private static boolean vpnBusy(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        if (prefs.getBoolean("vpn_session_active", false) || prefs.getBoolean("recovery_wanted", false)
                || TunnelService.isActive() || VpnService.prepare(context) != null) return true;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    static boolean vpnBusyBeforeEstablish(Context context, long generation) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        if (prefs.getLong(RecoveryService.SESSION_GENERATION, 0) != generation || prefs.getBoolean("vpn_session_active", false)
                || prefs.getBoolean("recovery_wanted", false) || VpnService.prepare(context) != null) return true;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    static long measure(Context context, String profile, int timeoutMs) throws Exception {
        Context app = context.getApplicationContext();
        SLOT.acquire();
        String token = UUID.randomUUID().toString();
        CountDownLatch done = new CountDownLatch(1);
        long[] result = {-1};
        boolean started = false;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!token.equals(intent.getStringExtra(TunnelService.ATTEMPT))) return;
                String state = intent.getStringExtra("state");
                if (TunnelService.CONNECTING.equals(state)) return;
                if (TunnelService.PROBED.equals(state)) result[0] = intent.getLongExtra(TunnelService.DELAY, -1);
                done.countDown();
            }
        };
        try {
            if (vpnBusy(app)) return BUSY;
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, new IntentFilter(TunnelService.USER_PING_STATE), TunnelService.STATE_PERMISSION, null, Context.RECEIVER_NOT_EXPORTED);
            else app.registerReceiver(receiver, new IntentFilter(TunnelService.USER_PING_STATE), TunnelService.STATE_PERMISSION, null);
            try {
                if (vpnBusy(app)) return BUSY;
                long generation = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                        .getLong(RecoveryService.SESSION_GENERATION, 0);
                Intent start = new Intent(app, AwgTunnelService.class).setAction(TunnelService.START)
                        .putExtra(TunnelService.PROFILE, profile).putExtra(TunnelService.ATTEMPT, token)
                        .putExtra(TunnelService.VERIFY, true).putExtra(TunnelService.PROBE, true).putExtra(TunnelService.USER_PING, true)
                        .putExtra(RecoveryService.SESSION_GENERATION, generation);
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(start); else app.startService(start);
                started = true;
                if (!done.await(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS)) return -1;
                return result[0];
            } finally {
                if (started && done.getCount() != 0) {
                    try { app.startService(new Intent(app, AwgTunnelService.class).setAction(TunnelService.STOP)
                            .putExtra(TunnelService.ATTEMPT, token)); } catch (RuntimeException ignored) { }
                    boolean interrupted = Thread.interrupted();
                    try { done.await(2, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { interrupted = true; }
                    finally { if (interrupted) Thread.currentThread().interrupt(); }
                }
                app.unregisterReceiver(receiver);
            }
        } finally {
            SLOT.release();
        }
    }
}
