package com.example.account.controller;

import com.example.account.dto.AccountBalanceResponse;
import com.example.account.dto.AccountResponse;
import com.example.account.dto.TransactionRequest;
import com.example.account.dto.TransactionResponse;
import com.example.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse applyTransaction(@PathVariable String accountId, @Valid @RequestBody TransactionRequest request) {
        return accountService.applyTransaction(accountId, request);
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getBalance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }
}
