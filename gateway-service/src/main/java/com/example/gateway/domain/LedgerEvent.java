package com.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_events")
public class LedgerEvent {
    @Id
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Lob
    private String metadataJson;

    protected LedgerEvent() {
    }

    public LedgerEvent(String eventId, String accountId, EventType type, BigDecimal amount, String currency,
                       Instant eventTimestamp, Instant receivedAt, EventStatus status, String metadataJson) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.receivedAt = receivedAt;
        this.status = status;
        this.metadataJson = metadataJson;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public EventType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void markApplied() {
        this.status = EventStatus.APPLIED;
    }

    public void markFailed() {
        this.status = EventStatus.FAILED;
    }
}
