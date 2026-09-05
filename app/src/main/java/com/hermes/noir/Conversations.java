package com.hermes.noir;

import org.json.*;
import java.util.*;

public final class Conversations {
    public static boolean group(JSONObject t){return t.optJSONArray("botIds")!=null;}
    public static JSONArray members(JSONObject t)throws Exception {
        return group(t)?t.getJSONArray("botIds"):new JSONArray().put(t.getString("botId"));
    }
    public static boolean contains(JSONObject t,String id)throws Exception {
        JSONArray a=members(t);for(int i=0;i<a.length();i++)if(a.getString(i).equals(id))return true;return false;
    }
    public static String handle(JSONObject bot){return bot.optString("handle","agent_"+bot.optString("id").replace("-", "").substring(0,8));}
    public static List<String> recipients(JSONObject t,JSONArray bots,String message)throws Exception {
        JSONArray ids=members(t);
        LinkedHashMap<String,String> lookup=new LinkedHashMap<>();
        for(int i=0;i<ids.length();i++){JSONObject b=Store.find(bots,ids.getString(i));lookup.put(handle(b),b.getString("id"));}
        if(!group(t))return new ArrayList<>(lookup.values());
        return MentionRouter.recipients(message,lookup);
    }
    public static JSONObject pending(JSONObject bot,String turn)throws Exception {
        return pending(bot,turn,null,null,null);
    }
    public static JSONObject pending(JSONObject bot,String turn,String model,String provider,String effort)throws Exception {
        JSONObject row=new JSONObject().put("id",Store.id()).put("turn",turn).put("role","assistant")
            .put("botId",bot.getString("id")).put("botName",bot.getString("name"))
            .put("avatar",bot.optString("avatar","🤖")).put("photo",bot.optString("photo")).put("content", "").put("status","queued");
        if(model!=null&&!model.trim().isEmpty())row.put("modelOverride",model.trim());
        if(provider!=null&&!provider.trim().isEmpty())row.put("providerOverride",provider.trim());
        if(effort!=null&&!effort.trim().isEmpty())row.put("effortOverride",effort.trim());
        return row;
    }
    public static JSONObject reply(JSONArray messages,String id)throws Exception {return Store.find(messages,id);}
}
