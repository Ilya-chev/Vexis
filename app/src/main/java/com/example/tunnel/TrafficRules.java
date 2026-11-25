package com.example.tunnel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class TrafficRules {
    static final String KEY = "traffic_rules";
    static final String VPN_DNS_ADDRESS = "10.71.0.2";
    static final String DEFAULT = "{\"domainStrategy\":\"AsIs\",\"defaultRoute\":\"proxy\",\"rules\":[]}";
    static final String LOCAL = "{\"domainStrategy\":\"AsIs\",\"defaultRoute\":\"proxy\","
            + "\"rules\":[{\"type\":\"field\",\"ip\":[\"geoip:private\"],\"outboundTag\":\"direct\"}]}";
    static final String LOCAL_RU = "{\"domainStrategy\":\"AsIs\",\"defaultRoute\":\"proxy\","
            + "\"rules\":[{\"type\":\"field\",\"ip\":[\"geoip:private\",\"geoip:ru\"],"
            + "\"outboundTag\":\"direct\"},{\"type\":\"field\",\"domain\":[\"domain:ru\",\"domain:su\",\"domain:xn--p1ai\"],"
            + "\"outboundTag\":\"direct\"}]}";
    static final Set<String> LIST_FIELDS = new HashSet<>(Arrays.asList("domain", "ip", "sourceIP", "source", "protocol"));
    static final Set<String> PORT_FIELDS = new HashSet<>(Arrays.asList("port", "sourcePort"));
    private static final Set<String> ACTIONS = new HashSet<>(Arrays.asList("proxy", "direct", "block"));
    private static final Set<String> STRATEGIES = new HashSet<>(Arrays.asList("AsIs", "IPIfNonMatch", "IPOnDemand"));

    static JSONObject parse(String input) throws JSONException {
        JSONObject root = new JSONObject(input);
        JSONObject source = root.has("routing") ? root.getJSONObject("routing") : root;
        String strategy = source.optString("domainStrategy", "AsIs");
        String fallback = source.optString("defaultRoute", root.optString("defaultRoute", "proxy"));
        if (!STRATEGIES.contains(strategy) || !ACTIONS.contains(fallback)) {
            throw new JSONException("Неверная стратегия или маршрут по умолчанию");
        }
        if (source.has("script") || source.has("balancers")) {
            throw new JSONException("Скрипты и балансировщики не поддерживаются");
        }
        JSONArray inputRules = source.getJSONArray("rules");
        JSONArray rules = new JSONArray();
        for (int i = 0; i < inputRules.length(); i++) {
            try {
                rules.put(normalizeRule(inputRules.getJSONObject(i)));
            } catch (JSONException e) {
                throw new JSONException("Правило " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new JSONObject().put("domainStrategy", strategy).put("defaultRoute", fallback).put("rules", rules);
    }

    static JSONObject normalizeRule(JSONObject input) throws JSONException {
        String action = input.optString("outboundTag");
        if (!ACTIONS.contains(action) || !"field".equals(input.optString("type", "field"))) {
            throw new JSONException("Допустимые маршруты: proxy, direct, block");
        }
        JSONObject rule = new JSONObject().put("type", "field").put("outboundTag", action);
        boolean hasCondition = false;
        Iterator<String> keys = input.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("type".equals(key) || "outboundTag".equals(key)) continue;
            if ("__name__".equals(key) || "ruleTag".equals(key)) {
                rule.put(key, input.getString(key));
                continue;
            }
            if (LIST_FIELDS.contains(key)) {
                JSONArray list = input.getJSONArray(key);
                if (list.length() == 0) continue;
                for (int i = 0; i < list.length(); i++) {
                    if (!(list.get(i) instanceof String) || list.getString(i).trim().isEmpty()) {
                        throw new JSONException("Пустое значение в " + key);
                    }
                }
                rule.put(key, list);
            } else if (PORT_FIELDS.contains(key)) {
                String ports = input.getString(key).replace(" ", "");
                for (String part : ports.split(",", -1)) {
                    if (!part.matches("[0-9]{1,5}(-[0-9]{1,5})?")) throw new JSONException("Неверный порт");
                    String[] range = part.split("-");
                    int low = Integer.parseInt(range[0]);
                    int high = Integer.parseInt(range[range.length - 1]);
                    if (low < 1 || high > 65535 || low > high) throw new JSONException("Неверный диапазон портов");
                }
                rule.put(key, ports);
            } else if ("network".equals(key)) {
                String network = input.getString(key).replace(" ", "");
                if (!network.matches("tcp|udp|tcp,udp|udp,tcp")) throw new JSONException("Допустимые сети: tcp, udp");
                rule.put(key, network);
            } else {
                throw new JSONException("Неподдерживаемое поле " + key);
            }
            hasCondition = true;
        }
        if (!hasCondition) throw new JSONException("Добавьте хотя бы одно условие");
        return rule;
    }

    static JSONObject vpnConfig(JSONObject proxy, JSONObject profile, int geoPort, String geoPassword,
                                boolean directIpv6, int mtu) throws JSONException {
        JSONObject direct = new JSONObject().put("protocol", "freedom").put("tag", "direct");
        JSONObject block = new JSONObject().put("protocol", "blackhole").put("tag", "block");
        JSONArray outbounds = new JSONArray();
        JSONObject[] candidates = {proxy, direct, block};
        String defaultOutbound = profile.getString("defaultRoute");
        for (JSONObject outbound : candidates) if (defaultOutbound.equals(outbound.getString("tag"))) outbounds.put(outbound);
        for (JSONObject outbound : candidates) if (!defaultOutbound.equals(outbound.getString("tag"))) outbounds.put(outbound);

        JSONObject tun = new JSONObject().put("protocol", "tun").put("port", 0).put("tag", "tun")
                .put("settings", new JSONObject().put("name", "tunnel").put("mtu", mtu))
                .put("sniffing", new JSONObject().put("enabled", true).put("routeOnly", true)
                        .put("destOverride", new JSONArray().put("http").put("tls").put("quic")));
        JSONArray routingRules = new JSONArray();
        routingRules.put(new JSONObject().put("type", "field")
                .put("inboundTag", new JSONArray().put("geo-in")).put("outboundTag", "proxy"));
        JSONArray configuredRules = profile.getJSONArray("rules");
        if (profile.optJSONObject("dns") != null) {
            routingRules.put(new JSONObject().put("type", "field")
                    .put("inboundTag", new JSONArray().put(RoutingPresets.DIRECT_DNS_TAG))
                    .put("outboundTag", "direct"));
            routingRules.put(new JSONObject().put("type", "field")
                    .put("inboundTag", new JSONArray().put(RoutingPresets.REMOTE_DNS_TAG))
                    .put("outboundTag", "proxy"));
            JSONObject dnsOutbound = new JSONObject().put("protocol", "dns").put("tag", "dns-out");
            if (!directIpv6) {
                // On an IPv4-only uplink, a direct IPv6 connection through TUN closes after TCP connects.
                // Return an empty AAAA answer for direct domains so clients can use the A record.
                JSONArray dnsRules = ipv6DnsRules(configuredRules);
                if (dnsRules.length() > 0) dnsOutbound.put("settings", new JSONObject().put("rules", dnsRules));
            }
            outbounds.put(dnsOutbound);
            routingRules.put(new JSONObject().put("type", "field").put("inboundTag", new JSONArray().put("tun"))
                    .put("ip", new JSONArray().put(VPN_DNS_ADDRESS)).put("port", "53")
                    .put("outboundTag", "dns-out"));
        }
        for (int i = 0; i < configuredRules.length(); i++) {
            JSONObject rule = new JSONObject(configuredRules.getJSONObject(i).toString());
            rule.put("inboundTag", new JSONArray().put("tun"));
            routingRules.put(rule);
        }
        JSONObject geoInbound = new JSONObject().put("protocol", "http").put("tag", "geo-in")
                .put("listen", "127.0.0.1").put("port", geoPort)
                .put("settings", new JSONObject().put("accounts", new JSONArray().put(
                        new JSONObject().put("user", "tunnel").put("pass", geoPassword))));
        JSONObject config = new JSONObject().put("inbounds", new JSONArray().put(tun).put(geoInbound)).put("outbounds", outbounds)
                .put("log", new JSONObject().put("loglevel", "none"))
                .put("stats", new JSONObject())
                .put("routing", new JSONObject().put("domainStrategy", profile.getString("domainStrategy"))
                        .put("rules", routingRules));
        if (profile.optJSONObject("dns") != null) config.put("dns", profile.getJSONObject("dns"));
        return config;
    }

    private static JSONArray ipv6DnsRules(JSONArray configuredRules) throws JSONException {
        JSONArray result = new JSONArray();
        int lastDirect = -1;
        for (int i = 0; i < configuredRules.length(); i++) {
            JSONObject rule = configuredRules.getJSONObject(i);
            JSONArray domains = rule.optJSONArray("domain");
            if (domains == null) continue;
            int fields = rule.length() - (rule.has("ruleTag") ? 1 : 0) - (rule.has("__name__") ? 1 : 0);
            // A DNS lookup has no destination port, protocol or IP to test extra conditions against.
            if (fields != 3) continue;
            boolean direct = "direct".equals(rule.getString("outboundTag"));
            result.put(new JSONObject().put("qType", 28).put("domain", domains)
                    .put("action", direct ? "return" : "hijack"));
            if (direct) lastDirect = result.length() - 1;
        }
        while (result.length() > lastDirect + 1) result.remove(result.length() - 1);
        return result;
    }
}
