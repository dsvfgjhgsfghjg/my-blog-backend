package com.leo.myblog.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求ID过滤器
 * 为每个请求生成唯一的追踪ID，便于日志追踪和问题排查
 */
@Slf4j
@Component
@Order(1)
public class RequestIdFilter implements Filter {
    
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // 从请求头获取Request ID，如果没有则生成新的
        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        
        // 将Request ID存入请求属性，供后续使用
        httpRequest.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        
        // 使用MDC（Mapped Diagnostic Context）将Request ID加入日志上下文
        org.slf4j.MDC.put(REQUEST_ID_HEADER, requestId);
        
        try {
            log.debug("请求开始: {} {} [{}]", 
                    httpRequest.getMethod(), 
                    httpRequest.getRequestURI(),
                    requestId);
            
            long startTime = System.currentTimeMillis();
            
            // 执行后续过滤器和业务逻辑
            chain.doFilter(request, response);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("请求完成: {} {} [{}] - {}ms", 
                    httpRequest.getMethod(), 
                    httpRequest.getRequestURI(),
                    requestId,
                    duration);
            
        } finally {
            // 清理MDC，防止内存泄漏
            org.slf4j.MDC.remove(REQUEST_ID_HEADER);
        }
    }
}
