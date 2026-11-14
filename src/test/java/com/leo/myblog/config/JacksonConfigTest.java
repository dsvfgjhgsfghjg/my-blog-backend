package com.leo.myblog.config;

import com.leo.myblog.dto.response.DashboardResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间与 ID 的序列化契约
 * <p>
 * 这两条都是「前端静默出错、后端看不出异常」的类型，必须有测试钉住：
 * <ul>
 *   <li>Long 不加引号会被 JS 截断精度（雪花 ID 超 2^53）</li>
 *   <li>LocalDateTime 不带时区后缀会被 JS 当成本地时间，UTC+8 下全站时间早 8 小时</li>
 * </ul>
 * 纯单测，不起 Spring 容器。
 */
class JacksonConfigTest {

    /** 复用生产同款构建路径，确保测的是真实配置而不是手搓的 ObjectMapper */
    private final ObjectMapper objectMapper = new JacksonConfig()
            .objectMapper(Jackson2ObjectMapperBuilder.json());

    @Test
    @DisplayName("LocalDateTime 带 Z 后缀输出，前端才能正确转本地时间")
    void serializesLocalDateTimeAsUtcInstant() throws Exception {
        String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 9, 27, 5, 33, 14));

        assertEquals("\"2026-09-27T05:33:14Z\"", json,
                "必须带 Z。少这个后缀，JS 的 new Date() 会按浏览器本地时区解析，"
                        + "UTC+8 下显示的时间就早 8 小时");
    }

    @Test
    @DisplayName("毫秒/纳秒不会被丢掉")
    void keepsSubSecondPrecision() throws Exception {
        String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 9, 27, 5, 33, 14, 123_000_000));

        assertTrue(json.startsWith("\"2026-09-27T05:33:14.123"), "亚秒精度应保留，实际: " + json);
        assertTrue(json.endsWith("Z\""), "后缀仍要是 Z，实际: " + json);
    }

    @Test
    @DisplayName("Long / long 序列化成字符串（雪花 ID 超出 JS 安全整数范围）")
    void serializesLongAsString() throws Exception {
        assertEquals("\"1234567890123456789\"",
                objectMapper.writeValueAsString(1234567890123456789L));
    }

    @Test
    @DisplayName("LocalDateTime 为 null 时输出 null，不炸")
    void handlesNullLocalDateTime() throws Exception {
        assertEquals("null", objectMapper.writeValueAsString((LocalDateTime) null));
    }

    @Test
    @DisplayName("App Dashboard 的手工时间字段也统一输出 Z 后缀")
    void serializesDashboardTimestampFieldsAsUtcInstant() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 11, 13, 13, 36, 2);
        DashboardResponse response = new DashboardResponse();
        response.setLastLoginAt(time);

        DashboardResponse.DashboardDeviceInfo device = new DashboardResponse.DashboardDeviceInfo();
        device.setBoundAt(time);
        response.setDevice(device);

        DashboardResponse.DashboardHistoryRecord record = new DashboardResponse.DashboardHistoryRecord();
        record.setRequestedAt(time);
        record.setRespondedAt(time);
        DashboardResponse.DashboardRecentHistory recentHistory = new DashboardResponse.DashboardRecentHistory();
        recentHistory.setRecords(java.util.List.of(record));
        response.setRecentHistory(recentHistory);

        DashboardResponse.DashboardLastAuthResult lastResult = new DashboardResponse.DashboardLastAuthResult();
        lastResult.setRequestedAt(time);
        response.setLastAuthResult(lastResult);

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"lastLoginAt\":\"2026-11-13T13:36:02Z\""), json);
        assertTrue(json.contains("\"boundAt\":\"2026-11-13T13:36:02Z\""), json);
        assertTrue(json.contains("\"requestedAt\":\"2026-11-13T13:36:02Z\""), json);
        assertTrue(json.contains("\"respondedAt\":\"2026-11-13T13:36:02Z\""), json);
    }
}
