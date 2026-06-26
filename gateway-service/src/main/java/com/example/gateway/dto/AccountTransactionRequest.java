package com.example.gateway.dto;

import com.example.gateway.domain.EventType;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionRequest(
        String eventId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp
) {
}
