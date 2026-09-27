package com.leo.myblog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

/**
 * Jackson配置
 * <p>
 * 两件事：
 * <ol>
 *   <li>Long → String，避免 JavaScript 精度丢失</li>
 *   <li>LocalDateTime → 带 {@code Z} 的 UTC ISO 字符串，避免前端把 UTC 值当本地时间显示
 *       （见 {@link UtcLocalDateTimeSerializer}）</li>
 * </ol>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();

        // 创建模块，注册Long转String的序列化器
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        objectMapper.registerModule(simpleModule);

        // 时间字段按时区语义输出（见 UtcLocalDateTimeSerializer）。
        //
        // ⚠️ 这里**不能**用 new JavaTimeModule()：Jackson 默认开启
        // IGNORE_DUPLICATE_MODULE_REGISTRATIONS，而 builder.build() 已经注册过一个
        // JavaTimeModule 了 —— 同类型的第二个模块会被**当作重复直接忽略**，
        // 我加的序列化器一个字都不会生效（表现是时间仍输出 [2026,9,27,5,33,14] 这种数组）。
        // 换成普通 SimpleModule（不同 typeId）就不会被判重；
        // 后注册的模块在 Jackson 里优先被查询，所以它能覆盖掉 JavaTime 的默认实现。
        SimpleModule utcTimeModule = new SimpleModule("utcLocalDateTimeModule");
        utcTimeModule.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        objectMapper.registerModule(utcTimeModule);

        return objectMapper;
    }
}
