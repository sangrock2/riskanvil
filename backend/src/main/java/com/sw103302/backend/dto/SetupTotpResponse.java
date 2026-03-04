package com.sw103302.backend.dto;

import java.util.List;

public record SetupTotpResponse(
    String secret,
    String qrCodeUrl,
    List<String> backupCodes
) {}
