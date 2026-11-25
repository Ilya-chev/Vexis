package com.example.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public class TunnelService extends VpnService {
    static final String START = "com.example.tunnel.VPN_START";
    static final String STOP = "com.example.tunnel.VPN_STOP";
    static final String QUERY = "com.example.tunnel.VPN_QUERY";
    static final String RECOVERED = "com.example.tunnel.VPN_RECOVERED";
    static final String MANUAL_RESUME = "com.example.tunnel.MANUAL_RETRY_RESUME";
    static final String MANUAL_FINAL = "manual_retry_final";
    static final String STATE_PERMISSION = "com.example.tunnel.VPN_STATE_PERMISSION";
    static final String STATE = "com.example.tunnel.VPN_STATE";
    static final String PROFILE = "profile";
    static final String ATTEMPT = "attempt";
    static final String VERIFY = "verify";
    static final String PROBE = "probe";
    static final String PROBED = "probe_result";
    static final String USER_PING = "user_ping";
    static final String USER_PING_STATE = "com.example.tunnel.USER_PING_STATE";
    static final String DELAY = "delay";
    static final String STARTED_AT = "started_at";
    static final String COUNTRY = "country";
    static final String COUNTRY_DONE = "country_done";
    static final String EXIT_IP = "exit_ip";
    static final String TIME_ZONE = "exit_time_zone";
    static final String LIVE_DELAY = "live_delay";
    static final String DELAY_DONE = "delay_done";
    static final String INTERNET_DIAGNOSIS = "internet_diagnosis";
    static final String WAITING_INTERNET = "waiting_internet";
    static final String CONNECTING = "Подключение…";
    static final String CONNECTED = "VPN запущен";
    static final String DISCONNECTED = "Не подключено";
    static final String LOST = "Соединение потеряно";
    static final String NO_APPS = "Нет выбранных установленных приложений";
    private static final String CHANNEL = "tunnel_vpn";
    private static volatile String state = DISCONNECTED;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean stopRequested;
    private String attempt;
    private boolean recoveryAttempt;
    private boolean userPing;
    private Intent pendingAfterUserPing;
    private boolean stoppingUserPing;
    private String recoveryStage;
    private boolean initialAttempt;
    private boolean manualRetried;
    private long sessionGeneration;
    private volatile String currentProfile;
    private String finalState;
    private long finalDelay;
    private long startedAt;
    private boolean connectionReady;
    private volatile String country = "";
    private volatile boolean countryDone;
    private volatile String exitIp = "";
    private volatile String exitTimeZone = "";
    private volatile long liveDelay = -1;
    private volatile boolean delayDone;
    private volatile int geoPort;
    private String geoPassword;
    private final java.util.concurrent.atomic.AtomicBoolean delayRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean healthFailed;
    private long firstHealthFailure;
    private int healthFailures;
    private int inconclusiveNetworkChecks;
    private final java.util.concurrent.atomic.AtomicInteger diagnosisGeneration = new java.util.concurrent.atomic.AtomicInteger();
    private boolean diagnosisActive;
    private boolean waitingInternet;
    private int physicalDownCount;
    private boolean physicalCheckPending;
    private boolean idleReceiverRegistered;
    private final Runnable delayTask = this::scheduleDelay;
    private final BroadcastReceiver idleReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!deviceIdle() && !stopRequested && CONNECTED.equals(state)) {
                android.util.Log.d("VpnHealth", "Device idle ended; checking tunnel");
                healthFailures = 0; firstHealthFailure = 0;
                if (internetDiagnosis != null && !physicalCheckPending) {
                    physicalCheckPending = true; internetDiagnosis.requestCheck();
                }
                if (!waitingInternet) restartHealthChecks();
            }
        }
    };
    private InternetMonitor internetDiagnosis;
    private InternetMonitor initialInternet;
    private int initialDownCount;
    private int internetDiagnosisGeneration;
    private boolean awgHasInternetRoute;
    private final ExecutorService monitor = Executors.newSingleThreadExecutor();
    private final ExecutorService retryWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService locationWorker = Executors.newSingleThreadExecutor();
    private ParcelFileDescriptor tunnel;
    private CoreController core;
    private int awgHandle = -1;
    private AwgRoutingBackend awgRouting;
    private long awgStartedAt;

    private static String remoteState = DISCONNECTED;
    static void receiveAwgState(String value) { remoteState = value; }
    static boolean isActive() { return isActive(state) || isActive(remoteState); }
    static boolean isActive(String value) { return CONNECTED.equals(value) || CONNECTING.equals(value); }

    private void publish(String value, long delay) {
        state = value;
        if (userPing) {
            sendBroadcast(new Intent(USER_PING_STATE).setPackage(getPackageName()).putExtra("state", value)
                    .putExtra(ATTEMPT, attempt).putExtra(DELAY, delay), STATE_PERMISSION);
            return;
        }
        if (CONNECTED.equals(value) && countryDone && delayDone && !recoveryAttempt && !connectionReady) {
            startedAt = SystemClock.elapsedRealtime();
            connectionReady = true;
        }
        if (CONNECTED.equals(value) && countryDone && delayDone && !recoveryAttempt) {
            SharedPreferences settings = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
            if (settings.getBoolean("auto_connect", false) && settings.getLong("auto_session_started", 0) == 0)
                settings.edit().putLong("auto_session_started", startedAt).apply();
        }
        if (CONNECTING.equals(value) && !waitingInternet) ConnectionToastReceiver.send(this, recoveryAttempt ? (initialAttempt ? AppLocale.context(this).getString(R.string.service_extra_2) : AppLocale.context(this).getString(R.string.service_extra_3)) : AppLocale.context(this).getString(R.string.service_extra_2), sessionGeneration);
        else if (CONNECTING.equals(value)) ConnectionToastReceiver.send(this, "", sessionGeneration);
        else if (CONNECTED.equals(value) && countryDone && delayDone && !waitingInternet) ConnectionToastReceiver.send(this, AppLocale.context(this).getString(R.string.service_extra_1), sessionGeneration);
        else if (LOST.equals(value)) ConnectionToastReceiver.send(this, AppLocale.context(this).getString(R.string.service_extra_3), sessionGeneration);
        else if (!isActive(value) && !recoveryAttempt) ConnectionToastReceiver.send(this, "", sessionGeneration);
        if (isActive(value) && !stopRequested) {
            try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1, notification()); }
            catch (RuntimeException ignored) { }
        }
        sendBroadcast(new Intent(STATE).setPackage(getPackageName())
                .putExtra("state", value).putExtra("awg", this instanceof AwgTunnelService)
                .putExtra(RecoveryService.RECOVERING, recoveryAttempt)
                .putExtra(RecoveryService.STAGE, recoveryStage)
                .putExtra(RecoveryService.SESSION_GENERATION, sessionGeneration)
                .putExtra(ATTEMPT, attempt).putExtra(DELAY, delay)
                .putExtra(STARTED_AT, startedAt).putExtra(COUNTRY, country).putExtra(LIVE_DELAY, liveDelay)
                .putExtra(WAITING_INTERNET, waitingInternet).putExtra(DELAY_DONE, delayDone)
                .putExtra(COUNTRY_DONE, countryDone)
                .putExtra(EXIT_IP, exitIp).putExtra(TIME_ZONE, exitTimeZone)
                .putExtra(PROFILE, currentProfile), STATE_PERMISSION);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23) {
            registerReceiver(idleReceiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
            idleReceiverRegistered = true;
        }
    }

    private boolean deviceIdle() {
        if (Build.VERSION.SDK_INT < 23) return false;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isDeviceIdleMode();
    }

    private void restartHealthChecks() {
        main.removeCallbacks(delayTask);
        if (!stopRequested && !waitingInternet && !deviceIdle() && CONNECTED.equals(state)) main.post(delayTask);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            requestStop();
            return START_NOT_STICKY;
        }
        if (VpnTileService.STATUS_QUERY.equals(intent.getAction())) {
            int phase = VpnTileService.INACTIVE;
            String label = null;
            if (!stopRequested && CONNECTED.equals(state)) {
                phase = waitingInternet ? VpnTileService.WAITING_CONNECTED : VpnTileService.CONNECTED;
                label = waitingInternet ? AppLocale.context(this).getString(R.string.service_extra_0)
                        : currentProfile == null ? AppLocale.context(this).getString(R.string.service_extra_1) : SubscriptionInfo.name(currentProfile);
            } else if (!stopRequested && CONNECTING.equals(state)) {
                phase = VpnTileService.CONNECTING;
                label = AppLocale.context(this).getString(R.string.service_extra_2);
            }
            VpnTileService.reply(intent, sessionGeneration, phase, label);
            if (attempt == null) stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        // A real connection takes priority over an in-flight user latency check.
        if (START.equals(intent.getAction()) && !intent.getBooleanExtra(USER_PING, false)
                && userPing && (isActive(state) || stoppingUserPing)) {
            pendingAfterUserPing = new Intent(intent);
            if (!stoppingUserPing) {
                stoppingUserPing = true;
                stopRequested = true;
                worker.execute(() -> {
                    closeTunnel();
                    main.post(() -> {
                        sendBroadcast(new Intent(USER_PING_STATE).setPackage(getPackageName())
                                .putExtra("state", DISCONNECTED).putExtra(ATTEMPT, attempt)
                                .putExtra(DELAY, -1), STATE_PERMISSION);
                        Intent pending = pendingAfterUserPing;
                        pendingAfterUserPing = null;
                        stoppingUserPing = false;
                        userPing = false;
                        state = DISCONNECTED;
                        attempt = null;
                        stopRequested = false;
                        if (pending != null) onStartCommand(pending, 0, startId);
                        else { stopForeground(true); stopSelf(); }
                    });
                });
            }
            return START_NOT_STICKY;
        }
        if (STOP.equals(intent.getAction()) && stoppingUserPing
                && (intent.getStringExtra(ATTEMPT) == null || intent.getBooleanExtra("user_stop", false))) {
            pendingAfterUserPing = null;
        }
        if (MANUAL_RESUME.equals(intent.getAction())) {
            String token=intent.getStringExtra(ATTEMPT);
            if (attempt!=null && attempt.equals(token) && !stopRequested && CONNECTING.equals(state)) {
                String profile=intent.getStringExtra(PROFILE);
                currentProfile=profile;manualRetried=true;recoveryStage="Повторное подключение…";
                publish(CONNECTING,-1);
                worker.execute(()->startTunnel(profile,true,false));
                return START_NOT_STICKY;
            }
            if(attempt!=null)return START_NOT_STICKY;
            intent.setAction(START).putExtra(MANUAL_FINAL,true);
        }
        if (RECOVERED.equals(intent.getAction())) {
            if (attempt != null && attempt.equals(intent.getStringExtra(ATTEMPT))) {
                recoveryAttempt = false;
                if (CONNECTED.equals(state)) publish(CONNECTED, -1);
            }
        } else if (QUERY.equals(intent.getAction())) {
            publish(state, -1);
            if (!isActive(state)) stopSelf(startId);
        } else if (STOP.equals(intent.getAction())) {
            if (intent.getBooleanExtra("user_stop", false)) {
                SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
                prefs.edit().putLong(RecoveryService.SESSION_GENERATION, prefs.getLong(RecoveryService.SESSION_GENERATION, 0) + 1)
                        .putBoolean("recovery_wanted", false).putBoolean("vpn_session_active", false).commit();
            }
            String token = intent.getStringExtra(ATTEMPT);
            if (token == null || token.equals(attempt)) requestStop();
            else if (attempt == null) stopSelf(startId);
        } else if (START.equals(intent.getAction()) && !isActive(state) && !stopRequested) {
            attempt = intent.getStringExtra(ATTEMPT);
            userPing = intent.getBooleanExtra(USER_PING, false);
            recoveryAttempt = intent.getBooleanExtra(RecoveryService.RECOVERING, false);
            manualRetried = intent.getBooleanExtra(MANUAL_FINAL, false);
            recoveryStage = intent.getStringExtra(RecoveryService.STAGE);
            initialAttempt = intent.getBooleanExtra(RecoveryService.INITIAL_CONNECTION, false);
            sessionGeneration = intent.getLongExtra(RecoveryService.SESSION_GENERATION, 0);
            startForeground(1, notification());
            long currentGeneration = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS)
                    .getLong(RecoveryService.SESSION_GENERATION, 0);
            if (sessionGeneration != currentGeneration) {
                stopForeground(true);
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            VpnJournal.add(this, "Начало подключения VPN", false);
            publish(CONNECTING, -1);
            String profile = intent.getStringExtra(PROFILE);
            currentProfile = profile;
            boolean verify = intent.getBooleanExtra(VERIFY, false);
            boolean probe = intent.getBooleanExtra(PROBE, false);
            if (!recoveryAttempt && !probe) {
                startWhenInternetAvailable(()->worker.execute(() -> startTunnel(profile, verify, false)));
            } else worker.execute(() -> startTunnel(profile, verify, probe));
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Notification.Builder notification;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, AppLocale.context(this).getString(R.string.service_extra_4), NotificationManager.IMPORTANCE_LOW));
            notification = new Notification.Builder(this, CHANNEL);
        } else {
            notification = new Notification.Builder(this);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, getClass()).setAction(STOP).putExtra("user_stop", true), flags);
        String heading = recoveryAttempt ? (initialAttempt ? AppLocale.context(this).getString(R.string.service_extra_5) : AppLocale.context(this).getString(R.string.service_extra_6)) : AppLocale.context(this).getString(R.string.service_extra_5);
        String subtitle = recoveryStage != null ? recoveryStage : AppLocale.context(this).getString(R.string.service_extra_7);
        if (waitingInternet) {
            heading = AppLocale.context(this).getString(R.string.service_extra_0);
            subtitle = AppLocale.context(this).getString(R.string.service_extra_8);
        }
        if (CONNECTED.equals(state) && !waitingInternet) {
            heading = country.isEmpty() ? AppLocale.context(this).getString(R.string.service_extra_1) : flag(country) + " " + SubscriptionInfo.countryLabel(this,country);
            subtitle = currentProfile == null ? AppLocale.context(this).getString(R.string.service_extra_9) : SubscriptionInfo.withoutFlagEmoji(SubscriptionInfo.name(currentProfile));
            if (liveDelay >= 0) subtitle += " · " + liveDelay + " мс";
            else if (delayDone) subtitle += " · n/a";
        }
        return notification.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle(heading).setContentText(ServiceText.translate(this, subtitle))
                .setContentIntent(open).setOngoing(true).addAction(0, AppLocale.context(this).getString(R.string.service_extra_10), stop).build();
    }

    private String flag(String code) {
        if (code == null || !code.matches("[A-Z]{2}")) return "";
        return new String(Character.toChars(0x1F1E6 + code.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + code.charAt(1) - 'A'));
    }
    private void alertConnectionLost(boolean automatic) {
        if (!getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS).getBoolean("connection_alerts", true)) return;
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String channel = "vpn_connection_alerts";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, AppLocale.context(this).getString(R.string.service_extra_11), NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH);
        if (!automatic && Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(3000);
        manager.notify(310, b.setSmallIcon(R.drawable.ic_tile_manual).setContentTitle("Vexis")
                .setContentText(automatic ? AppLocale.context(this).getString(R.string.service_extra_12) : AppLocale.context(this).getString(R.string.service_extra_13))
                .setAutoCancel(true).build());
        if (!automatic && Build.VERSION.SDK_INT < 26) main.postDelayed(() -> manager.cancel(310), 3000);
    }

    private void startTunnel(String link, boolean verify, boolean probe) {
        try {
            if (stopRequested) return;
            SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
            if (AwgProfile.isAwg(link) && !probe && RoutingPresets.enabled(prefs)) {
                startRoutedAwg(link, prefs, verify);
            } else if (AwgProfile.isAwg(link)) {
                startAwg(link, prefs, verify, probe);
                if (verify) {
                    long delay = awaitAwgHandshake(!recoveryAttempt && !probe ? 30000 : 8000);
                    if (stopRequested) return;
                    if (delay < 0) throw new IOException("AWG handshake timeout");
                    if (awgHasInternetRoute) delay = verifyTunnelInternet();
                    // Split-tunnel profiles can reach only their configured routes; their
                    // handshake is the only generally applicable connectivity check.
                    if (probe) {
                        if (!stopRequested) finishTunnel(PROBED, delay);
                        return;
                    }
                }
            } else {
                if (verify && !probe) verifyProfileBeforeStart(link);
                if (stopRequested) return;
                startXray(link, prefs, verify);
            }
            main.post(() -> {
                if (!stopRequested) {
                    VpnJournal.add(this, "VPN подключён: " + SubscriptionInfo.name(currentProfile), false);
                    startedAt = SystemClock.elapsedRealtime();
                    publish(CONNECTED, -1);
                    if (core != null || awgHasInternetRoute) {
                        locationWorker.execute(this::loadCountry);
                        scheduleDelay();
                    } else {
                        countryDone = true;
                        delayDone = true;
                        publish(CONNECTED, -1);
                    }
                }
            });
        } catch (NoAppsException e) {
            finishTunnel(NO_APPS, -1);
        } catch (LinkageError e) {
            if(!retryManual(link,verify,probe))finishTunnel(AwgProfile.isAwg(link) ? "Движок AWG недоступен" : "Движок Xray недоступен", -1);
        } catch (Exception e) {
            VpnJournal.add(this, "Не удалось подключиться: " + e.getClass().getSimpleName(), false);
            if(!retryManual(link,verify,probe))finishTunnel("Не удалось подключиться", -1);
        }
    }

    private void startWhenInternetAvailable(Runnable ready) {
        initialDownCount=0;
        initialInternet = new InternetMonitor(this, status -> {
            if (stopRequested || !CONNECTING.equals(state)) return;
            if (status == InternetMonitor.Status.DOWN) {
                if (++initialDownCount < 2) { initialInternet.requestCheck(); return; }
                if (!waitingInternet) {
                    waitingInternet = true;
                    VpnJournal.add(this, "Интернет недоступен; ожидание сети", false);
                    publish(CONNECTING, -1);
                }
                return;
            }
            if (waitingInternet && status != InternetMonitor.Status.UP) return;
            waitingInternet = false;
            InternetAlert.clear(this);
            initialInternet.close();initialInternet = null;
            publish(CONNECTING, -1);
            ready.run();
        });
        initialInternet.start(true);
    }

    private boolean retryManual(String link,boolean verify,boolean probe) {
        if(recoveryAttempt||probe||!verify||manualRetried||stopRequested)return false;
        manualRetried=true;
        closeTunnel();
        main.post(()->{
            if(!manualSessionCurrent())return;
            recoveryStage="Ожидание проверки интернета…";
            publish(CONNECTING,-1);
            startWhenInternetAvailable(()->retryWorker.execute(()->prepareManualRetry(link)));
        });
        return true;
    }

    private boolean manualSessionCurrent() {
        SharedPreferences prefs=getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
        return !stopRequested && prefs.getLong(RecoveryService.SESSION_GENERATION,0)==sessionGeneration
                && prefs.getBoolean("vpn_session_active",false) && !prefs.getBoolean("auto_connect",false)
                && java.util.Objects.equals(currentProfile,prefs.getString(MainActivity.MANUAL_PROFILE,null));
    }

    private void manualStage(String stage) {
        main.post(()->{if(manualSessionCurrent()){
            recoveryStage=stage;
            VpnJournal.add(this,"Ручное подключение: "+stage,true);
            publish(CONNECTING,-1);
        }});
    }

    private void prepareManualRetry(String original) {
        if(!manualSessionCurrent())return;
        String profile=original;
        try(LocalDatabase data=new LocalDatabase(this)){
            ArrayList<String> links=new ArrayList<>(),groups=new ArrayList<>(),owners=new ArrayList<>();
            data.loadServers(links,groups,owners);
            int index=links.indexOf(original);
            String preferred=getSharedPreferences("app_settings",MODE_PRIVATE | MODE_MULTI_PROCESS).getString(MainActivity.MANUAL_OWNER,null);
            String owner=null;
            for(int i=0;i<links.size();i++)if(original.equals(links.get(i))&&owners.get(i).equals(preferred)){owner=preferred;break;}
            if(owner==null&&index>=0)owner=owners.get(index);
            if(owner!=null&&!owner.startsWith("local:")){
                manualStage("Обновление подписки…");
                java.util.Map<String,ArrayList<String>> refreshed=BackgroundRecoverySupport.refresh(this,java.util.Collections.singleton(owner),this::manualSessionCurrent);
                ArrayList<String> updated=refreshed.get(owner);
                if(updated!=null&&!updated.contains(original)){
                    String match=null;int matches=0;
                    for(String candidate:updated)if(AwgProfile.isAwg(original)==AwgProfile.isAwg(candidate)
                            &&SubscriptionInfo.name(original).equals(SubscriptionInfo.name(candidate))){
                        match=candidate;matches++;
                    }
                    if(matches==1)profile=match;
                }
            }
        }catch(RuntimeException failure){
            VpnJournal.add(this,"Ошибка обновления подписки: "+failure.getClass().getSimpleName(),false);
        }
        if(!manualSessionCurrent())return;
        String retryProfile=profile;
        SharedPreferences prefs=getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
        if(!original.equals(retryProfile)){
            if(!original.equals(prefs.getString(MainActivity.MANUAL_PROFILE,null)))return;
            prefs.edit().putString(MainActivity.MANUAL_PROFILE,retryProfile).commit();
            currentProfile=retryProfile;
        }
        manualStage("Проверка движков…");
        if(EngineUpdates.checkManualBackground(this,this::manualSessionCurrent,retryProfile,attempt,sessionGeneration))return;
        if(!manualSessionCurrent())return;
        currentProfile=retryProfile;
        manualStage("Повторное подключение…");
        worker.execute(()->startTunnel(retryProfile,true,false));
    }

    private void verifyProfileBeforeStart(String profile) throws Exception {
        ExecutorService verifier = Executors.newSingleThreadExecutor();
        int timeout = XrayPing.timeout(getSharedPreferences("app_settings", MODE_PRIVATE));
        if (!recoveryAttempt) timeout = Math.max(timeout, 30000);
        final int probeTimeout = timeout;
        java.util.concurrent.Future<Long> result = verifier.submit(() -> XrayPing.measure(profile, probeTimeout));
        try {
            while (!stopRequested) {
                try {
                    if (result.get(100, java.util.concurrent.TimeUnit.MILLISECONDS) < 0)
                        throw new IOException("Server probe failed");
                    return;
                } catch (java.util.concurrent.TimeoutException ignored) { }
            }
        } finally {
            result.cancel(true);
            verifier.shutdownNow();
        }
    }

    private void startXray(String link, SharedPreferences prefs, boolean verify) throws Exception {
        startRoutedTunnel(XrayPing.outbound(link), prefs, verify, 1500);
    }

    private void startRoutedAwg(String link, SharedPreferences prefs, boolean verify) throws Exception {
        org.amnezia.awg.config.Config profile = AwgProfile.parse(link);
        awgRouting = AwgRoutingBackend.start(this, this, profile, verify);
        org.json.JSONObject user = new org.json.JSONObject().put("user", awgRouting.getSocksUsername())
                .put("pass", awgRouting.getSocksPassword());
        org.json.JSONObject server = new org.json.JSONObject().put("address", "127.0.0.1")
                .put("port", awgRouting.getSocksPort()).put("users", new org.json.JSONArray().put(user));
        org.json.JSONObject outbound = new org.json.JSONObject().put("protocol", "socks").put("tag", "proxy")
                .put("settings", new org.json.JSONObject().put("servers", new org.json.JSONArray().put(server)));
        startRoutedTunnel(outbound, prefs, verify, profile.getInterface().getMtu().orElse(1420));
    }

    private void startRoutedTunnel(org.json.JSONObject outbound, SharedPreferences prefs, boolean verify, int mtu) throws Exception {
        if (RoutingPresets.enabled(prefs)) RoutingPresets.installBundledAssets(this);
        org.json.JSONObject routing = RoutingPresets.compile(prefs);
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            geoPort = socket.getLocalPort();
        }
        geoPassword = java.util.UUID.randomUUID().toString();
        String config = TrafficRules.vpnConfig(outbound, routing, geoPort, geoPassword, hasDirectIpv6(), mtu).toString();
        // A virtual resolver keeps Android's opportunistic DNS-over-TLS from
        // selecting a public DNS server before Xray can apply the DNS policy.
        String dnsAddress = routing.has("dns") ? TrafficRules.VPN_DNS_ADDRESS : "1.1.1.1";
        Builder builder = new Builder().setSession("Vexis").setMtu(mtu)
                .addAddress("10.71.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fd71::1", 64).addRoute("::", 0)
                .addDnsServer(dnsAddress).setBlocking(false);
        applyAppRouting(builder, prefs);
        if (stopRequested) return;
        go.Seq.setContext(getApplicationContext());
        Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "");
        core = Libv2ray.newCoreController(new CoreCallbackHandler() {
            @Override public long startup() { return 0; }
            @Override public long shutdown() { return 0; }
            @Override public long onEmitStatus(long code, String message) { return 0; }
        });
        tunnel = builder.establish();
        if (tunnel == null) throw new IOException("TUN unavailable");
        core.startLoop(config, tunnel.getFd());
        if (!core.getIsRunning()) throw new IOException("Xray did not start");
        if (verify && !stopRequested) verifyTunnelInternet();
    }

    private boolean hasDirectIpv6() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        LinkProperties link = connectivity.getLinkProperties(connectivity.getActiveNetwork());
        if (link == null) return false;
        boolean ipv6Default = false;
        for (RouteInfo route : link.getRoutes()) {
            if (route.isDefaultRoute() && route.getDestination().getAddress() instanceof Inet6Address) {
                ipv6Default = true;
                break;
            }
        }
        if (!ipv6Default) return false;
        for (LinkAddress local : link.getLinkAddresses()) {
            InetAddress address = local.getAddress();
            if (address instanceof Inet6Address && (address.getAddress()[0] & 0xe0) == 0x20) return true;
        }
        return false;
    }

    private long verifyTunnelInternet() throws Exception {
        ExecutorService verifier = Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Long> result = verifier.submit(() -> {
            if (usesAndroidAwgNetwork()) {
                for (int retry = 0; retry < 10 && !stopRequested; retry++) {
                    try { awgNetwork(); break; }
                    catch (IOException unavailable) { Thread.sleep(200); }
                }
            }
            return checkAvailable(recoveryAttempt ? 5000 : 15000);
        });
        try {
            while (!stopRequested) {
                try {
                    long delay = result.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (delay < 0) throw new IOException("Tunnel probe failed");
                    return delay;
                } catch (java.util.concurrent.TimeoutException ignored) {
                    // Check cancellation while the native probe is running.
                }
            }
            throw new IOException("Tunnel probe cancelled");
        } finally {
            result.cancel(true);
            verifier.shutdownNow();
        }
    }

    private void scheduleDelay() {
        if (stopRequested || healthFailed || waitingInternet || deviceIdle() || !CONNECTED.equals(state)) return;
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
        boolean recover = prefs.getBoolean("auto_connect", false) && prefs.getBoolean("auto_failover", true);
        main.postDelayed(delayTask, recover ? 3000 : 5000);
        if (!delayRunning.compareAndSet(false, true)) return;
        monitor.execute(() -> {
            long result = -1;
            try { result = checkAvailable(2500); } catch (Exception | LinkageError ignored) { }
            final long value = result;
            main.post(() -> {
                delayRunning.set(false);
                if (stopRequested || !CONNECTED.equals(state)) return;
                // A timeout while the device is idle says nothing about the server.
                if (deviceIdle() || waitingInternet) return;
                liveDelay = value;
                delayDone = true;
                SharedPreferences settings = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
                boolean recoveryEnabled = !settings.getBoolean("auto_connect", false) || settings.getBoolean("auto_failover", true);
                if (value >= 0) {
                    healthFailures = 0; firstHealthFailure = 0;
                    if (diagnosisActive) {
                        diagnosisActive = false;
                        stopInternetDiagnosis(diagnosisGeneration.incrementAndGet());
                    }
                } else {
                    if (!diagnosisActive) {
                        diagnosisActive = true;
                        startInternetDiagnosis(diagnosisGeneration.incrementAndGet());
                    }
                    if (recoveryEnabled) {
                        if (++healthFailures == 1) firstHealthFailure = SystemClock.elapsedRealtime();
                        maybeRecover();
                    } else {
                        healthFailures = 0; firstHealthFailure = 0;
                    }
                }
                publish(CONNECTED, -1);
            });
        });
    }

    private void maybeRecover() {
        if (stopRequested || healthFailed || waitingInternet || deviceIdle() || healthFailures < 2
                || SystemClock.elapsedRealtime() - firstHealthFailure < 3000) return;
        InternetMonitor.Status status = internetDiagnosis == null ? null : internetDiagnosis.recentStatus();
        if (status == null) {
            if (internetDiagnosis != null && !physicalCheckPending) {
                physicalCheckPending = true; internetDiagnosis.requestCheck();
            }
            return;
        }
        if (status == InternetMonitor.Status.DOWN && physicalDownCount < 2) return;
        if (status == InternetMonitor.Status.UNKNOWN) {
            if (inconclusiveNetworkChecks < 3) {
                if (internetDiagnosis != null && !physicalCheckPending) {
                    physicalCheckPending = true;
                    internetDiagnosis.requestCheck();
                }
                return;
            }
        } else if (status != InternetMonitor.Status.UP) return;
        android.util.Log.d("VpnHealth", "Tunnel checks failed with physical network " + status);
        healthFailed = true;
        SharedPreferences recoveryPrefs = getSharedPreferences("app_settings", MODE_PRIVATE | MODE_MULTI_PROCESS);
        boolean automatic = recoveryPrefs.getBoolean("auto_connect", false);
        boolean recover = automatic ? recoveryPrefs.getBoolean("auto_failover", true) : recoveryPrefs.getBoolean("manual_reconnect", false);
        VpnJournal.add(this, recover ? "VPN-сервер недоступен; запущено переподключение" : "VPN-сервер недоступен; отключение", false);
        alertConnectionLost(recover);
        worker.execute(() -> finishTunnel(recover ? LOST : "Сервер недоступен", -1));
    }

    private void startInternetDiagnosis(int diagnosis) {
        if (diagnosis != diagnosisGeneration.get() || stopRequested || !CONNECTED.equals(state)) return;
        if (internetDiagnosis != null) internetDiagnosis.close();
        physicalDownCount = 0;
        physicalCheckPending = true;
        internetDiagnosis = new InternetMonitor(this, status -> {
            if (diagnosis != diagnosisGeneration.get() || stopRequested || !CONNECTED.equals(state)) return;
            physicalCheckPending = false;
            if (status == InternetMonitor.Status.UNKNOWN) inconclusiveNetworkChecks++;
            else inconclusiveNetworkChecks = 0;
            if (status == InternetMonitor.Status.DOWN) {
                if (++physicalDownCount < 2) { physicalCheckPending = true; internetDiagnosis.requestCheck(); return; }
                waitingInternet = true;
                InternetAlert.show(this);
                android.util.Log.d("VpnHealth", "Physical network unavailable; retaining tunnel");
                healthFailures = 0; firstHealthFailure = 0;
                main.removeCallbacks(delayTask);
                publish(CONNECTED, -1);
            } else {
                physicalDownCount = 0;
                if (waitingInternet && status == InternetMonitor.Status.UP) {
                    waitingInternet = false;
                    InternetAlert.clear(this);
                    android.util.Log.d("VpnHealth", "Physical network restored; checking retained tunnel");
                    healthFailures = 0; firstHealthFailure = 0;
                    publish(CONNECTED, -1);
                    restartHealthChecks();
                } else if (!waitingInternet) maybeRecover();
            }
        });
        internetDiagnosisGeneration = diagnosis;
        internetDiagnosis.start(true);
    }

    private void stopInternetDiagnosis(int diagnosis) {
        if (diagnosis != diagnosisGeneration.get()) return;
        if (internetDiagnosis != null) internetDiagnosis.close();
        internetDiagnosis = null;
        physicalDownCount = 0; physicalCheckPending = false;
    }

    private long checkAvailable(int timeoutMs) throws Exception {
        try {
            return checkConnection("www.google.com", "/generate_204", timeoutMs);
        } catch (IOException primaryFailure) {
            if (stopRequested || Thread.currentThread().isInterrupted()) throw primaryFailure;
            // Confirm failure against independent endpoints concurrently, through the same VPN path.
            ExecutorService checks = Executors.newFixedThreadPool(2);
            java.util.concurrent.CompletionService<Long> results =
                    new java.util.concurrent.ExecutorCompletionService<>(checks);
            try {
                results.submit(() -> checkConnection(XrayPing.FALLBACK_HOST, "/generate_204", timeoutMs));
                results.submit(() -> checkConnection("connectivitycheck.gstatic.com", "/generate_204", timeoutMs));
                int remaining = 2;
                while (remaining > 0 && !stopRequested && !Thread.currentThread().isInterrupted()) {
                    java.util.concurrent.Future<Long> result = results.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (result == null) continue;
                    remaining--;
                    try { return result.get(); }
                    catch (java.util.concurrent.ExecutionException ignored) { }
                }
                throw primaryFailure;
            } finally {
                checks.shutdownNow();
            }
        }
    }

    private Network awgNetwork() throws IOException {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        Network network = connectivity.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : connectivity.getNetworkCapabilities(network);
        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            throw new IOException("AWG VPN network unavailable");
        return network;
    }

    private boolean usesAndroidAwgNetwork() {
        return this instanceof AwgTunnelService && awgRouting == null;
    }

    private java.net.HttpURLConnection openAwgHttps(String host, String path, int timeoutMs) throws IOException {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) awgNetwork()
                .openConnection(new java.net.URL("https", host, path), java.net.Proxy.NO_PROXY);
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Connection", "close");
        return connection;
    }

    private long checkConnection(String host, String path, int timeoutMs) throws Exception {
        long started = SystemClock.elapsedRealtime();
        if (usesAndroidAwgNetwork()) {
            java.net.HttpURLConnection connection = openAwgHttps(host, path, timeoutMs);
            try {
                if (connection.getResponseCode() != 204) throw new IOException("Health check failed");
                return SystemClock.elapsedRealtime() - started;
            } finally {
                connection.disconnect();
            }
        }
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", geoPort), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            String credentials = android.util.Base64.encodeToString(
                    ("tunnel:" + geoPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP);
            String connect = "CONNECT " + host + ":443 HTTP/1.1\r\nHost: " + host + ":443\r\n"
                    + "Proxy-Authorization: Basic " + credentials + "\r\n\r\n";
            socket.getOutputStream().write(connect.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.io.InputStream input = socket.getInputStream();
            java.io.ByteArrayOutputStream headers = new java.io.ByteArrayOutputStream();
            while (headers.size() < 8192) {
                int value = input.read();
                if (value < 0) throw new IOException("Proxy closed connection");
                headers.write(value);
                String response = headers.toString("US-ASCII");
                if (response.endsWith("\r\n\r\n")) {
                    if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200"))
                        throw new IOException("Proxy connection failed");
                    break;
                }
            }
            javax.net.ssl.SSLSocketFactory factory =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            javax.net.ssl.SSLSocket secure =
                    (javax.net.ssl.SSLSocket) factory.createSocket(socket, host, 443, false);
            secure.setSoTimeout(timeoutMs);
            javax.net.ssl.SSLParameters params = secure.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(params);
            secure.startHandshake();
            secure.getOutputStream().write(("GET " + path + " HTTP/1.0\r\nHost: " + host + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(secure.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
            String status = reader.readLine();
            if (status == null || !status.matches("HTTP/1\\.[01] 204(?: .*|$)"))
                throw new IOException("Health check failed");
            return SystemClock.elapsedRealtime() - started;
        }
    }

    private void loadCountry() {
        try {
          String[][] providers={{"ipwho.is","/"},{"api.i.pn","/json/"},{"ipapi.co","/json/"},{"ipinfo.io","/json"}};
          for(String[] provider:providers){
           if(stopRequested)break;
           String host=provider[0],path=provider[1];
           try{
            if (usesAndroidAwgNetwork()) {
                java.net.HttpURLConnection connection = openAwgHttps(host, path, 5000);
                try {
                    if (connection.getResponseCode() != 200) throw new IOException("Location request failed");
                    try (java.io.InputStream input = connection.getInputStream()) {
                        applyLocation(normalizeLocation(new org.json.JSONObject(readLocation(input))));
                    }
                } finally {
                    connection.disconnect();
                }
                if(country!=null&&!country.isEmpty())break;
                continue;
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", geoPort), 8000);
                socket.setSoTimeout(8000);
                String credentials = android.util.Base64.encodeToString(
                        ("tunnel:" + geoPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP);
                String connect = "CONNECT "+host+":443 HTTP/1.1\r\nHost: "+host+":443\r\n"
                        + "Proxy-Authorization: Basic " + credentials + "\r\n\r\n";
                socket.getOutputStream().write(connect.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                java.io.InputStream raw = socket.getInputStream();
                java.io.ByteArrayOutputStream headers = new java.io.ByteArrayOutputStream();
                while (headers.size() < 8192) {
                    int value = raw.read();
                    if (value < 0) throw new IOException("Proxy closed connection");
                    headers.write(value);
                    String response = headers.toString("US-ASCII");
                    if (response.endsWith("\r\n\r\n")) {
                        if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200"))
                            throw new IOException("Proxy connection failed");
                        break;
                    }
                }
                javax.net.ssl.SSLSocketFactory factory =
                        (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                javax.net.ssl.SSLSocket secure =
                        (javax.net.ssl.SSLSocket) factory.createSocket(socket, host, 443, false);
                javax.net.ssl.SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                secure.setSSLParameters(parameters);
                secure.startHandshake();
                secure.getOutputStream().write(("GET "+path+" HTTP/1.0\r\nHost: "+host+"\r\n"
                        + "Connection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String response = readLocation(secure.getInputStream());
                int body = response.indexOf("\r\n\r\n");
                if (body < 0 || (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200")))
                    throw new IOException("Location request failed");
                applyLocation(normalizeLocation(new org.json.JSONObject(response.substring(body + 4))));
            }
            if(country!=null&&!country.isEmpty())break;
           }catch(Exception ignored){}
          }
        } catch (Exception ignored) {
        } finally {
            countryDone = true;
            main.post(() -> {
                if (!stopRequested && CONNECTED.equals(state)) publish(CONNECTED, -1);
            });
        }
    }

    private static org.json.JSONObject normalizeLocation(org.json.JSONObject data) throws org.json.JSONException {
        if(data.has("status")&&!"success".equalsIgnoreCase(data.optString("status")))data.put("success",false);
        if(!data.has("success"))data.put("success",true);
        if(!data.has("country_code"))data.put("country_code",data.optString("countryCode",data.optString("country", "")));
        if(!data.has("ip")&&data.has("query"))data.put("ip",data.optString("query"));
        return data;
    }

    private static String readLocation(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int count; (count = input.read(buffer)) != -1;) {
            if (bytes.size() + count > 65536) throw new IOException("Response too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toString("UTF-8");
    }

    private void applyLocation(org.json.JSONObject json) {
        if (!json.optBoolean("success", false)) return;
        String code = json.optString("country_code", "").toUpperCase(java.util.Locale.ROOT);
        if (code.matches("[A-Z]{2}")) country = code;
        String address = json.optString("ip", "");
        if (address.length() <= 45 && (android.util.Patterns.IP_ADDRESS.matcher(address).matches()
                || address.matches("[0-9a-fA-F:]+") && address.contains(":"))) exitIp = address;
        org.json.JSONObject zone = json.optJSONObject("timezone");
        if (zone != null) exitTimeZone = zone.optString("id", "");
    }

    private void startAwg(String link, SharedPreferences prefs, boolean verify, boolean probe) throws Exception {
        org.amnezia.awg.config.Config profile = AwgProfile.parse(link);
        String nativeConfig = AwgProfile.nativeConfig(profile, this, verify);
        AwgProfile.loadNative();
        Builder builder = new Builder().setSession("Vexi AWG")
                .setMtu(profile.getInterface().getMtu().orElse(1420)).setBlocking(true);
        for (org.amnezia.awg.config.InetNetwork address : profile.getInterface().getAddresses())
            builder.addAddress(address.getAddress(), address.getMask());
        for (java.net.InetAddress dns : profile.getInterface().getDnsServers()) builder.addDnsServer(dns);
        for (String search : profile.getInterface().getDnsSearchDomains()) builder.addSearchDomain(search);
        for (org.amnezia.awg.config.Peer peer : profile.getPeers()) {
            for (org.amnezia.awg.config.InetNetwork route : peer.getAllowedIps()) {
                builder.addRoute(route.getAddress(), route.getMask());
                if (route.getMask() == 0) awgHasInternetRoute = true;
            }
        }
        if (probe) builder.addAllowedApplication(getPackageName());
        else applyAppRouting(builder, prefs, true);
        if (stopRequested) return;
        if (userPing && AwgPing.vpnBusyBeforeEstablish(this, sessionGeneration)) throw new IOException("VPN busy");
        try (ParcelFileDescriptor fd = builder.establish()) {
            if (fd == null) throw new IOException("TUN unavailable");
            awgStartedAt = System.currentTimeMillis();
            awgHandle = org.amnezia.awg.GoBackend.awgTurnOn("tunnel-awg", fd.detachFd(),
                    nativeConfig, getDataDir().getAbsolutePath());
        }
        if (awgHandle < 0) throw new IOException("AWG startup failed");
        int v4 = org.amnezia.awg.GoBackend.awgGetSocketV4(awgHandle);
        int v6 = org.amnezia.awg.GoBackend.awgGetSocketV6(awgHandle);
        if ((v4 >= 0 && !protect(v4)) || (v6 >= 0 && !protect(v6))) throw new IOException("AWG socket protection failed");
    }

    private long awaitAwgHandshake(int timeoutMs) {
        long start = SystemClock.elapsedRealtime();
        while (!stopRequested && SystemClock.elapsedRealtime() - start < timeoutMs) {
            String config = org.amnezia.awg.GoBackend.awgGetConfig(awgHandle);
            if (config != null) {
                long handshake = AwgProfile.handshakeTime(config);
                if (handshake > 0) return Math.max(0, handshake - awgStartedAt);
            }
            SystemClock.sleep(25);
        }
        return -1;
    }

    private void applyAppRouting(Builder builder, SharedPreferences prefs) throws NoAppsException, PackageManager.NameNotFoundException {
        applyAppRouting(builder, prefs, false);
    }

    private void applyAppRouting(Builder builder, SharedPreferences prefs, boolean viaAwg) throws NoAppsException, PackageManager.NameNotFoundException {
        String mode = prefs.getString(RoutingActivity.APP_MODE, RoutingActivity.ALL);
        boolean only = RoutingActivity.ONLY.equals(mode);
        // Exclude our UID so Xray's outbound sockets cannot loop through this TUN.
        if (!only && !viaAwg) builder.addDisallowedApplication(getPackageName());
        if (RoutingActivity.ALL.equals(mode)) return;
        int applied = 0;
        int ownUid = getApplicationInfo().uid;
        for (String name : prefs.getStringSet(RoutingActivity.APP_LIST, Collections.emptySet())) {
            try {
                if (getPackageManager().getApplicationInfo(name, 0).uid == ownUid) continue;
                if (only) builder.addAllowedApplication(name);
                else builder.addDisallowedApplication(name);
                applied++;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Android may have uninstalled the package since it was selected.
            }
        }
        if (only && applied == 0) throw new NoAppsException();
        if (only && viaAwg) builder.addAllowedApplication(getPackageName());
    }

    private static final class NoAppsException extends Exception {}

    private void finishTunnel(String message, long delay) {
        closeTunnel();
        main.post(() -> {
            if (!stopRequested) stopAndPublish(message, delay);
        });
    }

    private void stopAndPublish(String message, long delay) {
        state = message;
        finalState = message;
        finalDelay = delay;
        stopForeground(true);
        stopSelf();
    }

    private void requestStop() {
        if (!recoveryAttempt) InternetAlert.clear(this);
        if (initialInternet != null) { initialInternet.close(); initialInternet = null; }
        if (stopRequested) return;
        stopRequested = true;
        VpnJournal.add(this, "Отключение VPN", false);
        worker.execute(() -> {
            closeTunnel();
            main.post(() -> stopAndPublish(DISCONNECTED, -1));
        });
    }

    private void closeTunnel() {
        if (awgHandle >= 0) {
            try { org.amnezia.awg.GoBackend.awgTurnOff(awgHandle); } catch (LinkageError ignored) {}
            awgHandle = -1;
        }
        if (core != null) {
            try { core.stopLoop(); } catch (Exception | LinkageError ignored) {}
            core = null;
        }
        if (awgRouting != null) {
            awgRouting.close();
            awgRouting = null;
        }
        if (tunnel != null) {
            try { tunnel.close(); } catch (IOException ignored) {}
            tunnel = null;
        }
    }

    @Override public void onRevoke() { requestStop(); }

    @Override public void onDestroy() {
        InternetMonitor.Status diagnosis = internetDiagnosis == null
                || internetDiagnosisGeneration != diagnosisGeneration.get() ? null : internetDiagnosis.recentStatus();
        diagnosisGeneration.incrementAndGet();
        main.removeCallbacks(delayTask);
        if (idleReceiverRegistered) { unregisterReceiver(idleReceiver); idleReceiverRegistered = false; }
        if (internetDiagnosis != null) internetDiagnosis.close();
        if (initialInternet != null) { initialInternet.close(); initialInternet = null; }
        internetDiagnosis = null;
        if (!stopRequested && isActive(state)) requestStop();
        worker.shutdown();
        monitor.shutdownNow();
        retryWorker.shutdownNow();
        locationWorker.shutdownNow();
        super.onDestroy();
        if (finalState != null) publish(finalState, finalDelay);
        if (LOST.equals(finalState)) {
            Intent recovery = new Intent(this, RecoveryService.class).setAction(RecoveryService.START)
                    .putExtra(PROFILE, currentProfile).putExtra(RecoveryService.SESSION_GENERATION, sessionGeneration);
            if (diagnosis != null) recovery.putExtra(INTERNET_DIAGNOSIS, diagnosis.name());
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(recovery);
                else startService(recovery);
            } catch (RuntimeException ignored) { publish(DISCONNECTED, -1); }
        }
    }
}
