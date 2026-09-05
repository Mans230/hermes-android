package com.hermes.noir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/** SSE framing, independent of Android so it can be unit tested. */
public final class SseReader {
    public interface Listener { boolean event(String type, String data) throws Exception; }
    public static void read(Reader input, Listener listener) throws Exception {
        BufferedReader reader = new BufferedReader(input);
        String line, type = "message";
        StringBuilder data = new StringBuilder();
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first && line.startsWith("\uFEFF")) line = line.substring(1);
            first = false;
            if (line.isEmpty()) {
                if (data.length() > 0 && !listener.event(type, data.substring(0, data.length()-1))) return;
                type = "message";
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) continue;
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if (field.equals("event")) type = value;
            if (field.equals("data")) {
                data.append(value).append('\n');
                if (data.length() > 2_000_000) throw new IOException("SSE event too large");
            }
        }
        // A final unterminated event is not complete; caller detects missing DONE.
    }
}
