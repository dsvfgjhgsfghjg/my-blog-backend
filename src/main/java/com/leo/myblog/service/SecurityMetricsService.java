package com.leo.myblog.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class SecurityMetricsService {

    private final MeterRegistry meterRegistry;

    public SecurityMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void authenticationFailure(String reason) {
        counter("security.auth.failure", "reason", reason);
    }

    public void csrfRejected() {
        counter("security.csrf.rejected", "scope", "cookie");
    }

    public void rateLimited(String scope) {
        counter("security.rate.limited", "scope", scope);
    }

    public void mfaLocked() {
        counter("security.mfa.locked", "scope", "user");
    }

    public void tokenRefresh(String result) {
        counter("security.token.refresh", "result", result);
    }

    private void counter(String name, String tagName, String tagValue) {
        Counter.builder(name)
                .tag(tagName, tagValue)
                .register(meterRegistry)
                .increment();
    }
}
