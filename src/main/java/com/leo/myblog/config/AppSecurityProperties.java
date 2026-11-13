package com.leo.myblog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-level security settings that are safe to keep in version control.
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private Cors cors = new Cors();
    private List<String> trustedProxyCidrs = new ArrayList<>();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
