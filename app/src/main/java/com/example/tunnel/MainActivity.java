package com.example.tunnel;
import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.app.*; import android.os.*; import android.content.*; import android.graphics.Color; import android.view.*; import android.widget.*; import java.util.*; import java.net.*; import java.io.*;

public class MainActivity extends Activity {
 // Frame-driven motion is independent of the system animator duration scale.
 final java.util.WeakHashMap<View,Runnable> frameAnimations=new java.util.WeakHashMap<>();
 void cancelFrameAnimation(View view){Runnable old=frameAnimations.remove(view);if(old!=null)view.removeCallbacks(old);}
 void animateFrame(View view,float target,long duration,int property,Runnable end){
  cancelFrameAnimation(view);
  final float start=property==0?view.getTranslationX():property==1?view.getRotation():view.getAlpha();
  final long began=android.os.SystemClock.uptimeMillis();
  final android.view.animation.Interpolator easing=new android.view.animation.DecelerateInterpolator(2f);
  Runnable step=new Runnable(){public void run(){
   if(frameAnimations.get(view)!=this)return;
   if(!view.isAttachedToWindow()){frameAnimations.remove(view);return;}
   float progress=Math.min(1f,(android.os.SystemClock.uptimeMillis()-began)/(float)duration);
   float value=start+(target-start)*easing.getInterpolation(progress);
   if(property==0)view.setTranslationX(value);else if(property==1)view.setRotation(value);else view.setAlpha(value);
   if(progress<1f)view.postOnAnimation(this);else{frameAnimations.remove(view);if(end!=null)end.run();}
  }};
  frameAnimations.put(view,step);view.postOnAnimation(step);
 }
 void translateFrame(View view,float target,long duration){animateFrame(view,target,duration,0,null);}
 @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLocale.context(base));}
 TextView connectionStatus,connectionStage;
 TextView connectionCountry,connectionDetails,connectionTime,connectionPing;
 LinearLayout connectionNameRow;
 View tabIndicator;
 Boolean outlinedConnectionButton;
 TextView detailsIp,detailsProtocol,detailsTimeZone,detailsReconnect;
 TextView detailsTitle;
 AlertDialog detailsDialog;
 LinearLayout countryRow,timeRow;
 VexisCrystalView connectionCrystal;
 VexisMotion.ConnectedDot connectedDot;
 VexisCrystalView.State displayedConnectionState;
 View connectionFlag;
 String displayedCountry;
 LinearLayout modeButtons;
 View modeThumb;
 ScrollView mainScroll;
 FrameLayout serverStack;
 LinearLayout serverSections;
 volatile int groupingGeneration;
 boolean rendering,renderQueued;
 final java.util.concurrent.ExecutorService groupingPool=java.util.concurrent.Executors.newSingleThreadExecutor();
 final java.util.concurrent.ExecutorService databasePool=java.util.concurrent.Executors.newSingleThreadExecutor();
 int fixedHeaderHeight;
 long connectedAt;
 long connectionDelay=-1;
 String countryCode="";
 boolean countryDone;
 boolean delayDone;
 String exitIp="",exitTimeZone="";
 Button connectionButton;
 String pendingProfile;
 boolean pendingAuto;
 String connectedProfile;
 String manualProbe;
 String switchProfile;
 String switchAttempt;
 boolean switchToAuto;
 boolean awgActive;
 String activeAttempt;
 String vpnStatus = TunnelService.DISCONNECTED;
 String connectionMessage;
 volatile AutoConnect autoConnect;
 boolean autoPing;
 boolean backgroundRecovering;
 boolean waitingInternet;
 boolean initialConnection;
 String recoveryStage;
 static final String AUTO_MODE = "auto_connect";
 static final String MANUAL_PROFILE = "manual_profile";
 static final String MANUAL_OWNER = "manual_owner";
 boolean changingSessionGeneration;
 final SharedPreferences.OnSharedPreferenceChangeListener modeListener=(prefs,key)->{
  if(RecoveryService.SESSION_GENERATION.equals(key)&&!changingSessionGeneration){
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    pendingAuto=false;pendingProfile=null;manualProbe=null;switchAttempt=null;switchProfile=null;
    if(autoConnect!=null)autoConnect.cancel();
    activeAttempt=null;backgroundRecovering=false;waitingInternet=false;initialConnection=false;
    vpnStatus=TunnelService.DISCONNECTED;connectionMessage=null;connectedProfile=null;
    updateConnectionHeader();
   });
  }
  if(AUTO_MODE.equals(key))runOnUiThread(()->{
   if(isFinishing()||isDestroyed()||modeButtons==null)return;
   updateModeControl(true);
   for(Map.Entry<String,ArrayList<View>> entry:this.manualViews.entrySet())for(View view:entry.getValue())
    ripple(view,!prefs.getBoolean(AUTO_MODE,false)&&entry.getKey().equals(prefs.getString(MANUAL_PROFILE,null))?VexisUi.PALE:(VexisUi.isDark(this)?VexisUi.BACKGROUND:VexisUi.SURFACE),9);
   String manual=prefs.getString(MANUAL_PROFILE,null);
   for(Map.Entry<String,View> entry:this.countryHeaders.entrySet()){
    boolean highlight=this.expanded.contains(entry.getKey())||(!prefs.getBoolean(AUTO_MODE,false)&&manual!=null&&entry.getKey().equals(country(manual)));
    ripple(entry.getValue(),highlight?VexisUi.PALE:Color.TRANSPARENT,9);
   }
   updateConnectionHeader();
  });
 };
 final BroadcastReceiver vpnState = new BroadcastReceiver(){
  @Override public void onReceive(Context context,Intent intent){
   String token=intent.getStringExtra(TunnelService.ATTEMPT);
   boolean recoveryEvent=intent.hasExtra(RecoveryService.RECOVERING);
   String incomingState=intent.getStringExtra("state");
   if(intent.hasExtra(RecoveryService.SESSION_GENERATION)
      && intent.getLongExtra(RecoveryService.SESSION_GENERATION,0)!=settings.getLong(RecoveryService.SESSION_GENERATION,0)
      && !(TunnelService.DISCONNECTED.equals(incomingState)&&switchAttempt!=null&&switchAttempt.equals(token)))return;
   if(activeAttempt!=null&&!activeAttempt.equals(token)&&!intent.getBooleanExtra(RecoveryService.RECOVERING,false))return;
   if(recoveryEvent)backgroundRecovering=intent.getBooleanExtra(RecoveryService.RECOVERING,false);
   if(intent.hasExtra(RecoveryService.INITIAL_CONNECTION))initialConnection=intent.getBooleanExtra(RecoveryService.INITIAL_CONNECTION,false);
   if(intent.hasExtra(RecoveryService.STAGE))recoveryStage=intent.getStringExtra(RecoveryService.STAGE);
   vpnStatus=intent.getStringExtra("state");
   if(intent.hasExtra(TunnelService.WAITING_INTERNET))waitingInternet=intent.getBooleanExtra(TunnelService.WAITING_INTERNET,false);
   else if(!TunnelService.isActive(vpnStatus))waitingInternet=false;
   if(TunnelService.CONNECTED.equals(vpnStatus)){
    connectedAt=intent.getLongExtra(TunnelService.STARTED_AT,0);
    if(settings.getBoolean(AUTO_MODE,false)){
     long started=settings.getLong("auto_session_started",0);
    if(started>0&&started<=android.os.SystemClock.elapsedRealtime())connectedAt=started;
    }
    countryCode=intent.getStringExtra(TunnelService.COUNTRY);
    countryDone=intent.getBooleanExtra(TunnelService.COUNTRY_DONE,false);
    delayDone=intent.getBooleanExtra(TunnelService.DELAY_DONE,false);
    connectionDelay=intent.getLongExtra(TunnelService.LIVE_DELAY,-1);
    exitIp=intent.getStringExtra(TunnelService.EXIT_IP);
    exitTimeZone=intent.getStringExtra(TunnelService.TIME_ZONE);
   }else if(!TunnelService.CONNECTING.equals(vpnStatus)){
    connectedAt=0;countryCode="";countryDone=false;delayDone=false;exitIp="";exitTimeZone="";connectionDelay=-1;
   }
   if(TunnelService.CONNECTED.equals(vpnStatus)&&intent.getStringExtra(TunnelService.PROFILE)!=null)
    connectedProfile=intent.getStringExtra(TunnelService.PROFILE);
   awgActive=intent.getBooleanExtra("awg",false);
   if(manualProbe!=null&&!TunnelService.CONNECTING.equals(vpnStatus)){
    String profile=manualProbe;
    manualProbe=null;
    activeAttempt=null;
    if(TunnelService.PROBED.equals(vpnStatus))startProfile(profile,true,false);
    else {vpnStatus="Не удалось подключиться";updateConnectionHeader();}
    return;
   }
   if(awgActive)TunnelService.receiveAwgState(vpnStatus);
   activeAttempt=TunnelService.isActive(vpnStatus)?token:null;
   if(TunnelService.CONNECTED.equals(vpnStatus))settings.edit().putBoolean("vpn_was_started",true).apply();
   if(switchAttempt!=null&&switchAttempt.equals(token)&&!TunnelService.isActive(vpnStatus)){
    String next=switchProfile;
    switchAttempt=null;
    switchProfile=null;
    if(switchToAuto)startSelectedMode();
    else if(next!=null&&items.contains(next))prepareVpn(next,false);
    else startSelectedMode();
   }
   if(autoConnect!=null)autoConnect.onState(token,vpnStatus,intent.getLongExtra(TunnelService.DELAY,-1));
   if(TunnelService.LOST.equals(vpnStatus)){backgroundRecovering=true;initialConnection=false;recoveryStage=null;}
   updateConnectionHeader();
   updateDetailsDialog();
  }
 };
 LinearLayout root,body,header,tabs; int page=0; boolean countries=false; String serverQuery=""; ArrayList<String> urls=new ArrayList<>(), items=new ArrayList<>(), groups=new ArrayList<>(), owners=new ArrayList<>(); int pingGeneration=0; HashSet<String> selected=new LinkedHashSet<>(); LocalDatabase db; SharedPreferences settings; Handler refreshHandler=new Handler(); HashSet<String> expanded=new HashSet<>(); java.util.concurrent.ConcurrentHashMap<String,String> latency=new java.util.concurrent.ConcurrentHashMap<>(); java.util.concurrent.ExecutorService pingPool=java.util.concurrent.Executors.newCachedThreadPool(); boolean pingRunning=false;
 PingBatch pingBatch;
 Set<String> pendingSelectedSort;
 FrameLayout sortControl;
 boolean sortingSelected;
 final Set<String> userPingProfiles=new HashSet<>();
 final Map<String,Long> pingExpiry=new HashMap<>();
 final BroadcastReceiver pingState=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){syncUserPings();}};
 final Map<String,ArrayList<CheckBox>> selectionChecks=new HashMap<>();
 final ArrayList<SelectionGroup> selectionGroups=new ArrayList<>();
 static class SelectionGroup {
  final CheckBox checkbox; final ArrayList<String> items; final boolean country;
  SelectionGroup(CheckBox checkbox,ArrayList<String> items,boolean country){this.checkbox=checkbox;this.items=items;this.country=country;}
 }
 void clearSelectionViews(){selectionChecks.clear();selectionGroups.clear();}
 void updateSelectionViews(){
  for(Map.Entry<String,ArrayList<CheckBox>> entry:selectionChecks.entrySet())
   for(CheckBox checkbox:entry.getValue())checkbox.setChecked(selected.contains(entry.getKey()));
  for(SelectionGroup group:selectionGroups){
   int checked=0;for(String item:group.items)if(selected.contains(item))checked++;
   boolean partial=checked>0&&checked<group.items.size();
   group.checkbox.setButtonDrawable(partial?R.drawable.vexis_main_check_mixed_selector:R.drawable.vexis_main_check_selector);
   group.checkbox.setChecked(!group.items.isEmpty()&&checked==group.items.size());
   group.checkbox.setContentDescription(group.country?(partial?getString(R.string.main_text_1):getString(R.string.main_text_2)):(partial?getString(R.string.main_text_3):getString(R.string.main_text_4)));
  }
 }
 final HashSet<String> countryExpanded=new HashSet<>(),subscriptionExpanded=new HashSet<>();
 final LinearLayout[] groupingCache=new LinearLayout[2];
 final int[] groupingScroll=new int[2];
 static class GroupingBuild {int pending;boolean finished;}
 final Map<LinearLayout,GroupingBuild> groupingBuilds=new HashMap<>();
 void invalidateGroupingCache(){groupingCache[0]=null;groupingCache[1]=null;groupingBuilds.clear();}
 void finishGroupingBuild(LinearLayout container,GroupingBuild build,boolean byCountry){
  if(build.finished&&build.pending==0&&groupingBuilds.get(container)==build){
   groupingCache[byCountry?0:1]=container;groupingBuilds.remove(container);
   if(serverSections==container&&page==0&&groupingScroll[byCountry?0:1]>0)restoreGroupingScroll(container,groupingGeneration,byCountry);
  }
 }
 int selectionVersion,subscriptionReloadVersion;
 final Runnable subscriptionReloadTask=this::reloadSubscriptionsInBackground;
 final BroadcastReceiver subscriptionState=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){requestSubscriptionsReload();}};
 void requestSubscriptionsReload(){
  refreshHandler.removeCallbacks(subscriptionReloadTask);
  refreshHandler.postDelayed(subscriptionReloadTask,200);
 }
 void reloadSubscriptionsInBackground(){
  final int request=++subscriptionReloadVersion,selection=selectionVersion;
  databasePool.execute(()->{
   ArrayList<String> nextUrls,nextItems=new ArrayList<>(),nextGroups=new ArrayList<>(),nextOwners=new ArrayList<>();
   Set<String> nextSelected;
   try(LocalDatabase data=new LocalDatabase(getApplicationContext())){
    nextUrls=data.urls();data.loadServers(nextItems,nextGroups,nextOwners);nextSelected=data.selected();
   }
   runOnUiThread(()->{
    if(isDestroyed()||request!=subscriptionReloadVersion)return;
    urls.clear();urls.addAll(nextUrls);items.clear();items.addAll(nextItems);
    groups.clear();groups.addAll(nextGroups);owners.clear();owners.addAll(nextOwners);
    if(selection==selectionVersion){selected.clear();selected.addAll(nextSelected);}
    if(page<2)render();
   });
  });
 }
 void syncUserPings(){
  Map<String,String> snapshot=PingService.snapshot();
  Set<String> changed=new HashSet<>();
  for(String key:userPingProfiles)if(!snapshot.containsKey(key)){latency.remove(key);changed.add(key);}
  for(Map.Entry<String,String> result:snapshot.entrySet()){
   if(!java.util.Objects.equals(latency.put(result.getKey(),result.getValue()),result.getValue()))changed.add(result.getKey());
  }
  userPingProfiles.clear();userPingProfiles.addAll(snapshot.keySet());
  for(String key:changed)updatePingViews(key);
  if(pendingSelectedSort!=null){
   boolean complete=true;
   for(String key:pendingSelectedSort){String value=snapshot.get(key);if(value==null){pendingSelectedSort=null;complete=false;setSortingSelected(false);break;}if("Проверка".equals(value)||"Ожидание".equals(value))complete=false;}
   if(complete&&pendingSelectedSort!=null){pendingSelectedSort=null;applySelectedSort(6);setSortingSelected(false);}
  }
 }
 final Map<String,java.util.concurrent.CompletableFuture<Void>> refreshing=new HashMap<>();
 final Map<String,String> subscriptionTitleCache=new HashMap<>();
 final Map<String,ArrayList<FrameLayout>> pingViews=new HashMap<>();
 final Map<String,ArrayList<View>> refreshButtons=new HashMap<>();
 final Map<String,ArrayList<View>> manualViews=new HashMap<>();
 final Map<String,View> countryHeaders=new HashMap<>();
 final Set<String> newSubscriptions=new HashSet<>();
 final java.util.concurrent.ExecutorService subscriptionPool=java.util.concurrent.Executors.newFixedThreadPool(6);
 public void onCreate(Bundle b){VexisUi.applyTheme(this);super.onCreate(b);if(b!=null)page=b.getInt("active_page",0);db=new LocalDatabase(this);db.migrate(getSharedPreferences("data",0));settings=getSharedPreferences("app_settings",0);boolean firstLaunch=!settings.contains("migrated");if(firstLaunch){Context app=getApplicationContext();new Thread(()->EngineUpdates.checkBackground(app,()->true),"initial-engine-check").start();}settings.registerOnSharedPreferenceChangeListener(modeListener);if(!settings.getBoolean("migrated",false)){if(settings.edit().putInt("interval",db.interval()).putBoolean("migrated",true).commit())db.clearLegacyInterval();}urls.addAll(db.urls());selected.addAll(db.selected());db.loadServers(items,groups,owners);expanded=subscriptionExpanded;expanded.addAll(owners);registerVpnReceiver();if(Build.VERSION.SDK_INT>=33){registerReceiver(pingState,new IntentFilter(PingService.CHANGED),TunnelService.STATE_PERMISSION,null,Context.RECEIVER_NOT_EXPORTED);registerReceiver(subscriptionState,new IntentFilter(SubscriptionRefreshJob.CHANGED),TunnelService.STATE_PERMISSION,null,Context.RECEIVER_NOT_EXPORTED);}else{registerReceiver(pingState,new IntentFilter(PingService.CHANGED),TunnelService.STATE_PERMISSION,null);registerReceiver(subscriptionState,new IntentFilter(SubscriptionRefreshJob.CHANGED),TunnelService.STATE_PERMISSION,null);}syncUserPings();draw();if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !settings.getBoolean("notification_permission_requested",false)){settings.edit().putBoolean("notification_permission_requested",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1001);}refreshHandler.post(tickConnectionTime);if(!getIntent().getBooleanExtra("subscription_list",false)&&!getIntent().getBooleanExtra("engine_settings",false)&&settings.getBoolean("refresh_on_open",true))SubscriptionRefreshJob.schedule(this,0);else SubscriptionRefreshJob.ensureScheduled(this);}
 @Override protected void onSaveInstanceState(Bundle state){state.putInt("active_page",page);super.onSaveInstanceState(state);}
 final Runnable tickConnectionTime=new Runnable(){
  @Override public void run(){
   updateConnectionTime();
   refreshHandler.postDelayed(this,1000);
  }
 };
 void save(){selectionVersion++;db.setSelected(selected);}
 boolean isRtl(){return getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;}
 int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView label(String s,int size){
  TextView t=text(s,size,VexisUi.TEXT,false);
  t.setPadding(dp(8),dp(8),dp(8),dp(8));
  return t;
 }
 Button button(String s,Runnable r){
  Button b=new Button(this);
  b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(VexisUi.DEEP);
  b.setMinHeight(dp(44));b.setMinWidth(0);b.setMinimumWidth(0);
  b.setPadding(dp(11),0,dp(11),0);b.setStateListAnimator(null);
  b.setBackground(VexisUi.buttonBackground(this,VexisUi.QUIET));
  b.setOnClickListener(v->r.run());
  return b;
 }
 void ripple(View view,int color,int radius){
  view.setBackground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x26a67df2),
   Color.alpha(color)==0?null:shape(color,radius),shape(Color.WHITE,radius)));
 }
 void fitHeaderText(TextView view, int maxSize){
  view.setSingleLine(true);
  view.setEllipsize(android.text.TextUtils.TruncateAt.END);
  view.setPadding(dp(4),0,dp(4),0);
  view.setGravity(Gravity.CENTER_VERTICAL);
  if(Build.VERSION.SDK_INT>=26){
   view.setAutoSizeTextTypeUniformWithConfiguration(9,maxSize,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  }else view.setTextSize(Math.min(maxSize,14));
 }
 void draw(){
  displayedCountry=null;
  connectionFlag=null;
  root=column();
  root.setBackgroundColor(VexisUi.BACKGROUND);
  getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(VexisUi.BACKGROUND));
  getWindow().setStatusBarColor(VexisUi.BACKGROUND);
  getWindow().setNavigationBarColor(VexisUi.SURFACE);
  int systemUi=VexisUi.isDark(this)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
  if(Build.VERSION.SDK_INT>=26&&!VexisUi.isDark(this))systemUi|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
  getWindow().getDecorView().setSystemUiVisibility(systemUi);
  root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
  if(getIntent().getBooleanExtra("subscription_list",false)||getIntent().getBooleanExtra("engine_settings",false))root.post(()->VexisMotion.enter(root,dp(6)));

  boolean subscriptions=getIntent().getBooleanExtra("subscription_list",false);
  boolean engines=getIntent().getBooleanExtra("engine_settings",false);
  if(subscriptions||engines){
   page=subscriptions?4:5;
   showToolbar(subscriptions?getString(R.string.main_text_5):getString(R.string.main_text_6));
  }else{
   buildHomeHero();
  }
  mainScroll=new ScrollView(this);
  mainScroll.setFillViewport(true);
  mainScroll.setClipToPadding(false);
  mainScroll.setVerticalScrollBarEnabled(false);
  body=column();
  body.setPadding(dp(17),dp(subscriptions||engines?8:0),dp(17),dp(12));
  mainScroll.addView(body,new ScrollView.LayoutParams(-1,-2));
  root.addView(mainScroll,new LinearLayout.LayoutParams(-1,0,1));
  if(!subscriptions&&!engines)buildNavigation();
  render();
 }
 LinearLayout column(){
  LinearLayout layout=new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  return layout;
 }
 TextView text(String value,int size,int color,boolean bold){
  return VexisUi.text(this,value,size,color,bold);
 }
 ImageView icon(int resource,int color){
  ImageView image=new ImageView(this);
  image.setImageResource(resource);
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  if(color!=0)image.setColorFilter(color);
  image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  return image;
 }
 View iconAction(int resource,String description,Runnable action){
  ImageButton button=new ImageButton(this);
  button.setImageResource(resource);
  button.setColorFilter(VexisUi.INK);
  int padding=dp(10);
  button.setPadding(padding,padding,padding,padding);
  button.setScaleType(ImageView.ScaleType.FIT_CENTER);
  button.setContentDescription(description);
  ripple(button,Color.TRANSPARENT,10);
  if(action!=null)button.setOnClickListener(view->action.run());
  return button;
 }
 void addDivider(LinearLayout parent){
  parent.addView(VexisUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
 }
 void showToolbar(String title){
  LinearLayout toolbar=new LinearLayout(this);
  toolbar.setGravity(Gravity.CENTER_VERTICAL);
  toolbar.setPaddingRelative(dp(6),0,dp(16),0);
  View back=iconAction(R.drawable.vexis_ic_back,getString(R.string.main_text_7),this::finish);
  if(isRtl())back.setScaleX(-1);
  toolbar.addView(back,
    new LinearLayout.LayoutParams(dp(44),dp(48)));
  TextView heading=VexisUi.toolbar(this,title);
  heading.setPaddingRelative(dp(4),0,0,0);
  toolbar.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
  root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(63)));
  addDivider(root);
 }
 void buildHomeHero(){
  header=column();
  header.setPadding(dp(17),0,dp(17),dp(13));
  GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
   VexisUi.isDark(this)?new int[]{VexisUi.PALE,VexisUi.BACKGROUND,VexisUi.BACKGROUND}:new int[]{0xfff1e5fc,0xfffbf8fe,VexisUi.WHITE});
  backdrop.setGradientType(GradientDrawable.RADIAL_GRADIENT);
  backdrop.setGradientCenter(.84f,.32f);
  backdrop.setGradientRadius(dp(213));
  header.setBackground(backdrop);
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(247)));
  root.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   if(bottom<=top)return;
   int fullHeight=Math.max(dp(219),Math.round((bottom-top)*.32f));
   if(fullHeight>fixedHeaderHeight)fixedHeaderHeight=fullHeight;
   if(header.getLayoutParams().height==fixedHeaderHeight)return;
   ViewGroup.LayoutParams params=header.getLayoutParams();
   params.height=fixedHeaderHeight;
   header.setLayoutParams(params);
  });

  LinearLayout brand=new LinearLayout(this);
  brand.setGravity(Gravity.CENTER_VERTICAL);
  brand.addView(icon(R.drawable.vexis_main_brand,0),new LinearLayout.LayoutParams(dp(37),dp(35)));
  TextView brandName=text("Vexis",22,VexisUi.INK,true);
  brandName.setPaddingRelative(dp(8),0,0,0);
  brand.addView(brandName);
  header.addView(brand,new LinearLayout.LayoutParams(-1,dp(43)));

  LinearLayout hero=new LinearLayout(this);
  header.addView(hero,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout copy=column();
  copy.setGravity(Gravity.CENTER_VERTICAL);
  copy.setPaddingRelative(0,0,dp(8),0);
  hero.addView(copy,new LinearLayout.LayoutParams(0,-1,.62f));
  buildHeroCopy(copy);
  connectionCrystal=new VexisCrystalView(this);
  connectionCrystal.setOnClickListener(v->showConnectionDetails());
  connectionCrystal.setClickable(false);
  hero.addView(connectionCrystal,new LinearLayout.LayoutParams(0,-1,.38f));
  buildHeroMeta();
  buildHeroControls();
  addDivider(root);
  updateConnectionHeader();
 }


 void buildHeroCopy(LinearLayout copy){
  LinearLayout stateLine=new LinearLayout(this);
  stateLine.setGravity(Gravity.CENTER_VERTICAL);
  connectedDot=new VexisMotion.ConnectedDot(this);
  stateLine.addView(connectedDot,new LinearLayout.LayoutParams(dp(16),dp(20)));
  connectionStatus=text(ServiceText.translate(this,vpnStatus),22,VexisUi.INK,true);
  connectionStatus.setGravity(Gravity.CENTER_VERTICAL);
  connectionStatus.setLetterSpacing(-.018f);
  connectionStatus.setMaxLines(2);
  connectionStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
  stateLine.addView(connectionStatus,new LinearLayout.LayoutParams(0,-2,1));
  copy.addView(stateLine,new LinearLayout.LayoutParams(-1,-2));

  connectionTime=text("",16,VexisUi.ACCENT,true);
  connectionTime.setFontFeatureSettings("tnum");
  connectionTime.setPaddingRelative(dp(16),dp(5),0,0);
  copy.addView(connectionTime,new LinearLayout.LayoutParams(-1,-2));
  connectionStage=text("",13,VexisUi.MUTED,false);
  connectionStage.setMaxLines(2);
  connectionStage.setEllipsize(android.text.TextUtils.TruncateAt.END);
  connectionStage.setPaddingRelative(dp(16),dp(5),0,0);
  copy.addView(connectionStage,new LinearLayout.LayoutParams(-1,-2));

  connectionNameRow=new LinearLayout(this);
  connectionNameRow.setGravity(Gravity.TOP);
  ImageView pin=icon(R.drawable.vexis_main_pin,VexisUi.INK);
  connectionNameRow.addView(pin,new LinearLayout.LayoutParams(dp(17),dp(17)));
  connectionDetails=text("",14,VexisUi.INK,false);
  connectionDetails.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  connectionDetails.setMaxLines(3);
  connectionDetails.setEllipsize(android.text.TextUtils.TruncateAt.END);
  connectionDetails.setPaddingRelative(dp(7),0,0,0);
  connectionNameRow.addView(connectionDetails,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout.LayoutParams nameSpace=new LinearLayout.LayoutParams(-1,-2);
  nameSpace.topMargin=dp(9);
  copy.addView(connectionNameRow,nameSpace);
 }
 void buildHeroMeta(){
  timeRow=new LinearLayout(this);
  timeRow.setGravity(Gravity.CENTER_VERTICAL);
  countryRow=new LinearLayout(this);
  countryRow.setGravity(Gravity.CENTER_VERTICAL);
  connectionCountry=text("",13,VexisUi.INK,false);
  connectionCountry.setSingleLine();
  connectionCountry.setEllipsize(android.text.TextUtils.TruncateAt.END);
  int countryWidth=Math.max(dp(74),Math.min(dp(133),(getResources().getDisplayMetrics().widthPixels-dp(34))/3));
  connectionCountry.setMaxWidth(countryWidth);
  countryRow.addView(connectionCountry,new LinearLayout.LayoutParams(-2,-2));
  timeRow.addView(countryRow,new LinearLayout.LayoutParams(-2,-1));
  View separator=new View(this);
  separator.setBackgroundColor(VexisUi.LINE);
  LinearLayout.LayoutParams dividerSpace=new LinearLayout.LayoutParams(dp(1),dp(14));
  dividerSpace.setMargins(dp(5),0,dp(9),0);
  timeRow.addView(separator,dividerSpace);
  timeRow.addView(icon(R.drawable.vexis_main_signal,VexisUi.INK),new LinearLayout.LayoutParams(dp(15),dp(15)));
  connectionPing=text("",13,VexisUi.INK,false);
  connectionPing.setFontFeatureSettings("tnum");
  connectionPing.setPadding(dp(5),0,dp(8),0);
  timeRow.addView(connectionPing,new LinearLayout.LayoutParams(-2,-2));
  timeRow.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));
  LinearLayout details=new LinearLayout(this);
  details.setGravity(Gravity.CENTER_VERTICAL);
  details.setOnClickListener(v->showConnectionDetails());
  TextView info=text(getString(R.string.main_text_8),13,VexisUi.ACCENT,true);
  info.setSingleLine();
  details.addView(info);
  details.addView(icon(R.drawable.vexis_ic_chevron,VexisUi.ACCENT),new LinearLayout.LayoutParams(dp(15),dp(15)));
  timeRow.addView(details,new LinearLayout.LayoutParams(-2,-1));
  LinearLayout.LayoutParams metaSpace=new LinearLayout.LayoutParams(-1,dp(37));
  metaSpace.bottomMargin=dp(6);
  header.addView(timeRow,metaSpace);
 }
 void buildHeroControls(){
  LinearLayout controls=new LinearLayout(this);
  connectionButton=button(getString(R.string.connect),this::toggleVpn);
  fitHeaderText(connectionButton,15);
  connectionButton.setGravity(Gravity.CENTER);
  connectionButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  LinearLayout.LayoutParams connectSpace=new LinearLayout.LayoutParams(0,-1,1.16f);
  connectSpace.setMarginEnd(dp(10));
  controls.addView(connectionButton,connectSpace);

  FrameLayout track=new FrameLayout(this);
  track.setPadding(dp(3),dp(3),dp(3),dp(3));
  track.setBackground(shape(VexisUi.PALE,12));
  modeThumb=new View(this);
  modeThumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  modeThumb.setBackground(shape(VexisUi.ACCENT,9));
  track.addView(modeThumb,new FrameLayout.LayoutParams(0,-1,Gravity.LEFT));
  modeButtons=new LinearLayout(this);
  track.addView(modeButtons,new FrameLayout.LayoutParams(-1,-1));
  for(int i=0;i<2;i++){
   final boolean automatic=i==1;
   Button mode=button(getString(automatic?R.string.automatic:R.string.manual),()->changeConnectionMode(automatic));
   mode.setOnLongClickListener(view->{chooseConnectionMode();return true;});
   mode.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
   ripple(mode,Color.TRANSPARENT,9);
   fitHeaderText(mode,13);
   mode.setGravity(Gravity.CENTER);
   modeButtons.addView(mode,new LinearLayout.LayoutParams(0,-1,1));
  }
  controls.addView(track,new LinearLayout.LayoutParams(0,-1,1));
  header.addView(controls,new LinearLayout.LayoutParams(-1,dp(44)));
  track.addOnLayoutChangeListener((view,l,t,r,b,oldL,oldT,oldR,oldB)->{
   if(r-l!=oldR-oldL)updateModeControl(false);
  });
  updateModeControl(false);
 }


 void buildNavigation(){
  addDivider(root);
  FrameLayout bar=new FrameLayout(this);
  bar.setBackgroundColor(VexisUi.WHITE);
  tabs=new LinearLayout(this);
  bar.addView(tabs,new FrameLayout.LayoutParams(-1,-1));
  int[] icons={R.drawable.vexis_ic_servers,R.drawable.vexis_main_star,R.drawable.vexis_ic_settings};
  String[] names={getString(R.string.tab_servers),getString(R.string.tab_selected),getString(R.string.tab_settings)};
  for(int i=0;i<names.length;i++){
   final int target=i;
   LinearLayout tab=column();
   tab.setGravity(Gravity.CENTER);
   tab.setContentDescription(names[i]);
   tab.setOnClickListener(v->showMainPage(target));
   ripple(tab,Color.TRANSPARENT,8);
   tab.addView(icon(icons[i],VexisUi.INK),new LinearLayout.LayoutParams(dp(22),dp(22)));
   TextView title=text(names[i],11,VexisUi.INK,false);
   title.setPadding(0,dp(3),0,0);
   title.setGravity(Gravity.CENTER);
   title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   tab.addView(title,new LinearLayout.LayoutParams(-2,-2));
   tabs.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
  }
  tabIndicator=new View(this);
  tabIndicator.setBackground(shape(VexisUi.ACCENT,3));
  bar.addView(tabIndicator,new FrameLayout.LayoutParams(dp(25),dp(3),Gravity.LEFT));
  tabs.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->{
   if(r-l!=oldR-oldL)moveTabIndicator(false);
  });
  root.addView(bar,new LinearLayout.LayoutParams(-1,dp(58)));
  updateTabs();
 }
 void moveTabIndicator(boolean animate){
  if(tabs==null||tabIndicator==null||tabs.getWidth()==0)return;
  View tab=tabs.getChildAt(page);
  if(tab==null)return;
  float x=tab.getLeft()+(tab.getWidth()-dp(25))*.5f;
  cancelFrameAnimation(tabIndicator);
  if(animate)translateFrame(tabIndicator,x,210);
  else tabIndicator.setTranslationX(x);
 }


 void updateModeControl(boolean animate){
  if(modeButtons==null||modeThumb==null)return;
  boolean automatic=settings.getBoolean(AUTO_MODE,false);
  for(int index=0;index<modeButtons.getChildCount();index++){
   Button button=(Button)modeButtons.getChildAt(index);
   boolean active=(index==1)==automatic;
   button.setSelected(active);
   button.setTextColor(active?VexisUi.ON_ACCENT:VexisUi.INK);
  }
  View track=(View)modeThumb.getParent();
  int half=(track.getWidth()-track.getPaddingLeft()-track.getPaddingRight())/2;
  if(half==0)return;
  if(modeThumb.getLayoutParams().width!=half){
   ViewGroup.LayoutParams params=modeThumb.getLayoutParams();
   params.width=half;
   modeThumb.setLayoutParams(params);
  }
  float target=(automatic!=isRtl())?half:0;
  cancelFrameAnimation(modeThumb);
  if(animate)translateFrame(modeThumb,target,200);
  else modeThumb.setTranslationX(target);
 }
 void updateTabs(){
  for(int index=0;index<tabs.getChildCount();index++){
   LinearLayout tab=(LinearLayout)tabs.getChildAt(index);
   boolean active=index==page;
   tab.setSelected(active);
   int color=active?VexisUi.ACCENT:VexisUi.TEXT;
   ((ImageView)tab.getChildAt(0)).setColorFilter(color);
   TextView label=(TextView)tab.getChildAt(1);
   label.setTextColor(color);
   label.setTypeface(Typeface.DEFAULT,active?Typeface.BOLD:Typeface.NORMAL);
  }
  moveTabIndicator(true);
 }
 void showMainPage(int next){
  if(page==next||mainScroll==null)return;
  View focus=getCurrentFocus();
  if(focus instanceof EditText){
   android.view.inputmethod.InputMethodManager keyboard=getSystemService(android.view.inputmethod.InputMethodManager.class);
   if(keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);
   focus.clearFocus();
  }
  page=next;
  updateTabs();
  render();
  mainScroll.scrollTo(0,0);
  VexisMotion.enter(body,dp(6));
 }
 void render(){
  if(page==1&&getCurrentFocus() instanceof EditText)return;
  if(rendering||(body!=null&&body.isInLayout())){
   if(!renderQueued&&body!=null){
    renderQueued=true;
    body.post(()->{renderQueued=false;if(!isFinishing()&&!isDestroyed())render();});
   }
   return;
  }
  rendering=true;
  try{
  groupingGeneration++;
  invalidateGroupingCache();
  subscriptionTitleCache.clear();
  pingViews.clear();
  refreshButtons.clear();
  manualViews.clear();countryHeaders.clear();clearSelectionViews();
  body.removeAllViews();
  if(page==4){showSubscriptions();return;}
  if(page==5){showEngines();return;}
  if(page==2){
   showListTitle(getString(R.string.tab_settings),false);
   showSettings();
   return;
  }
  if(page==1){
   showListTitle(getString(R.string.tab_selected),false);
   showPingLimitField();
  }else{
   showListTitle(getString(R.string.tab_servers),true);
   showServerSearch();
   showServerGrouping();
  }
  if(page==0){
   serverStack=new FrameLayout(this);
   serverStack.setClipChildren(true);
   body.addView(serverStack,new LinearLayout.LayoutParams(-1,-2));
   serverSections=column();
   serverStack.addView(serverSections);
   renderServersInBackground(serverSections);
  }else if(page==1)renderSelectedInBatches(body,groupingGeneration);
  else renderServerSections(body);
  }finally{rendering=false;}
 }
 void renderSelectedInBatches(LinearLayout container,int generation){
  Map<String,Integer> indices=new HashMap<>();
  for(int i=0;i<items.size();i++)if(!indices.containsKey(items.get(i)))indices.put(items.get(i),i);
  ArrayList<Integer> rows=new ArrayList<>();for(String link:selected){Integer i=indices.get(link);if(i!=null)rows.add(i);}
  if(rows.isEmpty()){showEmptyServers(container);return;}
  Runnable add=new Runnable(){int position;public void run(){
   if(isDestroyed()||page!=1||groupingGeneration!=generation||body!=container)return;
   int end=Math.min(position+4,rows.size());populateSection(container,"",rows.subList(position,end),false,false);position=end;
   if(position<rows.size())container.postOnAnimation(this);
  }};container.postOnAnimation(add);
 }
 void renderServerSections(LinearLayout container){
  LinkedHashMap<String,ArrayList<Integer>> sections=new LinkedHashMap<>();
  if(page==0&&!countries)for(String owner:urls)sections.put(owner,new ArrayList<>());
  Set<String> displayed=new HashSet<>();
  for(int i=0;i<items.size();i++){
   String item=items.get(i);
   if(page==1&&!selected.contains(item))continue;
   if((page==1||countries)&&!displayed.add(item))continue;
   String key=page==1?"":countries?country(item):owners.get(i);
   sections.computeIfAbsent(key,k->new ArrayList<>()).add(i);
  }
  if(page==1&&!sections.isEmpty()){
   Map<String,Integer> rank=new HashMap<>();int order=0;for(String link:selected)rank.put(link,order++);
   Collections.sort(sections.get(""),(a,b)->Integer.compare(rank.get(items.get(a)),rank.get(items.get(b))));
  }
  if(sections.isEmpty()){
   showEmptyServers(container);
   return;
  }
  for(Map.Entry<String,ArrayList<Integer>> section:sections.entrySet()){
   if(page==1)populateSection(container,section.getKey(),section.getValue(),false);
   else showServerSection(container,section.getKey(),section.getValue());
  }
 }
 void renderServersInBackground(LinearLayout container){
  int generation=groupingGeneration;
  boolean byCountry=countries;
  GroupingBuild build=new GroupingBuild();groupingBuilds.put(container,build);
  ArrayList<String> snapshot=new ArrayList<>(items),sources=new ArrayList<>(owners),subscriptions=new ArrayList<>(urls),titles=new ArrayList<>(groups);
  String query=serverQuery.trim().toLowerCase(Locale.ROOT);
  groupingPool.execute(()->{
   if(generation!=groupingGeneration)return;
   LinkedHashMap<String,ArrayList<Integer>> grouped=new LinkedHashMap<>();
   if(!byCountry&&query.isEmpty())for(String owner:subscriptions)grouped.put(owner,new ArrayList<>());
   Set<String> displayed=new HashSet<>();
   for(int i=0;i<snapshot.size();i++){
    if(generation!=groupingGeneration)return;
    String item=snapshot.get(i);
    if(!serverMatches(item,titles.get(i),query))continue;
    if(byCountry&&!displayed.add(item))continue;
    String key=byCountry?SubscriptionInfo.country(SubscriptionInfo.name(item)):sources.get(i);
    grouped.computeIfAbsent(key,k->new ArrayList<>()).add(i);
   }
   runOnUiThread(()->{
    if(isDestroyed()||page!=0||generation!=groupingGeneration||serverSections!=container)return;
    if(!items.equals(snapshot)||!owners.equals(sources)||!urls.equals(subscriptions)||!groups.equals(titles)){render();return;}
    if(grouped.isEmpty()){showEmptyServers(container);build.finished=true;finishGroupingBuild(container,build,byCountry);return;}
    java.util.Iterator<Map.Entry<String,ArrayList<Integer>>> iterator=grouped.entrySet().iterator();
    Runnable addGroups=new Runnable(){@Override public void run(){
     if(generation!=groupingGeneration||serverSections!=container||page!=0)return;
     if(iterator.hasNext()){
      Map.Entry<String,ArrayList<Integer>> entry=iterator.next();
      showServerSection(container,entry.getKey(),entry.getValue(),true,generation);
      if(iterator.hasNext())container.postOnAnimation(this);
      else {build.finished=true;finishGroupingBuild(container,build,byCountry);}
     }
    }};
    container.postOnAnimation(addGroups);
   });
  });
 }
 boolean serverMatches(String item,String subscription,String query){
  if(query.isEmpty())return true;
  String server=SubscriptionInfo.name(item);
  String country=SubscriptionInfo.countryLabel(this,SubscriptionInfo.country(server));
  return server.toLowerCase(Locale.ROOT).contains(query)||country.toLowerCase(Locale.ROOT).contains(query)||subscription.toLowerCase(Locale.ROOT).contains(query);
 }
 void showServerSearch(){
  EditText search=new EditText(this);
  search.setSingleLine(true);search.setTextSize(15);search.setTextColor(VexisUi.INK);search.setHintTextColor(VexisUi.MUTED);
  search.setHint(getString(R.string.main_text_9));search.setContentDescription(getString(R.string.main_text_9));search.setPadding(dp(11),0,dp(11),0);
  GradientDrawable border=shape(VexisUi.WHITE,9);border.setStroke(dp(1),VexisUi.OUTLINE);search.setBackground(border);
  search.setText(serverQuery);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    serverQuery=s.toString();
    if(page!=0||serverSections==null)return;
    groupingGeneration++;invalidateGroupingCache();pingViews.clear();refreshButtons.clear();manualViews.clear();countryHeaders.clear();clearSelectionViews();
    serverSections.removeAllViews();renderServersInBackground(serverSections);
   }
   public void afterTextChanged(android.text.Editable value){}
  });
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(44));params.bottomMargin=dp(8);body.addView(search,params);
 }
 void showListTitle(String title,boolean serverActions){
  LinearLayout bar=new LinearLayout(this);
  bar.setGravity(Gravity.CENTER_VERTICAL);
  TextView heading=text(title,23,VexisUi.INK,true);
  heading.setLetterSpacing(-.02f);
  bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  if(serverActions){
   View plus=iconAction(R.drawable.vexis_ic_add,getString(R.string.main_text_10),this::add);
   View more=iconAction(R.drawable.vexis_ic_more,getString(R.string.main_text_11),null);
   more.setOnClickListener(v->showServerMenu(more));
   bar.addView(plus,new LinearLayout.LayoutParams(dp(43),dp(44)));
   bar.addView(more,new LinearLayout.LayoutParams(dp(40),dp(44)));
  }
  if(page==1){
   sortControl=new FrameLayout(this);
   bar.addView(sortControl,new LinearLayout.LayoutParams(dp(40),dp(44)));
   setSortingSelected(sortingSelected);
  }
  body.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
  if(!serverActions)addDivider(body);
 }
 void showPingLimitField(){
  LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=text(getString(R.string.main_text_12),12,VexisUi.MUTED,false);
  title.setPadding(0,dp(13),dp(6),dp(6));titleRow.addView(title);
  TextView help=text("?",12,VexisUi.ACCENT,true);help.setGravity(Gravity.CENTER);help.setBackground(shape(VexisUi.PALE,11));
  help.setContentDescription(getString(R.string.main_text_13));
  help.setOnClickListener(v->showSettingHelp(help,getString(R.string.main_extra_1)));
  titleRow.addView(help,new LinearLayout.LayoutParams(dp(22),dp(22)));body.addView(titleRow);
  LinearLayout input=new LinearLayout(this);
  input.setGravity(Gravity.CENTER_VERTICAL);
  GradientDrawable border=shape(VexisUi.WHITE,9);
  border.setStroke(dp(1),VexisUi.OUTLINE);
  input.setBackground(border);
  EditText field=new EditText(this);
  field.setBackgroundColor(Color.TRANSPARENT);
  field.setSingleLine(true);
  field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
  field.setTextColor(VexisUi.INK);
  field.setHintTextColor(VexisUi.MUTED);
  field.setTextSize(15);
  field.setHint("0");
  field.setPadding(dp(11),0,dp(11),0);
  field.setContentDescription(getString(R.string.main_text_12));
  field.setText(settings.getString("max_ping","0"));
  field.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    settings.edit().putString("max_ping",s.toString()).apply();
   }
   public void afterTextChanged(android.text.Editable value){}
  });
  input.addView(field,new LinearLayout.LayoutParams(0,-1,1));
  TextView suffix=text(getString(R.string.main_text_14),13,VexisUi.MUTED,false);
  suffix.setPadding(dp(7),0,dp(11),0);
  input.addView(suffix);
  LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,dp(44));
  spacing.bottomMargin=dp(8);
  body.addView(input,spacing);
 }
 void showServerGrouping(){
  FrameLayout track=new FrameLayout(this);
  track.setBackground(shape(VexisUi.PALE,9));
  View thumb=new View(this);
  thumb.setBackground(shape(VexisUi.ACCENT,9));
  track.addView(thumb,new FrameLayout.LayoutParams(0,-1,Gravity.LEFT));
  LinearLayout row=new LinearLayout(this);
  track.addView(row,new FrameLayout.LayoutParams(-1,-1));
  for(int i=0;i<2;i++){
   final boolean byCountry=i==0;
   Button option=button(getString(byCountry?R.string.group_countries:R.string.group_subscriptions),()->{
    if(countries==byCountry)return;
    groupingScroll[countries?0:1]=mainScroll.getScrollY();
    countries=byCountry;expanded=countries?countryExpanded:subscriptionExpanded;
    for(int j=0;j<row.getChildCount();j++)
     ((Button)row.getChildAt(j)).setTextColor((j==0)==countries?VexisUi.ON_ACCENT:VexisUi.INK);
    cancelFrameAnimation(thumb);
    float target=(countries!=isRtl())?0:track.getWidth()/2f;
    translateFrame(thumb,target,200);
    switchServerGrouping(byCountry);
   });
   option.setTextSize(13);
   option.setTextColor(countries==byCountry?VexisUi.ON_ACCENT:VexisUi.INK);
   option.setBackgroundColor(Color.TRANSPARENT);
   row.addView(option,new LinearLayout.LayoutParams(0,dp(40),1));
  }
  track.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob)->{
   int half=(r-l)/2;
   if(thumb.getLayoutParams().width!=half){
    ViewGroup.LayoutParams params=thumb.getLayoutParams();params.width=half;thumb.setLayoutParams(params);
    thumb.setTranslationX((countries!=isRtl())?0:half);
   }
  });
  body.addView(track,new LinearLayout.LayoutParams(-1,dp(40)));
 }
 boolean insideGrouping(View view,View root){
  for(View current=view;current!=null;){
   if(current==root)return true;
   android.view.ViewParent parent=current.getParent();current=parent instanceof View?(View)parent:null;
  }
  return false;
 }
 <T extends View> void discardGroupingViews(Map<String,ArrayList<T>> registry,View root){
  for(ArrayList<T> views:registry.values())views.removeIf(view->insideGrouping(view,root));
  registry.values().removeIf(List::isEmpty);
 }
 void discardIncompleteGrouping(LinearLayout container){
  groupingBuilds.remove(container);
  discardGroupingViews(selectionChecks,container);discardGroupingViews(pingViews,container);
  discardGroupingViews(refreshButtons,container);discardGroupingViews(manualViews,container);
  selectionGroups.removeIf(group->insideGrouping(group.checkbox,container));
  countryHeaders.values().removeIf(view->insideGrouping(view,container));
 }
 void restoreGroupingScroll(LinearLayout container,int generation,boolean byCountry){
  container.postOnAnimation(()->{
   if(page==0&&generation==groupingGeneration&&serverSections==container&&countries==byCountry)
    mainScroll.scrollTo(0,groupingScroll[byCountry?0:1]);
  });
 }
 void switchServerGrouping(boolean byCountry){
  if(serverStack==null||serverSections==null){render();return;}
  int generation=++groupingGeneration;
  LinearLayout previous=serverSections;
  cancelFrameAnimation(previous);previous.setTranslationX(0);
  if(groupingCache[byCountry?1:0]!=previous)discardIncompleteGrouping(previous);
  if(previous.getParent()==serverStack)serverStack.removeView(previous);
  LinearLayout next=groupingCache[byCountry?0:1];
  if(next==null){next=column();serverSections=next;serverStack.addView(next);renderServersInBackground(next);}
  else {serverSections=next;serverStack.addView(next);updateSelectionViews();}
  next.setTranslationX((byCountry?-1f:1f)*(isRtl()?-1f:1f)*serverStack.getWidth());
  translateFrame(next,0,240);
  restoreGroupingScroll(next,generation,byCountry);
 }
 void showEmptyServers(LinearLayout container){
  LinearLayout empty=column();
  empty.setGravity(Gravity.CENTER_HORIZONTAL);
  empty.setPadding(0,dp(32),0,dp(15));
  ImageView image=icon(page==1?R.drawable.vexis_main_star:R.drawable.vexis_ic_servers,VexisUi.ACCENT);
  image.setPadding(dp(15),dp(15),dp(15),dp(15));
  image.setBackground(shape(VexisUi.PALE,16));
  empty.addView(image,new LinearLayout.LayoutParams(dp(54),dp(54)));
  TextView note=text(page==1?getString(R.string.main_text_15):serverQuery.trim().isEmpty()?getString(R.string.main_text_16):getString(R.string.main_text_17),15,VexisUi.MUTED,true);
  note.setPadding(0,dp(9),0,0);
  note.setGravity(Gravity.CENTER);
  empty.addView(note,new LinearLayout.LayoutParams(-2,-2));
  container.addView(empty,new LinearLayout.LayoutParams(-1,-2));
 }


 void showServerSection(LinearLayout container,String key,List<Integer> ids){
  showServerSection(container,key,ids,false,groupingGeneration);
 }
 void showServerSection(LinearLayout container,String key,List<Integer> ids,boolean progressive,int generation){
  boolean open=expanded.contains(key)||!serverQuery.trim().isEmpty();
  LinearLayout group=column();
  LinearLayout row=new LinearLayout(this);
  row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(6),0,dp(6),0);
  ripple(row,groupColor(ids,open),9);
  if(countries)countryHeaders.put(key,row);
  ArrayList<Integer> allIds=new ArrayList<>();
  for(int i=0;i<items.size();i++)if(countries?country(items.get(i)).equals(key):owners.get(i).equals(key))allIds.add(i);
  int checked=0;
  for(int i:allIds)if(selected.contains(items.get(i)))checked++;
  boolean partial=checked>0&&checked<allIds.size();
  CheckBox all=serverCheck(!allIds.isEmpty()&&checked==allIds.size(),partial,
   countries?(partial?getString(R.string.main_text_1):getString(R.string.main_text_2)):
    (partial?getString(R.string.main_text_3):getString(R.string.main_text_4)));
  all.setEnabled(!ids.isEmpty());
  ArrayList<String> groupItems=new ArrayList<>();
  for(int i:allIds)groupItems.add(items.get(i));
  selectionGroups.add(new SelectionGroup(all,groupItems,countries));
  all.setOnClickListener(v->{
   for(String item:groupItems)if(all.isChecked())selected.add(item);else selected.remove(item);
   selectionVersion++;db.updateSelected(groupItems,all.isChecked());
   updateSelectionViews();
  });
  row.addView(all,new LinearLayout.LayoutParams(dp(32),dp(48)));
  if(countries&&!key.isEmpty())row.addView(flagView(key));
  String title=countries?SubscriptionInfo.countryLabel(this,key):db.title(key);
  TextView groupTitle=text(title,15,VexisUi.INK,true);
  groupTitle.setMaxLines(2);
  groupTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
  groupTitle.setPadding(dp(3),0,dp(4),0);
  row.addView(groupTitle,new LinearLayout.LayoutParams(0,-2,1));
  if(countries){
   TextView count=text(Integer.toString(ids.size()),13,VexisUi.INK,false);
   count.setPadding(dp(3),0,dp(9),0);
   row.addView(count);
  }else{
   View test=iconAction(R.drawable.vexis_main_signal,getString(R.string.main_text_18)+title,()->pingSubscription(key));
   test.setEnabled(!ids.isEmpty());
   row.addView(test,new LinearLayout.LayoutParams(dp(37),dp(42)));
   if(!key.startsWith("local:")){
    View update=iconAction(R.drawable.vexis_ic_refresh,getString(R.string.main_text_19)+title,()->refreshSubscription(key));
    refreshButtons.computeIfAbsent(key,k->new ArrayList<>()).add(update);
    updateRefreshButton(update,refreshing.containsKey(key));
    row.addView(update,new LinearLayout.LayoutParams(dp(37),dp(42)));
   }
  }
  ImageView arrow=icon(R.drawable.vexis_ic_down,VexisUi.MUTED);
  arrow.setRotation(open?180f:0f);
  row.addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(18)));
  group.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));
  VexisMotion.Section content=new VexisMotion.Section(this);
  content.setBranchGuide(countries);
  group.addView(content,new LinearLayout.LayoutParams(-1,-2));
  final boolean[] filling={open&&progressive};
  if(open&&progressive){
   GroupingBuild build=groupingBuilds.get(container);
   if(build!=null)build.pending++;
   final boolean buildCountry=countries;
   if(!countries)showSubscriptionInfo(content,key);
   final boolean branch=countries;
   Runnable append=new Runnable(){int offset;
    @Override public void run(){
     if(generation!=groupingGeneration||page!=0||content.getParent()!=group)return;
     int end=Math.min(offset+3,ids.size());
     populateSection(content,key,ids.subList(offset,end),branch,false);
     offset=end;
     if(offset<ids.size())content.postOnAnimation(this);else {filling[0]=false;if(build!=null){build.pending--;finishGroupingBuild(container,build,buildCountry);}}
    }
   };
   content.postOnAnimation(append);
  }else if(open)populateSection(content,key,ids,countries);
  else content.setVisibility(View.GONE);
  row.setOnClickListener(v->{
   boolean opening=!expanded.remove(key);
   if(opening){
    expanded.add(key);
    if(content.getChildCount()==0&&!filling[0])populateSection(content,key,ids,countries);
   }
   ripple(row,groupColor(ids,opening),9);
   float angle=opening?180f:0f;
   animateFrame(arrow,angle,180,1,null);
   content.setExpanded(opening);
  });
  addDivider(group);
  LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);
  spacing.topMargin=dp(4);
  container.addView(group,spacing);
 }
 int groupColor(List<Integer> ids,boolean open){
  if(open)return VexisUi.PALE;
  if(countries&&!settings.getBoolean(AUTO_MODE,false)){
   String manual=settings.getString(MANUAL_PROFILE,null);
   for(int index:ids)if(items.get(index).equals(manual))return VexisUi.PALE;
  }
  return Color.TRANSPARENT;
 }
 CheckBox serverCheck(boolean checked,boolean partial,String description){
  CheckBox checkbox=new CheckBox(this);
  checkbox.setButtonTintList(null);
  checkbox.setButtonDrawable(partial?R.drawable.vexis_main_check_mixed_selector:R.drawable.vexis_main_check_selector);
  checkbox.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
  checkbox.setMinWidth(0);
  checkbox.setMinimumWidth(0);
  checkbox.setPadding(0,0,0,0);
  checkbox.setChecked(checked);
  checkbox.setContentDescription(description);
  ripple(checkbox,Color.TRANSPARENT,9);
  return checkbox;
 }


 void populateSection(LinearLayout content,String key,List<Integer> ids,boolean branch){
  populateSection(content,key,ids,branch,true);
 }
 void populateSection(LinearLayout content,String key,List<Integer> ids,boolean branch,boolean includeInfo){
  if(includeInfo&&page==0&&!countries)showSubscriptionInfo(content,key);
  for(int i:ids){
   String item=items.get(i);
   LinearLayout server=new LinearLayout(this);
   server.setGravity(Gravity.CENTER_VERTICAL);
   server.setMinimumHeight(dp(52));
   server.setPadding(dp(6),dp(6),dp(4),dp(6));
   String title=name(item);
   if(page==1){
    View remove=iconAction(R.drawable.vexis_ic_close,getString(R.string.main_text_20)+title,()->{selected.remove(item);save();render();});
    server.addView(remove,new LinearLayout.LayoutParams(dp(34),dp(40)));
   }else{
    CheckBox checkbox=serverCheck(selected.contains(item),false,getString(R.string.main_text_21)+title);
    selectionChecks.computeIfAbsent(item,k->new ArrayList<>()).add(checkbox);
    checkbox.setOnClickListener(v->{if(checkbox.isChecked())selected.add(item);else selected.remove(item);selectionVersion++;db.updateSelected(Collections.singletonList(item),checkbox.isChecked());updateSelectionViews();});
    server.addView(checkbox,new LinearLayout.LayoutParams(dp(34),dp(40)));
   }
   if(page==0){
    int fill=!settings.getBoolean(AUTO_MODE,false)&&item.equals(settings.getString(MANUAL_PROFILE,null))?VexisUi.PALE:VexisUi.SURFACE;
    ripple(server,fill,9);
    manualViews.computeIfAbsent(item,k->new ArrayList<>()).add(server);
    server.setOnClickListener(v->{if(!settings.getBoolean(AUTO_MODE,false))selectManualServer(item,owners.get(i));});
   }
   LinearLayout description=column();
   TextView endpoint=text(title,14,VexisUi.INK,true);
   endpoint.setMaxLines(3);
   endpoint.setEllipsize(android.text.TextUtils.TruncateAt.END);
   TextView transport=text(SubscriptionInfo.connectionType(item),12,VexisUi.MUTED,false);
   transport.setPadding(0,dp(3),0,0);
   description.addView(endpoint);
   LinearLayout metadata=new LinearLayout(this);
   metadata.setGravity(Gravity.CENTER_VERTICAL);
   metadata.addView(transport,new LinearLayout.LayoutParams(-2,-2));
   if(page==1||countries){
    String owner=owners.get(i);
    String subscription=subscriptionTitleCache.get(owner);
    if(subscription==null){subscription=db.title(owner);subscriptionTitleCache.put(owner,subscription);}
    TextView source=text(subscription,11,VexisUi.MUTED,false);
    source.setPaddingRelative(dp(10),dp(3),0,0);
    source.setSingleLine();source.setEllipsize(android.text.TextUtils.TruncateAt.END);
    metadata.addView(source,new LinearLayout.LayoutParams(0,-2,1));
   }
   description.addView(metadata);
   server.addView(description,new LinearLayout.LayoutParams(0,-2,1));
   if(page==0){
    FrameLayout pingSlot=new FrameLayout(this);
    server.addView(pingSlot,new LinearLayout.LayoutParams(-2,-2));
    pingViews.computeIfAbsent(item,k->new ArrayList<>()).add(pingSlot);
    updatePingSlot(pingSlot,latency.get(item));
   }
   LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(-1,-2);
   slot.setMarginStart(branch?dp(41):0);
   if(page==1){
    TextView handle=text("⋮⋮",18,VexisUi.MUTED,false);
    handle.setContentDescription(getString(R.string.main_text_22));
    server.addView(handle,new LinearLayout.LayoutParams(dp(24),-2));
    server.setOnLongClickListener(v->{
     android.content.ClipData data=android.content.ClipData.newPlainText("server",item);
     if(android.os.Build.VERSION.SDK_INT>=24)v.startDragAndDrop(data,new View.DragShadowBuilder(v),item,0);
     else v.startDrag(data,new View.DragShadowBuilder(v),item,0);
     return true;
    });
    server.setOnDragListener((v,event)->{
     if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED)ripple(v,VexisUi.PALE,9);
     if(event.getAction()==DragEvent.ACTION_DRAG_EXITED||event.getAction()==DragEvent.ACTION_DRAG_ENDED)ripple(v,Color.TRANSPARENT,9);
     if(event.getAction()==DragEvent.ACTION_DROP && event.getLocalState() instanceof String){
      String moving=(String)event.getLocalState();
      content.post(()->moveSelected(content,moving,item));return true;
     }
     return true;
    });
    LinearLayout entry=column();
    entry.setTag(item);
    entry.addView(server,new LinearLayout.LayoutParams(-1,-2));
    entry.addView(VexisUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));
    content.addView(entry,slot);
    continue;
   }
   else content.addView(serverSwipe(server,endpoint,transport,item,owners.get(i)),slot);
   LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));
   line.setMarginStart(slot.getMarginStart());
   content.addView(VexisUi.divider(this),line);
  }
 }

 void updatePingViews(String item){
  List<FrameLayout> slots=pingViews.get(item);
  if(slots!=null)for(FrameLayout slot:slots)updatePingSlot(slot,latency.get(item));
 }
 void updateRefreshViews(String url){
  List<View> buttons=refreshButtons.get(url);
  if(buttons!=null)for(View button:buttons)updateRefreshButton(button,refreshing.containsKey(url));
 }
 void updateRefreshButton(View button,boolean running){
  if(!running){cancelFrameAnimation(button);button.setRotation(0);return;}
  if(frameAnimations.containsKey(button))return;
  final long began=android.os.SystemClock.uptimeMillis();
  Runnable spin=new Runnable(){public void run(){
   if(frameAnimations.get(button)!=this)return;
   if(!button.isAttachedToWindow()){frameAnimations.remove(button);return;}
   button.setRotation((android.os.SystemClock.uptimeMillis()-began)%850L*360f/850f);
   button.postOnAnimation(this);
  }};
  frameAnimations.put(button,spin);button.postOnAnimation(spin);
 }
 void updatePingSlot(FrameLayout slot,String ping){
  if(java.util.Objects.equals(slot.getTag(),ping))return;
  slot.setTag(ping);slot.removeAllViews();
  if("Проверка".equals(ping)){
   View indicator=new View(this){
    final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    @Override protected void onDraw(android.graphics.Canvas canvas){
     super.onDraw(canvas);
     float stroke=dp(2), inset=stroke/2f;
     paint.setColor(VexisUi.ACCENT);paint.setStyle(android.graphics.Paint.Style.STROKE);
     paint.setStrokeWidth(stroke);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
     float angle=(android.os.SystemClock.uptimeMillis()%850L)*360f/850f;
     canvas.drawArc(inset,inset,getWidth()-inset,getHeight()-inset,angle,250f,false,paint);
     if(isShown())postInvalidateOnAnimation();
    }
   };
   indicator.setContentDescription(getString(R.string.main_text_23));
   FrameLayout.LayoutParams space=new FrameLayout.LayoutParams(dp(15),dp(15));
   space.setMargins(dp(7),0,dp(8),0);slot.addView(indicator,space);
  }else if(ping!=null&&!"Ожидание".equals(ping)){
   TextView delay=text(ServiceText.translate(this,ping),13,"n/a".equals(ping)?VexisUi.DANGER:VexisUi.INK,false);
   delay.setPadding(dp(6),0,0,0);delay.setFontFeatureSettings("tnum");slot.addView(delay);
  }
 }


 View flagView(String code){
  ImageView flag=new ImageView(this);
  LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(24),dp(16));
  space.setMarginEnd(dp(8));
  flag.setLayoutParams(space);
  flag.setScaleType(ImageView.ScaleType.FIT_CENTER);
  flag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  android.graphics.drawable.GradientDrawable border=new android.graphics.drawable.GradientDrawable();
  border.setColor(Color.TRANSPARENT);border.setStroke(dp(1),VexisUi.OUTLINE);
  flag.setForeground(border);
  try{
   com.caverock.androidsvg.SVG svg=com.caverock.androidsvg.SVG.getFromAsset(getAssets(),
    "flags/"+code.toLowerCase(Locale.ROOT)+".svg");
   flag.setImageDrawable(new android.graphics.drawable.PictureDrawable(svg.renderToPicture(dp(24),dp(16))));
   flag.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  }catch(Exception ignored){}
  return flag;
 }
 void moveSelected(LinearLayout list,String moving,String target){
  if(page!=1||!list.isAttachedToWindow()||moving.equals(target)||!selected.contains(moving)||!selected.contains(target))return;
  View movingRow=list.findViewWithTag(moving);
  View targetRow=list.findViewWithTag(target);
  if(movingRow==null||targetRow==null||movingRow.getParent()!=list||targetRow.getParent()!=list)return;
  int targetIndex=list.indexOfChild(targetRow);
  pendingSelectedSort=null;
  setSortingSelected(false);
  ArrayList<String> order=new ArrayList<>(selected);
  int to=order.indexOf(target);
  order.remove(moving);
  order.add(to,moving);
  selected.clear();
  selected.addAll(order);
  list.removeView(movingRow);
  list.addView(movingRow,targetIndex);
  save();
 }
 void showSelectedSort(View anchor){
  if(sortingSelected)return;
  String[] options={getString(R.string.main_text_24),getString(R.string.main_text_25),getString(R.string.main_text_26),getString(R.string.main_text_27),getString(R.string.main_text_28),getString(R.string.main_text_29),getString(R.string.main_text_30)};
  int[] icons=new int[options.length];java.util.Arrays.fill(icons,R.drawable.vexis_ic_servers);
  showMenu(anchor,options,icons,index->{
   if(index==6){
    if(pingBusy()||selected.isEmpty())return;
    LinkedHashMap<String,PingGroup> groups=new LinkedHashMap<>();Set<String> added=new HashSet<>();
    for(int i=0;i<items.size();i++)if(selected.contains(items.get(i))&&added.add(items.get(i)))groups.computeIfAbsent(owners.get(i),k->new PingGroup()).queue.add(items.get(i));
    if(groups.isEmpty())return;
    pendingSelectedSort=new HashSet<>(added);setSortingSelected(true);PingService.start(this,new ArrayList<>(groups.values()));syncUserPings();return;
   }
   pendingSelectedSort=null;setSortingSelected(true);
   if(sortControl!=null)sortControl.post(()->{try{applySelectedSort(index);}finally{setSortingSelected(false);}});
  });
 }
 void setSortingSelected(boolean busy){
  sortingSelected=busy;
  if(sortControl==null)return;
  sortControl.removeAllViews();
  if(busy){
   ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleSmall);
   progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(VexisUi.ACCENT));
   progress.setContentDescription(getString(R.string.main_text_31));
   sortControl.addView(progress,new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));
  }else{
   View button=iconAction(R.drawable.vexis_ic_more,getString(R.string.main_text_32),null);
   button.setOnClickListener(v->showSelectedSort(button));
   sortControl.addView(button,new FrameLayout.LayoutParams(-1,-1));
  }
 }
 void applySelectedSort(int index){
   ArrayList<String> order=new ArrayList<>(selected);
   Map<String,Integer> indices=new HashMap<>();for(int i=0;i<items.size();i++)if(!indices.containsKey(items.get(i)))indices.put(items.get(i),i);
   Map<String,String> titles=new HashMap<>();for(String owner:owners)if(!titles.containsKey(owner))titles.put(owner,db.title(owner));
   Collections.sort(order,(a,b)->{
    if(index>=6){long x=selectedPing(a),y=selectedPing(b);if(x==Long.MAX_VALUE||y==Long.MAX_VALUE)return Long.compare(x,y);return index==6?Long.compare(x,y):Long.compare(y,x);}
    int ai=indices.containsKey(a)?indices.get(a):-1,bi=indices.containsKey(b)?indices.get(b):-1;
    String x=index<2?(ai<0?"":titles.get(owners.get(ai))):index<4?name(a):SubscriptionInfo.countryLabel(this,country(a));
    String y=index<2?(bi<0?"":titles.get(owners.get(bi))):index<4?name(b):SubscriptionInfo.countryLabel(this,country(b));
    int result=String.CASE_INSENSITIVE_ORDER.compare(x,y);return index%2==0?result:-result;
   });
   selected.clear();selected.addAll(order);save();render();
 }
 long selectedPing(String link){
  String value=latency.get(link);if(value==null)return Long.MAX_VALUE;
  try{return Long.parseLong(value.replaceAll("[^0-9]",""));}catch(NumberFormatException e){return Long.MAX_VALUE;}
 }
 void showServerMenu(View anchor){
  showMenu(anchor,new String[]{getString(R.string.main_text_33),getString(R.string.main_text_34),getString(R.string.main_text_35),
   getString(R.string.main_text_36),getString(R.string.main_text_37)},new int[]{R.drawable.vexis_ic_refresh,R.drawable.vexis_main_signal,
   R.drawable.vexis_main_doc,R.drawable.vexis_ic_check,R.drawable.vexis_ic_close},position->{
   switch(position){
    case 0:refresh();break;
    case 1:pingAll();break;
    case 2:
     pingGeneration++;PingService.clear(this);userPingProfiles.clear();latency.clear();render();break;
    case 3:selected.addAll(items);save();render();break;
    case 4:selected.clear();save();render();break;
   }
  });
 }
 void showMenu(View anchor,String[] actions,int[] icons,java.util.function.IntConsumer select){
  if(!anchor.isAttachedToWindow())return;
  LinearLayout entries=column();
  entries.setPadding(dp(9),dp(5),dp(9),dp(5));
  ScrollView content=new ScrollView(this);
  content.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
  content.setVerticalScrollBarEnabled(false);
  content.addView(entries);
  int width=Math.min(dp(275),root.getWidth()-dp(32));
  int height=Math.min(dp(10+actions.length*44),root.getHeight()-dp(22));
  if(width<=0||height<=0)return;
  PopupWindow popup=new PopupWindow(content,width,height,true);
  GradientDrawable surface=shape(VexisUi.WHITE,14);
  surface.setStroke(dp(1),VexisUi.LINE);
  popup.setBackgroundDrawable(surface);
  popup.setElevation(dp(10));
  popup.setOutsideTouchable(true);
  for(int i=0;i<actions.length;i++){
   final int index=i;
   LinearLayout row=new LinearLayout(this);
   row.setGravity(Gravity.CENTER_VERTICAL);
   row.setPadding(dp(7),0,dp(5),0);
   ripple(row,Color.TRANSPARENT,9);
   row.addView(icon(icons[i],VexisUi.ACCENT),new LinearLayout.LayoutParams(dp(18),dp(18)));
   TextView name=text(actions[i],14,VexisUi.TEXT,false);
   name.setPaddingRelative(dp(10),0,0,0);
   row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   row.setOnClickListener(view->{popup.dismiss();select.accept(index);});
   entries.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));
  }
  popup.setAnimationStyle(0);
  popup.showAsDropDown(anchor,0,dp(3),Gravity.END);
  VexisMotion.enter(entries,-dp(4));
 }
 void showSubscriptionInfo(LinearLayout container,String owner){
  String[] info=db.subscriptionInfo(owner);
  HashMap<String,Long> usage=new HashMap<>();
  if(info[0]!=null)for(String pair:info[0].split(";")){
   String[] entry=pair.split("=",2);
   if(entry.length<2)continue;
   try{
    long count=Long.parseLong(entry[1].trim());
    if(count>=0)usage.put(entry[0].trim().toLowerCase(Locale.ROOT),count);
   }catch(NumberFormatException ignored){}
  }
  LinearLayout summary=column();
  summary.setPadding(dp(7),dp(9),dp(7),dp(6));
  if(usage.containsKey("upload")||usage.containsKey("download")){
   long up=usage.getOrDefault("upload",0L), down=usage.getOrDefault("download",0L);
   long used=up>Long.MAX_VALUE-down?Long.MAX_VALUE:up+down;
   long total=usage.getOrDefault("total",0L);
   String traffic=android.text.format.Formatter.formatFileSize(this,used);
   if(total>0)traffic+=" / "+android.text.format.Formatter.formatFileSize(this,total);
   summary.addView(text(traffic,13,VexisUi.INK,true));
   if(total>0){
    ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(1000);
    progress.setProgress((int)Math.min(1000,1000.0*used/total));
    progress.setProgressTintList(android.content.res.ColorStateList.valueOf(VexisUi.ACCENT));
    progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(VexisUi.PALE));
    progress.setContentDescription(getString(R.string.main_text_38)+traffic);
    LinearLayout.LayoutParams height=new LinearLayout.LayoutParams(-1,dp(6));
    height.topMargin=dp(5);
    summary.addView(progress,height);
   }
  }
  Long expire=usage.get("expire");
  String expires=null;
  if(expire!=null&&expire>0&&expire<=Long.MAX_VALUE/1000){
   String date=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("ru")).format(new Date(expire*1000));
   expires=getString(R.string.main_text_39)+date;
  }
  if(expires!=null){
   TextView expiry=text(expires,12,VexisUi.MUTED,false);
   expiry.setSingleLine(true);
   expiry.setTextSize(10);
   if(summary.getChildCount()==0){
    summary.addView(expiry);
   }else{
    View traffic=summary.getChildAt(0);
    summary.removeViewAt(0);
    LinearLayout totals=new LinearLayout(this);
    totals.setGravity(Gravity.CENTER_VERTICAL);
    totals.addView(traffic,new LinearLayout.LayoutParams(0,-2,1));
    expiry.setMaxWidth(Math.min(dp(204),getResources().getDisplayMetrics().widthPixels/2));
    expiry.setGravity(Gravity.END);
    expiry.setPaddingRelative(dp(8),0,0,0);
    totals.addView(expiry,new LinearLayout.LayoutParams(-2,-2));
    summary.addView(totals,0,new LinearLayout.LayoutParams(-1,-2));
   }
  }
  if(info[1]!=null&&!info[1].isEmpty())appendSubscriptionLine(summary,info[1],13);
  if(info[2]!=null)try{
   String date=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault())
    .format(new Date(Long.parseLong(info[2])));
   appendSubscriptionLine(summary,getString(R.string.main_text_40)+date,12);
  }catch(NumberFormatException ignored){}
  if(summary.getChildCount()>0)container.addView(summary,new LinearLayout.LayoutParams(-1,-2));
 }
 void appendSubscriptionLine(LinearLayout summary,String value,int size){
  TextView row=text(value,size,VexisUi.MUTED,false);
  row.setMaxLines(4);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
  row.setOnClickListener(v->{boolean expanded=row.getMaxLines()>4;row.setMaxLines(expanded?4:Integer.MAX_VALUE);row.setEllipsize(expanded?android.text.TextUtils.TruncateAt.END:null);});
  row.setPadding(0,dp(5),0,0);
  summary.addView(row);
 }
 void showEngines(){
  for(EngineUpdates.Engine engine:EngineUpdates.Engine.values()){
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(55));
   LinearLayout.LayoutParams emblem=new LinearLayout.LayoutParams(dp(21),dp(21));
   emblem.setMarginEnd(dp(11));
   row.addView(icon(R.drawable.vexis_main_layers,VexisUi.ACCENT),emblem);
   TextView version=VexisUi.text(this,engine.title,14,VexisUi.TEXT,true);
   String versionLabel=engine.display();
   TextView detail=VexisUi.text(this,getString(R.string.main_text_41)+versionLabel,12,VexisUi.MUTED,false);
   LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
   info.addView(version);LinearLayout.LayoutParams sub=new LinearLayout.LayoutParams(-1,-2);sub.topMargin=dp(3);info.addView(detail,sub);
   row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
   Button update=button(getString(R.string.main_text_42),()->{});
   update.setAllCaps(false);update.setTextSize(13);update.setTextColor(VexisUi.DEEP);
   update.setBackground(VexisUi.buttonBackground(this,VexisUi.OUTLINE_BUTTON));
   update.setOnClickListener(v->{update.setEnabled(false);update.setText(getString(R.string.main_text_43));EngineUpdates.check(this,engine,()->{update.setEnabled(true);update.setText(getString(R.string.main_text_42));});});
   row.addView(update,new LinearLayout.LayoutParams(dp(104),dp(38)));body.addView(row);vexisSettingsDivider();
  }
 }
 void showSettings(){
  settingsHeading(getString(R.string.main_text_44));
  settingsSwitch(getString(R.string.main_text_45), "auto_failover", true);
  settingsSwitch(getString(R.string.manual_reconnect), "manual_reconnect", false);
  int rounds=settings.getInt("recovery_round_limit",0);
  numberSetting(getString(R.string.main_text_46), "recovery_round_limit", 0, 0, 100000,
    rounds==0?getString(R.string.main_text_47):String.valueOf(rounds), getString(R.string.main_text_48), null);
  settingsRow(getString(R.string.main_text_49), null, ()->openRouting(true));
  settingsRow(getString(R.string.main_text_50), null, ()->openRouting(false));

  settingsHeading(getString(R.string.main_text_51));
  boolean fixed=settings.getInt("ping_fixed",10)>0;
  settingsRow(getString(R.string.main_text_52), fixed?getString(R.string.main_text_53):getString(R.string.main_text_54), this::choosePingStrategy);
  if(fixed){
   numberSetting(getString(R.string.main_text_55), "ping_fixed", 10, 1, 100000,
     String.valueOf(settings.getInt("ping_fixed",10)), null, null);
  }else{
   int denominator=settings.getInt("ping_denominator",5);
   numberSetting(getString(R.string.main_text_56), "ping_denominator", 5, 1, 100000,
     "1 / "+denominator, getString(R.string.main_text_57), null);
   numberSetting(getString(R.string.main_text_58), "ping_min_total", 10, 0, 100000,
     String.valueOf(settings.getInt("ping_min_total",10)), null, null);
   numberSetting(getString(R.string.main_text_59), "ping_max", 100, 1, 100000,
     String.valueOf(settings.getInt("ping_max",100)), null, null);
  }
  numberSetting(getString(R.string.main_text_60), "ping_timeout", 10000, 1, 100000,
    settings.getInt("ping_timeout",10000)+getString(R.string.main_extra_16), null, null);

  settingsHeading(getString(R.string.main_text_61));
  settingsRow(getString(R.string.main_text_5), String.valueOf(db.urls().size()),
    ()->openSettingsScreen(new Intent(this,MainActivity.class).putExtra("subscription_list",true)));
  settingsSwitch(getString(R.string.main_text_62), "refresh_on_open", true);
  settingsSwitch(getString(R.string.main_text_63), "refresh_on_reconnect", true);
  settingsSwitch(getString(R.string.main_text_64), "refresh_wifi_only", false);
  numberSetting(getString(R.string.main_text_65), "interval", 60, 1, Integer.MAX_VALUE,
    settings.getInt("interval",60)+getString(R.string.main_text_66), null, this::schedule);

  settingsHeading(getString(R.string.main_text_67));
  settingsSwitch(getString(R.string.main_text_68), "connection_alerts", true);
  settingsRow(getString(R.string.main_language_title), languageLabel(), this::chooseLanguage);
  settingsRow(getString(R.string.main_text_69), themeLabel(), this::chooseTheme);

  settingsHeading(getString(R.string.main_text_70));
  settingsRow(getString(R.string.main_text_71), null, ()->openSettingsScreen(new Intent(this,DiagnosticsActivity.class)));
  settingsRow(getString(R.string.main_text_72), null, ()->openSettingsScreen(new Intent(this,JournalActivity.class)));
  settingsRow(getString(R.string.main_text_6), getString(R.string.main_text_73), ()->openSettingsScreen(new Intent(this,MainActivity.class).putExtra("engine_settings",true)));
 }
 void openSettingsScreen(Intent intent){
  startActivity(intent);
  overridePendingTransition(0,0);
 }

 void settingsHeading(String title){
  TextView heading=VexisUi.text(this,title,13,VexisUi.DEEP,true);
  heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(9),0,dp(9),0);
  heading.setBackground(VexisUi.rounded(VexisUi.PALE,7,this));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(29));lp.topMargin=dp(12);
  body.addView(heading,lp);
 }

 int vexisSettingsIcon(String title){
  if(getString(R.string.main_text_45).equals(title)||getString(R.string.manual_reconnect).equals(title))return R.drawable.vexis_ic_refresh;
  if(getString(R.string.main_text_46).equals(title)||getString(R.string.main_text_60).equals(title)||getString(R.string.main_text_65).equals(title))return R.drawable.vexis_main_clock;
  if(getString(R.string.main_text_49).equals(title))return R.drawable.vexis_main_apps;
  if(getString(R.string.main_text_50).equals(title))return R.drawable.vexis_ic_shield;
  if(getString(R.string.main_text_52).equals(title)||getString(R.string.main_text_6).equals(title))return R.drawable.vexis_main_layers;
  if(getString(R.string.main_text_5).equals(title))return R.drawable.vexis_ic_link;
  if(getString(R.string.main_text_68).equals(title))return R.drawable.vexis_main_bell;
  if(getString(R.string.main_language_title).equals(title))return R.drawable.vexis_ic_globe;
  if(getString(R.string.main_text_69).equals(title))return R.drawable.vexis_ic_theme;
  if(getString(R.string.main_text_71).equals(title))return R.drawable.vexis_main_signal;
  if(getString(R.string.main_text_72).equals(title))return R.drawable.vexis_main_doc;
  return 0;
 }
 void vexisSettingsIcon(LinearLayout row,String title){
  int icon=vexisSettingsIcon(title);if(icon==0)return;
  android.widget.ImageView image=new android.widget.ImageView(this);image.setImageResource(icon);image.setColorFilter(VexisUi.ACCENT);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(19),dp(19));lp.setMarginEnd(dp(11));row.addView(image,lp);
 }
 void vexisSettingsDivider(){body.addView(VexisUi.divider(this),new LinearLayout.LayoutParams(-1,dp(1)));}
 void settingsRow(String title,String value,Runnable action){
  settingsRow(title,value,vexisSettingsIcon(title),action);
 }
 void settingsRow(String title,String value,int emblem,Runnable action){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(page==4?55:50));
  row.setPadding(0,dp(7),0,dp(7));
  if(emblem!=0){
   LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(19),dp(19));space.setMarginEnd(dp(11));
   row.addView(icon(emblem,VexisUi.ACCENT),space);
  }
  row.addView(VexisUi.text(this,title,14,VexisUi.TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
  if(value!=null){TextView summary=VexisUi.text(this,value,12,VexisUi.MUTED,false);summary.setGravity(Gravity.END);summary.setMaxWidth(dp(145));summary.setPadding(dp(8),0,dp(8),0);row.addView(summary);}
  android.widget.ImageView arrow=new android.widget.ImageView(this);arrow.setImageResource(R.drawable.vexis_ic_chevron);arrow.setColorFilter(VexisUi.MUTED);if(isRtl())arrow.setScaleX(-1);
  row.addView(arrow,new LinearLayout.LayoutParams(dp(17),dp(17)));
  ripple(row,Color.TRANSPARENT,8);
  row.setOnClickListener(v->action.run());row.setFocusable(true);body.addView(row);vexisSettingsDivider();
 }

 void settingsSwitch(String title,String key,boolean fallback){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(50));
  row.setPadding(0,dp(7),0,dp(7));
  vexisSettingsIcon(row,title);
  TextView caption=VexisUi.text(this,title,14,VexisUi.TEXT,false);
  if(isRtl())caption.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
  String explanation=settingsExplanation(key);
  if(explanation!=null){
   TextView help=text("?",12,VexisUi.ACCENT,true);help.setGravity(Gravity.CENTER);
   help.setContentDescription(getString(R.string.main_text_74)+title);help.setBackground(shape(VexisUi.PALE,14));
   LinearLayout.LayoutParams helpSize=new LinearLayout.LayoutParams(dp(22),dp(22));helpSize.setMarginEnd(dp(8));
   row.addView(help,helpSize);help.setOnClickListener(v->showSettingHelp(help,explanation));
  }
  Switch toggle=VexisUi.switchControl(this);toggle.setChecked(settings.getBoolean(key,fallback));
  toggle.setContentDescription(title);
  toggle.setOnCheckedChangeListener((v,checked)->{settings.edit().putBoolean(key,checked).apply();if("refresh_wifi_only".equals(key))schedule();});
  row.addView(toggle);ripple(row,Color.TRANSPARENT,8);
  row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));body.addView(row);vexisSettingsDivider();
 }
 String settingsExplanation(String key){
  switch(key){
   case "auto_failover":return getString(R.string.main_extra_2);
   case "manual_reconnect":return getString(R.string.manual_reconnect_help);
   case "refresh_on_open":return getString(R.string.main_extra_3);
   case "refresh_on_reconnect":return getString(R.string.main_extra_4);
   case "refresh_wifi_only":return getString(R.string.main_extra_5);
   case "connection_alerts":return getString(R.string.main_extra_6);
   case "recovery_round_limit":return getString(R.string.main_extra_7);
   case "ping_fixed":return getString(R.string.main_extra_8);
   case "ping_denominator":return getString(R.string.main_extra_9);
   case "ping_min_total":return getString(R.string.main_extra_10);
   case "ping_max":return getString(R.string.main_extra_11);
   case "ping_timeout":return getString(R.string.main_extra_12);
   case "interval":return getString(R.string.main_extra_13);
   default:return null;
  }
 }
 void showSettingHelp(View anchor,String message){
  TextView bubble=text(message,14,VexisUi.TEXT,false);
  bubble.setPadding(dp(14),dp(12),dp(14),dp(12));
  bubble.setBackground(shape(VexisUi.PALE,12));
  android.widget.PopupWindow popup=new android.widget.PopupWindow(bubble,Math.min(dp(300),getResources().getDisplayMetrics().widthPixels-dp(48)),-2,true);
  popup.setBackgroundDrawable(shape(VexisUi.SURFACE,12));popup.setElevation(dp(8));popup.setOutsideTouchable(true);
  popup.showAsDropDown(anchor,0,dp(4),Gravity.END);
 }
 void vexisSettingsField(EditText field){
  android.graphics.drawable.GradientDrawable shape=VexisUi.rounded(VexisUi.SURFACE,9,this);
  shape.setStroke(dp(1),VexisUi.OUTLINE);field.setBackground(shape);field.setTextColor(VexisUi.TEXT);
  field.setHintTextColor(VexisUi.MUTED);field.setTextSize(14);field.setPadding(dp(11),dp(9),dp(11),dp(9));field.setMinHeight(dp(43));
 }
 void showCurrentOption(String title,String option){
  showChoices(title,new String[]{option},0,getString(R.string.main_text_75),null);
 }
 String languageLabel(){
  String mode=AppLocale.getMode(this);
  if("ar".equals(mode))return "العربية";
  if("fr".equals(mode))return "Français";
  if("zh".equals(mode))return "中文";
  return getString("ru".equals(mode)?R.string.main_language_russian:"en".equals(mode)?R.string.main_language_english:R.string.main_language_system);
 }
 void chooseLanguage(){
  String mode=AppLocale.getMode(this);
  int current="ru".equals(mode)?1:"en".equals(mode)?2:"ar".equals(mode)?3:"fr".equals(mode)?4:"zh".equals(mode)?5:0;
  showChoices(getString(R.string.main_language_title),new String[]{getString(R.string.main_language_system),getString(R.string.main_language_russian),getString(R.string.main_language_english),"العربية","Français","中文"},current,getString(R.string.main_language_cancel),index->{
   String next=index==1?"ru":index==2?"en":index==3?"ar":index==4?"fr":index==5?"zh":"system";
   if(!next.equals(mode)){AppLocale.setMode(this,next);recreate();}
  });
 }
 String themeLabel(){
  String mode=VexisUi.getThemeMode(this);
  return "light".equals(mode)?getString(R.string.main_text_76):"dark".equals(mode)?getString(R.string.main_text_77):getString(R.string.main_text_78);
 }
 void chooseTheme(){
  String mode=VexisUi.getThemeMode(this);
  int current="light".equals(mode)?1:"dark".equals(mode)?2:0;
  showChoices(getString(R.string.main_text_69),new String[]{getString(R.string.main_text_79),getString(R.string.main_text_76),getString(R.string.main_text_77)},current,getString(R.string.main_text_80),index->{
   if(index==current)return;
   android.graphics.Bitmap previous=null;
   if(root.getWidth()>0&&root.getHeight()>0){
    try{previous=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(previous));}catch(OutOfMemoryError ignored){}
   }
   VexisUi.setThemeMode(this,new String[]{"system","light","dark"}[index]);
   int scrollY=mainScroll.getScrollY();
   VexisUi.applyTheme(this);
   draw();
   mainScroll.post(()->mainScroll.scrollTo(0,scrollY));
   if(previous!=null){
    ImageView overlay=new ImageView(this);overlay.setImageBitmap(previous);overlay.setScaleType(ImageView.ScaleType.FIT_XY);
    ViewGroup decor=(ViewGroup)getWindow().getDecorView();
    decor.addView(overlay,new ViewGroup.LayoutParams(root.getWidth(),root.getHeight()));
    overlay.setY(root.getTop());
    overlay.postDelayed(()->animateFrame(overlay,0f,260,2,()->{decor.removeView(overlay);overlay.setImageDrawable(null);}),100);
   }
  });
 }
 void choosePingStrategy(){
  int current=settings.getInt("ping_fixed",10);
  showChoices(getString(R.string.main_text_52),new String[]{getString(R.string.main_text_54),getString(R.string.main_text_53)},current>0?1:0,getString(R.string.main_text_80),index->{
   SharedPreferences.Editor edit=settings.edit();
   if(index==0&&current>0)edit.putInt("ping_fixed_saved",current).putInt("ping_fixed",0);
   if(index==1&&current==0)edit.putInt("ping_fixed",Math.max(1,settings.getInt("ping_fixed_saved",10)));
   edit.apply();
   render();
  });
 }
 void showChoices(String title,String[] labels,int current,String cancel,java.util.function.IntConsumer selectedOption){
  AlertDialog dialog=dialogShell(title);
  LinearLayout content=dialogContent(dialog);
  if(getString(R.string.main_text_52).equals(title))dialogParagraph(content,getString(R.string.main_extra_14),VexisUi.MUTED);
  for(int i=0;i<labels.length;i++){
   final int index=i;
   RadioButton choice=new RadioButton(this);
   choice.setText(labels[i]);
   choice.setTextColor(VexisUi.TEXT);
   choice.setTextSize(14);
   choice.setChecked(current==i);
   choice.setButtonTintList(android.content.res.ColorStateList.valueOf(current==i?VexisUi.ACCENT:VexisUi.MUTED));
   choice.setCompoundDrawablePadding(dp(8));
   choice.setPadding(dp(5),0,dp(5),0);
   ripple(choice,current==i?VexisUi.PALE:Color.TRANSPARENT,9);
   LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
   space.topMargin=dp(i==0?10:4);
   choice.setMinHeight(dp(44));
   content.addView(choice,space);
   choice.setOnClickListener(v->{
    dialog.dismiss();
    if(selectedOption!=null)selectedOption.accept(index);
   });
  }
  if(cancel!=null)dialogButtons(content,dialog,null,cancel,selectedOption==null?VexisUi.SOLID:VexisUi.QUIET,dialog::dismiss);
  showVexisDialog(dialog);
 }
 void numberSetting(String title,String key,int fallback,int min,int max,String summary,
                    String hint,Runnable onSave){
  settingsRow(title,summary,()->{
   AlertDialog dialog=dialogShell(title);
   LinearLayout form=dialogContent(dialog);
   String explanation=settingsExplanation(key);
   if(explanation!=null)dialogParagraph(form,explanation,VexisUi.MUTED);
   EditText input=new EditText(this);
   input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
   input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(String.valueOf(max).length())});
   input.setSingleLine(true);
   input.setText(String.valueOf(settings.getInt(key,fallback)));
   input.setSelectAllOnFocus(true);
   LinearLayout.LayoutParams field=new LinearLayout.LayoutParams(-1,-2);
   field.topMargin=dp(13);
   String unit="ping_timeout".equals(key)?getString(R.string.main_text_14):"interval".equals(key)?getString(R.string.main_extra_17):"";
   vexisSettingsField(input);
   if(unit.isEmpty())form.addView(input,field);
   else{
    LinearLayout valueRow=new LinearLayout(this);
    valueRow.setGravity(Gravity.CENTER_VERTICAL);
    android.graphics.drawable.Drawable outline=input.getBackground();
    input.setBackgroundColor(Color.TRANSPARENT);
    valueRow.setBackground(outline);
    valueRow.addView(input,new LinearLayout.LayoutParams(0,-2,1));
    TextView suffix=text(unit,12,VexisUi.MUTED,false);
    suffix.setPadding(0,0,dp(11),0);
    valueRow.addView(suffix,new LinearLayout.LayoutParams(-2,-2));
    form.addView(valueRow,field);
   }
   if(hint!=null&&explanation==null)dialogParagraph(form,hint,VexisUi.MUTED);
   dialogButtons(form,dialog,getString(R.string.main_text_80),getString(R.string.main_text_81),()->{
    try{
     String value=input.getText().toString();
     int number=value.isEmpty()&&min==0?0:Integer.parseInt(value);
     if(number<min||number>max)throw new NumberFormatException();
     settings.edit().putInt(key,number).apply();
     if(onSave!=null)onSave.run();
     dialog.dismiss();
     render();
    }catch(NumberFormatException e){
     input.setError(getString(R.string.main_text_82)+min+getString(R.string.main_text_83)+max);
    }
   });
   showVexisDialog(dialog);
  });
 }
 void dialogParagraph(LinearLayout content,String value,int color){
  TextView line=text(value,14,color,false);
  line.setPadding(0,dp(12),0,0);
  content.addView(line);
 }


 @Override public void onBackPressed(){if(getIntent().getBooleanExtra("subscription_list",false)||getIntent().getBooleanExtra("engine_settings",false)){finish();return;}super.onBackPressed();}
 void showSubscriptions(){
  if(urls.isEmpty())body.addView(label(getString(R.string.main_text_84),15));
  for(String url:new ArrayList<>(urls)){
   String title=db.title(url);
   settingsRow(title,null,db.isHidden(url)?R.drawable.vexis_main_lock:R.drawable.vexis_ic_link,
    ()->configureSubscription(url));
  }
 }
 void configureSubscription(String original){
  boolean hidden=db.isHidden(original);
  AlertDialog dialog=dialogShell(getString(R.string.main_text_85));
  LinearLayout form=dialogContent(dialog);
  EditText title=new EditText(this);
  title.setSingleLine(true);
  title.setText(db.title(original));
  addSubscriptionField(form,title,getString(R.string.main_text_86));
  EditText address=new EditText(this);
  boolean editableUrl=!hidden&&!original.startsWith("local:");
  if(editableUrl){
   address.setSingleLine(true);
   address.setText(original);
   address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
   addSubscriptionField(form,address,"URL");
  }
  if(!original.startsWith("local:")){
   long updated=0;
   try{updated=Long.parseLong(db.subscriptionInfo(original)[2]);}catch(Exception ignored){}
   java.text.DateFormat dateFormat=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault());
   dialogParagraph(form,getString(R.string.main_text_87)+(updated>0?dateFormat.format(new Date(updated)):getString(R.string.main_text_88)),VexisUi.MUTED);
   long failure=SubscriptionRefreshJob.lastFailure(this,original);
   if(failure>0)dialogParagraph(form,getString(R.string.main_text_89)+dateFormat.format(new Date(failure)),VexisUi.MUTED);
   long due=SubscriptionRefreshJob.nextDue(this,original,updated);
   dialogParagraph(form,getString(R.string.main_text_90)+(due<=System.currentTimeMillis()?getString(R.string.main_text_91):dateFormat.format(new Date(due))),VexisUi.MUTED);
  }
  LinearLayout concealRow=new LinearLayout(this);
  concealRow.setGravity(Gravity.CENTER_VERTICAL);
  if(hidden){
   LinearLayout.LayoutParams emblem=new LinearLayout.LayoutParams(dp(18),dp(18));
   emblem.setMarginEnd(dp(10));
   concealRow.addView(icon(R.drawable.vexis_main_lock,VexisUi.ACCENT),emblem);
  }
  TextView concealLabel=text(getString(R.string.main_text_92),14,VexisUi.TEXT,false);
  concealLabel.setPaddingRelative(0,0,dp(8),0);
  concealRow.addView(concealLabel,new LinearLayout.LayoutParams(0,-2,1));
  CheckBox conceal=serverCheck(hidden,false,getString(R.string.main_text_92));
  conceal.setEnabled(!hidden);
  concealRow.addView(conceal,new LinearLayout.LayoutParams(dp(25),dp(46)));
  form.addView(concealRow,new LinearLayout.LayoutParams(-1,-2));
  concealRow.setOnClickListener(v->{if(!hidden)conceal.setChecked(!conceal.isChecked());});
  if(!hidden)conceal.setOnCheckedChangeListener((v,checked)->{
   if(!checked)return;
   AlertDialog warning=dialogShell(getString(R.string.main_text_93));
   LinearLayout content=dialogContent(warning);
   dialogParagraph(content,getString(R.string.main_text_94),VexisUi.MUTED);
   final boolean[] confirmed={false};
   warning.setOnDismissListener(dismissed->{
    dialogContentMap.remove(warning);
    if(!confirmed[0])conceal.setChecked(false);
    if(!isFinishing()&&!isDestroyed())dialog.show();
   });
   dialogButtons(content,warning,getString(R.string.main_text_80),getString(R.string.main_text_95),()->{confirmed[0]=true;warning.dismiss();});
   dialog.hide();
   showVexisDialog(warning);
  });
  LinearLayout actions=dialogActionRow(form);
  TextView delete=dialogButton(getString(R.string.main_text_96),VexisUi.QUIET);
  delete.setTextColor(VexisUi.DANGER);
  ripple(delete,VexisUi.PALE,10);
  delete.setOnClickListener(v->{
   AlertDialog removal=dialogShell(getString(R.string.main_text_97));
   LinearLayout content=dialogContent(removal);
   dialogParagraph(content,db.title(original),VexisUi.MUTED);
   final boolean[] removed={false};
   removal.setOnDismissListener(dismissed->{
    dialogContentMap.remove(removal);
    if(!removed[0]&&!isFinishing()&&!isDestroyed())dialog.show();
   });
   dialogButtons(content,removal,getString(R.string.main_text_80),getString(R.string.main_text_96),VexisUi.DESTRUCTIVE,()->{
    removed[0]=true;
    removal.dismiss();
    dialog.dismiss();
    removeSubscription(original);
   });
   dialog.hide();
   showVexisDialog(removal);
  });
  TextView cancel=dialogButton(getString(R.string.main_text_80),VexisUi.QUIET);
  cancel.setOnClickListener(v->dialog.dismiss());
  TextView accept=dialogButton(getString(R.string.main_text_81),VexisUi.SOLID);
  accept.setOnClickListener(v->{
   String newTitle=title.getText().toString().trim();
   String newUrl=editableUrl?address.getText().toString().trim():original;
   if(newTitle.isEmpty()){title.setError(getString(R.string.main_text_98));return;}
   if(editableUrl)try{
    URL parsed=new URL(newUrl);
    if(!"https".equalsIgnoreCase(parsed.getProtocol())||parsed.getHost().isEmpty())throw new IllegalArgumentException();
   }catch(Exception ex){address.setError(getString(R.string.main_text_99));return;}
   final boolean hide=conceal.isChecked();
   Runnable save=()->{
    subscriptionReloadVersion++;
    try{db.editSubscription(original,newUrl,newTitle,hide);}
    catch(Exception ex){AppToast.show(this,getString(R.string.main_text_100));return;}
    if(!newUrl.equals(original))invalidateRefresh(original);
    urls.clear();urls.addAll(db.urls());db.loadServers(items,groups,owners);
    if(subscriptionExpanded.remove(original))subscriptionExpanded.add(newUrl);
    dialog.dismiss();
    render();
    if(!newUrl.equals(original))refreshSubscription(newUrl);
   };
   if(newUrl.equals(original)){save.run();return;}
   if(urls.contains(newUrl)){address.setError(getString(R.string.main_text_101));return;}
   if(!SubscriptionNetwork.refreshAllowed(this)){address.setError(getString(R.string.main_text_102));return;}
   accept.setEnabled(false);delete.setEnabled(false);title.setEnabled(false);address.setEnabled(false);conceal.setEnabled(false);
   accept.setText(getString(R.string.main_text_43));
   subscriptionPool.execute(()->{
    Exception error=null;
    try{SubscriptionNetwork.validate(newUrl);}catch(Exception ex){error=ex;}
    final boolean valid=error==null;
    runOnUiThread(()->{
     if(isDestroyed()||!dialog.isShowing())return;
     accept.setEnabled(true);delete.setEnabled(true);title.setEnabled(true);address.setEnabled(true);conceal.setEnabled(!hidden);
     accept.setText(getString(R.string.main_text_81));
     if(valid)save.run();else address.setError(getString(R.string.main_text_103));
    });
   });
  });
  boolean compact=getResources().getConfiguration().screenWidthDp<380;
  for(TextView action:new TextView[]{delete,cancel,accept}){
   LinearLayout.LayoutParams space=compact?new LinearLayout.LayoutParams(0,-2,1):
    new LinearLayout.LayoutParams(-2,-2);
   if(action!=delete)space.setMarginStart(dp(7));
   int horizontal=dp(compact?4:12);
   action.setPadding(horizontal,dp(5),horizontal,dp(5));
   actions.addView(action,space);
  }
  showVexisDialog(dialog);
 }
 void addSubscriptionField(LinearLayout form,EditText field,String label){
  TextView caption=text(label,12,VexisUi.MUTED,false);
  caption.setPadding(0,dp(11),0,dp(6));
  form.addView(caption);
  field.setContentDescription(label);
  field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter("URL".equals(label)?8192:120)});
  vexisSettingsField(field);
  form.addView(field,new LinearLayout.LayoutParams(-1,-2));
 }


 void removeSubscription(String url){subscriptionReloadVersion++;selectionVersion++;invalidateRefresh(url);db.deleteSubscription(url);SubscriptionExpiry.schedule(this);urls.clear();urls.addAll(db.urls());selected.clear();selected.addAll(db.selected());db.loadServers(items,groups,owners);render();}
 static class PingGroup {ArrayDeque<String> queue=new ArrayDeque<>();int active,limit;}
 int pingLimit(int count){return PingLimits.forCount(settings,count);}
 void pingAll(){
  if(pingBusy())return;
  if(items.isEmpty()){AppToast.show(this, getString(R.string.main_text_16));return;}
  LinkedHashMap<String,PingGroup> subscriptions=new LinkedHashMap<>();
  for(int i=0;i<items.size();i++)subscriptions.computeIfAbsent(owners.get(i),k->new PingGroup()).queue.add(items.get(i));
  PingService.start(this,new ArrayList<>(subscriptions.values()));
  syncUserPings();
 }
 void pingSubscription(String owner){
  PingGroup group=new PingGroup();for(int i=0;i<items.size();i++)if(owners.get(i).equals(owner))group.queue.add(items.get(i));
  startSingleGroup(group);
 }
 void pingServer(String item){
  if(pingBusy())return;
  int index=items.indexOf(item);if(index<0)return;
  String owner=owners.get(index);int population=0;
  for(String candidate:owners)if(owner.equals(candidate))population++;
  PingService.startOne(this,item,owner,population);syncUserPings();
 }
 boolean pingBusy(){
  if(autoConnect==null&&!autoPing)return false;
  AppToast.show(this, getString(R.string.main_text_104));
  return true;
 }
 void startSingleGroup(PingGroup group){
  if(pingBusy())return;
  if(group.queue.isEmpty()){AppToast.show(this, getString(R.string.main_text_16));return;}
  PingService.enqueueGroup(this,group);
  syncUserPings();
 }
 void runPingBatch(ArrayList<PingGroup> groups,java.util.function.Consumer<Map<String,Long>> complete){
  if(pingBatch!=null)pingBatch.cancel();
  autoPing=complete!=null;
  pingRunning=true;
  pingBatch=new PingBatch(groups,complete);
  PingBatch batch=pingBatch;batch.startChecks();
  for(String item:batch.profiles)updatePingViews(item);
 }
 int maxPing(){
  try{return Math.max(0,Integer.parseInt(settings.getString("max_ping","")));}catch(NumberFormatException e){return 0;}
 }
 void cancelAutoPing(){
  if(autoPing&&pingBatch!=null){
   PingBatch cancelled=pingBatch;cancelled.cancel();
   for(String item:cancelled.profiles)updatePingViews(item);
  }
 }
 final class PingBatch {
  final ArrayList<PingGroup> groups;
  final java.util.function.Consumer<Map<String,Long>> complete;
  final Map<String,Long> results=new LinkedHashMap<>();
  final Set<String> profiles=new HashSet<>();
  final int generation=pingGeneration;
  boolean finishingEarly;

  PingBatch(ArrayList<PingGroup> groups,java.util.function.Consumer<Map<String,Long>> complete){
   this.groups=groups;
   this.complete=complete;
   for(PingGroup group:groups){
    group.limit=pingLimit(group.queue.size());
    for(String item:group.queue){
     profiles.add(item);
     latency.remove(item);
    }
   }
  }
  void cancel(){
   pingBatch=null;
   pingRunning=false;
   autoPing=false;
   for(String item:profiles)if("Проверка".equals(latency.get(item)))latency.remove(item);
  }
  void startChecks(){
   int active=0;
   for(PingGroup group:groups)active+=group.active;
   for(PingGroup group:groups){
    while(active<100&&group.active<group.limit&&!group.queue.isEmpty()){
     String item=group.queue.removeFirst();
     group.active++;
     active++;
     if(generation==pingGeneration){latency.put(item,"Проверка");updatePingViews(item);}
     pingPool.execute(()->measure(group,item));
    }
   }
   if(active==0){
    pingBatch=null;
    pingRunning=false;
    autoPing=false;
    if(complete!=null)complete.accept(results);
   }
  }
  void measure(PingGroup group,String item){
   long delay=-1;
   String result="n/a";
   try{
    delay=XrayPing.measure(item,XrayPing.timeout(settings));
    if(delay>=0)result=delay+getString(R.string.main_extra_16);
   }catch(Exception e){
    if(e.getMessage()!=null&&e.getMessage().contains("Unsupported"))result=getString(R.string.main_text_105);
   }catch(LinkageError e){result=getString(R.string.main_text_106);}
   long measured=delay;
   String value=result;
   runOnUiThread(()->{
    if(pingBatch!=this)return;
    group.active--;
    if(measured>=0)results.put(item,measured);
    if(complete!=null&&measured>=0&&!finishingEarly){
     int limit=maxPing();
     if(limit>0&&measured<=limit){
      finishingEarly=true;
      refreshHandler.postDelayed(()->{
       if(pingBatch!=this)return;
       Map<String,Long> found=new LinkedHashMap<>(results);
       cancel();
       complete.accept(found);
       for(String profile:profiles)updatePingViews(profile);
      },300);
     }
    }
    if(generation==pingGeneration){latency.put(item,value); long stamp=android.os.SystemClock.elapsedRealtime(); pingExpiry.put(item,stamp); refreshHandler.postDelayed(()->{if(java.util.Objects.equals(pingExpiry.get(item),stamp)){pingExpiry.remove(item);if(!userPingProfiles.contains(item)){latency.remove(item);updatePingViews(item);}}},600000L); }
    startChecks();
    updatePingViews(item);
   });
  }
 }
 FrameLayout serverSwipe(LinearLayout row,TextView endpoint,TextView transport,String item,String owner){
  FrameLayout frame=new FrameLayout(this);
  if(VexisUi.isDark(this)){
   frame.setBackgroundColor(VexisUi.BACKGROUND);
   if(settings.getBoolean(AUTO_MODE,false)||!item.equals(settings.getString(MANUAL_PROFILE,null)))
    row.setBackgroundColor(VexisUi.BACKGROUND);
  }
  int width=dp(62);
  View share=iconAction(R.drawable.vexis_ic_share,getString(R.string.main_text_107),()->{translateFrame(row,0,180);shareServer(item,owner);});
  View ping=iconAction(R.drawable.vexis_main_signal,getString(R.string.main_text_34),()->{translateFrame(row,0,180);pingServer(item);});
  ((ImageButton)share).setColorFilter(VexisUi.ACCENT);((ImageButton)ping).setColorFilter(VexisUi.ACCENT);
  ripple(share,VexisUi.PALE,12);ripple(ping,VexisUi.PALE,12);
  FrameLayout left=new FrameLayout(this),right=new FrameLayout(this);
  left.addView(share,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
  right.addView(ping,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
  frame.addView(left,new FrameLayout.LayoutParams(width,-1,Gravity.START));
  frame.addView(right,new FrameLayout.LayoutParams(width,-1,Gravity.END));
  row.setBackgroundColor(VexisUi.isDark(this)?VexisUi.BACKGROUND:VexisUi.SURFACE);
  if(page==0&&!settings.getBoolean(AUTO_MODE,false))
   ripple(row,item.equals(settings.getString(MANUAL_PROFILE,null))?VexisUi.PALE:(VexisUi.isDark(this)?VexisUi.BACKGROUND:VexisUi.SURFACE),9);
  frame.addView(row,new FrameLayout.LayoutParams(-1,-2));
  final float[] start=new float[3];
  final boolean[] dragging={false};
  View.OnTouchListener touch=(view,event)->{
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     start[0]=event.getRawX();start[1]=event.getRawY();start[2]=row.getTranslationX();dragging[0]=false;cancelFrameAnimation(row);return true;
    case MotionEvent.ACTION_MOVE:
     float dx=event.getRawX()-start[0],dy=event.getRawY()-start[1];
     if(!dragging[0]&&Math.abs(dx)>dp(8)&&Math.abs(dx)>Math.abs(dy)){
      dragging[0]=true;frame.getParent().requestDisallowInterceptTouchEvent(true);
     }
     if(dragging[0]){
      float limit=db.isHidden(owner)?0:width;
      float logical=(start[2]+dx)*(isRtl()?-1:1);
      row.setTranslationX(Math.max(-width,Math.min(limit,logical))*(isRtl()?-1:1));
     }
     return true;
    case MotionEvent.ACTION_CANCEL:
     translateFrame(row,0,180);return true;
    case MotionEvent.ACTION_UP:
     if(dragging[0]){
      float position=row.getTranslationX();
      float destination=position>width*.4f?width:position< -width*.4f?-width:0;
      translateFrame(row,destination,180);
     }else if(Math.abs(start[2])>0){translateFrame(row,0,180);}
     else if(Math.abs(event.getRawX()-start[0])<dp(8)&&Math.abs(event.getRawY()-start[1])<dp(8)
       &&page==0&&!settings.getBoolean(AUTO_MODE,false))selectManualServer(item,owner);
     return true;
   }
   return true;
  };
  row.setOnTouchListener(touch);endpoint.setOnTouchListener(touch);transport.setOnTouchListener(touch);
  return frame;
 }
 void selectManualServer(String item,String owner){
  String previous=settings.getString(MANUAL_PROFILE,null);
  if(item.equals(previous)){settings.edit().putString(MANUAL_OWNER,owner).apply();return;}
  settings.edit().putString(MANUAL_PROFILE,item).putString(MANUAL_OWNER,owner).apply();
  updateManualSelection(previous,item);
  if(backgroundRecovering){
   String stopping=activeAttempt;
   advanceSessionGeneration();
   settings.edit().putBoolean("recovery_wanted",false).apply();
   backgroundRecovering=false;
   if(stopping!=null){switchAttempt=stopping;switchProfile=item;switchToAuto=false;}
   startService(new Intent(this,RecoveryService.class).setAction(RecoveryService.STOP));
   if(stopping==null)startSelectedMode();
   updateConnectionHeader();
   return;
  }
  if("Проверка сервера…".equals(connectionMessage)){
   activeAttempt=null;connectionMessage=null;vpnStatus=TunnelService.DISCONNECTED;
   prepareVpn(item,false);return;
  }
  if(manualProbe!=null){
   manualProbe=null;switchAttempt=activeAttempt;switchProfile=item;switchToAuto=false;
   stopProfile(activeAttempt);return;
  }
  if(switchAttempt!=null){
   switchProfile=item;
   switchToAuto=false;
  }else if(TunnelService.isActive(vpnStatus)&&autoConnect==null){
   switchProfile=item;
   switchAttempt=activeAttempt;
   switchToAuto=false;
   stopProfile(activeAttempt);
  }
 }
 void updateManualSelection(String previous,String current){
  for(String item:new String[]{previous,current}){
   List<View> rows=manualViews.get(item);
   if(rows!=null)for(View row:rows)ripple(row,item.equals(current)?VexisUi.PALE:(VexisUi.isDark(this)?VexisUi.BACKGROUND:VexisUi.SURFACE),9);
  }
  if(countries)for(Map.Entry<String,View> entry:countryHeaders.entrySet()){
   boolean active=expanded.contains(entry.getKey())||entry.getKey().equals(country(current));
   ripple(entry.getValue(),active?VexisUi.PALE:Color.TRANSPARENT,9);
  }
 }
 void shareServer(String link,String owner){
  if(db.isHidden(owner))return;
  AlertDialog dialog=dialogShell(getString(R.string.main_text_108));
  LinearLayout content=dialogContent(dialog);
  dialogOption(content,getString(R.string.main_text_109),R.drawable.vexis_ic_copy,()->{
   dialog.dismiss(); if(db.isHidden(owner))return;
   ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   if(clipboard==null){AppToast.show(this, getString(R.string.main_text_110));return;}
   clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.main_text_111),link));
   AppToast.show(this, getString(R.string.main_text_112));
  });
  dialogOption(content,getString(R.string.main_text_113),R.drawable.vexis_main_qr,()->{dialog.dismiss();if(!db.isHidden(owner))showServerQr(link);});
  showVexisDialog(dialog);
 }
 void showServerQr(String link){
  try{
   java.util.EnumMap<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
   hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");
   hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
   hints.put(com.google.zxing.EncodeHintType.MARGIN,4);
   com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,com.google.zxing.BarcodeFormat.QR_CODE,0,0,hints);
   int available=Math.min(getResources().getDisplayMetrics().widthPixels-dp(64),
    getResources().getDisplayMetrics().heightPixels-dp(180));
   int maxSide=Math.min(available,(int)(getResources().getDisplayMetrics().widthPixels*.8f));
   int scale=maxSide/matrix.getWidth();
   if(scale<1)throw new IllegalArgumentException("QR-код слишком большой для экрана");
   int side=matrix.getWidth()*scale;
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(side,side,android.graphics.Bitmap.Config.ARGB_8888);
   int[] pixels=new int[side*side];
   for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=matrix.get(x/scale,y/scale)?Color.BLACK:Color.WHITE;
   bitmap.setPixels(pixels,0,side,0,0,side,side);
   AlertDialog dialog=dialogShell(getString(R.string.main_text_114)); LinearLayout content=dialogContent(dialog);
   ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setBackgroundColor(Color.WHITE);image.setContentDescription(getString(R.string.main_text_114));
   LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,side);imageParams.setMargins(0,dp(9),0,dp(9));
   image.setScaleType(ImageView.ScaleType.FIT_CENTER);content.addView(image,imageParams);
   showVexisDialog(dialog);
  }catch(com.google.zxing.WriterException|IllegalArgumentException e){AppToast.show(this, getString(R.string.main_text_115));}
 }
 String name(String s){return SubscriptionInfo.name(s);} String country(String s){return SubscriptionInfo.country(name(s));}
 void add(){
  AlertDialog dialog=dialogShell(getString(R.string.main_text_10)); LinearLayout content=dialogContent(dialog);
  dialogOption(content,getString(R.string.main_text_116),R.drawable.vexis_ic_link,()->{dialog.dismiss();manual();});
  dialogOption(content,getString(R.string.main_text_117),R.drawable.vexis_main_paste,()->{dialog.dismiss();
   ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   if(c==null||!c.hasPrimaryClip()||c.getPrimaryClip().getItemCount()==0){AppToast.show(this, getString(R.string.main_text_118));return;}
   CharSequence value=c.getPrimaryClip().getItemAt(0).coerceToText(this);addUrl(value==null?"":value.toString());
  });
  dialogOption(content,getString(R.string.main_text_119),R.drawable.vexis_main_qr,()->{dialog.dismiss();startActivityForResult(new Intent(this,QrScannerActivity.class),101);});
  dialogOption(content,getString(R.string.main_text_120),R.drawable.vexis_main_doc,()->{dialog.dismiss();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),103);});
  showVexisDialog(dialog);
 }
 void manual(){
  AlertDialog dialog=dialogShell(getString(R.string.main_text_121)); LinearLayout content=dialogContent(dialog);
  EditText e=new EditText(this);e.setSingleLine(true);e.setHint("https://");
  e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8192)});
  e.setTextColor(VexisUi.TEXT);e.setHintTextColor(VexisUi.MUTED);e.setTextSize(16);
  e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
  e.setPadding(dp(12),dp(12),dp(12),dp(12));
  android.graphics.drawable.GradientDrawable input=VexisUi.rounded(VexisUi.SURFACE,9,this);input.setStroke(dp(1),VexisUi.OUTLINE);e.setBackground(input);
  LinearLayout.LayoutParams field=new LinearLayout.LayoutParams(-1,dp(48));field.setMargins(0,dp(18),0,dp(4));content.addView(e,field);
  TextView error=VexisUi.text(this,getString(R.string.main_text_99),13,VexisUi.DANGER,false);error.setVisibility(View.GONE);content.addView(error);
  dialogButtons(content,dialog,getString(R.string.main_text_80),getString(R.string.main_text_122),()->{
   String u=SubscriptionNetwork.clean(e.getText().toString());
   boolean accepted=u.startsWith("[Interface]")||SubscriptionNetwork.isProfile(u)||SubscriptionNetwork.isHttps(u);
  if(!accepted){input.setStroke(dp(1),VexisUi.DANGER);input.setColor(VexisUi.SURFACE);error.setVisibility(View.VISIBLE);return;}
   dialog.dismiss();addUrl(u);
  });
  showVexisDialog(dialog);
 }

 final java.util.Map<AlertDialog,LinearLayout> dialogContentMap=new java.util.WeakHashMap<>();

 // Dialog-only visual helpers; all actions continue to use the existing import and share paths.
 AlertDialog dialogShell(String title){
  LinearLayout content=new LinearLayout(this);content.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(22),dp(20),dp(18));
  TextView heading=VexisUi.text(this,title,21,VexisUi.INK,true);content.addView(heading);
  AlertDialog dialog=new AlertDialog.Builder(this).setView(content).create();dialogContentMap.put(dialog,content);dialog.setOnDismissListener(d -> dialogContentMap.remove(dialog));return dialog;
 }
 LinearLayout dialogContent(AlertDialog dialog){return dialogContentMap.get(dialog);}
 void showVexisDialog(AlertDialog dialog){
  dialog.show();android.view.Window window=dialog.getWindow();if(window!=null){
   window.setBackgroundDrawable(VexisUi.rounded(VexisUi.SURFACE,22,this));
   VexisUi.shadeModal(this,window);
   window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(432)),-2);
  }
 }
 void dialogOption(LinearLayout content,String label,int emblem,Runnable action){
  if(content.getChildCount()>1)addDivider(content);
  LinearLayout row=new LinearLayout(this);
  row.setGravity(Gravity.CENTER_VERTICAL);
  row.setMinimumHeight(dp(46));
  LinearLayout.LayoutParams symbol=new LinearLayout.LayoutParams(dp(20),dp(20));
  symbol.setMarginEnd(dp(11));
  row.addView(icon(emblem,VexisUi.ACCENT),symbol);
  row.addView(text(label,14,VexisUi.TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
  ripple(row,Color.TRANSPARENT,8);
  row.setOnClickListener(v->action.run());
  content.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }
 LinearLayout dialogActionRow(LinearLayout content){
  LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));
  line.setMargins(0,dp(13),0,dp(10));
  content.addView(VexisUi.divider(this),line);
  LinearLayout row=new LinearLayout(this);
  row.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
  content.addView(row,new LinearLayout.LayoutParams(-1,-2));
  return row;
 }
 void dialogButtons(LinearLayout content,AlertDialog dialog,String cancel,String positive,Runnable action){
  dialogButtons(content,dialog,cancel,positive,VexisUi.SOLID,action);
 }
 void dialogButtons(LinearLayout content,AlertDialog dialog,String cancel,String positive,int kind,Runnable action){
  LinearLayout row=dialogActionRow(content);
  if(cancel!=null){
   TextView close=dialogButton(cancel,VexisUi.QUIET);
   close.setOnClickListener(v->dialog.dismiss());
   row.addView(close);
  }
  TextView accept=dialogButton(positive,kind);
  LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-2,-2);
  if(cancel!=null)space.setMarginStart(dp(7));
  row.addView(accept,space);
  accept.setOnClickListener(v->action.run());
 }
 TextView dialogButton(String title,int kind){
  Button view=button(title,()->{});
  view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  view.setTextColor(kind==VexisUi.SOLID||kind==VexisUi.DESTRUCTIVE?VexisUi.ON_ACCENT:VexisUi.DEEP);
  view.setBackground(VexisUi.buttonBackground(this,kind));
  return view;
 }


 void addUrl(String value){
  String url=SubscriptionNetwork.clean(value);
  if(SubscriptionNetwork.isProfile(url)){
   int marker=url.indexOf("://");
   String link=url.substring(0,marker).toLowerCase(Locale.ROOT)+url.substring(marker);
   if(AwgProfile.isAwg(link))addAwg(link);else addServer(link);
   return;
  }
  if(url.startsWith("[Interface]")||AwgProfile.isAwg(url)){addAwg(url);return;}
  if(!SubscriptionNetwork.isHttps(url)){AppToast.show(this,getString(R.string.main_text_123));return;}
  if(urls.contains(url)&&owners.contains(url)){AppToast.show(this,getString(R.string.main_text_101));return;}
  if(!SubscriptionNetwork.refreshAllowed(this)){AppToast.show(this,getString(R.string.main_text_124));return;}
  if(!newSubscriptions.add(url))return;
  subscriptionPool.execute(()->{
   try{
    SubscriptionNetwork.Payload payload=SubscriptionNetwork.fetch(url);
    try(LocalDatabase data=new LocalDatabase(getApplicationContext())){
     data.importSubscription(url,payload);
    }
    SubscriptionRefreshJob.recordSuccess(this,url);
    runOnUiThread(()->{
     newSubscriptions.remove(url);
     if(isDestroyed())return;
     subscriptionExpanded.add(url);
     requestSubscriptionsReload();
     AppToast.show(this,getString(R.string.main_extra_18)+payload.title+getString(R.string.main_extra_19)+payload.links.size());
    });
   }catch(Exception failure){
    runOnUiThread(()->{
     newSubscriptions.remove(url);
     if(!isDestroyed())AppToast.show(this,getString(R.string.main_text_125));
    });
   }
  });
 }
 void addServer(String link){
  try{
   String scheme=android.net.Uri.parse(link).getScheme().toLowerCase(Locale.ROOT);
   if("vmess".equals(scheme)){
    org.json.JSONObject config=new org.json.JSONObject(XrayPing.decode(link.substring(8).split("#",2)[0]));
    if(config.optString("add").isEmpty()||config.optString("id").isEmpty()||config.optInt("port")<1||config.optInt("port")>65535)throw new Exception();
   }else if("ss".equals(scheme)){
    XrayPing.outbound(link);
   }else{
    android.net.Uri parsed=android.net.Uri.parse(link);
    if(parsed.getHost()==null||parsed.getHost().isEmpty()||parsed.getPort()<1||parsed.getPort()>65535||(("vless".equals(scheme)||"trojan".equals(scheme))&&(parsed.getUserInfo()==null||parsed.getUserInfo().isEmpty())))throw new Exception();
   }
   if(items.contains(link)){AppToast.show(this, getString(R.string.main_text_126));return;}
   String owner="local:server:"+UUID.randomUUID();String title=name(link);
   db.putSubscription(owner,title);db.updateSubscription(owner,title,new ArrayList<>(Collections.singletonList(link)));
   urls.add(owner);db.loadServers(items,groups,owners);subscriptionExpanded.add(owner);render();
   AppToast.show(this, getString(R.string.main_text_127));
  }catch(Exception|LinkageError e){AppToast.show(this, getString(R.string.main_text_128));}
 }
 void addAwg(String value){
  try{
   String profile=AwgProfile.encode(value);
   if(items.contains(profile)){AppToast.show(this, getString(R.string.main_text_129));return;}
   String owner="local:awg:"+UUID.randomUUID();
   String title=name(profile);
   db.putSubscription(owner,title);
   db.updateSubscription(owner,title,new ArrayList<>(Collections.singletonList(profile)));
   urls.add(owner);db.loadServers(items,groups,owners);subscriptionExpanded.add(owner);render();
  }catch(Exception|LinkageError e){AppToast.show(this, getString(R.string.main_text_130));}
 }
 boolean routingOpening;
 void openRouting(boolean perApp){
  if(routingOpening)return;
  routingOpening=true;
  openSettingsScreen(new Intent(this,RoutingActivity.class).putExtra("per_app",perApp));
 }
 @Override protected void onStart(){
  super.onStart();
  if(!getIntent().getBooleanExtra("subscription_list",false)&&!getIntent().getBooleanExtra("engine_settings",false)){SubscriptionExpiry.schedule(this);SubscriptionExpiry.showExpiredOnEntry(this);}
  if(activeAttempt!=null){updateConnectionHeader();return;}
  ActivityManager manager=getSystemService(ActivityManager.class);
  for(ActivityManager.RunningServiceInfo service:manager.getRunningServices(Integer.MAX_VALUE)){
   String serviceName=service.service.getClassName();
   if(RecoveryService.class.getName().equals(serviceName)){
    backgroundRecovering=true;
    startService(new Intent(this,RecoveryService.class).setAction(RecoveryService.QUERY));
    continue;
   }
   boolean isAwg=AwgTunnelService.class.getName().equals(serviceName);
   if(isAwg||TunnelService.class.getName().equals(serviceName)){
    startService(new Intent(this,isAwg?AwgTunnelService.class:TunnelService.class).setAction(TunnelService.QUERY));
   }
  }
  updateConnectionHeader();
 }
 void registerVpnReceiver(){
  IntentFilter filter=new IntentFilter(TunnelService.STATE);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(vpnState,filter,TunnelService.STATE_PERMISSION,null,Context.RECEIVER_NOT_EXPORTED);
  else registerReceiver(vpnState,filter,TunnelService.STATE_PERMISSION,null);
 }
 @Override protected void onResume(){
  super.onResume();
  routingOpening=false;
  if(page==2 && body!=null){
   urls.clear();
   urls.addAll(db.urls());
   selected.clear();
   selected.addAll(db.selected());
   db.loadServers(items,groups,owners);
   render();
  }
  int resumeFrom=settings.getInt(EngineUpdates.RETRY_AFTER_UPDATE,-1);
  if(resumeFrom>=0&&resumeFrom<BuildConfig.VERSION_CODE){
   if(EngineUpdates.hasManualRetry(this))EngineUpdates.resumeManual(this);
   else if(!TunnelService.isActive(vpnStatus)&&!pendingAuto)prepareVpn(null,true);
  }
 }
 String connectionModeTitle(){return settings.getBoolean(AUTO_MODE,false)?getString(R.string.main_text_131):getString(R.string.main_text_132);}
 void chooseConnectionMode(){
  showChoices(getString(R.string.main_text_133),new String[]{getString(R.string.main_text_134),getString(R.string.main_text_135)},
   settings.getBoolean(AUTO_MODE,false)?1:0,null,choice->changeConnectionMode(choice==1));
 }
 void updateConnectionHeader(){
  if(connectionStatus==null)return;
  String status=autoConnect!=null?autoConnect.status():connectionMessage;
  boolean connected=TunnelService.CONNECTED.equals(vpnStatus)&&countryDone&&delayDone&&switchAttempt==null&&!backgroundRecovering&&!waitingInternet;
  String progress=initialConnection?getString(R.string.main_text_44): getString(R.string.main_text_136);
  String stage=null;
  String title;
  if(connected)title=getString(R.string.connected);
  else if(waitingInternet)title="Ожидание интернета";
  else if(switchAttempt!=null)title="Подключение…";
  else if(backgroundRecovering){
   if("Ожидание интернета…".equals(recoveryStage))title="Ожидание интернета";
   else {title=progress+"…";stage=recoveryStage;}
  }else if(TunnelService.CONNECTED.equals(vpnStatus)){
   title=progress+"…";stage=getString(R.string.main_text_137);
  }else if(autoConnect!=null){
   title="Подключение…";stage=status;
  }else if(TunnelService.CONNECTING.equals(vpnStatus)||manualProbe!=null){
   title="Подключение…";stage=manualProbe==null?(recoveryStage==null?status:recoveryStage):"Проверка · "+profileTitle(manualProbe);
  }else{
   title=status==null?vpnStatus:status;
  }
  if("Ожидание интернета".equals(title)&&(backgroundRecovering||settings.getBoolean(AUTO_MODE,false)))
   stage=getString(R.string.main_text_138);
  else if(title!=null&&title.startsWith("Не удалось")&&stage==null)
   stage=getString(R.string.main_text_139);
  String visibleTitle=ServiceText.translate(this,title);
  if(!android.text.TextUtils.equals(connectionStatus.getText(),visibleTitle))connectionStatus.setText(visibleTitle);
  String stageText=stage==null?"":stage;
  String visibleStage=ServiceText.translate(this,stageText);
  boolean stageChanged=!android.text.TextUtils.equals(connectionStage.getText(),visibleStage);
  connectionStage.setVisibility(!connected&&stage!=null?View.VISIBLE:View.GONE);
  if(stageChanged)connectionStage.setText(visibleStage);
  connectionNameRow.setVisibility(connected?View.VISIBLE:View.GONE);
  boolean waiting=waitingInternet||(backgroundRecovering&&"Ожидание интернета…".equals(recoveryStage));
  long sessionStarted=settings.getBoolean(AUTO_MODE,false)?settings.getLong("auto_session_started",0):connectedAt;
  boolean showWaitingTime=waiting&&sessionStarted>0&&sessionStarted<=android.os.SystemClock.elapsedRealtime();
  if(showWaitingTime)connectedAt=sessionStarted;
  connectionTime.setVisibility(connected||showWaitingTime?View.VISIBLE:View.GONE);
  timeRow.setVisibility(connected?View.VISIBLE:View.INVISIBLE);
  if(connected){
   if(settings.getBoolean(AUTO_MODE,false)){
    long started=settings.getLong("auto_session_started",0);
    if(started<=0||started>android.os.SystemClock.elapsedRealtime()){
     started=android.os.SystemClock.elapsedRealtime();
     settings.edit().putLong("auto_session_started",started).apply();
    }
    connectedAt=started;
   }
   String code=countryCode==null?"":countryCode;
   if(!code.equals(displayedCountry)){
    displayedCountry=code;
    if(connectionFlag!=null)countryRow.removeView(connectionFlag);
    connectionFlag=code.isEmpty()?null:flagView(code);
    if(connectionFlag!=null)countryRow.addView(connectionFlag,0);
    connectionCountry.setText(code.isEmpty()?getString(R.string.main_text_140):SubscriptionInfo.countryLabel(this,code));
   }
   String server=connectedProfile==null?getString(R.string.main_text_111):profileTitle(connectedProfile);
   connectionDetails.setText(server);
   connectionPing.setText(connectionDelay>=0?connectionDelay+getString(R.string.main_extra_16):"n/a");
   updateConnectionTime();
  }
  boolean canDisconnect=switchAttempt!=null||autoConnect!=null||backgroundRecovering||TunnelService.isActive(vpnStatus);
  if(outlinedConnectionButton==null||outlinedConnectionButton!=canDisconnect){
   outlinedConnectionButton=canDisconnect;
   connectionButton.setText(getString(canDisconnect?R.string.disconnect:R.string.connect));
   connectionButton.setTextColor(canDisconnect?VexisUi.DEEP:VexisUi.ON_ACCENT);
   connectionButton.setBackground(VexisUi.buttonBackground(this,canDisconnect?VexisUi.OUTLINE_BUTTON:VexisUi.SOLID));
  }
  VexisCrystalView.State next=visualConnectionState(connected);
  connectionCrystal.setState(next);
  connectedDot.setStyle(next==VexisCrystalView.State.ERROR?VexisUi.DANGER:
   next==VexisCrystalView.State.IDLE||next==VexisCrystalView.State.WAITING?VexisUi.MUTED:VexisUi.ACCENT,connected);
  connectionCrystal.setClickable(connected);
  connectionCrystal.setImportantForAccessibility(connected?View.IMPORTANT_FOR_ACCESSIBILITY_YES:
   View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  if(displayedConnectionState!=next){
   if(displayedConnectionState!=null){
    VexisMotion.enter(connectionStatus,dp(4));
    if(connected){
     VexisMotion.enter(connectionNameRow,dp(4));
     VexisMotion.enter(connectionTime,0);
     VexisMotion.enter(timeRow,0);
    }
   }
   displayedConnectionState=next;
  }
  if(stageChanged&&connectionStage.getVisibility()==View.VISIBLE)VexisMotion.enter(connectionStage,dp(3));
 }
 VexisCrystalView.State visualConnectionState(boolean connected){
  if(connected)return VexisCrystalView.State.CONNECTED;
  if(waitingInternet||(backgroundRecovering&&"Ожидание интернета…".equals(recoveryStage)))
   return VexisCrystalView.State.WAITING;
  if(backgroundRecovering&&!initialConnection)return VexisCrystalView.State.RECOVERING;
  if(switchAttempt!=null||backgroundRecovering||autoConnect!=null||manualProbe!=null||
     TunnelService.CONNECTING.equals(vpnStatus)||TunnelService.CONNECTED.equals(vpnStatus))
   return VexisCrystalView.State.CONNECTING;
  String stateMessage=autoConnect!=null?autoConnect.status():connectionMessage;
  if((stateMessage!=null&&stateMessage.startsWith("Не удалось"))||(vpnStatus!=null&&vpnStatus.startsWith("Не удалось")))return VexisCrystalView.State.ERROR;
  return VexisCrystalView.State.IDLE;
 }
 void updateConnectionTime(){
  if(connectionTime==null||connectedAt<=0)return;
  long seconds=Math.max(0,(android.os.SystemClock.elapsedRealtime()-connectedAt)/1000);
  connectionTime.setText(String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60));
 }
 void showConnectionDetails(){
  if(!TunnelService.CONNECTED.equals(vpnStatus)||connectedProfile==null||
    (detailsDialog!=null&&detailsDialog.isShowing()))return;
  AlertDialog dialog=dialogShell(profileTitle(connectedProfile));
  LinearLayout content=dialogContent(dialog);
  detailsDialog=dialog;
  detailsTitle=(TextView)content.getChildAt(0);
  detailsIp=connectionDetail(content,getString(R.string.main_text_141),R.drawable.vexis_ic_servers);
  ((View)detailsIp.getParent()).setOnClickListener(v->{
   String address=exitIp==null?"":exitIp.trim();
   if(address.isEmpty())return;
   ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   if(clipboard==null){AppToast.show(this,getString(R.string.main_text_110));return;}
   clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.main_text_141),address));
   AppToast.show(this,getString(R.string.main_text_142));
  });
  detailsProtocol=connectionDetail(content,getString(R.string.main_text_143),R.drawable.vexis_main_layers);
  detailsTimeZone=connectionDetail(content,getString(R.string.main_text_144),R.drawable.vexis_main_clock);
  detailsReconnect=null;
  if(settings.getBoolean(AUTO_MODE,false)&&settings.getBoolean("auto_failover",true))
   detailsReconnect=connectionDetail(content,getString(R.string.main_text_145),R.drawable.vexis_ic_refresh);
  dialog.setOnDismissListener(dismissed->{
   dialogContentMap.remove(dialog);
   if(detailsDialog==dialog)detailsDialog=null;
  });
  showVexisDialog(dialog);
  updateDetailsDialog();
 }
 TextView connectionDetail(LinearLayout content,String name,int emblem){
  if(content.getChildCount()>1)addDivider(content);
  LinearLayout row=new LinearLayout(this);
  row.setMinimumHeight(dp(45));
  row.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(dp(18),dp(18));
  space.setMarginEnd(dp(10));
  row.addView(icon(emblem,VexisUi.ACCENT),space);
  row.addView(text(name,14,VexisUi.TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
  TextView value=text("",12,VexisUi.MUTED,false);
  value.setGravity(Gravity.END);
  value.setMaxWidth(dp(134));
  row.addView(value);
  content.addView(row,new LinearLayout.LayoutParams(-1,-2));
  return value;
 }
 void updateDetailsDialog(){
  if(detailsDialog==null||!detailsDialog.isShowing())return;
  if(!TunnelService.CONNECTED.equals(vpnStatus)||connectedProfile==null){
   detailsDialog.dismiss();
   return;
  }
  detailsTitle.setText(profileTitle(connectedProfile));
  detailsIp.setText(exitIp==null||exitIp.isEmpty()?"n/a":exitIp);
  detailsProtocol.setText(exitIp==null||exitIp.isEmpty()?"n/a":exitIp.contains(":")?"IPv6":"IPv4");
  detailsTimeZone.setText(exitTimeZone==null||exitTimeZone.isEmpty()?"n/a":exitTimeZone);
  if(detailsReconnect!=null)detailsReconnect.setText(String.valueOf(settings.getInt("reconnect_count",0)));
 }


 void changeConnectionMode(boolean automatic){
  if(settings.getBoolean(AUTO_MODE,false)==automatic)return;
  settings.edit().putBoolean(AUTO_MODE,automatic).apply();
  updateModeControl(true);
  refreshHandler.postDelayed(()->finishConnectionModeChange(automatic),32);
 }
 void finishConnectionModeChange(boolean automatic){
  if(settings.getBoolean(AUTO_MODE,false)!=automatic)return;
  advanceSessionGeneration();
  if(backgroundRecovering){
   String stopping=activeAttempt;
   settings.edit().putBoolean("recovery_wanted",false).apply();
   backgroundRecovering=false;
   if(stopping!=null){
    switchAttempt=stopping;
    switchToAuto=automatic;
    switchProfile=null;
   }
   startService(new Intent(this,RecoveryService.class).setAction(RecoveryService.STOP));
   if(stopping==null)startSelectedMode();
   updateConnectionHeader();
   return;
  }
  if("Проверка сервера…".equals(connectionMessage)){
   activeAttempt=null;
   connectionMessage=null;
   vpnStatus=TunnelService.DISCONNECTED;
   startSelectedMode();
   updateConnectionHeader();
   return;
  }
  if(switchAttempt!=null){
   switchToAuto=automatic;
   switchProfile=null;
  }else if(autoConnect!=null){
   String token=activeAttempt;
   boolean active=TunnelService.isActive(vpnStatus);
   if(active){switchAttempt=token;switchToAuto=automatic;switchProfile=null;}
   autoConnect.cancel();
   if(!active)startSelectedMode();
  }else if(TunnelService.isActive(vpnStatus)){
   switchAttempt=activeAttempt;
   switchToAuto=automatic;
   switchProfile=null;
   stopProfile(activeAttempt);
  }
  updateConnectionHeader();
 }
 void startSelectedMode(){
  if(settings.getBoolean(AUTO_MODE,false)){
   if(selectedSubscriptions().isEmpty()){
    connectionMessage="Выберите серверы";
    updateConnectionHeader();
    return;
   }
   prepareVpn(null,true);
  }else{
   String profile=settings.getString(MANUAL_PROFILE,null);
   if(profile==null||!items.contains(profile)){
    connectionMessage="Выберите сервер в списке";
    showMainPage(0);
    updateConnectionHeader();
    return;
   }
   prepareVpn(profile,false);
  }
 }
 void toggleVpn(){
  advanceSessionGeneration();
  if(backgroundRecovering||manualProbe!=null||switchAttempt!=null||autoConnect!=null
    ||TunnelService.isActive(vpnStatus)||pendingAuto||pendingProfile!=null){
   settings.edit().putBoolean("recovery_wanted",false).putBoolean("vpn_session_active",false).apply();
   if(autoConnect!=null)autoConnect.cancel();
   pendingAuto=false;pendingProfile=null;manualProbe=null;switchAttempt=null;switchProfile=null;
   ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
   if(manager!=null)for(ActivityManager.RunningServiceInfo service:manager.getRunningServices(Integer.MAX_VALUE)){
    String name=service.service.getClassName();
    Class<?> type=RecoveryService.class.getName().equals(name)?RecoveryService.class:
     TunnelService.class.getName().equals(name)?TunnelService.class:
     AwgTunnelService.class.getName().equals(name)?AwgTunnelService.class:null;
    if(type!=null)try{startService(new Intent(this,type).setAction(type==RecoveryService.class?RecoveryService.STOP:TunnelService.STOP));}
    catch(RuntimeException ignored){}
   }
   backgroundRecovering=false;waitingInternet=false;activeAttempt=null;connectedProfile=null;
   connectionMessage=null;vpnStatus=TunnelService.DISCONNECTED;
   updateConnectionHeader();updateDetailsDialog();return;
  }
  settings.edit().remove(EngineUpdates.RETRY_AFTER_UPDATE).apply();
  settings.edit().putInt("reconnect_count",0).putBoolean("vpn_session_active",true)
   .putLong("auto_session_started",0).apply();
  startSelectedMode();
 }
 void advanceSessionGeneration(){
  changingSessionGeneration=true;
  try{settings.edit().putLong(RecoveryService.SESSION_GENERATION,settings.getLong(RecoveryService.SESSION_GENERATION,0)+1).commit();}
  finally{changingSessionGeneration=false;}
 }
 String profileTitle(String profile){
  int position=items.indexOf(profile);
  return SubscriptionInfo.withoutFlagEmoji(name(profile));
 }
 void prepareVpn(String profile,boolean automatic){
  pendingProfile=profile;
  pendingAuto=automatic;
  Intent permission=android.net.VpnService.prepare(this);
  if(permission!=null)startActivityForResult(permission,102);
  else launchVpn();
 }
 void launchVpn(){
  if(pendingAuto){
   pendingAuto=false;
   settings.edit().remove(EngineUpdates.RETRY_AFTER_UPDATE).putLong("auto_session_started",0).apply();
   backgroundRecovering=true;
   initialConnection=true;recoveryStage=null;
   vpnStatus=TunnelService.CONNECTING;
   updateConnectionHeader();
   Intent start=new Intent(this,RecoveryService.class).setAction(RecoveryService.INITIAL)
    .putExtra(RecoveryService.SESSION_GENERATION,settings.getLong(RecoveryService.SESSION_GENERATION,0));
   if(Build.VERSION.SDK_INT>=26)startForegroundService(start);else startService(start);
  }else if(pendingProfile!=null){
   initialConnection=true;
   String profile=pendingProfile;
   pendingProfile=null;
   startProfile(profile,true,false);
  }
 }
 String startProfile(String profile,boolean verify,boolean probe){
  String token=UUID.randomUUID().toString();
  if(!probe)connectedProfile=profile;
  awgActive=AwgProfile.isAwg(profile);
  activeAttempt=token;
  vpnStatus=TunnelService.CONNECTING;
  connectionMessage=null;
  updateConnectionHeader();
  Intent intent=new Intent(this,awgActive?AwgTunnelService.class:TunnelService.class).setAction(TunnelService.START)
   .putExtra(TunnelService.PROFILE,profile).putExtra(TunnelService.ATTEMPT,token)
   .putExtra(TunnelService.VERIFY,verify).putExtra(TunnelService.PROBE,probe)
   .putExtra(RecoveryService.SESSION_GENERATION,settings.getLong(RecoveryService.SESSION_GENERATION,0));
  try{
   if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);
   else startService(intent);
   return token;
  }catch(RuntimeException e){
   activeAttempt=null;
   vpnStatus="Не удалось запустить VPN";
   updateConnectionHeader();
   return null;
  }
 }
 void stopProfile(String token){
  try{
   startService(new Intent(this,awgActive?AwgTunnelService.class:TunnelService.class)
    .setAction(TunnelService.STOP).putExtra(TunnelService.ATTEMPT,token));
  }catch(IllegalStateException ignored){}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==103&&result==RESULT_OK&&data!=null){try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in!=null)addAwg(AwgProfile.read(in));}catch(Exception e){AppToast.show(this, getString(R.string.main_text_146));}return;}if(request==102){if(result==RESULT_OK)launchVpn();else{pendingProfile=null;pendingAuto=false;settings.edit().remove(EngineUpdates.RETRY_AFTER_UPDATE).apply();}return;}if(request==101&&result==RESULT_OK&&data!=null){String value=data.getStringExtra("SCAN_RESULT");if(value!=null)addUrl(value);}}
 Set<String> selectedSubscriptions(){
  Set<String> result=new LinkedHashSet<>();
  for(int i=0;i<items.size();i++)if(selected.contains(items.get(i)))result.add(owners.get(i));
  return result;
 }
 void schedule(){SubscriptionRefreshJob.schedule(this,0);}
 @Override protected void onDestroy(){
  settings.unregisterOnSharedPreferenceChangeListener(modeListener);
  if(autoConnect!=null)autoConnect.cancel();
  refreshing.clear();
  if(pingBatch!=null)pingBatch.cancel();
  unregisterReceiver(vpnState);
  unregisterReceiver(pingState);
  unregisterReceiver(subscriptionState);
  refreshHandler.removeCallbacksAndMessages(null);
  pingPool.shutdownNow();
  subscriptionPool.shutdownNow();
  groupingPool.shutdownNow();
  databasePool.shutdownNow();
  super.onDestroy();
 }
 void refresh(){for(String url:new ArrayList<>(urls))refreshSubscription(url);}
 void invalidateRefresh(String url){
  java.util.concurrent.CompletableFuture<Void> pending=refreshing.remove(url);
  if(pending!=null)refreshHandler.post(()->pending.complete(null));
 }
 java.util.concurrent.CompletableFuture<Void> refreshSubscription(String url){
  if(url.startsWith("local:")||!urls.contains(url))return java.util.concurrent.CompletableFuture.completedFuture(null);
  if(!SubscriptionNetwork.refreshAllowed(this))return java.util.concurrent.CompletableFuture.completedFuture(null);
  java.util.concurrent.CompletableFuture<Void> pending=refreshing.get(url);
  if(pending!=null)return pending;
  java.util.concurrent.CompletableFuture<Void> complete=new java.util.concurrent.CompletableFuture<>();
  refreshing.put(url,complete);
  updateRefreshViews(url);
  String journalTitle=db.title(url);
  String expectedUpdate=db.subscriptionInfo(url)[2];
  VpnJournal.add(this,"Обновление подписки: "+journalTitle,true);
  subscriptionPool.execute(()->{
   try{
    if(!SubscriptionNetwork.refreshAllowed(this)){runOnUiThread(()->finishRefresh(url,complete,()->{}));return;}
     SubscriptionNetwork.Payload payload=SubscriptionNetwork.fetch(url);
     ArrayList<String> links=new ArrayList<>(payload.links);
     if(complete.isDone())return;
     String manual=settings.getString(MANUAL_PROFILE,null), replacement=null;
     String updatedTitle;
     try(LocalDatabase dataStore=new LocalDatabase(getApplicationContext())){
      ArrayList<String> oldItems=new ArrayList<>(),oldGroups=new ArrayList<>(),oldOwners=new ArrayList<>();
      dataStore.loadServers(oldItems,oldGroups,oldOwners);
      int oldIndex=oldItems.indexOf(manual);
      boolean manualInSubscription=oldIndex>=0&&oldOwners.get(oldIndex).equals(url);
      boolean applied=dataStore.updateSubscriptionInfo(url,payload.title,links,payload.userinfo,payload.announcement,expectedUpdate==null?"0":expectedUpdate);
      if(!applied){runOnUiThread(()->finishRefresh(url,complete,this::requestSubscriptionsReload));return;}
      SubscriptionRefreshJob.recordSuccess(this,url);
      updatedTitle=dataStore.title(url);
      if(applied&&manualInSubscription&&!links.contains(manual)){
       int matches=0;
       for(String link:links)if(name(link).equals(name(manual))){replacement=link;matches++;}
       if(matches!=1)replacement=null;
      }
     }
     VpnJournal.add(this,"Подписка обновлена: "+updatedTitle+" · серверов: "+links.size(),false);
     final String selectedReplacement=replacement;
     runOnUiThread(()->finishRefresh(url,complete,()->{
      if(selectedReplacement!=null&&java.util.Objects.equals(manual,settings.getString(MANUAL_PROFILE,null)))
       settings.edit().putString(MANUAL_PROFILE,selectedReplacement).apply();
      AppToast.show(this,getString(R.string.main_extra_18)+updatedTitle+getString(R.string.main_extra_20));
      requestSubscriptionsReload();
     }));
   }catch(Exception failure){if(!complete.isDone())SubscriptionRefreshJob.recordFailure(this,url);VpnJournal.add(this,"Не удалось обновить подписку: "+journalTitle+" · "+failure.getClass().getSimpleName(),false);runOnUiThread(()->finishRefresh(url,complete,null));}
  });
  return complete;
 }
 void finishRefresh(String url,java.util.concurrent.CompletableFuture<Void> pending,Runnable apply){
  if(refreshing.get(url)!=pending)return;
  refreshing.remove(url);
  updateRefreshViews(url);
  try{
   if(isDestroyed())return;
   if(apply==null){
    String title=db.title(url);
    AppToast.show(this, getString(R.string.main_extra_21)+title+getString(R.string.main_extra_22));
   }else{apply.run();}
  }finally{
   pending.complete(null);
  }
 }
}

