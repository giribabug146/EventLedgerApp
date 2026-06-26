package com.example.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountServiceApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateTransactionDoesNotUpdateBalanceTwice() throws Exception {
        String body = """
                {"eventId":"evt-acct-dup","type":"CREDIT","amount":10000,"currency":"INR","eventTimestamp":"2026-05-15T09:00:00Z"}""";

        mockMvc.perform(post("/accounts/sbi-001/transactions")
                        .header("X-Trace-Id", "trace-account-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-account-dup"))
                .andExpect(jsonPath("$.balance").value(10000));

        mockMvc.perform(post("/accounts/sbi-001/transactions")
                        .header("X-Trace-Id", "trace-account-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(10000));
    }

    @Test
    void debitSubtractsFromBalance() throws Exception {
        mockMvc.perform(post("/accounts/sbi-002/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-acct-credit","type":"CREDIT","amount":10000,"currency":"INR","eventTimestamp":"2026-05-15T09:00:00Z"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/sbi-002/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"evt-acct-debit","type":"DEBIT","amount":2000,"currency":"INR","eventTimestamp":"2026-05-15T10:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(8000));

        mockMvc.perform(get("/accounts/sbi-002/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(8000));
    }
}
