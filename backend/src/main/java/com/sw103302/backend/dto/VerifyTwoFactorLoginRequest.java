package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyTwoFactorLoginRequest(
    @NotBlank String pendingToken,
    String totpCode,
    String backupCode
) {}
