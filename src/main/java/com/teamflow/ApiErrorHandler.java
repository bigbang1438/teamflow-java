package com.teamflow;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> application(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(error(exception.code(), exception.getMessage(), List.of()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream().map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage() == null ? "输入不合法" : e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(error("validation_error", "请检查输入内容", fields));
    }
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> parameters(Exception exception) {
        return ResponseEntity.badRequest().body(error("validation_error", "查询参数或资源编号不合法", List.of()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> json(Exception exception) {
        return ResponseEntity.badRequest().body(error("invalid_json", "请求格式、字段或日期不合法", List.of()));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(HttpRequestMethodNotSupportedException exception) {
        var builder = ResponseEntity.status(405);
        if (exception.getSupportedHttpMethods() != null) builder.allow(exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        return builder.body(error("method_not_allowed", "该资源不支持此操作", List.of()));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> media(Exception exception) {
        return ResponseEntity.status(415).body(error("unsupported_media_type", "请使用 JSON 格式提交请求", List.of()));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> notFound(Exception exception) {
        return ResponseEntity.status(404).body(error("not_found", "资源不存在", List.of()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception exception) {
        LOGGER.error("Unhandled API error", exception);
        return ResponseEntity.internalServerError().body(error("internal_error", "服务暂时无法完成操作，请稍后再试", List.of()));
    }
    private Map<String, Object> error(String code, String message, Object details) {
        return Map.of("error", Map.of("code", code, "message", message, "details", details));
    }
}
