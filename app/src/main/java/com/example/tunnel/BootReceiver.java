package com.example.tunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restores the selected connection mode after the user has unlocked the device. */
public final class BootReceiver extends BroadcastReceiver {
    static final String BOOT_CONNECT = "boot_connect";
    static final String EXTRA_BOOT = "boot_start";

    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                !context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean(BOOT_CONNECT, false)) return;
        Intent start = new Intent(context, TileCommandService.class).putExtra(EXTRA_BOOT, true);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start);
            else context.startService(start);
        } catch (RuntimeException failure) {
            android.util.Log.w("VpnBoot", "Unable to start VPN after boot", failure);
        }
    }
}
