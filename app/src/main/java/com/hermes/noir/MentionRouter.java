package com.hermes.noir;

import java.util.*;
import java.util.regex.*;

/** Only user-authored mentions route calls. Agent output never schedules another call. */
public final class MentionRouter {
    private static final Pattern MENTION=Pattern.compile("(?<![\\p{L}\\p{N}_@])@([a-zA-Z0-9_-]+)");
    public static List<String> recipients(String message, LinkedHashMap<String,String> members) {
        if(members.isEmpty())throw new IllegalArgumentException("This group has no members");
        String prose=message.replaceAll("(?s)```.*?```", " ").replaceAll("`[^`]*`", " ");
        Matcher matcher=MENTION.matcher(prose);
        LinkedHashSet<String> handles=new LinkedHashSet<>();
        while(matcher.find()){
            String handle=matcher.group(1).toLowerCase(Locale.ROOT);
            if(!handle.equals("everyone")&&!members.containsKey(handle))
                throw new IllegalArgumentException("Unknown group member @"+handle);
            handles.add(handle);
        }
        ArrayList<String> result=new ArrayList<>();
        for(Map.Entry<String,String> entry:members.entrySet())
            if(handles.isEmpty() || handles.contains("everyone") || handles.contains(entry.getKey()))
                if(!result.contains(entry.getValue()))result.add(entry.getValue());
        return result;
    }
    public static String handle(String value){
        String h=value.trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        if(!h.matches("[a-z][a-z0-9_-]{0,31}")||h.equals("everyone"))
            throw new IllegalArgumentException("Use a unique handle: 1–32 English letters, digits, _ or -, starting with a letter");
        return h;
    }
}
