package com.hermes.noir;

import android.content.Context;
import android.content.Intent;
import org.json.*;

/**
 * Shared submit pipeline for entry points without a composer (notification quick reply,
 * scheduled messages). Adds the user message, queues one reply per recipient and hands
 * the turn to ChatService. When a turn is already active the message is stored as queued
 * with resumeTurn and drains automatically when the running turn finishes.
 */
public final class Send {
    private Send(){}
    public static boolean arabic(Context c){try{return Store.get(c).read().optString("language","en").equals("ar");}catch(Exception e){return false;}}

    /** Returns true when the turn was started now, false when it was queued for later. */
    public static boolean submit(Context c,String threadId,String text)throws Exception {
        Store store=Store.get(c);
        JSONObject snapshot=store.read();
        JSONObject t=Store.find(snapshot.getJSONArray("threads"),threadId);
        JSONArray bots=snapshot.getJSONArray("bots");
        java.util.List<String> recipients=Conversations.recipients(t,bots,text);
        for(String id:recipients){Store.find(bots,id);if(!Conversations.contains(t,id))throw new Exception("This bot is not in this conversation");}
        final String turn=Store.id();
        if(ChatService.isActive()){
            store.edit(d->{
                JSONObject target=Store.find(d.getJSONArray("threads"),threadId);JSONArray rows=target.getJSONArray("messages");
                rows.put(userMessage(turn,text));
                for(String botId:recipients)rows.put(Conversations.pending(Store.find(d.getJSONArray("bots"),botId),turn));
                target.put("updated",System.currentTimeMillis()).put("resumeTurn",turn);
            });
            ChatService.watchNetwork(c);
            return false;
        }
        if(!ChatService.reserve())return false;
        try{
            store.edit(d->{
                JSONObject target=Store.find(d.getJSONArray("threads"),threadId);JSONArray rows=target.getJSONArray("messages");
                rows.put(userMessage(turn,text));
                for(String botId:recipients)rows.put(Conversations.pending(Store.find(d.getJSONArray("bots"),botId),turn));
                target.put("updated",System.currentTimeMillis());
            });
        }catch(Exception e){ChatService.releaseReservation();throw e;}
        try{c.startForegroundService(new Intent(c,ChatService.class).putExtra("thread",threadId).putExtra("turn",turn));}
        catch(Exception e){
            ChatService.releaseReservation();
            store.edit(d->{
                JSONObject target=Store.find(d.getJSONArray("threads"),threadId);JSONArray rows=target.getJSONArray("messages");
                for(int j=0;j<rows.length();j++){JSONObject row=rows.getJSONObject(j);
                    if(row.optString("turn").equals(turn)&&(row.optString("status").equals("queued")||row.optString("status").equals("done")&&row.optLong("ts",0)==0))
                        if(row.optString("role").equals("assistant"))row.put("status","error").put("error","Could not start reply service");}
                target.put("resumeTurn",turn);
            });
            ChatService.watchNetwork(c);
            return false;
        }
        return true;
    }
    private static JSONObject userMessage(String turn,String text)throws Exception {
        return new JSONObject().put("id",Store.id()).put("turn",turn).put("role","user")
            .put("content",text).put("status","done").put("ts",System.currentTimeMillis());
    }
}
