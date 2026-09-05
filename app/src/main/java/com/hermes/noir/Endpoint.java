package com.hermes.noir;

import java.net.URI;

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
}
