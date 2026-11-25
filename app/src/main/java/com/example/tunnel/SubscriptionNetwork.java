package com.example.tunnel;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SubscriptionNetwork {
    static boolean refreshAllowed(Context context) {
        if (!context.getSharedPreferences("app_settings", 0).getBoolean("refresh_wifi_only", false)) return true;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        return isWifi(manager, manager.getActiveNetwork(), new HashSet<>());
    }

    static final class Payload {
        String title, userinfo, announcement;
        final LinkedHashSet<String> links = new LinkedHashSet<>();
    }

    static void validate(String url) throws Exception { fetch(url); }

    static Payload fetch(String url) throws Exception {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        try {
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (bytes.size() + count > 8 * 1024 * 1024) throw new java.io.IOException("Subscription too large");
                    bytes.write(buffer, 0, count);
                }
            }
            Payload payload = new Payload();
            payload.title = connection.getHeaderField("profile-title");
            payload.userinfo = connection.getHeaderField("subscription-userinfo");
            payload.announcement = connection.getHeaderField("announce");
            parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8), payload);
            if (payload.links.isEmpty()) throw new IOException("No supported servers");
            String fallback = payload.links.size() == 1 ? SubscriptionInfo.name(payload.links.iterator().next()) : "";
            payload.title = SubscriptionInfo.title(payload.title, connection.getURL().toString());
            if (payload.links.size() == 1 && payload.title.equals(connection.getURL().getHost()) && !"Сервер".equals(fallback))
                payload.title = fallback;
            if (payload.announcement != null && payload.announcement.regionMatches(true,0,"base64:",0,7))
                payload.announcement = decode(payload.announcement.substring(7));
            return payload;
        } finally { connection.disconnect(); }
    }

    static boolean isProfile(String value) {
        int marker = value.indexOf("://");
        return marker > 0 && marker <= 6 && value.substring(0,marker).matches("(?i)vless|vmess|trojan|ss|socks|awg");
    }

    static boolean isHttps(String value) {
        try {
            java.net.URL url = new java.net.URL(value);
            return "https".equalsIgnoreCase(url.getProtocol()) && !url.getHost().isEmpty() && !value.matches("(?s).*\\s.*");
        } catch (Exception ignored) { return false; }
    }

    static String clean(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("\uFEFF") ? trimmed.substring(1).trim() : trimmed;
    }

    private static void parse(String body, Payload payload) {
        StringBuilder encoded = new StringBuilder();
        for (String row : body.split("\\R")) {
            String line = clean(row);
            if (line.startsWith("#")) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(1,colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon+1).trim();
                if (value.isEmpty()) continue;
                if ("profile-title".equals(key) && payload.title == null) payload.title = value;
                if ("subscription-userinfo".equals(key) && payload.userinfo == null) payload.userinfo = value;
                if ("announce".equals(key) && payload.announcement == null) payload.announcement = value;
            } else if (isProfile(line)) {
                addProfile(line, payload);
            } else if (!line.isEmpty()) {
                encoded.append(line);
            }
        }
        if (payload.links.isEmpty() && encoded.length() > 0) {
            String decoded = decode(encoded.toString());
            if (decoded != null && decoded.contains("://")) parse(decoded, payload);
        }
    }

    private static void addProfile(String value, Payload payload) {
        int separator = value.indexOf("://");
        String link = value.substring(0,separator).toLowerCase(Locale.ROOT) + value.substring(separator);
        try {
            if (AwgProfile.isAwg(link)) AwgProfile.parse(link);
            else XrayPing.outbound(link);
            payload.links.add(link);
        } catch (Exception | LinkageError ignored) { }
    }

    private static String decode(String value) {
        try { return XrayPing.decode(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean isWifi(ConnectivityManager manager, Network network, Set<Network> visited) {
        if (network == null || !visited.add(network)) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) return false;
        // VPN capabilities may also include the transport of their underlying network.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
}
