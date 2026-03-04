package com.sw103302.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaperAccountResponse(
    Long id,
    String market,
    BigDecimal balance,
    BigDecimal initialBalance,
    String currency,
    List<PaperPositionResponse> positions
) {}
