package com.hermes.noir;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import org.json.*;

/** User-initiated group turns run once per selected agent, in membership order. */
public final class ChatService extends Service {
    public static volatile boolean busy=false;
    private static volatile boolean reserved=false;
    public static synchronized boolean reserve(){if(busy||reserved)return false;reserved=true;return true;}
    public static synchronized void releaseReservation(){reserved=false;}
    public static boolean isActive(){return busy||reserved;}
    public static volatile String threadId="", replyId="", text="", model="", progress="", activity="[]";
    public static volatile long revision=0;
    @Override public IBinder onBind(Intent i){return null;}
    private Notification notification(String message,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class).putExtra("thread",threadId);
        PendingIntent pi=PendingIntent.getActivity(this,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"responses").setSmallIcon(R.drawable.ic_noir)
            .setContentTitle("Hermes").setContentText(message).setContentIntent(pi)
            .setOngoing(ongoing).setAutoCancel(!ongoing).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){releaseReservation();stopSelf();return START_NOT_STICKY;}
        if(busy)return START_NOT_STICKY;
        threadId=intent.getStringExtra("thread");
        final String activeThread=threadId,turn=intent.getStringExtra("turn");
        text="";model="";progress="";busy=true;reserved=false;revision++;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("responses","Agent replies",NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(1,notification("Working…",true));
        new Thread(()->{
            boolean allDone=true;
            try{
                Store store=Store.get(this);JSONObject snapshot=store.read();
                JSONObject thread=Store.find(snapshot.getJSONArray("threads"),activeThread);
                JSONArray planned=thread.getJSONArray("messages");
                for(int i=0;i<planned.length();i++){
                    JSONObject item=planned.getJSONObject(i);
                    if(!item.optString("turn").equals(turn)||!item.optString("status").equals("queued"))continue;
                    final String id=item.getString("id");replyId=id;text="";model="";activity="[]";progress="Thinking…";revision++;
                    JSONArray tools=new JSONArray();
                    try{
                        store.edit(d->Conversations.reply(Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages"),id).put("status","pending"));
                        revision++;
                        JSONObject root=store.read();JSONObject current=Store.find(root.getJSONArray("threads"),activeThread);
                        JSONObject bot=Store.find(root.getJSONArray("bots"),item.getString("botId"));
                        JSONObject requestBot=new JSONObject(bot.toString());
                        if(item.has("modelOverride"))requestBot.put("model",item.optString("modelOverride"));
                        if(item.has("providerOverride"))requestBot.put("provider",item.optString("providerOverride"));
                        if(item.has("effortOverride"))requestBot.put("effort",item.optString("effortOverride"));
                        HermesApi.Stream streamListener=new HermesApi.Stream(){
                            public void delta(String value,String m){text=value;model=m;progress="";revision++;}
                            public void progress(String tool){
                                if(tools.length()<100)tools.put(tool);
                                activity=tools.toString();progress="Using "+tool+"…";revision++;
                            }
                        };
                        JSONObject result;
                        try {
                            result=HermesApi.stream(requestBot,current.getJSONArray("messages"),Conversations.group(current),activeThread,streamListener);
                        } catch(Exception first) {
                            String fallback=item.optString("fallbackModel",bot.optString("fallbackModel"));
                            if(fallback.isEmpty() || fallback.equals(requestBot.optString("model"))) throw first;
                            requestBot.put("model",fallback).put("provider",item.optString("fallbackProvider",bot.optString("fallbackProvider")))
                                .put("effort",item.optString("fallbackEffort",bot.optString("fallbackEffort")));
                            progress="Retrying with fallback model…";revision++;
                            result=HermesApi.stream(requestBot,current.getJSONArray("messages"),Conversations.group(current),activeThread,streamListener);
                        }
                        final JSONObject completed=result;
                        store.edit(d->{
                            JSONObject target=Store.find(d.getJSONArray("threads"),activeThread);
                            JSONObject reply=Conversations.reply(target.getJSONArray("messages"),id);
                            reply.put("content",completed.getString("content")).put("status","done")
                                .put("model",completed.optString("model")).put("usage",completed.getJSONObject("usage")).put("tools",tools);
                            target.put("updated",System.currentTimeMillis());
                        });
                    }catch(Exception e){
                        allDone=false;
                        store.edit(d->Conversations.reply(Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages"),id)
                            .put("status","error").put("content",text).put("tools",tools).put("error",safeError(e)));
                    }
                    revision++;
                }
            }catch(Exception e){
                allDone=false;progress="Could not finish this turn. Check app storage and connection.";
                try{Store.get(this).edit(d->{JSONArray rows=Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages");for(int j=0;j<rows.length();j++){
                    JSONObject row=rows.getJSONObject(j);if(row.optString("turn").equals(turn) && (row.optString("status").equals("queued")||row.optString("status").equals("pending")))row.put("status","error").put("error","Turn interrupted. Check the bot before retrying.");
                }});}catch(Exception ignored){}
            }finally{
                busy=false;replyId="";revision++;stopForeground(STOP_FOREGROUND_REMOVE);
                if(Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                    nm.notify(2,notification(allDone?"Your agents replied • الرد جاهز":"Some replies were interrupted • راجع المحادثة",false));
                stopSelf();
            }
        },"hermes-group-turn").start();
        return START_NOT_STICKY;
    }
    private static String safeError(Exception e){
        if(e instanceof javax.net.ssl.SSLException)return "TLS connection failed. Check the certificate.";
        if(e instanceof java.net.SocketTimeoutException)return "Connection timed out. Check the bot before retrying.";
        if(e instanceof java.net.UnknownHostException)return "Server is unreachable. Check the address and network.";
        if(e instanceof java.io.IOException && e.getMessage()!=null)return e.getMessage();
        return "Could not complete this reply. Check the connection and Hermes API version.";
    }
    @Override public void onTimeout(int startId,int type){stopSelf();}
}
