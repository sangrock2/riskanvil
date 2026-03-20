package com.sw103302.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyTwoFactorLoginRequestValidationTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void invalidTotpCode_shouldFailValidation() {
        VerifyTwoFactorLoginRequest request = new VerifyTwoFactorLoginRequest(
                "pending-token",
                "abc123",
                null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("TOTP code must be 6 digits");
    }

    @Test
    void multipleCodeTypes_shouldFailValidation() {
        VerifyTwoFactorLoginRequest request = new VerifyTwoFactorLoginRequest(
                "pending-token",
                "123456",
                "BACKUP1234"
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Provide only one verification code type");
    }
}
