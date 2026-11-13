package com.leo.myblog.service;

import com.leo.myblog.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MfaAttemptServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void fifthFailureCreatesLock() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SecurityMetricsService metrics = mock(SecurityMetricsService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("mfa:failures:7")).thenReturn(5L);

        MfaAttemptService service = new MfaAttemptService(redis, metrics);
        service.recordFailure(7L);

        verify(values).set(eq("mfa:lock:7"), eq("locked"), eq(15L), any());
        verify(metrics).mfaLocked();
    }

    @Test
    void activeLockRejectsVerification() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityMetricsService metrics = mock(SecurityMetricsService.class);
        when(redis.hasKey("mfa:lock:7")).thenReturn(true);
        when(redis.getExpire(eq("mfa:lock:7"), any())).thenReturn(42L);

        MfaAttemptService service = new MfaAttemptService(redis, metrics);
        assertThrows(BusinessException.class, () -> service.assertAllowed(7L));
    }

    @Test
    void noLockAllowsVerification() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SecurityMetricsService metrics = mock(SecurityMetricsService.class);
        when(redis.hasKey("mfa:lock:7")).thenReturn(false);

        MfaAttemptService service = new MfaAttemptService(redis, metrics);
        assertDoesNotThrow(() -> service.assertAllowed(7L));
        verify(redis, never()).getExpire(anyString(), any());
    }
}
