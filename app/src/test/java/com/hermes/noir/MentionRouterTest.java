package com.hermes.noir;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class MentionRouterTest {
    private LinkedHashMap<String,String> members(){LinkedHashMap<String,String> m=new LinkedHashMap<>();m.put("fixer","a");m.put("rev","b");return m;}
    @Test public void directMentionsDoNotBroadcast(){assertEquals(List.of("b"),MentionRouter.recipients("@Rev review this @rev",members()));}
    @Test public void unknownMemberFailsClosed(){try{MentionRouter.recipients("@missing",members());fail();}catch(IllegalArgumentException expected){}}
    @Test public void emailsAndCodeDoNotRoute(){assertEquals(List.of("a","b"),MentionRouter.recipients("mail user@example.com; use `@decorator`",members()));}
    @Test public void everyoneRoutesInMembershipOrder(){assertEquals(List.of("a","b"),MentionRouter.recipients("@everyone",members()));}
}
