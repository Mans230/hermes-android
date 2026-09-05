package com.hermes.noir;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import org.json.*;

/**
 * User-initiated group turns run once per selected agent, in membership order.
 * Optional relay rounds extend a broadcast turn into up to Relay.MAX_ROUNDS extra
 * discussion rounds. The service itself schedules every round from user settings —
 * agent output never schedules another call — and the user can stop the relay at
 * any moment between replies.
 */
public final class ChatService extends Service {
    public static volatile boolean busy=false;
    private static volatile boolean reserved=false;
    public static synchronized boolean reserve(){if(busy||reserved)return false;reserved=true;return true;}
    public static synchronized void releaseReservation(){reserved=false;}
    public static boolean isActive(){return busy||reserved;}
    public static volatile String threadId="", replyId="", text="", model="", progress="", activity="[]", reasoning="";
    public static volatile int relayRound=0, relayTotal=0;
    public static volatile boolean relayStop=false;
    public static void requestRelayStop(){relayStop=true;}
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
        try{Store.get(this).edit(d->Store.find(d.getJSONArray("threads"),threadId).remove("resumeTurn"));}catch(Exception ignored){}
        text="";model="";progress="";busy=true;reserved=false;relayRound=0;relayTotal=0;relayStop=false;revision++;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("responses","Agent replies",NotificationManager.IMPORTANCE_DEFAULT));
        startForeground(1,notification("Working…",true));
        new Thread(()->{
            boolean allDone=true;
            try{
                Store store=Store.get(this);
                allDone=runTurn(store,activeThread,turn,false);
                if(allDone){
                    JSONObject thread=Store.find(store.read().getJSONArray("threads"),activeThread);
                    int rounds=Relay.clamp(thread.optInt("relayRounds",0));
                    if(Conversations.group(thread)&&rounds>0){
                        relayTotal=rounds;
                        for(int round=1;round<=relayTotal&&!relayStop;round++){
                            relayRound=round;revision++;
                            nm.notify(1,notification("Discussion round "+round+" of "+relayTotal+" • جولة حوار "+round+" من "+relayTotal,true));
                            final String relayTurn=Store.id();
                            store.edit(d->{
                                JSONObject target=Store.find(d.getJSONArray("threads"),activeThread);
                                JSONArray ids=Conversations.members(target),rows=target.getJSONArray("messages");
                                for(int i=0;i<ids.length();i++)rows.put(Conversations.pending(Store.find(d.getJSONArray("bots"),ids.getString(i)),relayTurn));
                                target.put("updated",System.currentTimeMillis());
                            });
                            revision++;
                            if(!runTurn(store,activeThread,relayTurn,true))break;
                        }
                    }
                }
            }catch(Exception e){
                allDone=false;progress="Could not finish this turn. Check app storage and connection.";
                try{Store.get(this).edit(d->{JSONArray rows=Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages");for(int j=0;j<rows.length();j++){
                    JSONObject row=rows.getJSONObject(j);if(row.optString("turn").equals(turn) && (row.optString("status").equals("queued")||row.optString("status").equals("pending")))row.put("status","error").put("error","Turn interrupted. Check the bot before retrying.");
                }});}catch(Exception ignored){}
            }finally{
                busy=false;replyId="";relayRound=0;relayTotal=0;relayStop=false;revision++;stopForeground(STOP_FOREGROUND_REMOVE);
                if(Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                    nm.notify(2,notification(allDone?"Your agents replied • الرد جاهز":"Some replies were interrupted • راجع المحادثة",false));
                stopSelf();
            }
        },"hermes-group-turn").start();
        return START_NOT_STICKY;
    }
    /** Runs every queued reply of one turn in membership order; false when any reply failed. */
    private boolean runTurn(Store store,String activeThread,String turn,boolean stoppable) throws Exception {
        JSONObject snapshot=store.read();
        JSONObject thread=Store.find(snapshot.getJSONArray("threads"),activeThread);
        JSONArray planned=thread.getJSONArray("messages");
        boolean ok=true;
        for(int i=0;i<planned.length();i++){
            JSONObject item=planned.getJSONObject(i);
            if(!item.optString("turn").equals(turn)||!item.optString("status").equals("queued"))continue;
            if(stoppable&&relayStop){stopRelayTurn(store,activeThread,turn);return false;}
            final String id=item.getString("id");replyId=id;text="";model="";activity="[]";progress="Thinking…";reasoning="";revision++;
            JSONArray tools=new JSONArray();
            try{
                store.edit(d->Conversations.reply(Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages"),id).put("status","pending"));
                revision++;
                JSONObject root=store.read();JSONObject current=Store.find(root.getJSONArray("threads"),activeThread);
                JSONObject bot=Store.find(root.getJSONArray("bots"),item.getString("botId"));
                JSONObject result=HermesApi.stream(bot,current.getJSONArray("messages"),Conversations.group(current),new HermesApi.Stream(){
                    public void delta(String value,String m){text=value;model=m;progress="";revision++;}
                    public void progress(String tool){
                        if(tools.length()<100)tools.put(tool);
                        activity=tools.toString();progress="Using "+tool+"…";revision++;
                    }
                    public void reasoning(String value){reasoning=value;revision++;}
                });
                store.edit(d->{
                    JSONObject target=Store.find(d.getJSONArray("threads"),activeThread);
                    JSONObject reply=Conversations.reply(target.getJSONArray("messages"),id);
                            reply.put("content",result.getString("content")).put("status","done")
                                .put("ts",System.currentTimeMillis())
                                .put("model",result.optString("model")).put("usage",result.getJSONObject("usage")).put("tools",tools);
                            if(result.optString("thinking").length()>0)reply.put("thinking",result.optString("thinking"));
                    target.put("updated",System.currentTimeMillis());
                });
            }catch(Exception e){
                ok=false;
                store.edit(d->Conversations.reply(Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages"),id)
                    .put("status","error").put("content",text).put("tools",tools).put("error",safeError(e)));
            }
            revision++;
        }
        return ok;
    }
    private void stopRelayTurn(Store store,String activeThread,String turn){
        try{store.edit(d->{JSONArray rows=Store.find(d.getJSONArray("threads"),activeThread).getJSONArray("messages");for(int j=0;j<rows.length();j++){
            JSONObject row=rows.getJSONObject(j);if(row.optString("turn").equals(turn)&&row.optString("status").equals("queued"))row.put("status","error").put("error","Relay stopped • تم إيقاف الحوار");
        }});}catch(Exception ignored){}
    }
    private static String safeError(Exception e){
        if(e instanceof javax.net.ssl.SSLException)return "TLS connection failed. Check the certificate.";
        if(e instanceof java.net.SocketTimeoutException)return "Connection timed out. Check the bot before retrying.";
        if(e instanceof java.net.UnknownHostException)return "Server is unreachable. Check the address and network.";
        if(e instanceof java.io.IOException && e.getMessage()!=null)return e.getMessage();
        return "Could not complete this reply. Check the connection and Hermes API version.";
    }
    private static volatile boolean netWatch=false;
    /** Saves a turn for auto-delivery when connectivity returns; nothing has executed yet, so resending is safe. */
    public static void queueOffline(android.content.Context c,String threadId,String turn){
        try{Store.get(c).edit(d->Store.find(d.getJSONArray("threads"),threadId).put("resumeTurn",turn));}catch(Exception ignored){}
        watchNetwork(c);
    }
    public static synchronized void watchNetwork(final android.content.Context c){
        if(netWatch)return;netWatch=true;
        android.net.ConnectivityManager cm=(android.net.ConnectivityManager)c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if(cm==null)return;
        cm.registerNetworkCallback(new android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            new android.net.ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(android.net.Network network){
                    try{
                        Store store=Store.get(c);JSONArray threads=store.read().getJSONArray("threads");
                        for(int i=0;i<threads.length();i++){
                            JSONObject t=threads.getJSONObject(i);final String resumeTurn=t.optString("resumeTurn","");
                            if(resumeTurn.isEmpty())continue;
                            boolean has=false;JSONArray rows=t.getJSONArray("messages");
                            for(int j=0;j<rows.length();j++){JSONObject r=rows.getJSONObject(j);if(r.optString("turn").equals(resumeTurn)&&r.optString("status").equals("queued"))has=true;}
                            store.edit(d->Store.find(d.getJSONArray("threads"),t.getString("id")).remove("resumeTurn"));
                            if(!has)continue;
                            Intent intent=new Intent(c,ChatService.class).putExtra("thread",t.getString("id")).putExtra("turn",resumeTurn);
                            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(intent);else c.startService(intent);
                        }
                    }catch(Exception ignored){}
                }
            });
    }
    @Override public void onTimeout(int startId,int type){stopSelf();}
}
