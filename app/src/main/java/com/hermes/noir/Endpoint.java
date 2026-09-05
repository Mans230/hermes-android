package com.hermes.noir;

import java.net.URI;
import java.util.Locale;

public final class Endpoint {
    public static String normalize(String value) {
        String base = value.trim().replaceAll("/+$", "");
        if (base.endsWith("/v1")) base = base.substring(0, base.length()-3);
        URI uri = URI.create(base);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
            uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Enter an HTTPS server URL without credentials or query parameters");
        return base;
    }

    /** Add Hermes' multiplex profile prefix without allowing path traversal. */
    public static String withProfile(String value, String profile) {
        String base = normalize(value);
        String p = profile == null ? "" : profile.trim();
        if (p.isEmpty() || p.equalsIgnoreCase("default")) return base;
        if (!p.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Profile must use letters, numbers, _ or -");
        if (base.matches(".*/p/[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")) return base;
        return base + "/p/" + p;
    }
}
