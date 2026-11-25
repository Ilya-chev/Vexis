package com.example.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks Internet access on physical networks, independently of the active VPN. */
final class InternetMonitor implements AutoCloseable {
    enum Status { UP, DOWN, UNKNOWN }
    interface Listener { void onInternetChecked(Status status); }

    private static final String[] ENDPOINTS = {
            "https://www.google.com/generate_204",
            "https://cp.cloudflare.com/generate_204"
    };
    private final ConnectivityManager connectivity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private volatile boolean closed;
    private volatile int generation;
    private volatile HttpURLConnection connection;
    private boolean registered;
    private Status lastStatus;
    private int lastResultGeneration = -1;
    private long lastResultAt;
    private final Runnable check = this::launchCheck;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { schedule(); }
        @Override public void onLost(Network network) { schedule(); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) { schedule(); }
    };

    InternetMonitor(Context context, Listener listener) {
        connectivity = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    /** Start and stop on the main thread; results are delivered on the main thread. */
    void start() {
        start(false);
    }

    void start(boolean immediate) {
        if (closed || registered) return;
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build();
        try {
            connectivity.registerNetworkCallback(request, callback);
            registered = true;
        } catch (RuntimeException ignored) { }
        if (immediate) schedule(0); else schedule();
    }

    /** Trigger a new check, for example after an offline retry delay. */
    void requestCheck() { schedule(); }

    private void schedule() {
        schedule(1500);
    }

    private void schedule(long delay) {
        synchronized (lock) {
            if (closed) return;
            generation++;
            if (connection != null) connection.disconnect();
            main.removeCallbacks(check);
            main.postDelayed(check, delay);
        }
    }

    private void launchCheck() {
        final int current = generation;
        if (closed) return;
        try {
            worker.execute(() -> {
                Status available = probeNetworks(current);
                main.post(() -> {
                    if (!closed && current == generation) {
                        lastStatus = available;
                        lastResultGeneration = current;
                        lastResultAt = android.os.SystemClock.elapsedRealtime();
                        android.util.Log.d("InternetMonitor", "Internet status: " + available);
                        listener.onInternetChecked(available);
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    /** Only reuse a recent result for the same physical network generation. Called on main. */
    Status recentStatus() {
        return !closed && lastResultGeneration == generation
                && android.os.SystemClock.elapsedRealtime() - lastResultAt <= 10000 ? lastStatus : null;
    }

    private Status probeNetworks(int current) {
        boolean hasNetwork = false;
        boolean hasValidatedNetwork = false;
        try {
            for (Network network : connectivity.getAllNetworks()) {
                if (closed || current != generation) return Status.UNKNOWN;
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;
                hasNetwork = true;
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) hasValidatedNetwork = true;
                for (String endpoint : ENDPOINTS) {
                    if (probe(network, endpoint, current)) return Status.UP;
                    if (closed || current != generation) return Status.UNKNOWN;
                }
            }
        } catch (RuntimeException ignored) { return Status.UNKNOWN; }
        return hasNetwork && hasValidatedNetwork ? Status.UNKNOWN : Status.DOWN;
    }

    private boolean probe(Network network, String endpoint, int current) {
        HttpURLConnection candidate = null;
        try {
            candidate = (HttpURLConnection) network.openConnection(new URL(endpoint), java.net.Proxy.NO_PROXY);
            candidate.setConnectTimeout(3000);
            candidate.setReadTimeout(3000);
            candidate.setInstanceFollowRedirects(false);
            candidate.setUseCaches(false);
            synchronized (lock) {
                if (closed || current != generation) return false;
                connection = candidate;
            }
            return candidate.getResponseCode() == 204;
        } catch (SecurityException denied) {
            throw denied;
        } catch (java.io.IOException failure) {
            String message = failure.getMessage();
            if (message != null && (message.contains("EACCES") || message.contains("EPERM")
                    || message.toLowerCase(java.util.Locale.ROOT).contains("permission denied"))) {
                throw new SecurityException("Physical network access denied", failure);
            }
            return false;
        } catch (Exception ignored) {
            return false;
        } finally {
            synchronized (lock) {
                if (connection == candidate) connection = null;
            }
            if (candidate != null) candidate.disconnect();
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            generation++;
            main.removeCallbacks(check);
            if (connection != null) connection.disconnect();
        }
        if (registered) {
            try { connectivity.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) { }
            registered = false;
        }
        worker.shutdownNow();
    }
}
