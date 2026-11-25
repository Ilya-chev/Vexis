package com.example.tunnel;
import android.net.Uri;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.*;
import libv2ray.Libv2ray;

public final class XrayPing {
 static final String CHECK_URL = "https://www.google.com/generate_204";
 static final String FALLBACK_HOST = "cp.cloudflare.com";
 private static final int MAX_NATIVE_PROBES=32;
 private static final java.util.concurrent.Semaphore PROBE_SLOTS=new java.util.concurrent.Semaphore(MAX_NATIVE_PROBES,true);
 private static final java.util.concurrent.ExecutorService PROBES=java.util.concurrent.Executors.newFixedThreadPool(
   MAX_NATIVE_PROBES,runnable->{Thread thread=new Thread(runnable,"xray-ping");thread.setDaemon(true);return thread;});
 public static int timeout(android.content.SharedPreferences prefs) {
  return Math.max(1, Math.min(100000, prefs.getInt("ping_timeout", 10000)));
 }
 public static long measure(String link) throws Exception { return measure(link, 6000); }
 public static long measure(String link, int timeoutMs) throws Exception {
  PROBE_SLOTS.acquire();
  java.util.concurrent.CompletableFuture<Long> future=new java.util.concurrent.CompletableFuture<>();
  try {
   PROBES.execute(()->{
    try{if(!future.isCancelled())future.complete(measureNative(link,()->!future.isCancelled()));}
    catch(Throwable failure){future.completeExceptionally(failure);}
    finally{PROBE_SLOTS.release();}
   });
  }catch(java.util.concurrent.RejectedExecutionException e){PROBE_SLOTS.release();return -1;}
  try { return future.get(Math.max(1, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS); }
  catch (java.util.concurrent.TimeoutException e) { return -1; }
  catch (java.util.concurrent.ExecutionException e) {
   Throwable cause = e.getCause();
   if (cause instanceof Exception) throw (Exception) cause;
   if (cause instanceof Error) throw (Error) cause;
   throw new RuntimeException(cause);
  } finally { future.cancel(true); }
 }
 private static long measureNative(String link,java.util.function.BooleanSupplier active) throws Exception {
  JSONObject config=new JSONObject().put("log",new JSONObject().put("loglevel","none"))
    .put("outbounds",new JSONArray().put(outbound(link)));
  String outboundConfig = config.toString();
  try {
   long delay = Libv2ray.measureOutboundDelay(outboundConfig, CHECK_URL);
   if (delay >= 0) return delay;
  } catch (Exception ignored) { }
  if (!active.getAsBoolean() || Thread.currentThread().isInterrupted()) return -1;
  return Libv2ray.measureOutboundDelay(outboundConfig, "https://" + FALLBACK_HOST + "/generate_204");
 }

 static JSONObject outbound(String link) throws Exception {
  Uri u=Uri.parse(link); String scheme=u.getScheme(); JSONObject server=new JSONObject(); JSONObject settings=new JSONObject(); JSONObject outbound=new JSONObject(); JSONObject stream=new JSONObject();
  if("vless".equalsIgnoreCase(scheme)||"trojan".equalsIgnoreCase(scheme)) {
   if(u.getHost()==null||u.getPort()<1||u.getUserInfo()==null)throw new Exception("Invalid profile");
   server.put("address",u.getHost()).put("port",u.getPort());
   if("vless".equalsIgnoreCase(scheme)){JSONObject user=new JSONObject().put("id",u.getUserInfo()).put("encryption",param(u,"encryption","none"));String flow=u.getQueryParameter("flow");if(flow!=null&&!flow.isEmpty())user.put("flow",flow);server.put("users",new JSONArray().put(user));settings.put("vnext",new JSONArray().put(server));outbound.put("protocol","vless");}
   else {server.put("password",u.getUserInfo());settings.put("servers",new JSONArray().put(server));outbound.put("protocol","trojan");}
   String network=param(u,"type","tcp"),security=param(u,"security","none");stream.put("network",network).put("security",security);
   if("reality".equals(security)){JSONObject r=new JSONObject().put("serverName",param(u,"sni",u.getHost())).put("fingerprint",param(u,"fp","chrome")).put("publicKey",param(u,"pbk","")).put("shortId",param(u,"sid","")).put("spiderX",param(u,"spx","/"));stream.put("realitySettings",r);}
   if("tls".equals(security)){JSONObject t=new JSONObject().put("serverName",param(u,"sni",u.getHost())).put("fingerprint",param(u,"fp","chrome"));stream.put("tlsSettings",t);}
   if("ws".equals(network)){JSONObject ws=new JSONObject().put("path",param(u,"path","/"));String h=u.getQueryParameter("host");if(h!=null)ws.put("headers",new JSONObject().put("Host",h));stream.put("wsSettings",ws);}
   else if("grpc".equals(network))stream.put("grpcSettings",new JSONObject().put("serviceName",param(u,"serviceName","")));
   else if("xhttp".equals(network)||"splithttp".equals(network)){
    stream.put("network","xhttp");
    JSONObject xhttp=new JSONObject();
    String path=u.getQueryParameter("path"), host=u.getQueryParameter("host");
    String mode=u.getQueryParameter("mode"), extra=u.getQueryParameter("extra");
    if(path!=null&&!path.isEmpty())xhttp.put("path",path);
    if(host!=null&&!host.isEmpty())xhttp.put("host",host);
    if(mode!=null&&!mode.isEmpty())xhttp.put("mode",mode);
    if(extra!=null&&!extra.isEmpty())xhttp.put("extra",new JSONObject(extra));
    stream.put("xhttpSettings",xhttp);
   }
   else if(!"tcp".equals(network)&&!"raw".equals(network))throw new Exception("Unsupported transport");
  } else if ("vmess".equalsIgnoreCase(scheme)) {
   String encoded=link.substring(8).split("#",2)[0];
   JSONObject profile=new JSONObject(decode(encoded));
   String host=profile.getString("add");
   int port=profile.getInt("port");
   if(host.isEmpty()||port<1||port>65535)throw new Exception("Invalid profile");
   JSONObject user=new JSONObject().put("id",profile.getString("id"))
     .put("alterId",profile.optInt("aid")).put("security",profile.optString("scy","auto"));
   server.put("address",host).put("port",port).put("users",new JSONArray().put(user));
   settings.put("vnext",new JSONArray().put(server));
   String network=profile.optString("net","tcp");
   stream.put("network",network);
   if ("tls".equals(profile.optString("tls"))) {
    String sni=profile.optString("sni");
    stream.put("security","tls").put("tlsSettings",new JSONObject().put("serverName",sni.isEmpty()?host:sni));
   }
   if ("ws".equals(network)) {
    stream.put("wsSettings",new JSONObject().put("path",profile.optString("path","/"))
      .put("headers",new JSONObject().put("Host",profile.optString("host",host))));
   } else if ("grpc".equals(network)) {
    stream.put("grpcSettings",new JSONObject().put("serviceName",profile.optString("path")));
   } else if (!"tcp".equals(network) && !"raw".equals(network)) throw new Exception("Unsupported transport");
   outbound.put("protocol","vmess");
  } else if ("ss".equalsIgnoreCase(scheme)) {
   if (u.getQueryParameter("plugin")!=null) throw new Exception("Unsupported plugin");
   if (u.getUserInfo()==null) {
    String encoded=link.substring(5).split("[?#]",2)[0];
    u=Uri.parse("ss://"+decode(encoded));
   }
   String userInfo=u.getUserInfo();
   if(userInfo==null||u.getHost()==null||u.getPort()<1)throw new Exception("Invalid profile");
   String[] credentials=(userInfo.contains(":")?userInfo:decode(userInfo)).split(":",2);
   if(credentials.length!=2)throw new Exception("Invalid profile");
   server.put("address",u.getHost()).put("port",u.getPort())
     .put("method",credentials[0]).put("password",credentials[1]);
   settings.put("servers",new JSONArray().put(server));
   outbound.put("protocol","shadowsocks");
  } else if ("socks".equalsIgnoreCase(scheme)) {
   if (u.getHost()==null || u.getPort()<1) throw new Exception("Invalid profile");
   server.put("address",u.getHost()).put("port",u.getPort());
   if (u.getUserInfo()!=null) {
    String[] credentials=u.getUserInfo().split(":",2);
    server.put("users",new JSONArray().put(new JSONObject().put("user",credentials[0])
      .put("pass",credentials.length==2?credentials[1]:"")));
   }
   settings.put("servers",new JSONArray().put(server));
   outbound.put("protocol","socks");
  } else throw new Exception("Unsupported protocol");
  return outbound.put("settings",settings).put("streamSettings",stream).put("tag","proxy");
 }
 static String decode(String value) {
  try { return new String(Base64.decode(value,Base64.DEFAULT),StandardCharsets.UTF_8); }
  catch(IllegalArgumentException e){return new String(Base64.decode(value,Base64.URL_SAFE),StandardCharsets.UTF_8);}
 }
 static String param(Uri u,String k,String fallback){String v=u.getQueryParameter(k);return v==null||v.isEmpty()?fallback:v;}
}
