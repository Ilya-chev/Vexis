package com.example.tunnel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class RoutingPresets {
    static final String DIRECT_DNS_TAG = "domestic-dns";
    static final String REMOTE_DNS_TAG = "remote-dns";
    private static final String BUILTIN = "direct-russia-v1";
    private static final String LIST = "routing_presets_v1";
    private static final String ENABLED = "routing_presets_enabled";
    private static final String DIRECT_PRESET_ADDED = "direct_preset_added_v1";
    private static final String ROUTING_MIGRATED = "direct_preset_v2_migrated";
    private static final String PREFIX = "happ://routing/";
    static JSONArray load(SharedPreferences prefs) throws JSONException {
        String saved = prefs.getString(LIST, null);
        if (saved != null) {
            JSONArray sets = new JSONArray(saved);
            if (!prefs.getBoolean(ROUTING_MIGRATED, false)) {
                JSONArray updated = new JSONArray();
                boolean hadDirect = false;
                boolean hadOldHapp = false;
                for (int i = 0; i < sets.length(); i++) {
                    JSONObject item = sets.getJSONObject(i);
                    JSONObject data = item.optJSONObject("data");
                    if (data != null && "Happ Plus · Россия напрямую".equals(data.optString("Name"))) {
                        hadOldHapp = true;
                        continue;
                    }
                    if (isBuiltIn(item)) {
                        JSONObject replacement = directPreset();
                        replacement.put("enabled", item.optBoolean("enabled", true));
                        updated.put(replacement);
                        hadDirect = true;
                    } else {
                        updated.put(item);
                    }
                }
                if (!hadDirect && (hadOldHapp || !prefs.getBoolean(DIRECT_PRESET_ADDED, false))) {
                    JSONArray ordered = new JSONArray().put(directPreset());
                    for (int i = 0; i < updated.length(); i++) ordered.put(updated.get(i));
                    updated = ordered;
                }
                prefs.edit().putString(LIST, updated.toString())
                        .putBoolean(DIRECT_PRESET_ADDED, true).putBoolean(ROUTING_MIGRATED, true).commit();
                return updated;
            }
            boolean builtinFound = false;
            for (int i = 0; i < sets.length(); i++) if (isBuiltIn(sets.getJSONObject(i))) builtinFound = true;
            if (!builtinFound) { sets.put(directPreset()); prefs.edit().putString(LIST, sets.toString()).apply(); }
            return sets;
        }
        JSONArray sets = new JSONArray();
        sets.put(directPreset());
        String old = prefs.getString(TrafficRules.KEY,null);
        if(old!=null){JSONObject legacy=TrafficRules.parse(old);if(legacy.getJSONArray("rules").length()>0)sets.put(new JSONObject().put("name","Ранее сохранённые правила").put("legacy",legacy).put("enabled",false));}
        prefs.edit().putString(LIST,sets.toString()).putBoolean(DIRECT_PRESET_ADDED,true)
                .putBoolean(ROUTING_MIGRATED,true).commit();
        return sets;
    }
    private static JSONObject directPreset() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("Name", "Россия напрямую");
        data.put("GlobalProxy", true);
        data.put("bypassLan", true);
        data.put("RouteOrder", "block-proxy-direct");
        data.put("DomainStrategy", "AsIs");
        data.put("RemoteDNSType", "DoH");
        data.put("RemoteDNSDomain", "https://cloudflare-dns.com/dns-query");
        data.put("RemoteDNSIP", "1.1.1.1");
        data.put("DomesticDNSType", "DoH");
        data.put("DomesticDNSDomain", "https://common.dot.dns.yandex.net/dns-query");
        data.put("DomesticDNSIP", "77.88.8.8");
        data.put("DnsHosts", new JSONObject()
                .put("cloudflare-dns.com", "1.1.1.1")
                .put("common.dot.dns.yandex.net", "77.88.8.8"));
        data.put("DirectSites", new JSONArray(Arrays.asList(
                "domain:ru", "domain:xn--p1ai", "domain:su",
                "geosite:private", "geosite:tld-ru", "geosite:category-ru", "geosite:category-gov-ru",
                "geosite:category-bank-ru", "geosite:category-ecommerce-ru",
                "domain:vk.com", "domain:avito.st")));
        data.put("DirectIp", new JSONArray(Arrays.asList("geoip:private", "geoip:ru")));
        return new JSONObject().put("data", data).put("enabled", false)
                .put("geosite", "v2ray-geosite.dat")
                .put("geoip", "v2ray-geoip.dat")
                .put("builtin", BUILTIN);
    }
    // Geo files use protobuf GeoSiteList/GeoIPList: repeated field 1, each entry's name is field 1.
    static String[] bundledGeoNames(Context context, String asset) throws IOException {
        ArrayList<String> names = new ArrayList<>();
        try (InputStream in = context.getAssets().open(asset)) {
            int tag;
            while ((tag = in.read()) != -1) {
                if (tag != 10) throw new IOException("Неверный формат геофайла");
                long size = readVarint(in);
                if (size < 0 || size > 30L * 1024 * 1024) throw new IOException("Неверный размер геозаписи");
                byte[] entry = new byte[(int) size];
                int offset = 0;
                while (offset < entry.length) {
                    int count = in.read(entry, offset, entry.length - offset);
                    if (count < 0) throw new EOFException();
                    offset += count;
                }
                ByteArrayInputStream item = new ByteArrayInputStream(entry);
                if (item.read() != 10) throw new IOException("Нет названия геозаписи");
                long length = readVarint(item);
                if (length <= 0 || length > item.available()) throw new IOException("Неверное название геозаписи");
                byte[] name = new byte[(int) length];
                if (item.read(name) != name.length) throw new EOFException();
                names.add(new String(name, "UTF-8").toLowerCase(Locale.ROOT));
            }
        }
        Collections.sort(names);
        return names.toArray(new String[0]);
    }
    private static long readVarint(InputStream in) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int b = in.read();
            if (b < 0) throw new EOFException();
            result |= (long) (b & 127) << shift;
            if ((b & 128) == 0) return result;
        }
        throw new IOException("Неверный формат геофайла");
    }
    static JSONObject create(String name, boolean globalProxy, boolean bypassLan,
                             JSONArray directSites, JSONArray directIp,
                             JSONArray proxySites, JSONArray proxyIp,
                             JSONArray blockSites, JSONArray blockIp) throws JSONException {
        JSONObject data = new JSONObject().put("Name", name.trim()).put("GlobalProxy", globalProxy)
                .put("RouteOrder", "block-proxy-direct").put("DomainStrategy", "AsIs").put("bypassLan", bypassLan)
                .put("DirectSites", directSites).put("DirectIp", directIp)
                .put("ProxySites", proxySites).put("ProxyIp", proxyIp)
                .put("BlockSites", blockSites).put("BlockIp", blockIp);
        if (bypassLan) {
            directSites.put("geosite:private");
            directIp.put("geoip:private");
        }
        JSONObject item = new JSONObject().put("data", data).put("enabled", true).put("editable", true)
                .put("geosite", "v2ray-geosite.dat").put("geoip", "v2ray-geoip.dat");
        compileOne(item);
        return item;
    }
    static void validateValue(String value, boolean ip) throws JSONException {
        if (ip) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2) throw new JSONException("Неверный IP / CIDR: " + value);
            String address = parts[0];
            boolean v6 = address.contains(":");
            try {
                if (v6) {
                    if (!address.matches("[0-9a-fA-F:.]+") || !(InetAddress.getByName(address) instanceof Inet6Address)) throw new Exception();
                } else {
                    String[] octets = address.split("\\.", -1);
                    if (octets.length != 4) throw new Exception();
                    for (String octet : octets) if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > 255) throw new Exception();
                }
                if (parts.length == 2 && (!parts[1].matches("[0-9]{1,3}") || Integer.parseInt(parts[1]) > (v6 ? 128 : 32))) throw new Exception();
            } catch (Exception e) { throw new JSONException("Неверный IP / CIDR: " + value); }
        } else {
            String body = value;
            int colon = value.indexOf(':');
            if (colon >= 0) {
                String prefix = value.substring(0, colon);
                if (!Arrays.asList("domain", "full", "regexp", "keyword").contains(prefix)) throw new JSONException("Неверный тип домена: " + value);
                body = value.substring(colon + 1);
                if ("regexp".equals(prefix)) {
                    try { java.util.regex.Pattern.compile(body); } catch (Exception e) { throw new JSONException("Неверное выражение: " + value); }
                }
            }
            if (body.isEmpty() || body.matches(".*\\s+.*")) throw new JSONException("Неверный домен: " + value);
        }
    }
    static boolean isBuiltIn(JSONObject item) { return BUILTIN.equals(item.optString("builtin")); }
    static JSONObject displayData(JSONObject item) throws JSONException { return isBuiltIn(item) ? directPreset().getJSONObject("data") : item.optJSONObject("data"); }
    static void save(SharedPreferences prefs,JSONArray sets){prefs.edit().putString(LIST,sets.toString()).apply();}
    static void installBundledAssets(Context context) throws IOException {
        String[][] files = {{"v2ray-geoip.dat", "v2ray-geoip.dat"},
                {"v2ray-geosite.dat", "v2ray-geosite.dat"},
                {"v2ray-geoip.dat", "geoip.dat"}, {"v2ray-geosite.dat", "geosite.dat"}};
        for (String[] pair : files) {
            android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), pair[1]));
            if (file.getBaseFile().length() > 0) continue;
            try (InputStream in = context.getAssets().open(pair[0])) {
                FileOutputStream out = file.startWrite();
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    file.finishWrite(out);
                } catch (IOException e) {
                    file.failWrite(out);
                    throw e;
                }
            }
        }
    }
    static boolean enabled(SharedPreferences prefs){return prefs.getBoolean(ENABLED,false);}
    static void setEnabled(SharedPreferences prefs,boolean enabled){prefs.edit().putBoolean(ENABLED,enabled).apply();}
    static JSONObject importLink(Context context,String value) throws Exception {
        String text=value.trim();
        if(!text.startsWith(PREFIX+"onadd/")&&!text.startsWith(PREFIX+"add/"))throw new IOException("Нужна ссылка Happ с правилами");
        String encoded=text.substring(text.indexOf('/',PREFIX.length())+1);
        JSONObject data=new JSONObject(new String(Base64.decode(encoded,Base64.DEFAULT),"UTF-8"));
        if(data.optString("Name").trim().isEmpty())throw new IOException("Не указано название правил");
        if (data.optString("Geoipurl").isEmpty()) validateDefaultCategories(context, data, true);
        if (data.optString("Geositeurl").isEmpty()) validateDefaultCategories(context, data, false);
        String ip=download(context,data.optString("Geoipurl"), "geoip.dat");
        String site=download(context,data.optString("Geositeurl"), "geosite.dat");
        JSONObject result=new JSONObject().put("data",data).put("enabled",true).put("geoip",ip).put("geosite",site);
        compileOne(result);
        return result;
    }
    private static void validateDefaultCategories(Context context, JSONObject data, boolean ip) throws Exception {
        // An earlier installation may have its own default database. Preserve its behavior.
        if (new File(context.getFilesDir(), ip ? "geoip.dat" : "geosite.dat").length() > 0) return;
        HashSet<String> available = new HashSet<>(Arrays.asList(bundledGeoNames(context,
                ip ? "v2ray-geoip.dat" : "v2ray-geosite.dat")));
        String prefix = ip ? "geoip:" : "geosite:";
        for (String action : new String[]{"Direct", "Proxy", "Block"}) {
            JSONArray values = data.optJSONArray(action + (ip ? "Ip" : "Sites"));
            if (values == null) continue;
            for (int i = 0; i < values.length(); i++) {
                String value = values.getString(i);
                if (!value.startsWith(prefix)) continue;
                String category = value.substring(prefix.length());
                if (ip && category.startsWith("!")) category = category.substring(1);
                if (!ip && category.contains("@")) category = category.substring(0, category.indexOf('@'));
                if (!available.contains(category.toLowerCase(Locale.ROOT)))
                    throw new IOException("В стандартной геобазе нет категории " + value + ". Укажите геобазу в ссылке правил.");
            }
        }
    }
    private static String download(Context context,String address,String bundled) throws Exception {
        if(address.isEmpty())return bundled;
        URL url=new URL(address);
        if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("Геофайлы должны использовать HTTPS");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(30000);
        String name="routing-"+UUID.randomUUID()+".dat";
        File output=new File(context.getFilesDir(),name);
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Не удалось загрузить геофайл");
            try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(output)){
                byte[] buffer=new byte[8192];int n;long total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>30L*1024*1024)throw new IOException("Слишком большой геофайл");out.write(buffer,0,n);}
                if(total==0)throw new IOException("Пустой геофайл");
            }
            return name;
        }catch(Exception e){output.delete();throw e;}finally{connection.disconnect();}
    }
    static String name(JSONObject item){return item.has("legacy")?item.optString("name"):item.optJSONObject("data").optString("Name","Правила");}
    static JSONObject compile(SharedPreferences prefs) throws JSONException {
        JSONArray all=new JSONArray();String strategy="AsIs",fallback="proxy";boolean first=true;JSONObject dns=null;
        JSONArray presets=load(prefs);
        // Newer imports take precedence. The stock profile fills gaps in custom rules.
        if(enabled(prefs))for(int i=presets.length()-1;i>=0;i--){
            JSONObject item=presets.getJSONObject(i);if(!item.optBoolean("enabled",true))continue;
            JSONObject part=compileOne(item);JSONArray rules=part.getJSONArray("rules");
            for(int j=0;j<rules.length();j++)all.put(rules.get(j));
            if(first){strategy=part.getString("domainStrategy");fallback=part.getString("defaultRoute");dns=part.optJSONObject("dns");first=false;}
        }
        JSONObject result=new JSONObject().put("domainStrategy",strategy).put("defaultRoute",fallback).put("rules",all);
        if(dns!=null)result.put("dns",dns);
        return result;
    }
    private static JSONObject compileOne(JSONObject item) throws JSONException {
        if(item.has("legacy"))return TrafficRules.parse(item.getJSONObject("legacy").toString());
        if(isBuiltIn(item))item=directPreset();
        JSONObject data=item.getJSONObject("data");JSONArray rules=new JSONArray();
        if(data.optBoolean("FakeDNS",false))throw new JSONException("FakeDNS пока не поддерживается");
        String remoteType=data.optString("RemoteDNSType","");
        String domesticType=data.optString("DomesticDNSType","");
        if(!remoteType.isEmpty()&&!remoteType.equalsIgnoreCase("DoH")&&!remoteType.equalsIgnoreCase("DoU"))
            throw new JSONException("Неподдерживаемый тип внешнего DNS");
        if(!domesticType.isEmpty()&&!domesticType.equalsIgnoreCase("DoU")&&!domesticType.equalsIgnoreCase("DoH"))
            throw new JSONException("Неподдерживаемый тип локального DNS");
        String[] order=data.optString("RouteOrder","block-proxy-direct").split("-");
        if(order.length!=3||!new HashSet<>(Arrays.asList(order)).equals(new HashSet<>(Arrays.asList("block","proxy","direct"))))throw new JSONException("Неверный порядок маршрутов");
        for(String action:order){String field=action.equals("direct")?"Direct":action.equals("proxy")?"Proxy":"Block";
            addRule(rules,data.optJSONArray(field+"Sites"),"domain",action,item.optString("geosite"),"geosite:");
            addRule(rules,data.optJSONArray(field+"Ip"),"ip",action,item.optString("geoip"),"geoip:");
        }
        String strategy=data.optString("DomainStrategy","AsIs");
        JSONObject result=TrafficRules.parse(new JSONObject().put("domainStrategy",strategy).put("defaultRoute",data.optBoolean("GlobalProxy",true)?"proxy":"direct").put("rules",rules).toString());
        JSONObject dns=new JSONObject();JSONArray servers=new JSONArray();
        String remote=data.optString("RemoteDNSDomain");String remoteIp=data.optString("RemoteDNSIP");
        String domesticIp=data.optString("DomesticDNSIP");
        String domesticDomain=data.optString("DomesticDNSDomain");
        String domestic = "DoH".equalsIgnoreCase(domesticType) && !domesticDomain.isEmpty()
                ? dohAddress(domesticDomain) : domesticIp;
        String remoteAddress = "DoH".equalsIgnoreCase(remoteType) && !remote.isEmpty()
                ? dohAddress(remote) : remoteIp;
        if(!domestic.isEmpty()&&data.optJSONArray("DirectSites")!=null&&data.optJSONArray("DirectSites").length()>0){
            JSONArray domains=convert(data.getJSONArray("DirectSites"),item.optString("geosite"),"geosite:");
            JSONObject domesticServer = new JSONObject().put("address",domestic).put("domains",domains)
                    .put("tag",DIRECT_DNS_TAG).put("skipFallback",!remoteAddress.isEmpty());
            if (isBuiltIn(item)) domesticServer.put("timeoutMs", 1800);
            servers.put(domesticServer);
            if (isBuiltIn(item)) {
                servers.put(new JSONObject().put("address",domesticIp)
                        .put("domains",domains).put("tag",DIRECT_DNS_TAG).put("skipFallback",true)
                        .put("timeoutMs",1000));
            }
        }
        if(!remoteAddress.isEmpty())servers.put(new JSONObject().put("address",remoteAddress).put("tag",REMOTE_DNS_TAG));
        if(servers.length()>0)dns.put("servers",servers);
        if(data.optJSONObject("DnsHosts")!=null)dns.put("hosts",data.getJSONObject("DnsHosts"));
        if(dns.length()>0)result.put("dns",dns);
        return result;
    }
    private static String dohAddress(String address) throws JSONException {
        try {
            URL url = new URL(address);
            if ("https".equalsIgnoreCase(url.getProtocol()) && !url.getHost().isEmpty()) return address;
        } catch (MalformedURLException ignored) {}
        throw new JSONException("Для DoH нужен адрес HTTPS");
    }
    private static JSONArray convert(JSONArray values,String file,String prefix) throws JSONException {
        JSONArray result=new JSONArray();for(int i=0;i<values.length();i++){String value=values.getString(i);if(value.startsWith(prefix)){if(file.isEmpty())throw new JSONException("Для "+prefix+" нужен геофайл");value="ext:"+file+":"+value.substring(prefix.length());}result.put(value);}return result;
    }
    private static void addRule(JSONArray rules,JSONArray values,String field,String action,String file,String prefix) throws JSONException {
        if(values!=null&&values.length()>0)rules.put(new JSONObject().put("type","field").put(field,convert(values,file,prefix)).put("outboundTag",action));
    }
}
