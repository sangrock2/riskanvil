package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DisableTotpRequest(
    @NotBlank(message = "Password is required")
    String password,

    @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    String totpCode
) {}
