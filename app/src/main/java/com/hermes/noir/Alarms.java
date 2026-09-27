package com.hermes.noir;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Daily scheduled messages: each schedule fires one queued user message per day at
 * HH:MM. Alarms are inexact (no special permissions needed) and reschedule themselves
 * after every fire and on boot; the next occurrence is also re-armed on every app open.
 */
public final class Alarms {
    private Alarms(){}
    public static JSONArray list(JSONObject data){JSONArray s=data.optJSONArray("schedules");return s==null?new JSONArray():s;}
    public static long nextAt(String time,long after)throws Exception {
        if(time==null||!time.trim().matches("([01]?\\d|2[0-3]):[0-5]\\d"))throw new IllegalArgumentException("Use HH:MM (24h)");
        String[] parts=time.trim().split(":");
        java.util.Calendar cal=java.util.Calendar.getInstance();
        cal.setTimeInMillis(after);
        cal.set(java.util.Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));
        cal.set(java.util.Calendar.MINUTE,Integer.parseInt(parts[1]));
        cal.set(java.util.Calendar.SECOND,0);cal.set(java.util.Calendar.MILLISECOND,0);
        if(cal.getTimeInMillis()<=after)cal.add(java.util.Calendar.DAY_OF_YEAR,1);
        return cal.getTimeInMillis();
    }
    private static PendingIntent pending(Context c,String id){
        Intent i=new Intent(c,AlarmReceiver.class).putExtra("id",id);
        return PendingIntent.getBroadcast(c,id.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void scheduleNext(Context c,JSONObject schedule){
        try{
            if(!schedule.optBoolean("enabled",true))return;
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
            long at=nextAt(schedule.optString("time"),System.currentTimeMillis());
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(c,schedule.optString("id")));
        }catch(Exception ignored){}
    }
    public static void cancel(Context c,String id){
        try{AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(pending(c,id));}catch(Exception ignored){}
    }
    public static void scheduleAll(Context c){
        try{
            JSONArray schedules=list(Store.get(c).read());
            for(int i=0;i<schedules.length();i++)scheduleNext(c,schedules.getJSONObject(i));
        }catch(Exception ignored){}
    }
}
