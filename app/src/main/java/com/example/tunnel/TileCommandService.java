package com.example.tunnel;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.util.*;

/** Serializes Quick Settings commands without bringing an activity to the foreground. */
public final class TileCommandService extends Service {
    private static final String CHANNEL = "tile_commands";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;
    private boolean automatic;
    private boolean pendingStop;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CHANNEL, AppLocale.context(this).getString(R.string.service_vpn_control), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        startForeground(42, b.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle(AppLocale.context(this).getString(R.string.service_vpn_control)).setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        int command = ++generation;
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean boot = intent.getBooleanExtra(BootReceiver.EXTRA_BOOT, false);
        automatic = boot ? prefs.getBoolean(MainActivity.AUTO_MODE, false)
                : intent.getBooleanExtra(VpnTileService.EXTRA_AUTOMATIC, false);
        boolean active = !boot && !pendingStop && (prefs.getBoolean("vpn_session_active", false)
                || isRunning(RecoveryService.class) || isRunning(TunnelService.class) || isRunning(AwgTunnelService.class));
        boolean same = prefs.getBoolean(MainActivity.AUTO_MODE, false) == automatic;
        long session = prefs.getLong(RecoveryService.SESSION_GENERATION, 0) + 1;
        prefs.edit().putLong(RecoveryService.SESSION_GENERATION, session).putBoolean(MainActivity.AUTO_MODE, automatic)
                .putBoolean("recovery_wanted", false).putBoolean("vpn_session_active", false).commit();
        if (active) {
            stopIfRunning(RecoveryService.class, RecoveryService.STOP);
            stopIfRunning(TunnelService.class, TunnelService.STOP);
            stopIfRunning(AwgTunnelService.class, TunnelService.STOP);
            pendingStop = same;
            awaitStop(command, session, SystemClock.elapsedRealtime() + 15000, same);
        } else if (isAnyRunning()) {
            pendingStop = false;
            awaitStop(command, session, SystemClock.elapsedRealtime() + 15000, false);
        } else {
            pendingStop = false;
            launch(command, session);
        }
        return START_NOT_STICKY;
    }

    private boolean isRunning(Class<?> type) {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE))
            if (type.getName().equals(service.service.getClassName())) return true;
        return false;
    }
    private void stopIfRunning(Class<?> type, String action) {
        if (!isRunning(type)) return;
        try { startService(new Intent(this, type).setAction(action)); } catch (RuntimeException ignored) { }
    }
    private boolean isAnyRunning() {
        return isRunning(RecoveryService.class) || isRunning(TunnelService.class) || isRunning(AwgTunnelService.class);
    }
    private boolean current(int command, long session) {
        return command == generation && getSharedPreferences("app_settings", MODE_PRIVATE)
                .getLong(RecoveryService.SESSION_GENERATION, 0) == session;
    }
    private void awaitStop(int command, long session, long deadline, boolean stopOnly) {
        if (command != generation) return;
        if (!current(command, session)) { finish(); return; }
        if (!isAnyRunning()) {
            pendingStop = false;
            if (stopOnly) finish(); else launch(command, session);
        } else if (SystemClock.elapsedRealtime() >= deadline) {
            notifyUser(AppLocale.context(this).getString(R.string.service_stop_failed)); finish();
        } else handler.postDelayed(() -> awaitStop(command, session, deadline, stopOnly), 250);
    }
    private void launch(int command, long session) {
        if (!current(command, session)) { if (command == generation) finish(); return; }
        if (VpnService.prepare(this) != null) { notifyUser(AppLocale.context(this).getString(R.string.service_allow_vpn)); finish(); return; }
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        LocalDatabase db = new LocalDatabase(this);
        ArrayList<String> items = new ArrayList<>(), groups = new ArrayList<>(), owners = new ArrayList<>();
        db.loadServers(items, groups, owners);
        String profile = prefs.getString(MainActivity.MANUAL_PROFILE, null);
        boolean valid = automatic ? !Collections.disjoint(items, db.selected()) : profile != null && items.contains(profile);
        db.close();
        if (!valid) { notifyUser(automatic ? AppLocale.context(this).getString(R.string.service_select_servers) : AppLocale.context(this).getString(R.string.service_select_server)); finish(); return; }
        prefs.edit().remove(EngineUpdates.RETRY_AFTER_UPDATE).putInt("reconnect_count", 0)
                .putBoolean("vpn_session_active", true).putLong("auto_session_started", 0).apply();
        Intent start = automatic ? new Intent(this, RecoveryService.class).setAction(RecoveryService.INITIAL)
                : new Intent(this, AwgProfile.isAwg(profile) ? AwgTunnelService.class : TunnelService.class)
                    .setAction(TunnelService.START).putExtra(TunnelService.PROFILE, profile)
                    .putExtra(TunnelService.ATTEMPT, UUID.randomUUID().toString()).putExtra(TunnelService.VERIFY, true);
        start.putExtra(RecoveryService.SESSION_GENERATION, session);
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start); }
        catch (RuntimeException e) { prefs.edit().putBoolean("vpn_session_active", false).apply(); notifyUser(AppLocale.context(this).getString(R.string.service_start_failed)); }
        finish();
    }
    private void notifyUser(String message) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(43, b.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle("Vexis").setContentText(message).setContentIntent(pending).setAutoCancel(true).build());
    }
    private void finish() { stopForeground(true); stopSelf(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}

