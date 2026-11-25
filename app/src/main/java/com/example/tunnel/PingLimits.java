package com.example.tunnel;

import android.content.SharedPreferences;

final class PingLimits {
    private PingLimits() { }
    static int deviceCapacity(android.content.Context context) {
        android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        int memory=manager==null?128:manager.getMemoryClass();
        return Math.max(4,Math.min(100,memory/16));
    }
    static int forCount(SharedPreferences settings, int count) {
        int fixed = settings.getInt("ping_fixed", 10);
        if (fixed > 0) return Math.min(count, fixed);
        int denominator = Math.max(1, settings.getInt("ping_denominator", 5));
        int minimum = settings.getInt("ping_min_total", 10);
        int maximum = Math.max(1, settings.getInt("ping_max", 100));
        int proportional = count < minimum ? count : (int)(((long) count + denominator - 1) / denominator);
        return Math.min(maximum, proportional);
    }
}
