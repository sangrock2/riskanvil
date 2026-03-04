package com.sw103302.backend.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateSettingsRequest(
    Boolean emailOnAlerts,
    Boolean dailySummaryEnabled,
    @Pattern(regexp = "^(?i)(dark|light)$", message = "theme must be dark or light")
    String theme,
    @Pattern(regexp = "^(?i)(ko|en)$", message = "language must be ko or en")
    String language,
    @Pattern(regexp = "^(?i)(US|KR|CRYPTO)$", message = "defaultMarket must be US, KR, or CRYPTO")
    String defaultMarket
) {}
