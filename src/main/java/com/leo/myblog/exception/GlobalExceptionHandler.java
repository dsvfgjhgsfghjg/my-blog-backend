package com.leo.myblog.exception;

import com.leo.myblog.common.Result;
import com.leo.myblog.dto.response.CommentResponse;
import com.leo.myblog.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLException;
import java.util.Collections;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        
        // 根据错误码判断日志级别
        if (e.getCode() >= 400 && e.getCode() < 500) {
            log.warn("业务异常 [{}] [{}]: {}", e.getCode(), requestId, e.getMessage());
        } else {
            log.error("业务异常 [{}] [{}]: {}", e.getCode(), requestId, e.getMessage());
        }
        
        // 根据异常类型判断返回的data类型
        // 对于评论相关接口，返回空的CommentResponse对象
        if (e.getMessage().contains("评论")) {
            CommentResponse emptyResponse = new CommentResponse();
            emptyResponse.setReplies(Collections.emptyList());
            return Result.errorWithDefault(e.getCode(), e.getMessage(), emptyResponse);
        }
        
        return Result.error(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数校验异常
     * 安全修复V6: 不暴露具体字段名，仅返回通用错误消息
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<?> handleValidationException(Exception e) {
        log.warn("参数校验异常：{}", e.getMessage());
        return Result.error(400, "请求参数无效");
    }
    
    /**
     * 处理缺少请求参数异常
     * 安全修复V6: 不暴露参数名
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少必需参数");
    }
    
    /**
     * 处理路径变量缺失异常
     * 安全修复V6: 不暴露参数名
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Result<?> handleMissingPathVariableException(MissingPathVariableException e) {
        log.warn("缺少路径参数: {}", e.getVariableName());
        return Result.error(400, "缺少路径参数");
    }
    
    /**
     * 处理参数类型不匹配异常
     * 安全修复V6: 不暴露参数名和类型
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName());
        return Result.error(400, "参数类型错误");
    }
    
    /**
     * 处理请求体解析异常（JSON格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求数据格式错误，请检查JSON格式");
    }
    
    /**
     * 处理重复键异常（数据库唯一约束冲突）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据重复: {}", e.getMessage());
        return Result.error(409, "数据已存在，请勿重复提交");
    }
    
    /**
     * 处理数据库访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<?> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常", e);
        return Result.error(500, "数据库操作失败，请稍后重试");
    }
    
    /**
     * 处理SQL异常
     */
    @ExceptionHandler(SQLException.class)
    public Result<?> handleSQLException(SQLException e) {
        log.error("SQL异常: SQLState={}, ErrorCode={}", e.getSQLState(), e.getErrorCode(), e);
        return Result.error(500, "数据库操作异常，请稍后重试");
    }
    
    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超限");
        return Result.error(413, "文件大小超过限制");
    }
    
    /**
     * 处理HTTP方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的HTTP方法: {}", e.getMethod());
        return Result.error(405, String.format("不支持的请求方法: %s", e.getMethod()));
    }
    
    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.debug("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(404, "接口不存在");
    }
    
    /**
     * 处理权限拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝: {}", e.getMessage());
        return Result.error(403, "无权访问该资源");
    }
    
    /**
     * 处理其他异常
     * 安全修复V6/V7: 生产环境统一返回通用错误，不暴露Java类名/参数名
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        log.error("系统异常 [{}]", requestId, e);  // 已经包含堆栈信息
        // 安全修复V6/V7: 返回通用错误消息，不暴露内部实现细节
        return Result.error("系统繁忙，请稍后重试");
    }
}
