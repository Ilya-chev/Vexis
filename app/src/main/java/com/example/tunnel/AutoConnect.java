package com.example.tunnel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Coordinates one automatic connection from the activity's main thread. */
final class AutoConnect {
    private final MainActivity activity;
    private final Set<String> subscriptions;
    private final ArrayDeque<String> candidates = new ArrayDeque<>();
    private final ArrayDeque<String> probes = new ArrayDeque<>();
    private final Map<String, Long> delays = new LinkedHashMap<>();
    private final Set<String> attempted = new LinkedHashSet<>();
    private boolean earlyResult;
    private boolean retry;
    private String status;
    private String attempt;
    private String probing;
    private int pingGeneration;

    AutoConnect(MainActivity activity, boolean retry) {
        this.activity = activity;
        this.retry = retry;
        subscriptions = activity.selectedSubscriptions();
    }
    void skip(String profile) {
        if (profile != null) attempted.add(profile);
    }

    String status() { return status; }

    private boolean current() { return activity.autoConnect == this; }

    void start() {
        if (subscriptions.isEmpty()) {
            finish("Выберите серверы");
            return;
        }
        setStatus("Обновление подписок…");
        ArrayList<CompletableFuture<Void>> refreshes = new ArrayList<>();
        for (String subscription : subscriptions) {
            if (retry) {
                refreshes.add(activity.refreshSubscription(subscription));
            }
        }
        CompletableFuture.allOf(refreshes.toArray(new CompletableFuture<?>[0]))
                .thenRun(() -> { if (current()) testServers(); });
    }

    private void testServers() {
        setStatus("Проверка серверов…");
        pingGeneration = activity.pingGeneration;
        delays.clear();
        probes.clear();
        for (String profile : activity.selected) {
            if (activity.items.contains(profile) && !attempted.contains(profile)) probes.add(profile);
        }
        earlyResult = false;
        probeNext();
    }

    private void probeNext() {
        if (!current()) return;
        probing = probes.poll();
        if (probing == null) {
            ArrayList<Map.Entry<String, Long>> ranked = new ArrayList<>(delays.entrySet());
            ranked.sort(Comparator.comparingLong(Map.Entry::getValue));
            for (Map.Entry<String, Long> result : ranked) candidates.add(result.getKey());
            tryNext();
            return;
        }
        showPing("Проверка");
        if (AwgProfile.isAwg(probing)) {
            attempt = activity.startProfile(probing, true, true);
            if (attempt == null) completeProbe(-1);
        } else {
            String checked = probing;
            int timeout = XrayPing.timeout(activity.settings);
            CompletableFuture.supplyAsync(() -> {
                try { return XrayPing.measure(checked, timeout); }
                catch (Exception | LinkageError e) { return -1L; }
            }).thenAccept(delay -> activity.runOnUiThread(() -> {
                if (current() && checked.equals(probing)) completeProbe(delay);
            }));
        }
    }

    private void completeProbe(long delay) {
        String checked = probing;
        if (delay >= 0) delays.put(checked, delay);
        showPing(delay >= 0 ? delay + " мс" : "n/a");
        probing = null;
        if (delay >= 0 && activity.maxPing() > 0 && delay <= activity.maxPing()) {
            delays.remove(checked);
            candidates.add(checked);
            earlyResult = true;
            tryNext();
        } else probeNext();
    }

    private void showPing(String value) {
        if (pingGeneration == activity.pingGeneration) {
            activity.latency.put(probing, value);
            if (activity.page < 2) activity.render();
        }
    }

    private void tryNext() {
        while (!candidates.isEmpty()) {
            String profile = candidates.removeFirst();
            if (!activity.items.contains(profile) || !activity.selected.contains(profile)) continue;
            attempted.add(profile);
            setStatus("Подключение · " + activity.profileTitle(profile));
            attempt = activity.startProfile(profile, true, false);
            if (attempt != null) return;
        }
        if (earlyResult) {
            earlyResult = false;
            probeNext();
            return;
        }
        if (retry) {
            finish("Не удалось подключиться");
            return;
        }
        retry = true;
        attempted.clear();
        setStatus("Проверка движков…");
        EngineUpdates.checkBoth(activity, () -> {
            if (current()) start();
        }, () -> setStatus("Установка обновления…"), this::current);
    }

    void onState(String token, String state, long delay) {
        if (attempt == null || !attempt.equals(token) || TunnelService.CONNECTING.equals(state)) return;
        attempt = null;
        if (TunnelService.CONNECTED.equals(state) || TunnelService.DISCONNECTED.equals(state)) finish(null);
        else if (probing != null) {
            completeProbe(TunnelService.PROBED.equals(state) ? delay : -1);
        } else if (TunnelService.NO_APPS.equals(state)) finish(state);
        else tryNext();
    }

    void cancel() {
        finish(null);
        activity.settings.edit().remove(EngineUpdates.RETRY_AFTER_UPDATE).apply();
        activity.cancelAutoPing();
        if (probing != null && "Проверка".equals(activity.latency.get(probing))) {
            activity.latency.remove(probing);
            if (activity.page < 2) activity.render();
        }
        if (attempt != null) activity.stopProfile(attempt);
    }

    private void setStatus(String value) {
        status = value;
        activity.updateConnectionHeader();
    }

    private void finish(String message) {
        activity.autoConnect = null;
        activity.connectionMessage = message;
        activity.updateConnectionHeader();
    }
}
