package com.example.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

final class InternetAlert {
    private static final int ID = 311;
    private static final String CHANNEL = "vpn_connection_alerts";

    static void show(Context context) {
        if (!context.getSharedPreferences("app_settings", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                .getBoolean("connection_alerts", true)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, AppLocale.context(context).getString(R.string.service_extra_11), NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH);
        boolean manual=!context.getSharedPreferences("app_settings",Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                .getBoolean("auto_connect",false);
        if(manual && Build.VERSION.SDK_INT>=26)notification.setTimeoutAfter(3000);
        manager.notify(ID, notification.setSmallIcon(R.drawable.ic_tile_manual)
                .setContentTitle(AppLocale.context(context).getString(R.string.service_extra_15)).setContentText(AppLocale.context(context).getString(R.string.service_extra_0))
                .setAutoCancel(true).build());
        if(manual && Build.VERSION.SDK_INT<26)new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(()->manager.cancel(ID),3000);
    }

    static void clear(Context context) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);
    }
}
