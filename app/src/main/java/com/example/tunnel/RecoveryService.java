package com.example.tunnel;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Owns failover independently of the activity. */
public final class RecoveryService extends Service {
    static final String START = "com.example.tunnel.RECOVERY_START";
    static final String INITIAL = "com.example.tunnel.AUTO_START";
    static final String STOP = "com.example.tunnel.RECOVERY_STOP";
    static final String QUERY = "com.example.tunnel.RECOVERY_QUERY";
    static final String RECOVERING = "recovering";
    static final String RECONNECT_COUNT = "reconnect_count";
    static final String INITIAL_CONNECTION = "initial_connection";
    static final String STAGE = "recovery_stage";
    private volatile String stage = "Подготовка…";
    static final String SESSION_GENERATION = "session_generation";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private String token, profile;
    private long generation;
    private volatile boolean running;
    private volatile int workGeneration;
    private volatile boolean waitingNetwork;
    private boolean restoredNetwork, resumePending, diagnosisPending, searchExhausted;
    private boolean initialSearchPending;
    private int consecutiveDown;
    private int recoveryRetries;
    private int completedRounds;
    private volatile boolean hasSelectedProfiles;
    private String stoppingToken;
    private InternetMonitor internetMonitor;
    private boolean awg, retried, probing, initial, fullSearch, earlyRound, manual;
    private final Set<String> attempted = new HashSet<>();
    private final ArrayDeque<String> awgProbes = new ArrayDeque<>();
    private final Map<String, Long> rankedResults = new LinkedHashMap<>();
    private int pingThreshold;
    private String failedProfile;
    private final java.util.concurrent.ExecutorService background = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable checkStopped = this::checkAttemptStopped;
    private final Runnable timeout = () -> {
        if (!running || !sessionCurrent() || token == null) return;
        VpnJournal.add(this, probing ? "Время проверки сервера истекло" : "Время подключения к серверу истекло", true);
        publishStage("Завершение попытки подключения…");
        stopCurrentAttempt();
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!running || !sessionCurrent()) return;
            String received = intent.getStringExtra(TunnelService.ATTEMPT);
            if (stoppingToken != null && stoppingToken.equals(received)) {
                String stoppedState = intent.getStringExtra("state");
                if (!TunnelService.CONNECTING.equals(stoppedState) && !TunnelService.CONNECTED.equals(stoppedState)) {
                    attemptStopped();
                }
                return;
            }
            if (token == null || !token.equals(received)) return;
            String state = intent.getStringExtra("state");
            if (TunnelService.CONNECTING.equals(state)) return;
            main.removeCallbacks(timeout);
            token = null;
            if (probing) {
                probing = false;
                long delay = intent.getLongExtra(TunnelService.DELAY, -1);
                if (TunnelService.PROBED.equals(state) && delay >= 0) {
                    rankedResults.put(profile, delay);
                    VpnJournal.add(thisService(), "Проверка " + SubscriptionInfo.name(profile) + " (AWG): " + delay + " мс", true);
                } else VpnJournal.add(thisService(), "Проверка " + SubscriptionInfo.name(profile) + " (AWG): n/a", true);
                completeProbe(TunnelService.PROBED.equals(state) ? delay : -1);
            } else if (TunnelService.CONNECTED.equals(state)) {
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(310);
                android.content.SharedPreferences settings = getSharedPreferences("app_settings", MODE_PRIVATE);
                int count = settings.getInt(RECONNECT_COUNT, 0) + (initial ? 0 : 1);
                settings.edit().putInt(RECONNECT_COUNT, count).apply();
                try { startService(new Intent(thisService(), awg ? AwgTunnelService.class : TunnelService.class)
                        .setAction(TunnelService.RECOVERED).putExtra(TunnelService.ATTEMPT, received)); }
                catch (RuntimeException ignored) { }
                // Preserve the VPN's original connection details for the UI.
                Intent update = new Intent(intent).setPackage(getPackageName())
                        .putExtra(RECOVERING, false).putExtra(INITIAL_CONNECTION, initial).putExtra(RECONNECT_COUNT, count);
                sendBroadcast(update, TunnelService.STATE_PERMISSION);
                running = false;
                settings.edit().putBoolean("recovery_wanted", false).apply();
                stopForeground(false);
                stopSelf();
            } else if (TunnelService.NO_APPS.equals(state)) {
                finishRecovery(state);
            } else {
                VpnJournal.add(thisService(), "Попытка подключения не удалась", true);
                postContinuation(thisService()::next, 500);
            }
        }
    };
    private RecoveryService thisService() { return this; }
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(TunnelService.STATE), TunnelService.STATE_PERMISSION, main, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(TunnelService.STATE), TunnelService.STATE_PERMISSION, main);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && VpnTileService.STATUS_QUERY.equals(intent.getAction())) {
            String label = waitingNetwork ? AppLocale.context(this).getString(R.string.service_extra_0)
                    : initial ? AppLocale.context(this).getString(R.string.service_extra_2) : AppLocale.context(this).getString(R.string.service_extra_3);
            VpnTileService.reply(intent, generation, running ? (waitingNetwork ? VpnTileService.WAITING : VpnTileService.RECOVERING) : VpnTileService.INACTIVE,
                    running ? label : null);
            if (!running) stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (intent != null && QUERY.equals(intent.getAction())) {
            Intent response = new Intent(TunnelService.STATE).setPackage(getPackageName())
                    .putExtra("state", running ? TunnelService.CONNECTING : TunnelService.DISCONNECTED)
                    .putExtra(RECOVERING, running).putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, stage)
                    .putExtra(SESSION_GENERATION, generation).putExtra(TunnelService.ATTEMPT, token)
                    .putExtra(TunnelService.PROFILE, profile).putExtra("awg", awg);
            sendBroadcast(response, TunnelService.STATE_PERMISSION);
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (intent == null || STOP.equals(intent.getAction())) {
            running = false;
            workGeneration++;
            InternetAlert.clear(this);
            if (intent != null && intent.getBooleanExtra("user_stop", false)) {
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                prefs.edit().putLong(SESSION_GENERATION, prefs.getLong(SESSION_GENERATION, 0) + 1)
                        .putBoolean("vpn_session_active", false).commit();
            }
            getSharedPreferences("app_settings", MODE_PRIVATE).edit().putBoolean("recovery_wanted", false).apply();
            ConnectionToastReceiver.send(this, "", getSharedPreferences("app_settings", MODE_PRIVATE).getLong(SESSION_GENERATION, 0));
            main.removeCallbacksAndMessages(null);
            stopAttempt();
            token = null;
            sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                    .putExtra("state", TunnelService.DISCONNECTED).putExtra(RECOVERING, false), TunnelService.STATE_PERMISSION);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if ((!START.equals(intent.getAction()) && !INITIAL.equals(intent.getAction())) || running) return START_NOT_STICKY;
        initial = INITIAL.equals(intent.getAction());
        startForeground(1, notification());
        generation = intent.getLongExtra(SESSION_GENERATION, 0);
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        manual = !prefs.getBoolean("auto_connect", false);
        if (!recoveryEnabled(prefs)
                || prefs.getLong(SESSION_GENERATION, 0) != generation || android.net.VpnService.prepare(this) != null) {
            sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                    .putExtra("state", TunnelService.DISCONNECTED).putExtra(RECOVERING, false), TunnelService.STATE_PERMISSION);
            stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }
        running = true;
        VpnJournal.add(this, initial ? "Автоматическое подключение VPN" : "Начато восстановление VPN", false);
        prefs.edit().putBoolean("recovery_wanted", true).apply();
        failedProfile = intent.getStringExtra(TunnelService.PROFILE);
        publishStage("Подготовка…");
        diagnosisPending = true;
        initialSearchPending = initial;
        internetMonitor = new InternetMonitor(this, this::onInternetChecked);
        internetMonitor.start(initial);
        if (!initial) {
            rankCandidates(false);
            String previous = intent.getStringExtra(TunnelService.INTERNET_DIAGNOSIS);
            if (previous != null) {
                try { onInternetChecked(InternetMonitor.Status.valueOf(previous)); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return START_NOT_STICKY;
    }
    private void onInternetChecked(InternetMonitor.Status status) {
        if (!running || !sessionCurrent()) return;
        if (status == InternetMonitor.Status.DOWN && !waitingNetwork && ++consecutiveDown < 2) {
            internetMonitor.requestCheck();
            return;
        }
        if (status != InternetMonitor.Status.DOWN) consecutiveDown = 0;
        diagnosisPending = false;
        if (status == InternetMonitor.Status.DOWN) resumePending = false;
        if (status == InternetMonitor.Status.DOWN && !waitingNetwork) {
            VpnJournal.add(this, "Интернет недоступен; ожидание сети", false);
            waitingNetwork = true;
            if (!initial) InternetAlert.show(this);
            resumePending = false;
            workGeneration++;
            main.removeCallbacks(timeout);
            queue.clear(); awgProbes.clear(); rankedResults.clear();
            if (token != null) stopCurrentAttempt();
            publishStage("Ожидание интернета…");
        } else if (status == InternetMonitor.Status.UP && waitingNetwork) {
            resumePending = true;
            if (stoppingToken == null) resumeAfterNetwork();
        } else if (!waitingNetwork && initialSearchPending) {
            initialSearchPending = false;
            rankCandidates(false);
        } else if (!waitingNetwork && searchExhausted) {
            searchExhausted = false;
            next();
        }
    }
    private void resumeAfterNetwork() {
        if (!running || !waitingNetwork || !resumePending || stoppingToken != null || !sessionCurrent()) return;
        waitingNetwork = false;
        InternetAlert.clear(this);
        initialSearchPending = false;
        VpnJournal.add(this, "Интернет восстановлен", false);
        resumePending = false;
        restoredNetwork = true;
        recoveryRetries = 0;
        if (!manual) failedProfile = null;
        attempted.clear(); retried = false; fullSearch = false; earlyRound = false;
        publishStage("Проверка серверов…");
        rankCandidates(false);
    }
    private void publishStage(String value) {
        android.util.Log.d("VpnRecovery", value.startsWith("Подключение:") ? "Starting server connection" : "Stage: " + value);
        VpnJournal.add(this, value, true);
        stage = value;
        main.post(() -> {
            if (!running || !value.equals(stage)) return;
            ConnectionToastReceiver.send(this, "Ожидание интернета…".equals(value) ? "" : initial ? "Подключение…" : "Переподключение…", generation);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1, notification());
            sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                    .putExtra("state", TunnelService.CONNECTING).putExtra(RECOVERING, true)
                    .putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, value)
                    .putExtra(SESSION_GENERATION, generation).putExtra(TunnelService.ATTEMPT, token)
                    .putExtra(TunnelService.PROFILE, profile).putExtra("awg", awg), TunnelService.STATE_PERMISSION);
        });
    }
    private boolean recoveryEnabled(android.content.SharedPreferences prefs) {
        return manual ? !initial && !prefs.getBoolean("auto_connect", false) && prefs.getBoolean("manual_reconnect", false)
                : prefs.getBoolean("auto_connect", false) && (initial || prefs.getBoolean("auto_failover", true));
    }
    private void rankCandidates(boolean refresh) {
        if (manual) { prepareManualCandidate(refresh); return; }
        final int work = ++workGeneration;
        final Set<String> attemptedSnapshot = new HashSet<>(attempted);
        final boolean retriedSnapshot = retried, fullSearchSnapshot = fullSearch;
        background.execute(() -> {
            LocalDatabase db = new LocalDatabase(this);
            ArrayList<String> items = new ArrayList<>(), groups = new ArrayList<>(), owners = new ArrayList<>();
            try {
                db.loadServers(items, groups, owners);
                Set<String> selected = db.selected();
                hasSelectedProfiles = !Collections.disjoint(items, selected);
                LinkedHashSet<String> subscriptions = new LinkedHashSet<>();
                for (int i = 0; i < items.size(); i++) if (selected.contains(items.get(i))) subscriptions.add(owners.get(i));
                if (refresh && running) {
                    if (!workCurrent(work)) return;
                    publishStageForWork(work, "Обновление движков…");
                    if (BackgroundRecoverySupport.checkEngines(this, () -> workCurrent(work))) {
                        main.post(() -> { if (workCurrent(work)) { running = false; stopForeground(true); stopSelf(); } });
                        return;
                    }
                    if (!workCurrent(work)) return;
                    if(initial || getSharedPreferences("app_settings",MODE_PRIVATE).getBoolean("refresh_on_reconnect",true)){
                        publishStageForWork(work, "Обновление подписок…");
                        BackgroundRecoverySupport.refresh(this, subscriptions, () -> workCurrent(work));
                    }
                    db.loadServers(items, groups, owners);
                    selected = db.selected();
                }
                ArrayDeque<String> ordered = new ArrayDeque<>();
                for (String item : selected) {
                    if (items.contains(item) && (retriedSnapshot || !item.equals(failedProfile)) && !attemptedSnapshot.contains(item)) ordered.add(item);
                }
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                int threshold;
                try { threshold = Math.max(0, Integer.parseInt(prefs.getString("max_ping", ""))); }
                catch (NumberFormatException e) { threshold = 0; }
                if (fullSearchSnapshot) threshold = 0;
                final int finalThreshold = threshold;
                main.post(() -> {
                    if (!workCurrent(work)) return;
                    publishStage("Проверка серверов…");
                    rankedResults.clear();
                    awgProbes.clear(); awgProbes.addAll(ordered);
                    pingThreshold = finalThreshold;
                    probeNext();
                });
            } catch (Exception | LinkageError failure) {
                main.post(() -> {
                    if (!workCurrent(work)) return;
                    VpnJournal.add(this, "Ошибка подготовки VPN: " + failure.getClass().getSimpleName(), false);
                    if (!recoveryLimitReached()) scheduleSearchRetry();
                });
            } finally { db.close(); }
        });
    }
    private void prepareManualCandidate(boolean refresh) {
        final int work = ++workGeneration;
        background.execute(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            String original = prefs.getString(MainActivity.MANUAL_PROFILE, null);
            String candidate = original;
            if (original == null) { main.post(() -> { if (workCurrent(work)) finishRecovery("Не удалось подключиться"); }); return; }
            if (refresh) {
                publishStageForWork(work, "Обновление движков…");
                if (BackgroundRecoverySupport.checkEngines(this, () -> workCurrent(work))) {
                    main.post(() -> { if (workCurrent(work)) { running = false; stopForeground(true); stopSelf(); } });
                    return;
                }
                if (!workCurrent(work)) return;
                String owner = prefs.getString(MainActivity.MANUAL_OWNER, null);
                if (owner != null && !owner.startsWith("local:")) {
                    publishStageForWork(work, "Обновление подписок…");
                    java.util.Map<String, ArrayList<String>> result = BackgroundRecoverySupport.refresh(this, Collections.singleton(owner), () -> workCurrent(work));
                    ArrayList<String> updated = result.get(owner);
                    if (updated != null && !updated.contains(original)) {
                        String match = null; int count = 0;
                        for (String item : updated) if (AwgProfile.isAwg(original) == AwgProfile.isAwg(item)
                                && SubscriptionInfo.name(original).equals(SubscriptionInfo.name(item))) { match = item; count++; }
                        if (count == 1) candidate = match;
                    }
                }
            }
            final String selected = candidate;
            main.post(() -> {
                if (!workCurrent(work) || !original.equals(prefs.getString(MainActivity.MANUAL_PROFILE, null))) return;
                if (!original.equals(selected)) prefs.edit().putString(MainActivity.MANUAL_PROFILE, selected).apply();
                hasSelectedProfiles = true;
                publishStage("Проверка серверов…");
                rankedResults.clear(); queue.clear(); awgProbes.clear();
                awgProbes.add(selected); pingThreshold = 0;
                probeNext();
            });
        });
    }
    private boolean workCurrent(int work) {
        return running && !waitingNetwork && work == workGeneration && sessionCurrent();
    }
    private void publishStageForWork(int work, String value) {
        main.post(() -> { if (workCurrent(work)) publishStage(value); });
    }
    private void postContinuation(Runnable continuation, long delay) {
        int work = workGeneration;
        main.postDelayed(() -> { if (workCurrent(work)) continuation.run(); }, delay);
    }
    private void completeProbe(long delay) {
        if (delay >= 0) rankedResults.put(profile, delay);
        if (delay >= 0 && pingThreshold > 0 && delay <= pingThreshold) {
            rankedResults.remove(profile);
            queue.clear(); queue.add(profile);
            earlyRound = true;
            next();
        } else postContinuation(this::probeNext, 0);
    }
    private void probeNext() {
        if (waitingNetwork || token != null) return;
        if (!running || !sessionCurrent()) { stopSelf(); return; }
        profile = awgProbes.poll();
        if (profile == null) {
            ArrayList<Map.Entry<String, Long>> ranked = new ArrayList<>(rankedResults.entrySet());
            ranked.sort(Comparator.comparingLong(Map.Entry::getValue));
            queue.clear();
            earlyRound = false;
            for (Map.Entry<String, Long> entry : ranked) if (!attempted.contains(entry.getKey())) queue.add(entry.getKey());
            next(); return;
        }
        if (!AwgProfile.isAwg(profile)) {
            final String checked = profile;
            final int work = workGeneration;
            final int timeoutMs = XrayPing.timeout(getSharedPreferences("app_settings", MODE_PRIVATE));
            background.execute(() -> {
                long measured;
                try { measured = XrayPing.measure(checked, timeoutMs); }
                catch (Exception | LinkageError e) { measured = -1; }
                final long delay = measured;
                main.post(() -> {
                    if (!workCurrent(work) || !checked.equals(profile)) return;
                    VpnJournal.add(this, "Проверка " + SubscriptionInfo.name(checked) + ": " + (delay >= 0 ? delay + " мс" : "n/a"), true);
                    completeProbe(delay);
                });
            });
            return;
        }
        probing = true; awg = true; token = UUID.randomUUID().toString();
        sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                .putExtra("state", TunnelService.CONNECTING).putExtra(TunnelService.ATTEMPT, token)
                .putExtra(TunnelService.PROFILE, profile).putExtra("awg", true)
                .putExtra(RECOVERING, true).putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, stage)
                .putExtra(SESSION_GENERATION, generation), TunnelService.STATE_PERMISSION);
        Intent start = new Intent(this, AwgTunnelService.class).setAction(TunnelService.START)
                .putExtra(TunnelService.PROFILE, profile).putExtra(TunnelService.ATTEMPT, token)
                .putExtra(TunnelService.VERIFY, true).putExtra(TunnelService.PROBE, true).putExtra(RECOVERING, true).putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, stage).putExtra(SESSION_GENERATION, generation);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
            main.postDelayed(timeout, XrayPing.timeout(getSharedPreferences("app_settings", MODE_PRIVATE)));
        } catch (RuntimeException e) { token = null; probing = false; postContinuation(this::probeNext, 500); }
    }
    private boolean sessionCurrent() {
        return getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS)
                .getLong(SESSION_GENERATION, 0) == generation;
    }
    private void next() {
        if (waitingNetwork || token != null) return;
        if (!running || !sessionCurrent()) { stopSelf(); return; }
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        if (!recoveryEnabled(prefs)) {
            finishRecovery(TunnelService.DISCONNECTED); return;
        }
        if (android.net.VpnService.prepare(this) != null) {
            finishRecovery("Требуется разрешение VPN"); return;
        }
        profile = queue.poll();
        if (profile == null) {
            if (earlyRound) { earlyRound = false; probeNext(); return; }
            if (diagnosisPending) { searchExhausted = true; return; }
            if (hasSelectedProfiles && recoveryLimitReached()) return;
            if (!retried && !restoredNetwork) { retried = true; fullSearch = false; earlyRound = false; attempted.clear(); rankCandidates(true); return; }
            if (hasSelectedProfiles) {
                scheduleSearchRetry();
                return;
            }
            VpnJournal.add(this, "Не удалось подключиться: доступные серверы закончились", false);
            finishRecovery("Не удалось подключиться"); return;
        }
        publishStage("Подключение: " + SubscriptionInfo.name(profile));
        token = UUID.randomUUID().toString();
        attempted.add(profile);
        awg = AwgProfile.isAwg(profile);
        Intent start = new Intent(this, awg ? AwgTunnelService.class : TunnelService.class)
                .setAction(TunnelService.START).putExtra(TunnelService.PROFILE, profile)
                .putExtra(TunnelService.ATTEMPT, token).putExtra(TunnelService.VERIFY, true)
                .putExtra(RECOVERING, true).putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, stage).putExtra(SESSION_GENERATION, generation);
        sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                .putExtra("state", TunnelService.CONNECTING).putExtra(TunnelService.ATTEMPT, token)
                .putExtra(TunnelService.PROFILE, profile).putExtra("awg", awg)
                .putExtra(RECOVERING, true).putExtra(INITIAL_CONNECTION, initial).putExtra(STAGE, stage)
                .putExtra(SESSION_GENERATION, generation), TunnelService.STATE_PERMISSION);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
            main.postDelayed(timeout, 45000);
        } catch (RuntimeException e) { token = null; postContinuation(this::next, 500); }
    }
    private boolean recoveryLimitReached() {
        if (initial) return false;
        completedRounds++;
        VpnJournal.add(this, "Завершён круг восстановления: " + completedRounds, false);
        int limit = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("recovery_round_limit", 0);
        if (limit <= 0 || completedRounds < limit) return false;
        VpnJournal.add(this, "Достигнут предел кругов восстановления", false);
        finishRecovery("Достигнут предел попыток");
        return true;
    }
    private void scheduleSearchRetry() {
        recoveryRetries = Math.min(recoveryRetries + 1, 6);
        publishStage("Повторная проверка серверов…");
        postContinuation(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
            if (!recoveryEnabled(prefs)) {
                finishRecovery(TunnelService.DISCONNECTED);
                return;
            }
            attempted.clear();
            if (!manual) failedProfile = null;
            fullSearch = false;
            earlyRound = false;
            rankCandidates(false);
        }, recoveryRetries * 10000L);
    }
    private void finishRecovery(String state) {
        running = false;
        getSharedPreferences("app_settings", MODE_PRIVATE).edit().putBoolean("recovery_wanted", false)
                .putBoolean("vpn_session_active", false).apply();
        sendBroadcast(new Intent(TunnelService.STATE).setPackage(getPackageName())
                .putExtra("state", state).putExtra(RECOVERING, false)
                .putExtra(SESSION_GENERATION, generation), TunnelService.STATE_PERMISSION);
        stopForeground(true);
        stopSelf();
    }
    private void stopCurrentAttempt() {
        stoppingToken = token;
        stopAttempt();
        main.removeCallbacks(checkStopped);
        main.postDelayed(checkStopped, 10000);
    }
    private void checkAttemptStopped() {
        if (!running || !sessionCurrent() || stoppingToken == null) return;
        ActivityManager manager = getSystemService(ActivityManager.class);
        Class<?> engine = awg ? AwgTunnelService.class : TunnelService.class;
        if (manager != null && manager.getRunningServices(Integer.MAX_VALUE).stream()
                .noneMatch(service -> engine.getName().equals(service.service.getClassName()))) {
            attemptStopped();
            return;
        }
        stopAttempt();
        main.postDelayed(checkStopped, 10000);
    }
    private void attemptStopped() {
        main.removeCallbacks(checkStopped);
        boolean wasProbing = probing;
        stoppingToken = null;
        token = null;
        probing = false;
        if (waitingNetwork) {
            if (resumePending) resumeAfterNetwork();
        } else {
            postContinuation(wasProbing ? this::probeNext : this::next, 500);
        }
    }
    private void stopAttempt() {
        if (token == null) return;
        try { startService(new Intent(this, awg ? AwgTunnelService.class : TunnelService.class)
                .setAction(TunnelService.STOP).putExtra(TunnelService.ATTEMPT, token)); }
        catch (RuntimeException ignored) { }
    }
    private Notification notification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("tunnel_recovery", AppLocale.context(this).getString(R.string.service_extra_14), NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, "tunnel_recovery");
        } else b = new Notification.Builder(this);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, RecoveryService.class).setAction(STOP).putExtra("user_stop", true),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return b.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle(waitingNetwork ? AppLocale.context(this).getString(R.string.service_extra_0) : initial ? AppLocale.context(this).getString(R.string.service_extra_5) : AppLocale.context(this).getString(R.string.service_extra_6))
                .setContentText(ServiceText.translate(this, stage)).setContentIntent(open).setOngoing(true).addAction(0, AppLocale.context(this).getString(R.string.service_extra_10), stop).build();
    }
    @Override public void onDestroy() {
        running = false; workGeneration++; if (internetMonitor != null) internetMonitor.close(); main.removeCallbacksAndMessages(null); background.shutdownNow(); unregisterReceiver(receiver); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

