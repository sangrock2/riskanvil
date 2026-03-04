package com.sw103302.backend.util;

import com.sw103302.backend.component.AiClientException;
import com.sw103302.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_CODE_HEADER = "X-Error-Code";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiErrorResponse.FieldViolation> fields = new ArrayList<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.add(new ApiErrorResponse.FieldViolation(fe.getField(), fe.getDefaultMessage()));
        }

        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Invalid request", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest req) {
        List<ApiErrorResponse.FieldViolation> fields = new ArrayList<>();
        e.getConstraintViolations().forEach(v ->
                fields.add(new ApiErrorResponse.FieldViolation(
                        String.valueOf(v.getPropertyPath()),
                        v.getMessage()
                ))
        );

        return build(HttpStatus.BAD_REQUEST, ErrorCode.CONSTRAINT_VIOLATION, "Invalid request", req, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleBadJson(HttpMessageNotReadableException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_JSON, "Request body is not readable JSON", req, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
        String msg = (e.getMessage() == null || e.getMessage().isBlank()) ? "bad_request" : e.getMessage();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, msg, req, null);
    }

    @ExceptionHandler(SecurityException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleSecurity(SecurityException e, HttpServletRequest req) {
        String msg = (e.getMessage() == null || e.getMessage().isBlank()) ? "forbidden" : e.getMessage();
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, msg, req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e, HttpServletRequest req) {
        String msg = (e.getMessage() == null) ? "illegal_state" : e.getMessage();

        // ✅ 너 코드에서 자주 쓰는 메시지 기반으로 상태코드만 정리 (기능 변화 최소)
        if ("unauthenticated".equalsIgnoreCase(msg)) {
            return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication required", req, null);
        }
        if ("user not found".equalsIgnoreCase(msg)) {
            return build(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found", req, null);
        }

        return build(HttpStatus.BAD_REQUEST, ErrorCode.ILLEGAL_STATE, msg, req, null);
    }

    @ExceptionHandler(AiClientException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleAiClient(AiClientException e, HttpServletRequest req) {
        // 운영 안전: 내부 메시지를 그대로 노출하지 않으려면 여기 msg를 고정해도 됨
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.AI_BAD_GATEWAY, "AI server error", req, null);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleWebClient(WebClientResponseException e, HttpServletRequest req) {
        // 정책 선택:
        // - 디버그 편의: body를 message로 내려줌(정보 노출 가능성)
        // - 운영 안전: 고정 메시지만 내려줌
        String body = e.getResponseBodyAsString();
        String msg = (body == null || body.isBlank()) ? "AI server error" : body;

        int status = e.getStatusCode().value();
        HttpStatus hs;
        try {
            hs = HttpStatus.valueOf(status);
        } catch (Exception ignore) {
            hs = HttpStatus.BAD_GATEWAY;
        }

        return build(hs, ErrorCode.AI_HTTP_ERROR, msg, req, null);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleWebClientRequest(WebClientRequestException e, HttpServletRequest req) {
        return build(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.AI_TIMEOUT, "AI server timeout", req, null);
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ApiErrorResponse> handleAny(Exception e, HttpServletRequest req) {
        // ✅ 운영 안전: 내부 예외 메시지를 그대로 노출하지 않음
        log.error("Unhandled exception. path={} requestId={}", req.getRequestURI(), requestId(req), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal server error", req, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, ErrorCode code, String message, HttpServletRequest req, List<ApiErrorResponse.FieldViolation> fields) {
        ApiErrorResponse body = new ApiErrorResponse(
                OffsetDateTime.now().toString(),
                status.value(),
                code.value(),
                message,
                req.getRequestURI(),
                requestId(req),
                fields
        );

        return ResponseEntity.status(status)
                .header(ERROR_CODE_HEADER, code.value())
                .body(body);
    }

    private String requestId(HttpServletRequest req) {
        // 우선순위: request attribute -> MDC -> header
        Object attr = req.getAttribute("requestId");
        if (attr != null) return String.valueOf(attr);

        String mdc = MDC.get("requestId");
        if (mdc != null && !mdc.isBlank()) return mdc;

        String hdr = req.getHeader("X-Request-Id");
        return (hdr == null || hdr.isBlank()) ? null : hdr;
    }
}
