package com.leo.myblog.security;

import com.leo.myblog.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private ClientIpResolver resolver() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setTrustedProxyCidrs(List.of("127.0.0.1/32", "10.0.0.0/8"));
        return new ClientIpResolver(properties);
    }

    @Test
    void ignoresForwardedHeadersFromUntrustedPeers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("203.0.113.9", resolver().resolve(request));
    }

    @Test
    void choosesFirstUntrustedAddressFromRightWhenProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");

        assertEquals("198.51.100.7", resolver().resolve(request));
    }

    @Test
    void rejectsHostnameAndMalformedForwardedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "attacker.example, 198.51.100.7");

        assertEquals("198.51.100.7", resolver().resolve(request));
    }
}
