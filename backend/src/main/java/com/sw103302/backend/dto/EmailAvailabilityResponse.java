package com.sw103302.backend.dto;

public record EmailAvailabilityResponse(
        String email,
        boolean available
) {
}
