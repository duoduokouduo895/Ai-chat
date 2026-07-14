package org.example.ai_agent.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(
            IllegalArgumentException ex, ServerWebExchange exchange) {

        log.warn("请求参数不合法: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(400, "请求参数不合法", exchange, "BAD_REQUEST")));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数校验失败");
        log.warn("请求体校验失败: {}", message);
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, message, exchange, "VALIDATION_ERROR")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, ServerWebExchange exchange) {

        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "请求参数校验失败")
                .orElse("请求参数校验失败");
        log.warn("方法参数校验失败: {}", message);
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, message, exchange, "VALIDATION_ERROR")));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConstraintViolationException(
            ConstraintViolationException ex, ServerWebExchange exchange) {

        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage() != null
                        ? violation.getMessage()
                        : "请求参数校验失败")
                .orElse("请求参数校验失败");
        log.warn("方法级约束校验失败: {}", message);
        return Mono.just(ResponseEntity.badRequest()
                .body(buildError(400, message, exchange, "VALIDATION_ERROR")));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {

        log.warn("请求输入无效: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, "请求参数格式不正确", exchange, "INVALID_INPUT")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, ServerWebExchange exchange) {

        log.warn("上传文件超出大小限制: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, "上传文件超过大小限制", exchange, "FILE_TOO_LARGE")));
    }

    @ExceptionHandler(SecurityException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSecurityException(
            SecurityException ex, ServerWebExchange exchange) {

        log.warn("请求被拒绝: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(403, "无权执行此操作", exchange, "FORBIDDEN")));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(
            RuntimeException ex, ServerWebExchange exchange) {

        log.error("运行时异常: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(500, "服务器内部错误，请稍后重试", exchange, "INTERNAL_ERROR")));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleException(
            Exception ex, ServerWebExchange exchange) {

        log.error("未处理异常: errorType={}, path={}",
                ex.getClass().getSimpleName(), exchange.getRequest().getPath().value());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(500, "服务器内部错误，请稍后重试", exchange, "INTERNAL_ERROR")));
    }

    private Map<String, Object> buildError(int code, String message, ServerWebExchange exchange, String errorCode) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("errorCode", errorCode);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        return error;
    }
}
