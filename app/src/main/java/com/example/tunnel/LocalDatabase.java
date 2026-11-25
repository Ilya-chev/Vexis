package com.example.tunnel;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class LocalDatabase extends SQLiteOpenHelper {
 private final Context appContext;
 public LocalDatabase(Context c){super(c,"tunnel.db",null,4);appContext=c.getApplicationContext();}
 @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");db.execSQL("CREATE TABLE subscriptions (url TEXT PRIMARY KEY, title TEXT NOT NULL, userinfo TEXT, announcement TEXT, updated_at INTEGER, custom_title TEXT, hidden INTEGER NOT NULL DEFAULT 0)");db.execSQL("CREATE TABLE servers (subscription_url TEXT NOT NULL, position INTEGER NOT NULL, link TEXT NOT NULL, PRIMARY KEY(subscription_url, position), FOREIGN KEY(subscription_url) REFERENCES subscriptions(url) ON DELETE CASCADE)");db.execSQL("CREATE TABLE selected (link TEXT PRIMARY KEY)");db.execSQL("CREATE TABLE refresh_attempts (url_hash TEXT PRIMARY KEY, attempted_at INTEGER NOT NULL)");}
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion<2){db.execSQL("ALTER TABLE subscriptions ADD COLUMN userinfo TEXT");db.execSQL("ALTER TABLE subscriptions ADD COLUMN announcement TEXT");db.execSQL("ALTER TABLE subscriptions ADD COLUMN updated_at INTEGER");}if(oldVersion<3){db.execSQL("ALTER TABLE subscriptions ADD COLUMN custom_title TEXT");db.execSQL("ALTER TABLE subscriptions ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0");}if(oldVersion<4)db.execSQL("CREATE TABLE refresh_attempts (url_hash TEXT PRIMARY KEY, attempted_at INTEGER NOT NULL)");}
 public long refreshAttempt(String hash){try(Cursor c=getReadableDatabase().rawQuery("SELECT attempted_at FROM refresh_attempts WHERE url_hash=?",new String[]{hash})){return c.moveToFirst()?c.getLong(0):0;}}
 public void setRefreshAttempt(String hash,long time){ContentValues v=new ContentValues();v.put("url_hash",hash);v.put("attempted_at",time);getWritableDatabase().insertWithOnConflict("refresh_attempts",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public void clearRefreshAttempt(String hash){getWritableDatabase().delete("refresh_attempts","url_hash=?",new String[]{hash});}
 public void migrate(SharedPreferences p){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(setting("migrated",null)==null){for(String url:p.getStringSet("urls",Collections.emptySet()))putSubscription(url,p.getString("title:"+url,SubscriptionInfo.title(null,url)));setSelected(p.getStringSet("selected",Collections.emptySet()));putSetting("interval",String.valueOf(p.getInt("interval",60)));putSetting("migrated","1");}db.setTransactionSuccessful();}finally{db.endTransaction();}}
 public String setting(String key,String fallback){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
 public int interval(){try{return Math.max(1,Integer.parseInt(setting("interval","60")));}catch(Exception e){return 60;}}
 public void clearLegacyInterval(){getWritableDatabase().delete("settings","key=?",new String[]{"interval"});}
 public void putSetting(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public void putSubscription(String url,String title){ContentValues v=new ContentValues();v.put("url",url);v.put("title",title);getWritableDatabase().insertWithOnConflict("subscriptions",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public void importSubscription(String url,SubscriptionNetwork.Payload payload){
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   putSubscription(url,payload.title);
   updateSubscriptionInfo(url,payload.title,new ArrayList<>(payload.links),payload.userinfo,payload.announcement);
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 public void updateSubscription(String url,String title,ArrayList<String> links){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("title",title);if(db.update("subscriptions",v,"url=?",new String[]{url})==0)return;db.delete("servers","subscription_url=?",new String[]{url});for(int i=0;i<links.size();i++){ContentValues row=new ContentValues();row.put("subscription_url",url);row.put("position",i);row.put("link",links.get(i));db.insertOrThrow("servers",null,row);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
 public void updateSubscriptionInfo(String url,String title,ArrayList<String> links,String userinfo,String announcement){
  updateSubscriptionInfo(url,title,links,userinfo,announcement,null);
 }
 public boolean updateSubscriptionInfo(String url,String title,ArrayList<String> links,String userinfo,String announcement,String expectedUpdatedAt){
  SQLiteDatabase db=getWritableDatabase();
  db.beginTransaction();
  try{
   ContentValues values=new ContentValues();
   values.put("title",title);
   values.put("userinfo",userinfo);
   values.put("announcement",announcement);
   values.put("updated_at",System.currentTimeMillis());
   String where = expectedUpdatedAt == null ? "url=?" : "url=? AND COALESCE(updated_at,0)=CAST(? AS INTEGER)";
   String[] args = expectedUpdatedAt == null ? new String[]{url} : new String[]{url,expectedUpdatedAt};
   if(db.update("subscriptions",values,where,args)==0)return false;
   Set<String> previouslySelected=new HashSet<>();
   String manual=appContext.getSharedPreferences("app_settings",Context.MODE_PRIVATE|Context.MODE_MULTI_PROCESS).getString(MainActivity.MANUAL_PROFILE,null);
   boolean preserveManual=false;
   if(manual!=null&&!links.contains(manual))try(Cursor c=db.rawQuery("SELECT 1 FROM servers WHERE subscription_url=? AND link=?",new String[]{url,manual})){preserveManual=c.moveToFirst();}
   try(Cursor c=db.rawQuery("SELECT link FROM servers WHERE subscription_url=? AND link IN (SELECT link FROM selected)",new String[]{url})){
    while(c.moveToNext())previouslySelected.add(c.getString(0));
   }
   db.delete("servers","subscription_url=?",new String[]{url});
   for(int i=0;i<links.size();i++){
    String link=links.get(i);
    ContentValues row=new ContentValues();
    row.put("subscription_url",url);
    row.put("position",i);
    row.put("link",link);
    db.insertOrThrow("servers",null,row);
   }
   int position=links.size();
   for(String old:previouslySelected){
    if(links.contains(old))continue;
    String replacement=null;
    int matches=0;
    for(String link:links){
     if(SubscriptionInfo.name(link).equals(SubscriptionInfo.name(old))){
      replacement=link;
      matches++;
     }
    }
    int oldMatches=0;
    for(String previous:previouslySelected)
     if(SubscriptionInfo.name(previous).equals(SubscriptionInfo.name(old)))oldMatches++;
    if(matches==1&&oldMatches==1){
     ContentValues selectedRow=new ContentValues();
     selectedRow.put("link",replacement);
     if(db.compileStatement("SELECT COUNT(*) FROM servers WHERE link="+android.database.DatabaseUtils.sqlEscapeString(old)).simpleQueryForLong()==0){
      if(db.updateWithOnConflict("selected",selectedRow,"link=?",new String[]{old},SQLiteDatabase.CONFLICT_IGNORE)==0)db.delete("selected","link=?",new String[]{old});
     }else db.insertWithOnConflict("selected",null,selectedRow,SQLiteDatabase.CONFLICT_IGNORE);
     continue;
    }
    ContentValues row=new ContentValues();
    row.put("subscription_url",url);
    row.put("position",position++);
    row.put("link",old);
    db.insertOrThrow("servers",null,row);
   }
   if(preserveManual){
    try(Cursor c=db.rawQuery("SELECT 1 FROM servers WHERE subscription_url=? AND link=?",new String[]{url,manual})){
     if(!c.moveToFirst()){ContentValues row=new ContentValues();row.put("subscription_url",url);row.put("position",position);row.put("link",manual);db.insertOrThrow("servers",null,row);}
    }
   }
   db.setTransactionSuccessful();
   return true;
  }finally{db.endTransaction();}
 }
 private static String profileKey(String link){
  int fragment=link.indexOf('#');
  return fragment<0?link:link.substring(0,fragment);
 }
 public String[] subscriptionInfo(String url){try(Cursor c=getReadableDatabase().rawQuery("SELECT userinfo,announcement,updated_at FROM subscriptions WHERE url=?",new String[]{url})){return c.moveToFirst()?new String[]{c.getString(0),c.getString(1),c.isNull(2)?null:c.getString(2)}:new String[3];}}
 public boolean isHidden(String url){try(Cursor c=getReadableDatabase().rawQuery("SELECT hidden FROM subscriptions WHERE url=?",new String[]{url})){return c.moveToFirst()&&c.getInt(0)!=0;}}
 public void editSubscription(String oldUrl,String newUrl,String title,boolean hide){
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
   if(!oldUrl.equals(newUrl)){
    if(db.compileStatement("SELECT COUNT(*) FROM subscriptions WHERE url="+android.database.DatabaseUtils.sqlEscapeString(newUrl)).simpleQueryForLong()>0)throw new IllegalArgumentException("Такая подписка уже существует");
    db.execSQL("INSERT INTO subscriptions(url,title,userinfo,announcement,updated_at,custom_title,hidden) SELECT ?,title,userinfo,announcement,updated_at,?,? FROM subscriptions WHERE url=?",new Object[]{newUrl,title,hide?1:0,oldUrl});
    db.execSQL("UPDATE servers SET subscription_url=? WHERE subscription_url=?",new Object[]{newUrl,oldUrl});
    db.delete("subscriptions","url=?",new String[]{oldUrl});
   }else{ContentValues v=new ContentValues();v.put("custom_title",title);v.put("hidden",hide?1:0);db.update("subscriptions",v,"url=?",new String[]{oldUrl});}
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 public void deleteSubscription(String url){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("DELETE FROM selected WHERE link IN (SELECT link FROM servers WHERE subscription_url=?) AND link NOT IN (SELECT link FROM servers WHERE subscription_url<>?)",new Object[]{url,url});db.delete("subscriptions","url=?",new String[]{url});db.setTransactionSuccessful();}finally{db.endTransaction();}}
 public void setSelected(Set<String> links){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("selected",null,null);for(String link:links){ContentValues v=new ContentValues();v.put("link",link);db.insertOrThrow("selected",null,v);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
 public void updateSelected(Collection<String> links,boolean select){
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
  try{for(String link:links){if(select){ContentValues v=new ContentValues();v.put("link",link);db.insertWithOnConflict("selected",null,v,SQLiteDatabase.CONFLICT_IGNORE);}else db.delete("selected","link=?",new String[]{link});}db.setTransactionSuccessful();}finally{db.endTransaction();}
 }
 public HashSet<String> selected(){HashSet<String> result=new LinkedHashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT link FROM selected ORDER BY rowid",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
 public ArrayList<String> urls(){ArrayList<String> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT url FROM subscriptions ORDER BY rowid",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
 public String title(String url){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(custom_title,title) FROM subscriptions WHERE url=?",new String[]{url})){return c.moveToFirst()?c.getString(0):SubscriptionInfo.title(null,url);}}
 public void loadServers(ArrayList<String> items,ArrayList<String> groups,ArrayList<String> owners){items.clear();groups.clear();owners.clear();try(Cursor c=getReadableDatabase().rawQuery("SELECT servers.link, COALESCE(subscriptions.custom_title,subscriptions.title), subscriptions.url FROM servers JOIN subscriptions ON subscriptions.url=servers.subscription_url ORDER BY subscriptions.rowid, servers.position",null)){while(c.moveToNext()){items.add(c.getString(0));groups.add(c.getString(1));owners.add(c.getString(2));}}}
}
