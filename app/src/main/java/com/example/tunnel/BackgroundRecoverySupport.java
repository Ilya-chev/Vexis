package com.example.tunnel;

import android.content.Context;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class BackgroundRecoverySupport {
    static java.util.Map<String,ArrayList<String>> refresh(Context context, Set<String> urls, BooleanSupplier active) {
        java.util.Map<String,ArrayList<String>> refreshed=new java.util.LinkedHashMap<>();
        try (LocalDatabase db = new LocalDatabase(context)) {
            for (String url : urls) {
                if (!active.getAsBoolean() || !SubscriptionNetwork.refreshAllowed(context)) return refreshed;
                if (url.startsWith("local:") || !db.urls().contains(url)) continue;
                String previous = db.subscriptionInfo(url)[2];
                String subscriptionName = db.title(url);
                VpnJournal.add(context, "Обновление подписки: " + subscriptionName, true);
                try {
                    SubscriptionNetwork.Payload payload = SubscriptionNetwork.fetch(url);
                    ArrayList<String> links = new ArrayList<>(payload.links);
                    if (active.getAsBoolean()) {
                        boolean applied=db.updateSubscriptionInfo(url, payload.title, links,
                                payload.userinfo, payload.announcement, previous == null ? "0" : previous);
                        if(applied){
                            SubscriptionRefreshJob.recordSuccess(context,url);
                            refreshed.put(url,new ArrayList<>(links));
                            VpnJournal.add(context, "Получено обновление подписки: " + db.title(url) + " · серверов: " + links.size(), false);
                            context.sendBroadcast(new android.content.Intent(SubscriptionRefreshJob.CHANGED).setPackage(context.getPackageName()),TunnelService.STATE_PERMISSION);
                        }
                    }
                } catch (Exception ignored) {
                    if(active.getAsBoolean())SubscriptionRefreshJob.recordFailure(context,url);
                    VpnJournal.add(context, "Не удалось обновить подписку: " + subscriptionName, false);
                }
            }
        }
        return refreshed;
    }

    static boolean checkEngines(Context context, BooleanSupplier active) {
        return EngineUpdates.checkBackground(context, active);
    }
}
