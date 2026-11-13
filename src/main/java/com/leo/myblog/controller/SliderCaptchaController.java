package com.leo.myblog.controller;

import com.leo.myblog.common.Result;
import com.leo.myblog.entity.SliderCaptcha;
import com.leo.myblog.service.CaptchaRateLimitService;
import com.leo.myblog.service.SliderCaptchaService;
import com.leo.myblog.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 二维滑块验证码控制器（增强版）
 * 
 * 注意：前端不再调用 /api/captcha/slider/verify 接口。
 * 验证码验证改为由业务接口（如登录）内部调用 verifyCaptcha 完成。
 * 前端只需持有一个验证通过的 nonceStr 即可。
 */
@Slf4j
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class SliderCaptchaController {
    
    private final SliderCaptchaService captchaService;
    private final CaptchaRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    
    /**
     * 获取二维滑块验证码
     * GET /api/captcha/slider
     * 
     * 返回数据中不包含任何真实位置信息，前端无法推导正确缺口位置。
     * 干扰色块列表告知前端在哪些位置绘制"疑似缺口"。
     */
    @GetMapping("/slider")
    public Result<SliderCaptcha> getSliderCaptcha(HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        
        // 1. 检查速率限制
        if (!rateLimitService.isRequestAllowed(clientIp)) {
            long unlockTime = rateLimitService.getUnlockTime(clientIp);
            log.warn("验证码请求被拒绝（频率超限）: IP={}, 剩余{}秒解锁", clientIp, unlockTime);
            return Result.error(String.format("请求过于频繁，请%d秒后再试", unlockTime));
        }
        
        // 2. 记录访问日志
        int remaining = rateLimitService.getRemainingRequests(clientIp);
        log.info("验证码请求: IP={}, User-Agent={}, 剩余次数={}/30", 
                clientIp, request.getHeader("User-Agent"), remaining);
        
        // 3. 生成验证码
        try {
            SliderCaptcha captcha = captchaService.generateCaptcha();
            log.debug("验证码生成成功: IP={}, nonceStr={}", clientIp, captcha.getNonceStr());
            return Result.success(captcha);
        } catch (Exception e) {
            log.error("验证码生成失败: IP={}", clientIp, e);
            return Result.error("验证码生成失败，请稍后重试");
        }
    }
    
}
