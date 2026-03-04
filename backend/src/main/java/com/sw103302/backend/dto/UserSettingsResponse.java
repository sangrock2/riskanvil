package com.sw103302.backend.dto;

public record UserSettingsResponse(
    Long id,
    boolean totpEnabled,
    boolean emailOnAlerts,
    boolean dailySummaryEnabled,
    String theme,
    String language,
    String defaultMarket
) {}
