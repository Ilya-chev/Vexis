package com.example.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Schedules subscription expiry notices independently of network refreshes. */
public final class SubscriptionExpiry extends JobService {
    private static final int JOB_ID = 24108;
    private static final long DAY = 24 * 60 * 60 * 1000L;
    private static final String CHANNEL = "subscription_expiry";

    static void schedule(Context context) { schedule(context, 0); }
    private static void schedule(Context context, long delay) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SubscriptionExpiry.class))
                .setMinimumLatency(Math.max(0, delay)).setPersisted(true).build();
        scheduler.schedule(job);
    }

    static long expiry(String userinfo) {
        if (userinfo == null) return 0;
        for (String pair : userinfo.split(";")) {
            String[] entry = pair.split("=", 2);
            if (entry.length != 2 || !entry[0].trim().equalsIgnoreCase("expire")) continue;
            try {
                long seconds = Long.parseLong(entry[1].trim());
                return seconds > 0 && seconds <= Long.MAX_VALUE / 1000 ? seconds * 1000 : 0;
            } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    static void showExpiredOnEntry(Context context) {
        try (LocalDatabase db = new LocalDatabase(context)) {
            int count = 0;
            String title = "";
            long now = System.currentTimeMillis();
            for (String url : db.urls()) {
                long end = expiry(db.subscriptionInfo(url)[0]);
                if (end > 0 && end <= now) { count++; title = db.title(url); }
            }
            if (count == 1) AppToast.show(context, AppLocale.context(context).getString(R.string.service_expired, title));
            else if (count > 1) AppToast.show(context, AppLocale.context(context).getString(R.string.service_expired_count, count));
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            long next = Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            SharedPreferences seen = getSharedPreferences("subscription_expiry_notices", MODE_PRIVATE);
            try (LocalDatabase db = new LocalDatabase(this)) {
                for (String url : db.urls()) {
                    long end = expiry(db.subscriptionInfo(url)[0]);
                    if (end <= 0) continue;
                    String key = key(url);
                    if (end <= now) {
                        if (seen.getLong(key + ":expired", 0) != end && notifyExpiry(key, db.title(url), true))
                            seen.edit().putLong(key + ":expired", end).apply();
                    } else {
                        long warning = Math.max(0, end - DAY);
                        if (warning <= now) {
                            if (seen.getLong(key + ":warning", 0) != end && notifyExpiry(key, db.title(url), false))
                                seen.edit().putLong(key + ":warning", end).apply();
                        } else next = Math.min(next, warning);
                        next = Math.min(next, end);
                    }
                }
            } catch (RuntimeException error) {
                android.util.Log.w("SubscriptionExpiry", "Expiry check failed", error);
                next = now + 60 * 60 * 1000L;
            }
            jobFinished(params, false);
            if (next != Long.MAX_VALUE) schedule(this, Math.max(1000, next - System.currentTimeMillis()));
        }, "subscription-expiry").start();
        return true;
    }

    private boolean notifyExpiry(String key, String title, boolean expired) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return false;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                new NotificationChannel(CHANNEL, AppLocale.context(this).getString(R.string.service_extra_16), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(this, MainActivity.class).putExtra("subscription_list", true);
        PendingIntent action = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        String message = expired ? AppLocale.context(this).getString(R.string.service_expired, title) : AppLocale.context(this).getString(R.string.service_expiring, title);
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        notification.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle(AppLocale.context(this).getString(R.string.service_extra_17))
                .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(action).setAutoCancel(true);
        manager.notify(key, expired ? 2 : 1, notification.build());
        return true;
    }

    private static String key(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
