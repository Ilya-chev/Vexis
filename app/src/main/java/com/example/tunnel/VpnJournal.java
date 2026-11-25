package com.example.tunnel;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A file lock protects the journal across the app's VPN processes. */
final class VpnJournal {
    private static final int MAX = 1000;
    private static final int MAX_MESSAGE = 500;
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor();

    static void add(Context context, String message, boolean detailed) {
        Context app = context.getApplicationContext();
        WRITER.execute(() -> withJournal(app, ServiceText.translate(app, message), detailed));
    }

    static String read(Context context) {
        return withJournal(context, null, false);
    }

    private static synchronized String withJournal(Context context, String message, boolean detailed) {
        File dir = context.getFilesDir();
        File file = new File(dir, "vpn-journal.txt");
        try (RandomAccessFile lockFile = new RandomAccessFile(new File(dir, "vpn-journal.lock"), "rw");
             FileLock ignored = lockFile.getChannel().lock()) {
            boolean exists = file.exists();
            try (RandomAccessFile journal = new RandomAccessFile(file, "rw")) {
                String entries;
                if (!exists) {
                    // Import the previous single-process journal once, while holding the lock.
                    entries = context.getSharedPreferences("vpn_journal", Context.MODE_PRIVATE)
                            .getString("entries", "");
                } else {
                    byte[] bytes = new byte[(int) journal.length()];
                    journal.readFully(bytes);
                    entries = new String(bytes, StandardCharsets.UTF_8);
                }
                if (message != null) {
                    String entry = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(new Date())
                            + (detailed ? " D " : " I ") + safe(message) + "\n";
                    entries = entry + entries;
                }
                if (!exists || message != null) {
                    StringBuilder trimmed = new StringBuilder();
                    int count = 0;
                    for (String line : entries.split("\n")) {
                        if (!line.isEmpty() && count++ < MAX) trimmed.append(exists ? line : safe(line)).append('\n');
                        if (count >= MAX) break;
                    }
                    entries = trimmed.toString();
                    journal.seek(0);
                    journal.setLength(0);
                    journal.write(entries.getBytes(StandardCharsets.UTF_8));
                }
                return entries;
            }
        } catch (Exception e) {
            Log.w("VpnJournal", "Journal unavailable");
            return "";
        }
    }

    private static String safe(String value) {
        String clean = value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("(?i)\\b[a-z][a-z0-9+.-]*://\\S+", "[адрес скрыт]")
                .replaceAll("(?i)\\b(?:private[_ -]?key|public[_ -]?key|preshared[_ -]?key|password|token|secret|uuid)\\s*[:=]\\s*\\S+", "[данные скрыты]");
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }
}
