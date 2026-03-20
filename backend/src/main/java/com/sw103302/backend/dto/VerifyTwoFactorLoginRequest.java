package com.sw103302.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyTwoFactorLoginRequest(
    @NotBlank(message = "Pending token is required")
    String pendingToken,

    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
    String totpCode,

    @Pattern(regexp = "^[A-Z0-9]{6,32}$", message = "Backup code format is invalid")
    String backupCode
) {
    @AssertTrue(message = "Provide either totpCode or backupCode")
    public boolean hasVerificationCode() {
        return hasText(totpCode) || hasText(backupCode);
    }

    @AssertTrue(message = "Provide only one verification code type")
    public boolean hasSingleVerificationCodeType() {
        return !(hasText(totpCode) && hasText(backupCode));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
