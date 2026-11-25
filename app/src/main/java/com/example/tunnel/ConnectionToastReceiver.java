package com.example.tunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives explicit same-app events in the main process, including from the AWG process. */
public final class ConnectionToastReceiver extends BroadcastReceiver {
    private static String last = "";
    private static long lastGeneration = Long.MIN_VALUE;

    static void send(Context context, String message, long generation) {
        context.sendBroadcast(new Intent(context, ConnectionToastReceiver.class)
                .putExtra("message", message).putExtra(RecoveryService.SESSION_GENERATION, generation));
    }

    @Override public void onReceive(Context context, Intent intent) {
        long generation = intent.getLongExtra(RecoveryService.SESSION_GENERATION, -1);
        if (generation != context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getLong(RecoveryService.SESSION_GENERATION, 0)) return;
        String message = intent.getStringExtra("message");
        if (message == null) return;
        if (generation == lastGeneration && message.equals(last)) return;
        lastGeneration = generation;
        last = message;
        if (!message.isEmpty()) {
            AppToast.showConnection(context, ServiceText.translate(context, message));
        } else AppToast.clearConnection();
    }
}
