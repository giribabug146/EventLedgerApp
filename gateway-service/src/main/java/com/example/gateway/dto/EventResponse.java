package com.example.gateway.dto;

import com.example.gateway.domain.EventStatus;
import com.example.gateway.domain.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record EventResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant receivedAt,
        EventStatus status,
        JsonNode metadata
) {
}
