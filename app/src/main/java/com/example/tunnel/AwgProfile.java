package com.example.tunnel;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import org.amnezia.awg.config.Config;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AwgProfile {
    private static final String PREFIX = "awg://";
    private static final int MAX_BYTES = 256 * 1024;
    static final String ARTIFACT_VERSION = "2.3.7";

    static boolean isAwg(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (data.size() + count > MAX_BYTES) throw new IOException("Профиль слишком большой");
            data.write(buffer, 0, count);
        }
        return new String(data.toByteArray(), StandardCharsets.UTF_8);
    }

    static String encode(String value) throws Exception {
        if (isAwg(value)) {
            parse(value);
            return value;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Профиль слишком большой");
        Config config = validate(Config.parse(new ByteArrayInputStream(bytes)));
        String host = config.getPeers().get(0).getEndpoint().get().getHost();
        return PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)
                + "#" + Uri.encode("AWG · " + host);
    }

    static Config parse(String link) throws Exception {
        String payload = link.substring(PREFIX.length()).split("#", 2)[0];
        if (payload.length() > MAX_BYTES * 2) throw new IOException("Профиль слишком большой");
        return validate(Config.parse(new ByteArrayInputStream(Base64.decode(payload, Base64.URL_SAFE))));
    }

    private static Config validate(Config config) throws IOException {
        if (config.getInterface().getAddresses().isEmpty() || config.getPeers().isEmpty()
                || !config.getPeers().get(0).getEndpoint().isPresent()) {
            throw new IOException("В профиле нужны адрес интерфейса и сервер");
        }
        if (!config.getProxies().isEmpty()) throw new IOException("Прокси AWG не поддерживается");
        return config;
    }

    static String nativeConfig(Config config, Context context, boolean verify) {
        String nativeConfig = config.toAwgQuickStringResolved(false, false, false, context);
        if (!verify) return nativeConfig;
        // Trigger an initial handshake even when the profile has no keepalive.
        String[] sections = nativeConfig.split("(?m)(?=^\\[Peer\\]$)");
        StringBuilder result = new StringBuilder(sections[0]);
        for (int i = 1; i < sections.length; i++) {
            String peer = sections[i].replaceAll("(?im)^PersistentKeepalive[ \\t]*=[ \\t]*0[ \\t]*\\r?\\n", "");
            if (!peer.contains("PersistentKeepalive")) peer = peer.replace("[Peer]\n", "[Peer]\nPersistentKeepalive = 25\n");
            result.append(peer);
        }
        return result.toString();
    }

    static long handshakeTime(String config) {
        long latest = 0;
        long seconds = 0;
        for (String line : config.split("\n")) {
            if (line.startsWith("last_handshake_time_sec=")) {
                seconds = Long.parseLong(line.substring("last_handshake_time_sec=".length()));
                latest = Math.max(latest, seconds * 1000);
            } else if (line.startsWith("last_handshake_time_nsec=") && seconds > 0) {
                long nanos = Long.parseLong(line.substring("last_handshake_time_nsec=".length()));
                latest = Math.max(latest, seconds * 1000 + nanos / 1_000_000);
            }
        }
        return latest;
    }

    static void loadNative() { System.loadLibrary("am-go"); }
}
