package com.leo.myblog.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 把 {@link LocalDateTime} 按 **UTC 语义**序列化成带 {@code Z} 的 ISO 字符串，
 * 例如 {@code 2026-09-27T05:33:14Z}。
 * <p>
 * 背景：容器与 JVM 都是 UTC，库里 {@code timestamp without time zone} 存的就是 UTC 值；
 * 但 {@link LocalDateTime} 序列化出来不带任何时区信息（{@code 2026-09-27T05:33:14}），
 * 而 JavaScript 的 {@code new Date("...")} 对这种**无时区**字符串是按**浏览器本地时间**解析的 ——
 * 于是 UTC+8 的用户看到的所有时间都早了 8 小时。
 * <p>
 * 补上 {@code Z} 之后，前端 {@code new Date()} 会正确地转换成本地时间，
 * 不需要改前端、也不需要动任何存量数据（这一条是关键：库里的值本来就是 UTC，只是没说）。
 * <p>
 * ⚠️ 兼容性：这个改动会让**所有**接口的时间字段多出一个 {@code Z} 后缀，语义随之变化。
 * Android App 消费同一套 API，如果它自己也在做时区处理，需要同步确认，
 * 否则 App 端可能反向偏移 8 小时。
 */
public class UtcLocalDateTimeSerializer extends StdSerializer<LocalDateTime> {

    public UtcLocalDateTimeSerializer() {
        super(LocalDateTime.class);
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        // 库里存的就是 UTC，所以直接按 UTC 解释成 Instant 即可（不做任何加减）
        gen.writeString(value.toInstant(ZoneOffset.UTC).toString());
    }
}
