package com.leo.myblog.security;

import com.leo.myblog.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the client IP without trusting proxy headers from arbitrary peers.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private final List<Cidr> trustedProxies;

    public ClientIpResolver(AppSecurityProperties properties) {
        this.trustedProxies = properties.getTrustedProxyCidrs().stream()
                .map(Cidr::parse)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public String resolveCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? "unknown" : resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalizeIp(request.getRemoteAddr());
        if (remote == null) {
            return "unknown";
        }
        if (!isTrustedProxy(remote)) {
            return remote;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] entries = forwardedFor.split(",");
            for (int i = entries.length - 1; i >= 0; i--) {
                String candidate = normalizeIp(entries[i]);
                if (candidate != null && !isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : remote;
    }

    boolean isTrustedProxy(String ip) {
        Cidr address = Cidr.parse(ip);
        if (address == null) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(address.address())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("[") && candidate.contains("]")) {
            candidate = candidate.substring(1, candidate.indexOf(']'));
        }
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }
        if (!candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception e) {
            log.debug("忽略非法 IP: {}", value);
            return null;
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                String[] parts = value.trim().split("/", 2);
                InetAddress address = InetAddress.getByName(parts[0]);
                byte[] bytes = address.getAddress();
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bytes.length * 8;
                if (prefix < 0 || prefix > bytes.length * 8) {
                    return null;
                }
                return new Cidr(bytes, prefix);
            } catch (Exception e) {
                return null;
            }
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int remaining = prefixLength;
            for (int i = 0; i < network.length; i++) {
                int bits = Math.min(8, remaining);
                if (bits <= 0) {
                    return true;
                }
                int mask = (0xFF << (8 - bits)) & 0xFF;
                if ((network[i] & mask) != (candidate[i] & mask)) {
                    return false;
                }
                remaining -= bits;
            }
            return true;
        }

        byte[] address() {
            return network.clone();
        }
    }
}
