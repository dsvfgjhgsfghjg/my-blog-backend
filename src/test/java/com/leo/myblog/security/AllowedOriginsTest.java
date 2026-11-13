package com.leo.myblog.security;

import com.leo.myblog.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowedOriginsTest {

    @Test
    void onlyExactConfiguredOriginsAreAllowed() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.getCors().setAllowedOrigins(List.of(
                "https://leo-blog.top",
                "http://localhost:5173"
        ));
        AllowedOrigins allowed = new AllowedOrigins(properties);

        assertTrue(allowed.isAllowed("https://leo-blog.top"));
        assertTrue(allowed.isAllowed("http://localhost:5173"));
        assertFalse(allowed.isAllowed("https://evil.example"));
        assertFalse(allowed.isAllowed("https://leo-blog.top.evil.example"));
        assertFalse(allowed.isAllowed("null"));
    }
}
