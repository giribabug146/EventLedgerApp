package com.example.gateway.dto;

import com.example.gateway.domain.EventType;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionResponse(
        String eventId,
        String accountId,
        EventType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        BigDecimal balance
) {
}
