package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.Transaction;
import com.example.account.dto.AccountBalanceResponse;
import com.example.account.dto.AccountResponse;
import com.example.account.dto.TransactionRequest;
import com.example.account.dto.TransactionResponse;
import com.example.account.observability.StructuredLogger;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Counter appliedCounter;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.appliedCounter = Counter.builder("account.transactions.applied").register(meterRegistry);
    }

    @Transactional
    public TransactionResponse applyTransaction(String accountId, TransactionRequest request) {
        return transactionRepository.findById(request.eventId())
                .map(existing -> {
                    Account account = accountRepository.findById(accountId).orElseThrow();
                    StructuredLogger.info(log, request.eventId(), "duplicate transaction ignored");
                    return toResponse(existing, account);
                })
                .orElseGet(() -> applyNewTransaction(accountId, request));
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findById(accountId).orElse(new Account(accountId, "INR"));
        return new AccountBalanceResponse(account.getAccountId(), account.getBalance(), account.getCurrency());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId).orElse(new Account(accountId, "INR"));
        List<TransactionResponse> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(transaction -> toResponse(transaction, account))
                .toList();
        return new AccountResponse(account.getAccountId(), account.getBalance(), account.getCurrency(), transactions);
    }

    private TransactionResponse applyNewTransaction(String accountId, TransactionRequest request) {
        Account account = accountRepository.findById(accountId).orElseGet(() -> new Account(accountId, request.currency()));
        Transaction transaction = new Transaction(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                Instant.now());
        account.apply(request.type(), request.amount());
        accountRepository.save(account);
        transactionRepository.save(transaction);
        appliedCounter.increment();
        StructuredLogger.info(log, request.eventId(), "transaction applied");
        return toResponse(transaction, account);
    }

    private TransactionResponse toResponse(Transaction transaction, Account account) {
        return new TransactionResponse(
                transaction.getEventId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getEventTimestamp(),
                transaction.getAppliedAt(),
                account.getBalance());
    }
}
