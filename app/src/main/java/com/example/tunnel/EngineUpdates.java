package com.example.tunnel;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EngineUpdates {
    static final String RETRY_AFTER_UPDATE = "retry_after_engine_update";
    private static final String MANUAL_RETRY = "manual_retry_after_engine_update";
    private static final String MANUAL_PROFILE = "manual_retry_profile";
    private static final String MANUAL_ATTEMPT = "manual_retry_attempt";
    private static final String MANUAL_GENERATION = "manual_retry_generation";
    private static volatile Runnable autoInstallFailed;
    enum Engine {
        XRAY("Xray", "https://api.github.com/repos/2dust/AndroidLibXrayLite/releases/latest"),
        AWG("AWG", "https://repo.maven.apache.org/maven2/com/zaneschepke/amneziawg-android/maven-metadata.xml");

        final String title;
        final String upstream;
        Engine(String title, String upstream) { this.title = title; this.upstream = upstream; }

        String installed() {
            return this == AWG ? AwgProfile.ARTIFACT_VERSION : versionNumber(libv2ray.Libv2ray.checkVersionX());
        }

        String display() {
            try {
                return installed();
            } catch (Exception | LinkageError e) {
                return "Недоступен";
            }
        }

        String latest() throws Exception {
            String response = metadata(upstream);
            if (this == XRAY) return versionNumber(new JSONObject(response).getString("tag_name"));
            Matcher matcher = Pattern.compile("<release>([0-9.]+)</release>").matcher(response);
            if (!matcher.find()) throw new IOException("Версия AWG не найдена");
            return matcher.group(1);
        }
    }

    private static String versionNumber(String value) {
        Matcher matcher = Pattern.compile("[0-9]+(?:\\.[0-9]+){1,3}").matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException("Не удалось определить версию");
        return matcher.group();
    }

    private static boolean newer(String candidate, String installed) {
        String[] a = versionNumber(candidate).split("\\.");
        String[] b = versionNumber(installed).split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long x = i < a.length ? Long.parseLong(a[i]) : 0;
            long y = i < b.length ? Long.parseLong(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    static void check(Activity activity, Engine engine, Runnable complete) {
        new Thread(() -> {
            String message;
            try {
                String installed = engine.installed();
                String latest = engine.latest();
                if (!newer(latest, installed)) {
                    message = AppLocale.context(activity).getString(R.string.service_engine_current, engine.title);
                } else if (BuildConfig.ENGINE_UPDATE_FEED.isEmpty()) {
                    message = AppLocale.context(activity).getString(R.string.service_engine_available, latest);
                } else {
                    JSONObject release = new JSONObject(metadata(BuildConfig.ENGINE_UPDATE_FEED));
                    String bundled = release.getString(engine.name().toLowerCase(Locale.ROOT));
                    if (newer(latest, bundled) || !newer(bundled, installed)) {
                        message = AppLocale.context(activity).getString(R.string.service_engine_no_build, latest);
                    } else if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                        activity.runOnUiThread(() -> {
                            if (!activity.isFinishing() && !activity.isDestroyed()) {
                                Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        android.net.Uri.parse("package:" + activity.getPackageName()));
                                if (settings.resolveActivity(activity.getPackageManager()) != null) activity.startActivity(settings);
                            }
                        });
                        message = AppLocale.context(activity).getString(R.string.service_engine_permission);
                    } else {
                        install(activity, release);
                        message = AppLocale.context(activity).getString(R.string.service_engine_sent);
                    }
                }
            } catch (Exception | LinkageError e) {
                message = AppLocale.context(activity).getString(R.string.service_engine_failure, engine.title);
            }
            String result = message;
            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed()) {
                    AppToast.show(activity, result);
                    complete.run();
                }
            });
        }, "engine-update").start();
    }

    static void checkBoth(Activity activity, Runnable complete, Runnable installing,
                          java.util.function.BooleanSupplier active) {
        java.util.concurrent.ExecutorService checks = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CompletableFuture<Boolean> xray = checkLatest(Engine.XRAY, checks);
        java.util.concurrent.CompletableFuture<Boolean> awg = checkLatest(Engine.AWG, checks);
        xray.thenCombine(awg, (x, a) -> x || a).thenAcceptAsync(outdated -> {
            try {
                if (!outdated || BuildConfig.ENGINE_UPDATE_FEED.isEmpty()) {
                    if (outdated) activity.runOnUiThread(() -> {
                        if (active.getAsBoolean()) AppToast.show(activity, AppLocale.context(activity).getString(R.string.service_engine_new_app));
                    });
                    activity.runOnUiThread(complete);
                    return;
                }
                JSONObject release = new JSONObject(metadata(BuildConfig.ENGINE_UPDATE_FEED));
                boolean updatesEngine = false;
                for (Engine engine : Engine.values()) {
                    String bundled = release.getString(engine.name().toLowerCase(Locale.ROOT));
                    String installed = engine.display();
                    if ("Недоступен".equals(installed)) {
                        updatesEngine = true;
                        continue;
                    }
                    if (newer(installed, bundled)) throw new IOException("Engine downgrade");
                    updatesEngine |= newer(bundled, installed);
                }
                if (!updatesEngine) {
                    activity.runOnUiThread(complete);
                    return;
                }
                if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                    activity.runOnUiThread(() -> {
                        if (active.getAsBoolean()) AppToast.show(activity, AppLocale.context(activity).getString(R.string.service_engine_allow_apk));
                        complete.run();
                    });
                    return;
                }
                install(activity, release, () -> activity.runOnUiThread(() -> {
                    if (active.getAsBoolean()) installing.run();
                }), complete, active);
            } catch (Exception | LinkageError e) {
                activity.runOnUiThread(complete);
            } finally {
                checks.shutdown();
            }
        }, checks);
    }

    private static java.util.concurrent.CompletableFuture<Boolean> checkLatest(
            Engine engine, java.util.concurrent.Executor executor) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                String latest = engine.latest();
                String installed = engine.display();
                return "Недоступен".equals(installed) || newer(latest, installed);
            } catch (Exception | LinkageError e) {
                return false;
            }
        }, executor);
    }

    static boolean checkBackground(Context context, java.util.function.BooleanSupplier active) {
        return checkBackground(context,active,null,null,0);
    }

    static boolean checkManualBackground(Context context, java.util.function.BooleanSupplier active,
                                         String profile, String attempt, long generation) {
        return checkBackground(context,active,profile,attempt,generation);
    }

    private static boolean checkBackground(Context context, java.util.function.BooleanSupplier active,
                                           String profile, String attempt, long generation) {
        boolean outdated = false;
        for (Engine engine : Engine.values()) {
            if (!active.getAsBoolean()) return false;
            try {
                String installed = engine.display();
                String latest = engine.latest();
                outdated |= "Недоступен".equals(installed) || newer(latest, installed);
            } catch (Exception | LinkageError ignored) {}
        }
        if (!outdated || !active.getAsBoolean() || BuildConfig.ENGINE_UPDATE_FEED.isEmpty()) return false;
        try {
            JSONObject release = new JSONObject(metadata(BuildConfig.ENGINE_UPDATE_FEED));
            boolean updatesEngine = false;
            for (Engine engine : Engine.values()) {
                String bundled = release.getString(engine.name().toLowerCase(Locale.ROOT));
                String installed = engine.display();
                if ("Недоступен".equals(installed)) updatesEngine = true;
                else {
                    if (newer(installed, bundled)) throw new IOException("Engine downgrade");
                    updatesEngine |= newer(bundled, installed);
                }
            }
            if (!updatesEngine || !active.getAsBoolean()) return false;
            if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) return false;
            if(profile!=null && !context.getSharedPreferences("app_settings",Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                    .edit().putBoolean(MANUAL_RETRY,true).putString(MANUAL_PROFILE,profile)
                    .putString(MANUAL_ATTEMPT,attempt).putLong(MANUAL_GENERATION,generation).commit()) return false;
            install(context, release, null, profile!=null ? () -> resumeManual(context) : () -> {
                if (!context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getBoolean("recovery_wanted", false)) return;
                Intent retry = new Intent(context, RecoveryService.class).setAction(RecoveryService.START)
                        .putExtra(RecoveryService.SESSION_GENERATION, context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getLong(RecoveryService.SESSION_GENERATION, 0));
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(retry);
                else context.startService(retry);
            }, active);
            boolean stillActive=active.getAsBoolean();
            if(profile!=null&&!stillActive)clearManualRetry(context);
            return stillActive;
        } catch (Exception | LinkageError ignored) {
            if(profile!=null)clearManualRetry(context);
            return false;
        }
    }

    private static void clearManualRetry(Context context) {
        context.getSharedPreferences("app_settings",Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).edit()
                .remove(MANUAL_RETRY).remove(MANUAL_PROFILE).remove(MANUAL_ATTEMPT).remove(MANUAL_GENERATION).commit();
    }

    static boolean hasManualRetry(Context context) {
        return context.getSharedPreferences("app_settings",Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).getBoolean(MANUAL_RETRY,false);
    }

    static void resumeManual(Context context) {
        android.content.SharedPreferences settings=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        if(!settings.getBoolean(MANUAL_RETRY,false))return;
        String profile=settings.getString(MANUAL_PROFILE,null), attempt=settings.getString(MANUAL_ATTEMPT,null);
        long generation=settings.getLong(MANUAL_GENERATION,-1);
        boolean active=profile!=null && attempt!=null && !settings.getBoolean("auto_connect",false)
                && settings.getBoolean("vpn_session_active",false)
                && settings.getLong(RecoveryService.SESSION_GENERATION,0)==generation
                && profile.equals(settings.getString(MainActivity.MANUAL_PROFILE,null));
        clearManualRetry(context);clearPendingRetry(context);
        if(!active)return;
        Intent retry=new Intent(context,AwgProfile.isAwg(profile)?AwgTunnelService.class:TunnelService.class)
                .setAction(TunnelService.MANUAL_RESUME).putExtra(TunnelService.PROFILE,profile)
                .putExtra(TunnelService.ATTEMPT,attempt).putExtra(TunnelService.VERIFY,true)
                .putExtra(RecoveryService.SESSION_GENERATION,generation);
        try {
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(retry);else context.startService(retry);
        } catch(RuntimeException error) { android.util.Log.w("EngineUpdates","Could not resume manual connection"); }
    }

    private static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Tunnel-Android");
        return connection;
    }

    private static String metadata(String address) throws IOException {
        HttpURLConnection connection = open(address);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) {
                if (bytes.size() + n > 1024 * 1024) throw new IOException("Metadata too large");
                bytes.write(buffer, 0, n);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void install(Context context, JSONObject release) throws Exception {
        install(context, release, null, null, () -> true);
    }

    private static void install(Context context, JSONObject release, Runnable submitted,
                                Runnable failed, java.util.function.BooleanSupplier active) throws Exception {
        String digest = release.getString("sha256");
        if (!digest.matches("(?i)[a-f0-9]{64}")) throw new IOException("Missing checksum");
        PackageInfo current = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        long currentCode = Build.VERSION.SDK_INT >= 28 ? current.getLongVersionCode() : current.versionCode;
        if (release.getLong("versionCode") <= currentCode) throw new IOException("Stale APK");
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            throw new IOException("App installs not allowed");
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int id = installer.createSession(params);
        boolean committed = false;
        HttpURLConnection connection = null;
        try (PackageInstaller.Session session = installer.openSession(id)) {
            connection = open(release.getString("apk"));
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (InputStream in = connection.getInputStream(); OutputStream out = session.openWrite("base.apk", 0, -1)) {
                byte[] buffer = new byte[64 * 1024];
                long size = 0;
                for (int n; (n = in.read(buffer)) != -1; ) {
                    if ((size += n) > 256L * 1024 * 1024) throw new IOException("APK too large");
                    hash.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
                session.fsync(out);
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : hash.digest()) actual.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (!digest.equalsIgnoreCase(actual.toString())) throw new IOException("APK checksum mismatch");
            Intent result = new Intent(context, InstallResult.class).putExtra("auto_retry", failed != null);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            if (!active.getAsBoolean()) return;
            if (failed != null) {
                autoInstallFailed = failed;
                if (!context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                        .putInt(RETRY_AFTER_UPDATE, BuildConfig.VERSION_CODE).commit()) {
                    autoInstallFailed = null;
                    throw new IOException("Could not save reconnect state");
                }
            }
            try {
                session.commit(PendingIntent.getBroadcast(context, id, result, flags).getIntentSender());
            } catch (RuntimeException e) {
                if (failed != null) clearPendingRetry(context);
                throw e;
            }
            committed = true;
            if (submitted != null) submitted.run();
        } finally {
            if (connection != null) connection.disconnect();
            if (!committed) installer.abandonSession(id);
        }
    }

    private static void clearPendingRetry(Context context) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().remove(RETRY_AFTER_UPDATE).apply();
        autoInstallFailed = null;
    }

    public static final class InstallResult extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent result) {
            int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirmation = result.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmation != null) context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (result.getBooleanExtra("auto_retry", false) && hasManualRetry(context)) {
                if(status!=PackageInstaller.STATUS_SUCCESS)AppToast.show(context,AppLocale.context(context).getString(R.string.service_engine_install_failed));
                resumeManual(context);
            } else if (status == PackageInstaller.STATUS_SUCCESS && result.getBooleanExtra("auto_retry", false)
                    && context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getBoolean("recovery_wanted", false)) {
                clearPendingRetry(context);
                Intent retry = new Intent(context, RecoveryService.class).setAction(RecoveryService.START)
                        .putExtra(RecoveryService.SESSION_GENERATION, context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getLong(RecoveryService.SESSION_GENERATION, 0));
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(retry);
                else context.startService(retry);
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                AppToast.show(context, AppLocale.context(context).getString(R.string.service_engine_install_failed));
                if (result.getBooleanExtra("auto_retry", false)) {
                    Runnable retry = autoInstallFailed;
                    clearPendingRetry(context);
                    if (retry != null) retry.run();
                }
            }
        }
    }
}
