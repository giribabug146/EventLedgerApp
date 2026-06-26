package com.example.gateway.service;

import com.example.gateway.client.AccountServiceClient;
import com.example.gateway.client.AccountServiceUnavailableException;
import com.example.gateway.domain.EventStatus;
import com.example.gateway.domain.LedgerEvent;
import com.example.gateway.dto.AccountBalanceResponse;
import com.example.gateway.dto.AccountTransactionRequest;
import com.example.gateway.dto.EventRequest;
import com.example.gateway.dto.EventResponse;
import com.example.gateway.observability.StructuredLogger;
import com.example.gateway.repository.LedgerEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final LedgerEventRepository repository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
    private final Counter receivedCounter;
    private final Counter duplicateCounter;

    public EventService(LedgerEventRepository repository, AccountServiceClient accountServiceClient,
                        ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
        this.receivedCounter = Counter.builder("gateway.events.received").register(meterRegistry);
        this.duplicateCounter = Counter.builder("gateway.events.duplicates").register(meterRegistry);
    }

    @Transactional(noRollbackFor = AccountServiceUnavailableException.class)
    public EventResponse receive(EventRequest request) {
        return repository.findById(request.eventId())
                .map(existing -> {
                    duplicateCounter.increment();
                    StructuredLogger.info(log, existing.getEventId(), "duplicate event returned without reapplying");
                    return toResponse(existing);
                })
                .orElseGet(() -> receiveNew(request));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        return repository.findById(eventId).map(this::toResponse)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsForAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream().map(this::toResponse).toList();
    }

    public AccountBalanceResponse getBalance(String accountId) {
        return accountServiceClient.getBalance(accountId);
    }

    private EventResponse receiveNew(EventRequest request) {
        LedgerEvent event = new LedgerEvent(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now(),
                EventStatus.RECEIVED,
                request.metadata() == null ? null : request.metadata().toString());
        repository.saveAndFlush(event);
        receivedCounter.increment();
        StructuredLogger.info(log, event.getEventId(), "event received");
        try {
            accountServiceClient.applyTransaction(event.getAccountId(), new AccountTransactionRequest(
                    event.getEventId(), event.getType(), event.getAmount(), event.getCurrency(), event.getEventTimestamp()));
            event.markApplied();
            StructuredLogger.info(log, event.getEventId(), "event applied by account service");
        } catch (RuntimeException exception) {
            event.markFailed();
            StructuredLogger.warn(log, event.getEventId(), "event failed while calling account service");
            throw exception;
        } finally {
            repository.save(event);
        }
        return toResponse(event);
    }

    private EventResponse toResponse(LedgerEvent event) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                event.getReceivedAt(),
                event.getStatus(),
                metadata(event.getMetadataJson()));
    }

    private JsonNode metadata(String metadataJson) {
        try {
            return metadataJson == null ? null : objectMapper.readTree(metadataJson);
        } catch (Exception exception) {
            return null;
        }
    }
}
