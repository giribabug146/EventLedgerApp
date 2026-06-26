package com.example.gateway.client;

import com.example.gateway.dto.AccountBalanceResponse;
import com.example.gateway.dto.AccountTransactionRequest;
import com.example.gateway.dto.AccountTransactionResponse;
import com.example.gateway.observability.TraceFilter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AccountServiceClient(RestTemplateBuilder builder, @Value("${account-service.base-url}") String baseUrl) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(2))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    @Retry(name = "accountService")
    public AccountTransactionResponse applyTransaction(String accountId, AccountTransactionRequest request) {
        return restTemplate.postForObject(
                baseUrl + "/accounts/{accountId}/transactions",
                withTrace(request),
                AccountTransactionResponse.class,
                accountId);
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "balanceFallback")
    @Retry(name = "accountService")
    public AccountBalanceResponse getBalance(String accountId) {
        return restTemplate.exchange(
                baseUrl + "/accounts/{accountId}/balance",
                org.springframework.http.HttpMethod.GET,
                withTrace(null),
                AccountBalanceResponse.class,
                accountId).getBody();
    }

    private org.springframework.http.HttpEntity<?> withTrace(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceFilter.TRACE_HEADER, MDC.get("traceId"));
        return new org.springframework.http.HttpEntity<>(body, headers);
    }

    AccountTransactionResponse applyTransactionFallback(String accountId, AccountTransactionRequest request, Throwable throwable) {
        throw new AccountServiceUnavailableException("Account Service is unavailable; event was stored but not applied", unwrap(throwable));
    }

    AccountBalanceResponse balanceFallback(String accountId, Throwable throwable) {
        throw new AccountServiceUnavailableException("Account Service is unavailable; balance cannot be refreshed right now", unwrap(throwable));
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable instanceof RestClientException ? throwable : throwable.getCause() == null ? throwable : throwable.getCause();
    }
}
