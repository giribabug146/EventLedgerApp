package com.example.account.dto;

import com.example.account.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        BigDecimal balance
) {
}
