package com.example.gateway.dto;

import com.example.gateway.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record EventRequest(
        @NotBlank String eventId,
        @NotBlank String accountId,
        @NotNull EventType type,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant eventTimestamp,
        JsonNode metadata
) {
}
