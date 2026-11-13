package com.leo.myblog.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CookieSecurityConfigTest {

    @Test
    void accessAndRefreshCookiesUseHttpOnlyAndSeparatePaths() {
        CookieSecurityConfig config = new CookieSecurityConfig(mock(Environment.class));
        ReflectionTestUtils.setField(config, "cookieName", "AUTH_TOKEN");
        ReflectionTestUtils.setField(config, "cookieSecure", false);
        ReflectionTestUtils.setField(config, "accessCookieMaxAge", 3600);
        ReflectionTestUtils.setField(config, "refreshCookieName", "REFRESH_TOKEN");
        ReflectionTestUtils.setField(config, "refreshCookieMaxAge", 2592000);
        ReflectionTestUtils.setField(config, "refreshCookiePath", "/api/auth2");

        Cookie access = config.createSecureCookie("a");
        Cookie refresh = config.createRefreshCookie("r");

        assertTrue(access.isHttpOnly());
        assertTrue(refresh.isHttpOnly());
        assertEquals("/", access.getPath());
        assertEquals("/api/auth2", refresh.getPath());
        assertEquals("Strict", access.getAttribute("SameSite"));
        assertEquals("Strict", refresh.getAttribute("SameSite"));
        assertFalse(access.getSecure());
    }
}
