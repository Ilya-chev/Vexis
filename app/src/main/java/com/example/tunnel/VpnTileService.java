package com.example.tunnel;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.ArrayList;
import java.util.List;

/** Reads live state from each VPN service, including the separate AWG process. */
public abstract class VpnTileService extends TileService {
    static final String EXTRA_AUTOMATIC = "automatic";
    static final String STATUS_QUERY = "com.example.tunnel.TILE_STATUS_QUERY";
    static final String STATUS_REPLY = "tile_status_reply";
    static final int INACTIVE = 0, CONNECTING = 1, RECOVERING = 2, CONNECTED = 3, WAITING = 4, WAITING_CONNECTED = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening;
    private int requestId;
    private String lastLabel;

    protected abstract boolean automatic();

    @Override public void onStartListening() {
        super.onStartListening();
        handler.removeCallbacksAndMessages(null);
        listening = true;
        lastLabel = null;
        refresh();
    }

    private long generation() {
        return getSharedPreferences("app_settings", MODE_PRIVATE)
                .getLong(RecoveryService.SESSION_GENERATION, 0);
    }

    private void refresh() {
        if (!listening) return;
        int id = ++requestId;
        long session = generation();
        List<Class<?>> services = new ArrayList<>();
        ActivityManager manager = getSystemService(ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
                String name = info.service.getClassName();
                if (TunnelService.class.getName().equals(name)) services.add(TunnelService.class);
                if (AwgTunnelService.class.getName().equals(name)) services.add(AwgTunnelService.class);
                if (RecoveryService.class.getName().equals(name)) services.add(RecoveryService.class);
            }
        }
        final class Snapshot extends ResultReceiver {
            int pending = services.size();
            int bestState = INACTIVE;
            String label;
            boolean delivered;

            Snapshot() { super(handler); }

            @Override protected void onReceiveResult(int state, Bundle data) {
                if (!listening || delivered || id != requestId) return;
                if (data != null && data.getLong(RecoveryService.SESSION_GENERATION, -1) == session
                        && state > bestState) {
                    bestState = state;
                    label = data.getString("label");
                }
                if (--pending == 0) deliver();
            }

            void deliver() {
                if (!listening || delivered || id != requestId) return;
                delivered = true;
                if (session == generation()) updateTile(label, bestState);
            }
        }
        Snapshot snapshot = new Snapshot();
        for (Class<?> service : services) {
            try {
                startService(new Intent(this, service).setAction(STATUS_QUERY).putExtra(STATUS_REPLY, snapshot));
            } catch (RuntimeException ignored) {
                if (--snapshot.pending == 0) snapshot.deliver();
            }
        }
        if (snapshot.pending == 0) snapshot.deliver();
        else handler.postDelayed(snapshot::deliver, 800);
        handler.postDelayed(this::refresh, 1000);
    }

    static void reply(Intent query, long generation, int state, String label) {
        ResultReceiver reply = query.getParcelableExtra(STATUS_REPLY);
        if (reply == null) return;
        Bundle data = new Bundle();
        data.putLong(RecoveryService.SESSION_GENERATION, generation);
        data.putString("label", label);
        reply.send(state, data);
    }

    @Override public void onStopListening() {
        listening = false;
        handler.removeCallbacksAndMessages(null);
        super.onStopListening();
    }

    private void updateTile(String label, int phase) {
        Tile tile = getQsTile();
        if (tile == null) return;
        SharedPreferences settings = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean matching = label != null && settings.getBoolean(MainActivity.AUTO_MODE, false) == automatic();
        boolean active = matching && phase != WAITING;
        String currentLabel = matching ? label : "Vexis";
        int currentState = active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        if (currentLabel.equals(lastLabel) && tile.getState() == currentState) return;
        lastLabel = currentLabel;
        tile.setState(currentState);
        tile.setLabel(currentLabel);
        tile.setIcon(Icon.createWithResource(this, automatic() ? R.drawable.ic_tile_auto : R.drawable.ic_tile_manual));
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(matching ? currentLabel : AppLocale.context(this).getString(R.string.service_disconnected));
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, TileCommandService.class).putExtra(EXTRA_AUTOMATIC, automatic());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }

    public static final class Manual extends VpnTileService {
        @Override protected boolean automatic() { return false; }
    }
    public static final class Automatic extends VpnTileService {
        @Override protected boolean automatic() { return true; }
    }
}




