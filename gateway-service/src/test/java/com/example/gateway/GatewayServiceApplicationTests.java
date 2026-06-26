package com.example.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway.repository.LedgerEventRepository;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "resilience4j.retry.instances.accountService.max-attempts=1",
        "resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls=10"
})
@AutoConfigureMockMvc
class GatewayServiceApplicationTests {
    private static MockWebServer accountService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerEventRepository repository;

    @BeforeAll
    static void startServer() throws IOException {
        accountService = new MockWebServer();
        accountService.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        accountService.shutdown();
    }

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> accountService.url("/").toString().replaceAll("/$", ""));
    }

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void validCreditEventUpdatesBalance() throws Exception {
        accountService.enqueue(jsonResponse("""
                {"eventId":"evt-credit","accountId":"sbi-001","type":"CREDIT","amount":10000,"currency":"INR","eventTimestamp":"2026-05-15T09:00:00Z","appliedAt":"2026-05-15T09:00:01Z","balance":10000}"""));
        accountService.enqueue(jsonResponse("""
                {"accountId":"sbi-001","balance":10000,"currency":"INR"}"""));

        mockMvc.perform(post("/events")
                        .header("X-Trace-Id", "trace-credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-credit", "sbi-001", "CREDIT", "10000", "2026-05-15T09:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-credit"))
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/accounts/sbi-001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10000));
    }

    @Test
    void validDebitEventUpdatesBalance() throws Exception {
        accountService.enqueue(jsonResponse("""
                {"eventId":"evt-debit","accountId":"sbi-002","type":"DEBIT","amount":2000,"currency":"INR","eventTimestamp":"2026-05-15T10:00:00Z","appliedAt":"2026-05-15T10:00:01Z","balance":8000}"""));
        accountService.enqueue(jsonResponse("""
                {"accountId":"sbi-002","balance":8000,"currency":"INR"}"""));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-debit", "sbi-002", "DEBIT", "2000", "2026-05-15T10:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/accounts/sbi-002/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(8000));
    }

    @Test
    void duplicateEventIdDoesNotCallAccountServiceTwice() throws Exception {
        int requestsBefore = accountService.getRequestCount();
        accountService.enqueue(jsonResponse("""
                {"eventId":"evt-dup","accountId":"sbi-003","type":"CREDIT","amount":10000,"currency":"INR","eventTimestamp":"2026-05-15T09:00:00Z","appliedAt":"2026-05-15T09:00:01Z","balance":10000}"""));

        String body = event("evt-dup", "sbi-003", "CREDIT", "10000", "2026-05-15T09:00:00Z");
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        assertThat(accountService.getRequestCount() - requestsBefore).isEqualTo(1);
    }

    @Test
    void outOfOrderEventsAreReturnedByBusinessTimestamp() throws Exception {
        accountService.enqueue(jsonResponse(accountResponse("evt-late", "CREDIT", 10000)));
        accountService.enqueue(jsonResponse(accountResponse("evt-early", "DEBIT", 2000)));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-late", "sbi-004", "CREDIT", "10000", "2026-05-15T11:00:00Z")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-early", "sbi-004", "DEBIT", "2000", "2026-05-15T08:00:00Z")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events").param("account", "sbi-004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-early"))
                .andExpect(jsonPath("$[1].eventId").value("evt-late"));
    }

    @Test
    void validationErrorForMissingFields() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-missing","type":"CREDIT","amount":100,"currency":"INR"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void validationErrorForAmountLessThanOrEqualToZero() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-zero", "sbi-005", "CREDIT", "0", "2026-05-15T09:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void accountServiceUnavailableReturns503AndGatewayReadsStillWork() throws Exception {
        accountService.enqueue(new MockResponse().setResponseCode(503).setBody("down"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-fail", "sbi-006", "CREDIT", "10000", "2026-05-15T09:00:00Z")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Account Service is unavailable; event was stored but not applied"));

        mockMvc.perform(get("/events/evt-fail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void traceIdPropagatesToAccountService() throws Exception {
        accountService.enqueue(jsonResponse(accountResponse("evt-trace", "CREDIT", 10000)));

        mockMvc.perform(post("/events")
                        .header("X-Trace-Id", "trace-from-client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-trace", "sbi-007", "CREDIT", "10000", "2026-05-15T09:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-from-client"));

        RecordedRequest request = accountService.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-Trace-Id")).isEqualTo("trace-from-client");
    }

    @Test
    void integrationStyleGatewayToAccountFlow() throws Exception {
        accountService.enqueue(jsonResponse(accountResponse("evt-salary", "CREDIT", 10000)));
        accountService.enqueue(jsonResponse(accountResponse("evt-emi", "DEBIT", 8000)));
        accountService.enqueue(jsonResponse("""
                {"accountId":"sbi-008","balance":8000,"currency":"INR"}"""));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-salary", "sbi-008", "CREDIT", "10000", "2026-05-15T09:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt-emi", "sbi-008", "DEBIT", "2000", "2026-05-15T10:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));
        mockMvc.perform(get("/accounts/sbi-008/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(8000));
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String accountResponse(String eventId, String type, int balance) {
        return """
                {"eventId":"%s","accountId":"sbi-test","type":"%s","amount":10000,"currency":"INR","eventTimestamp":"2026-05-15T09:00:00Z","appliedAt":"2026-05-15T09:00:01Z","balance":%d}"""
                .formatted(eventId, type, balance);
    }

    private static String event(String eventId, String accountId, String type, String amount, String eventTimestamp) {
        return """
                {"eventId":"%s","accountId":"%s","type":"%s","amount":%s,"currency":"INR","eventTimestamp":"%s","metadata":{"source":"SBI-demo"}}"""
                .formatted(eventId, accountId, type, amount, eventTimestamp);
    }
}
