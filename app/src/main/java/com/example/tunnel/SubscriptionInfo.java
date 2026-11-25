package com.example.tunnel;

import android.util.Base64;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import org.json.JSONObject;

public final class SubscriptionInfo {
 private static final Pattern FLAG_PATTERN=Pattern.compile("[\\x{1F1E6}-\\x{1F1FF}]{2}");
 private static final Pattern COUNTRY_CODE_PATTERN=Pattern.compile("(?i)(?:^|[^a-z])([a-z]{2})(?:[^a-z]|$)");
 private static final Set<String> ISO_COUNTRIES=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Locale.getISOCountries())));
 public static String title(String header, String url) {
  if(header != null && !header.trim().isEmpty()) {
   String s=header.trim();
   try { if(s.regionMatches(true,0,"base64:",0,7)) s=XrayPing.decode(s.substring(7)); else s=android.net.Uri.decode(s); }
   catch(Exception ignored) { s=""; }
   if(!s.trim().isEmpty()) return s.trim();
  }
  try { return new URL(url).getHost(); } catch(Exception e) { return "Подписка"; }
 }
 public static String withoutFlagEmoji(String value) {
  if (value == null) return "";
  return value.replaceAll("[\\x{1F1E6}-\\x{1F1FF}]{2}", "").trim();
 }
 public static String name(String link) {
  try {
   int fragment=link.indexOf('#');
   if(fragment>=0){String label=android.net.Uri.decode(link.substring(fragment+1)).trim();if(!label.isEmpty())return label;}
   if(link.regionMatches(true,0,"vmess://",0,8)){
    JSONObject profile=new JSONObject(XrayPing.decode(link.substring(8,fragment<0?link.length():fragment)));
    String label=profile.optString("ps").trim();
    if(!label.isEmpty())return label;
    String endpoint=profile.optString("add").trim();
    if(!endpoint.isEmpty())return endpoint;
   }
   android.net.Uri uri=android.net.Uri.parse(link);
   String host=uri.getHost();
   if(host!=null&&!host.isEmpty()&&!AwgProfile.isAwg(link)&&!"ss".equalsIgnoreCase(uri.getScheme()))return host;
  }catch(Exception ignored){}
  return "Сервер";
 }
 public static String connectionType(String link) {
  if (AwgProfile.isAwg(link)) return "AWG / UDP";
  try {
   android.net.Uri uri = android.net.Uri.parse(link);
   String scheme = uri.getScheme();
   if (scheme == null) return "Неизвестный протокол";
   String protocol = scheme.toUpperCase(Locale.ROOT);
   if ("SS".equals(protocol)) protocol = "SHADOWSOCKS";
   String transport = null;
   if ("VMESS".equals(protocol)) {
    String payload = link.substring(link.indexOf("://") + 3).split("#", 2)[0];
    transport = new JSONObject(XrayPing.decode(payload)).optString("net", "tcp");
   } else if ("VLESS".equals(protocol) || "TROJAN".equals(protocol)) {
    transport = uri.getQueryParameter("type");
   } else if ("SHADOWSOCKS".equals(protocol) || "SOCKS".equals(protocol)) {
    transport = "tcp";
   }
   if (transport == null || transport.trim().isEmpty()) transport = "tcp";
   if ("raw".equalsIgnoreCase(transport)) transport = "tcp";
   if ("splithttp".equalsIgnoreCase(transport)) transport = "xhttp";
   return protocol + " / " + transport.toUpperCase(Locale.ROOT);
  } catch (Exception e) {
   return "Неизвестный протокол";
  }
 }
 public static String country(String text) {
  Matcher m=FLAG_PATTERN.matcher(text);
  if(m.find()){String f=m.group();int a=f.codePointAt(0),b=f.codePointAt(Character.charCount(a));return display(""+(char)(65+a-0x1F1E6)+(char)(65+b-0x1F1E6));}
  String s=text.toLowerCase(Locale.ROOT);
  String[][] names={{"US","usa","united states","america","сша","америка"},{"GB","united kingdom","great britain","england","britain","великобритания","англия","лондон","london"},{"DE","germany","deutschland","german","германия","frankfurt","франкфурт"},{"NL","netherlands","holland","нидерланд","голланд","amsterdam","амстердам"},{"FR","france","франция","француз","paris","париж"},{"FI","finland","финлянд","helsinki","хельсинки"},{"RU","russia","россия","росси","moscow","москва"},{"PL","poland","польш","warsaw","варшав"},{"TR","turkey","türkiye","турци","istanbul","стамбул"},{"SE","sweden","швеци","stockholm","стокгольм"},{"CH","switzerland","швейцар","zurich","цюрих"},{"JP","japan","япони","tokyo","токио"},{"SG","singapore","сингапур"},{"CA","canada","канад"},{"UA","ukraine","украин"},{"KZ","kazakhstan","казахстан"},{"EE","estonia","эстони"},{"LV","latvia","латви"},{"LT","lithuania","литва","литвы"},{"AE","emirates","эмираты","dubai","дубай"}};
  for(String[] row:names)for(int i=1;i<row.length;i++)if(s.contains(row[i]))return display(row[0]);
  Matcher code=COUNTRY_CODE_PATTERN.matcher(text);while(code.find()){String c=code.group(1).toUpperCase(Locale.ROOT);if(ISO_COUNTRIES.contains(c))return display(c);}
  return "";
 }
 public static String countryLabel(android.content.Context context,String code){
  if(code.isEmpty())return AppLocale.context(context).getString(R.string.country_unknown);
  Locale locale=AppLocale.context(context).getResources().getConfiguration().getLocales().get(0);
  return new Locale("",code).getDisplayCountry(locale);
 }
 private static String display(String code){return code;}
}
