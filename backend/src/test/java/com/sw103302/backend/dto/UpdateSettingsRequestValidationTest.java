package com.sw103302.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSettingsRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validValuesPassValidation() {
        UpdateSettingsRequest req = new UpdateSettingsRequest(
                true,
                false,
                "Dark",
                "KO",
                "us"
        );

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void invalidEnumLikeValuesFailValidation() {
        UpdateSettingsRequest req = new UpdateSettingsRequest(
                true,
                false,
                "midnight",
                "jp",
                "EU"
        );

        assertThat(validator.validate(req)).hasSize(3);
    }
}
