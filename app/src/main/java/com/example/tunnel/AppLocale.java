package com.example.tunnel;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Applies the selected application language without changing the device language. */
final class AppLocale {
    private static final String KEY = "language_mode";
    private static final java.util.Set<String> SUPPORTED = new java.util.HashSet<>(java.util.Arrays.asList("ru", "en", "ar", "fr", "zh"));
    private AppLocale() {}

    static String getMode(Context context) {
        String mode = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
                .getString(KEY, "system");
        return SUPPORTED.contains(mode) ? mode : "system";
    }

    static void setMode(Context context, String mode) {
        String value = SUPPORTED.contains(mode) ? mode : "system";
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString(KEY, value).apply();
    }

    static Context context(Context base) {
        String mode = getMode(base);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Locale locale;
        if ("system".equals(mode)) {
            android.os.LocaleList locales = config.getLocales();
            locale = chooseSystemLocale(locales);
        } else locale = Locale.forLanguageTag(mode);
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    private static Locale chooseSystemLocale(android.os.LocaleList locales) {
        for (int i = 0; i < locales.size(); i++) {
            Locale candidate = locales.get(i);
            if (SUPPORTED.contains(candidate.getLanguage())) return candidate;
        }
        Locale primary = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        String region = primary.getCountry().toUpperCase(Locale.ROOT);
        String language = primary.getLanguage();
        if (java.util.Arrays.asList("uk","be","kk","ky","tg","uz","tk").contains(language)
                || java.util.Arrays.asList("RU","BY","KZ","KG","TJ","UZ","TM","UA").contains(region))
            return Locale.forLanguageTag("ru");
        if (java.util.Arrays.asList("AE","BH","DZ","EG","IQ","JO","KW","LB","LY","MA","MR","OM","PS","QA","SA","SD","SY","TN","YE").contains(region))
            return Locale.forLanguageTag("ar");
        if (java.util.Arrays.asList("CN","TW","HK","MO").contains(region)) return Locale.forLanguageTag("zh");
        if (java.util.Arrays.asList("FR","BE","CH","LU","MC","SN","CI","CM").contains(region)) return Locale.forLanguageTag("fr");
        return Locale.forLanguageTag("en");
    }
}
