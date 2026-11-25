package com.example.tunnel;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Refreshes stale subscriptions when Android reports an available network. */
public final class SubscriptionRefreshJob extends JobService {
    static final String CHANGED = "com.example.tunnel.SUBSCRIPTIONS_CHANGED";
    private static final int JOB_ID = 24107;
    private static final long RETRY_DELAY = 60 * 60 * 1000L;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile int generation;
    private InternetMonitor internet;

    static void schedule(Context context, long delay) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        SharedPreferences settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        JobInfo.Builder job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SubscriptionRefreshJob.class));
        if (Build.VERSION.SDK_INT >= 28) {
            NetworkRequest.Builder network = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
            if (settings.getBoolean("refresh_wifi_only", false))
                network.addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            job.setRequiredNetwork(network.build());
        } else job.setRequiredNetworkType(settings.getBoolean("refresh_wifi_only", false)
                ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY);
        job.setMinimumLatency(Math.max(0, delay));
        job.setPersisted(true);
        scheduler.schedule(job.build());
    }

    static void ensureScheduled(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null && scheduler.getPendingJob(JOB_ID) == null) {
            long interval=Math.max(1,context.getSharedPreferences("app_settings",Context.MODE_PRIVATE).getInt("interval",60))*60000L;
            schedule(context,interval);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        if(internet!=null){internet.close();internet=null;}
        running = true;
        final int run = ++generation;
        internet=new InternetMonitor(this,status->{
            if(!running||run!=generation||status==InternetMonitor.Status.DOWN)return;
            internet.close();internet=null;
            runRefresh(params,run);
        });
        internet.start(true);
        return true;
    }

    private void runRefresh(JobParameters params,int run) {
        worker.execute(() -> {
            long next = Long.MAX_VALUE;
            SharedPreferences settings = getSharedPreferences("app_settings", MODE_PRIVATE);
            long interval = Math.max(1, settings.getInt("interval", 60)) * 60000L;
            long now = System.currentTimeMillis();
            try (LocalDatabase db = new LocalDatabase(this)) {
                for (String url : db.urls()) {
                    if (!running || run != generation) break;
                    if (url.startsWith("local:")) continue;
                    long updated = parseTime(db.subscriptionInfo(url)[2]);
                    String key = digest(url);
                    long attempted = db.refreshAttempt(key);
                    long due = updated <= 0 || updated > now ? now : updated + interval;
                    if (attempted > 0 && attempted <= now) due = Math.max(due, attempted + RETRY_DELAY);
                    if (due > now) { next = Math.min(next, due - now); continue; }
                    if (!SubscriptionNetwork.refreshAllowed(this)) break;
                    BackgroundRecoverySupport.refresh(this, Collections.singleton(url), () -> running && run == generation);
                    if(!running || run != generation)break;
                    long after = parseTime(db.subscriptionInfo(url)[2]);
                    if (after != updated && after > 0) {
                        db.clearRefreshAttempt(key);
                        next = Math.min(next, interval);
                    } else {
                        if(db.refreshAttempt(key)<=attempted)db.setRefreshAttempt(key,System.currentTimeMillis());
                        next = Math.min(next, RETRY_DELAY);
                    }
                }
            } catch (RuntimeException error) {
                android.util.Log.w("SubscriptionRefresh", "Subscription refresh failed: " + error.getClass().getSimpleName());
            }
            long delay = next == Long.MAX_VALUE ? interval : Math.max(1000, next);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!running || run != generation) return;
                running = false;
                jobFinished(params, false);
                schedule(this, delay);
            });
        });
    }

    private static long parseTime(String value) {
        try { return value == null ? 0 : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0; }
    }
    static long lastFailure(Context context, String url) {
        try (LocalDatabase db = new LocalDatabase(context.getApplicationContext())) {
            return db.refreshAttempt(digest(url));
        }
    }
    static long nextDue(Context context, String url, long updated) {
        long now = System.currentTimeMillis();
        long interval = Math.max(1, context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getInt("interval", 60)) * 60000L;
        long due = updated <= 0 || updated > now ? now : updated + interval;
        long attempted = lastFailure(context, url);
        if (attempted > 0 && attempted <= now) due = Math.max(due, attempted + RETRY_DELAY);
        return due;
    }
    static void recordFailure(Context context,String url) {
        try(LocalDatabase db=new LocalDatabase(context.getApplicationContext())){db.setRefreshAttempt(digest(url),System.currentTimeMillis());}
        catch(RuntimeException error){android.util.Log.w("SubscriptionRefresh","Could not store failed attempt");}
    }
    static void recordSuccess(Context context,String url) {
        SubscriptionExpiry.schedule(context);
        try(LocalDatabase db=new LocalDatabase(context.getApplicationContext())){db.clearRefreshAttempt(digest(url));}
        catch(RuntimeException error){android.util.Log.w("SubscriptionRefresh","Could not clear failed attempt");}
    }
    private static String digest(String value) {
        try {
            byte[] data = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : data) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public boolean onStopJob(JobParameters params) {
        running = false; generation++;
        if(internet!=null){internet.close();internet=null;}
        return true;
    }
    @Override public void onDestroy() {
        running = false;
        if(internet!=null){internet.close();internet=null;}
        worker.shutdownNow(); super.onDestroy();
    }
}

