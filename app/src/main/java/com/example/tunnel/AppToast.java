package com.example.tunnel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

final class AppToast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Toast current;
    private static boolean connection;
    private static Runnable dismiss;

    private AppToast() { }

    static void show(Context context, CharSequence message) {
        display(context, message, false);
    }

    static void showConnection(Context context, CharSequence message) {
        display(context, message, true);
    }

    private static void display(Context context, CharSequence message, boolean isConnection) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            cancel();
            connection = isConnection;
            current = Toast.makeText(app, message, isConnection ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            current.show();
            if (!isConnection) {
                Toast shown = current;
                dismiss = () -> { if (current == shown) cancel(); };
                MAIN.postDelayed(dismiss, 3000);
            }
        });
    }

    static void clearConnection() {
        MAIN.post(() -> { if (connection) cancel(); });
    }

    private static void cancel() {
        if (dismiss != null) MAIN.removeCallbacks(dismiss);
        dismiss = null;
        if (current != null) current.cancel();
        current = null;
        connection = false;
    }
}
