package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
        @NotBlank(message = "Tag name is required")
        @Size(min = 1, max = 30, message = "Tag name must be between 1 and 30 characters")
        String name,

        @NotBlank(message = "Color is required")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF5733)")
        String color
) {}
