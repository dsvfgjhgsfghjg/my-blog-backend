package com.leo.myblog.security;

import com.leo.myblog.config.AppSecurityProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Exact origin allowlist shared by CORS and CSRF origin validation.
 */
@Component
public class AllowedOrigins {

    private final Set<String> origins;

    public AllowedOrigins(AppSecurityProperties properties) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String origin : properties.getCors().getAllowedOrigins()) {
            String value = normalize(origin);
            if (value != null) {
                normalized.add(value);
            }
        }
        this.origins = Set.copyOf(normalized);
    }

    public boolean isAllowed(String origin) {
        String normalized = normalize(origin);
        return normalized != null && origins.contains(normalized);
    }

    public List<String> getOrigins() {
        return List.copyOf(origins);
    }

    public String normalize(String origin) {
        if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            int port = uri.getPort();
            return scheme + "://" + uri.getHost().toLowerCase()
                    + (port > 0 ? ":" + port : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
