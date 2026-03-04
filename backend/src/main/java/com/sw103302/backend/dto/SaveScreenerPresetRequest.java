package com.sw103302.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveScreenerPresetRequest(
    @NotBlank
    @Size(max = 100)
    String name,

    @Size(max = 500)
    String description,

    @NotNull
    ScreenerRequest.ScreenerFilters filters,

    Boolean isPublic
) {}
