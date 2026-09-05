import com.hermes.noir.Endpoint;
import com.hermes.noir.SseReader;
import com.hermes.noir.MentionRouter;
import java.io.StringReader;
import java.util.*;

public class ProtocolSmoke {
    private static int checks=0;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception {
        List<String> events=new ArrayList<>();
        SseReader.read(new StringReader("\uFEFF: ping\r\nevent: hermes.tool.progress\r\ndata: {\"tool\":\"terminal\"}\r\n\r\ndata: first\ndata: second\n\ndata: [DONE]\n\ndata: should not dispatch\n\n"),
            (t,d)->{events.add(t+"|"+d);return !d.equals("[DONE]");});
        check(events.size()==3,"events stop at DONE");
        check(events.get(0).startsWith("hermes.tool.progress|"),"custom tool event");
        check(events.get(1).equals("message|first\nsecond"),"multiline data and reset event type");
        events.clear();SseReader.read(new StringReader("data: truncated"),(t,d)->events.add(d));
        check(events.isEmpty(),"incomplete event not dispatched");
        events.clear();SseReader.read(new StringReader("data:\n\n"),(t,d)->events.add(d));
        check(events.equals(List.of("")),"empty data event");
        boolean oversized=false;
        try{SseReader.read(new StringReader("data: "+"x".repeat(2_000_001)+"\n\n"),(t,d)->true);}catch(java.io.IOException e){oversized=true;}
        check(oversized,"oversized event bounded");
        check(Endpoint.normalize(" https://bot.example.com/v1/ ").equals("https://bot.example.com"),"v1 normalization");
        check(Endpoint.normalize("https://example.com/agent").equals("https://example.com/agent"),"proxy path preserved");
        for(String bad:List.of("http://example.com","https://user:key@example.com","https://example.com?token=x","https://example.com/#key","file:///tmp/a","https:///nohost")){
            boolean rejected=false;try{Endpoint.normalize(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject "+bad);
        }
        LinkedHashMap<String,String> members=new LinkedHashMap<>();members.put("fixer","one");members.put("rev","two");members.put("synth","three");
        check(MentionRouter.recipients("Morning everyone",members).equals(List.of("one","two","three")),"broadcast without mentions");
        check(MentionRouter.recipients("@REV please review with @fixer @rev",members).equals(List.of("one","two")),"dedup, ignore case, membership order");
        check(MentionRouter.recipients("Mail me user@example.com",members).size()==3,"email not a mention");
        check(MentionRouter.recipients("Use `@dataclass` and ```\n@decorator\n``` @synth",members).equals(List.of("three")),"code blocks excluded");
        check(MentionRouter.recipients("@everyone check in",members).size()==3,"explicit broadcast");
        boolean rejectedUnknown=false;try{MentionRouter.recipients("@missing",members);}catch(IllegalArgumentException e){rejectedUnknown=true;}check(rejectedUnknown,"unknown mention is not broadcast");
        check(MentionRouter.handle(" @Fixer ").equals("fixer"),"normalize handle");
        for(String h:List.of("everyone","9bot","two words","", "a".repeat(33))){boolean rejected=false;try{MentionRouter.handle(h);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid handle");}
        System.out.println("PASS: "+checks+" protocol, HTTPS URL and mention-routing checks");
    }
}
