package com.example.tunnel;

import android.content.Context;
import android.net.VpnService;
import android.os.SystemClock;

import org.amnezia.awg.ProxyGoBackend;
import org.amnezia.awg.backend.SocketProtector;
import org.amnezia.awg.config.Config;
import org.amnezia.awg.config.proxy.Socks5Proxy;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/** AWG userspace network exposed only on an authenticated loopback SOCKS5 listener. */
final class AwgRoutingBackend implements Closeable {
    private final int port;
    private final String username;
    private final String password;
    private final AtomicBoolean protectFailed = new AtomicBoolean();
    private final SocketProtector protector;
    private int handle = -1;
    private boolean closed;

    private AwgRoutingBackend(VpnService service, int port, String username, String password) {
        this.port = port;
        this.username = username;
        this.password = password;
        this.protector = fd -> {
            boolean ok;
            try { ok = service.protect(fd); } catch (Exception e) { ok = false; }
            if (!ok) protectFailed.set(true);
            return ok ? 1 : 0;
        };
    }

    static AwgRoutingBackend start(Context context, VpnService service, Config profile, boolean verify) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            port = socket.getLocalPort();
        }
        byte[] secret = new byte[24];
        new SecureRandom().nextBytes(secret);
        String password = android.util.Base64.encodeToString(secret,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        AwgRoutingBackend backend = new AwgRoutingBackend(service, port, "vexi", password);
        String config = AwgProfile.nativeConfig(profile, context, verify)
                + "\n" + new Socks5Proxy("127.0.0.1:" + port, backend.username, password).toQuickString();
        AwgProfile.loadNative();
        ProxyGoBackend.awgSetSocketProtector(backend.protector);
        try {
            backend.handle = ProxyGoBackend.awgStartProxy("tunnel-awg", config,
                    context.getDataDir().getAbsolutePath(), 1);
            if (backend.handle < 0) throw new IOException("AWG proxy startup failed: " + backend.handle);
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (true) {
                if (backend.protectFailed.get()) throw new IOException("AWG socket protection failed");
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                    break;
                } catch (IOException e) {
                    if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("AWG proxy unavailable", e);
                    Thread.sleep(50);
                }
            }
            return backend;
        } catch (Exception | LinkageError e) {
            backend.close();
            throw e;
        }
    }

    int getSocksPort() { return port; }
    String getSocksUsername() { return username; }
    String getSocksPassword() { return password; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            if (handle >= 0) ProxyGoBackend.awgStopProxy();
        } catch (LinkageError ignored) {
        } finally {
            handle = -1;
            try { ProxyGoBackend.awgResetJNIGlobals(); } catch (LinkageError ignored) { }
        }
    }
}
