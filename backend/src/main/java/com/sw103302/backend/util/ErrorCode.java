package com.sw103302.backend.util;

public enum ErrorCode {
    VALIDATION_FAILED("validation_failed"),
    CONSTRAINT_VIOLATION("constraint_violation"),
    BAD_JSON("bad_json"),
    BAD_REQUEST("bad_request"),
    FORBIDDEN("forbidden"),
    ILLEGAL_STATE("illegal_state"),
    UNAUTHENTICATED("unauthenticated"),
    USER_NOT_FOUND("user_not_found"),
    AI_BAD_GATEWAY("ai_bad_gateway"),
    AI_HTTP_ERROR("ai_http_error"),
    AI_TIMEOUT("ai_timeout"),
    INTERNAL_ERROR("internal_error");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
