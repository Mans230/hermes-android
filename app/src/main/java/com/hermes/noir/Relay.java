package com.hermes.noir;

/** Pure helpers for the opt-in relay discussion; kept Android-free for unit tests. */
public final class Relay {
    public static final int MAX_ROUNDS = 10;
    public static int clamp(int value) { return Math.max(0, Math.min(MAX_ROUNDS, value)); }
    public static int parse(String text) {
        if (text == null) return 0;
        text = text.trim();
        if (text.isEmpty()) return 0;
        try { return clamp(Integer.parseInt(text)); } catch (NumberFormatException e) { return 0; }
    }
}
