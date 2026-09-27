package com.hermes.noir;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fires one scheduled message; reschedules the next day. Disables itself if the thread is gone. */
public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        final String id=intent==null?"":intent.getStringExtra("id");
        if(id==null||id.isEmpty())return;
        final PendingResult pending=goAsync();
        new Thread(()->{
            try{
                Store store=Store.get(c);
                JSONObject schedule=null;
                JSONArray arr=Alarms.list(store.read());
                for(int i=0;i<arr.length();i++){JSONObject row=arr.getJSONObject(i);if(row.optString("id").equals(id))schedule=row;}
                if(schedule!=null&&schedule.optBoolean("enabled",true)){
                    String threadId=schedule.optString("threadId");
                    try{Store.find(store.read().getJSONArray("threads"),threadId);
                        String text=schedule.optString("text").trim();
                        if(text.isEmpty())text=Send.arabic(c)?"رسالتك المجدولة":"Your scheduled message";
                        Send.submit(c,threadId,text);
                    }catch(Exception gone){
                        Alarms.cancel(c,id);
                        store.edit(d->{JSONArray all=Alarms.list(d);
                            for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optString("id").equals(id))all.getJSONObject(i).put("enabled",false);});
                    }
                    Alarms.scheduleNext(c,schedule);
                }
            }catch(Exception ignored){}finally{pending.finish();}
        },"hermes-alarm").start();
    }
}
