package com.sw103302.backend.dto;

import java.util.List;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        List<FieldViolation> fields
) {
    public record FieldViolation(String field, String message) {}
}
