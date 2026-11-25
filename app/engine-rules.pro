-dontobfuscate
-dontoptimize
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class org.amnezia.awg.GoBackend { *; }
# Optional AWG backends are unused. Tunnel supplies its own VpnService and uses system DNS.
-dontwarn androidx.**
-dontwarn okhttp3.**
-dontwarn com.getkeepsafe.relinker.**
-dontwarn com.zaneschepke.**
-dontwarn org.amnezia.awg.hevtunnel.TProxyService
# EnumMap and ZXing access enum values through reflection.
-keepclassmembers enum com.google.zxing.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
