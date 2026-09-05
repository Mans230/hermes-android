package com.hermes.noir;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ProtocolTest {
    @Test public void handlesKeepaliveMultilineAndDone() throws Exception {
        List<String> events = new ArrayList<>();
        SseReader.read(new StringReader(":ping\r\nevent: custom\r\ndata: a\r\ndata: b\r\n\r\ndata: [DONE]\n\ndata: ignored\n\n"),
            (type,data)-> { events.add(type+"|"+data); return !data.equals("[DONE]"); });
        assertEquals(List.of("custom|a\nb", "message|[DONE]"), events);
    }
    @Test public void incompleteEventIsNotDispatched() throws Exception {
        List<String> events = new ArrayList<>();
        SseReader.read(new StringReader("data: incomplete"), (t,d)->events.add(d));
        assertTrue(events.isEmpty());
    }
    @Test public void supportsBomAndEmptyData() throws Exception {
        List<String> events = new ArrayList<>();
        SseReader.read(new StringReader("\uFEFFdata:\n\n"), (t,d)->events.add(d));
        assertEquals(List.of(""), events);
    }
    @Test public void normalizesApiRoot() {
        assertEquals("https://bot.example.com", Endpoint.normalize(" https://bot.example.com/v1/ "));
        assertEquals("https://example.com/hermes", Endpoint.normalize("https://example.com/hermes"));
    }
    @Test public void rejectsUnsafeUrls() {
        for (String url: List.of("http://bot.example.com", "https://user:pass@bot.example.com", "https://bot.example.com?key=secret", "file:///tmp/test", "https://bot.example.com/#token")) {
            try { Endpoint.normalize(url); fail("accepted: " + url); } catch (IllegalArgumentException expected) {}
        }
    }
}
