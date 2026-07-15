package com.leo.myblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    /**
     * 配置路径匹配策略
     * 确保Controller的路径映射优先于静态资源
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 使用后缀模式匹配，确保 /api/users/avatar/{userId} 能正确匹配
        configurer.setUseSuffixPatternMatch(false);
    }
}
