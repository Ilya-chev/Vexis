package com.example.tunnel;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs user-requested latency checks independently of the activity. */
public final class PingService extends Service {
 static final String CHANGED = "com.example.tunnel.PING_CHANGED";
 private static final Handler MAIN = new Handler(Looper.getMainLooper());
 private static final Map<String,String> values = new LinkedHashMap<>();
 private static Batch current;
 private static boolean changePending;
 private static final class Group {
  final ArrayDeque<String> queue; final int limit; int active; String owner; int population;
  Group(Collection<String> profiles, int limit) { queue=new ArrayDeque<>(profiles); this.limit=limit; }
 }
 private static final class Batch {
  final ArrayList<Group> groups = new ArrayList<>();
  final ExecutorService workers;
  final int capacity;
  int active; int timeoutMs; int allowedGroups=1; boolean shareCapacity; int cursor;
  Batch(Context context){capacity=PingLimits.deviceCapacity(context);workers=Executors.newFixedThreadPool(capacity);}
 }
 private static final Map<String,Long> expiry = new HashMap<>();
 static Map<String,String> snapshot() { return new LinkedHashMap<>(values); }
 static void startOne(Context context,String profile,String owner,int population) {
  String existing=values.get(profile);
  if("Проверка".equals(existing)||"Ожидание".equals(existing))return;
  android.content.SharedPreferences prefs=context.getSharedPreferences("app_settings",0);
  if(current==null){current=new Batch(context);current.timeoutMs=XrayPing.timeout(prefs);current.shareCapacity=true;}
  Group target=null;
  for(Group group:current.groups)if(owner.equals(group.owner)){target=group;break;}
  if(target==null){target=new Group(Collections.emptyList(),PingLimits.forCount(prefs,population));target.owner=owner;target.population=population;current.groups.add(target);}
  expiry.remove(profile);values.put(profile,"Ожидание");target.queue.add(profile);current.shareCapacity=true;
  schedule(context.getApplicationContext(),current);
 }
 static void start(Context context, ArrayList<MainActivity.PingGroup> groups) {
  cancelBatch();
  android.content.SharedPreferences prefs=context.getSharedPreferences("app_settings",0);
  Batch batch=new Batch(context);
  batch.timeoutMs=XrayPing.timeout(prefs);
  for(MainActivity.PingGroup input:groups) {
   int limit=PingLimits.forCount(prefs,input.queue.size());
   batch.groups.add(new Group(input.queue,limit));
   for(String profile:input.queue) { expiry.remove(profile); values.put(profile,"Ожидание"); }
  }
  current=batch;
  schedule(context.getApplicationContext(),batch);
 }
 static void enqueueGroup(Context context, MainActivity.PingGroup input) {
  if(current==null){start(context,new ArrayList<>(Collections.singletonList(input)));return;}
  ArrayList<String> pending=new ArrayList<>();
  for(String profile:input.queue){
   String state=values.get(profile);
   if(!"Проверка".equals(state)&&!"Ожидание".equals(state)){
    pending.add(profile);expiry.remove(profile);values.put(profile,"Ожидание");
   }
  }
  if(pending.isEmpty())return;
  current.groups.add(new Group(pending,PingLimits.forCount(context.getSharedPreferences("app_settings",0),pending.size())));
  current.shareCapacity=true;
  schedule(context.getApplicationContext(),current);
 }
 private static void cancelBatch() {
  Batch old=current; current=null;
  if(old!=null) old.workers.shutdownNow();
  for(Iterator<Map.Entry<String,String>> it=values.entrySet().iterator();it.hasNext();) {
   String value=it.next().getValue();
   if("Проверка".equals(value)||"Ожидание".equals(value)) it.remove();
  }
 }
 static void clear(Context context) { cancelBatch(); values.clear(); expiry.clear(); changed(context); }
 private static void changed(Context context) {
  if(changePending)return;
  changePending=true;
  Context app=context.getApplicationContext();
  MAIN.postDelayed(()->{changePending=false;app.sendBroadcast(new Intent(CHANGED).setPackage(app.getPackageName()));},75);
 }
 private static void schedule(Context app, Batch batch) {
  if(current!=batch)return;
  if(batch.shareCapacity){scheduleShared(app,batch);return;}
  for(int groupIndex=0;groupIndex<batch.groups.size()&&groupIndex<batch.allowedGroups;groupIndex++) {
   Group group=batch.groups.get(groupIndex);
   while(batch.active<batch.capacity&&group.active<group.limit&&!group.queue.isEmpty()) {
   String profile=group.queue.removeFirst(); group.active++; batch.active++; values.put(profile,"Проверка");
   batch.workers.execute(()->{
    String result="n/a";
    try { long delay=AwgProfile.isAwg(profile)?AwgPing.measure(app,profile,batch.timeoutMs):XrayPing.measure(profile,batch.timeoutMs); if(delay>=0) result=delay+" мс"; else if(delay==AwgPing.BUSY) result="VPN занят"; }
    catch(Exception e) { if(e.getMessage()!=null&&e.getMessage().contains("Unsupported"))result="Не поддерживается"; }
    catch(LinkageError e) { result="Движок недоступен"; }
    final String value=result;
    VpnJournal.add(app,"Проверка " + SubscriptionInfo.name(profile) + ": " + value,true);
    MAIN.post(()->{ if(current!=batch)return; group.active--; batch.active--; if(batch.allowedGroups<batch.groups.size())batch.allowedGroups++; values.put(profile,value); long timestamp=SystemClock.elapsedRealtime(); expiry.put(profile,timestamp); MAIN.postDelayed(()->{ if(Objects.equals(expiry.get(profile),timestamp)){expiry.remove(profile);values.remove(profile);changed(app);} },600000L); schedule(app,batch); });
   });
   }
   if(group.queue.isEmpty()&&group.active==0&&batch.allowedGroups<batch.groups.size())batch.allowedGroups++;
  }
  changed(app);
  if(batch.active==0) { current=null; batch.workers.shutdown(); }
 }
 private static void scheduleShared(Context app, Batch batch) {
  int groupCount=0, total=0;
  for(Group group:batch.groups)if(group.active>0||!group.queue.isEmpty()){
   groupCount++;total+=group.active+group.queue.size();
  }
  if(groupCount==0){changed(app);current=null;batch.workers.shutdown();return;}
  int population=0;
  for(Group group:batch.groups)if(group.active>0||!group.queue.isEmpty())population+=group.population>0?group.population:group.active+group.queue.size();
  int capacity=Math.min(batch.capacity,PingLimits.forCount(app.getSharedPreferences("app_settings",0),Math.max(total,population)));
  // Assign each available slot to the least busy eligible group. Recompute
  // after every assignment so remainders and newly added groups are fair.
  while(batch.active<capacity){
   Group selected=null; int selectedIndex=-1;
   for(int i=0;i<batch.groups.size();i++){
    int index=(batch.cursor+i)%batch.groups.size();
    Group candidate=batch.groups.get(index);
    if(candidate.queue.isEmpty()||candidate.active>=candidate.limit)continue;
    if(selected==null||candidate.active<selected.active){selected=candidate;selectedIndex=index;}
   }
   if(selected==null)break;
   final Group group=selected;
   batch.cursor=(selectedIndex+1)%batch.groups.size();
   String profile=group.queue.removeFirst();group.active++;batch.active++;values.put(profile,"Проверка");
   batch.workers.execute(()->{
    String result="n/a";
    try{long delay=AwgProfile.isAwg(profile)?AwgPing.measure(app,profile,batch.timeoutMs):XrayPing.measure(profile,batch.timeoutMs);if(delay>=0)result=delay+" мс";else if(delay==AwgPing.BUSY)result="VPN занят";}
    catch(Exception e){if(e.getMessage()!=null&&e.getMessage().contains("Unsupported"))result="Не поддерживается";}
    catch(LinkageError e){result="Движок недоступен";}
    final String value=result;
    VpnJournal.add(app,"Проверка " + SubscriptionInfo.name(profile) + ": " + value,true);
    MAIN.post(()->{if(current!=batch)return;group.active--;batch.active--;values.put(profile,value);long stamp=SystemClock.elapsedRealtime();expiry.put(profile,stamp);MAIN.postDelayed(()->{if(Objects.equals(expiry.get(profile),stamp)){expiry.remove(profile);values.remove(profile);changed(app);}},600000L);schedule(app,batch);});
   });
  }
  changed(app);
 }
 @Override public IBinder onBind(Intent intent) { return null; }
}
