package org.example.ai_agent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        
        log.error("参数验证失败: {}", ex.getMessage(), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", 400);
        error.put("message", ex.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        
        return Mono.just(ResponseEntity.badRequest().body(error));
    }
    
    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, ServerWebExchange exchange) {
        
        log.error("文件上传大小超限: {}", ex.getMessage(), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", 400);
        error.put("message", "文件大小超过限制，最大允许50MB");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        
        return Mono.just(ResponseEntity.badRequest().body(error));
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(
            RuntimeException ex, ServerWebExchange exchange) {
        
        log.error("运行时异常: {}", ex.getMessage(), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", 500);
        error.put("message", "服务器内部错误: " + ex.getMessage());
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleException(
            Exception ex, ServerWebExchange exchange) {
        
        log.error("未处理的异常: {}", ex.getMessage(), ex);
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", 500);
        error.put("message", "服务器内部错误");
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
}