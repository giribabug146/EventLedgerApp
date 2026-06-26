package com.example.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency;

    protected Account() {
    }

    public Account(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void apply(TransactionType type, BigDecimal amount) {
        balance = type == TransactionType.CREDIT ? balance.add(amount) : balance.subtract(amount);
    }
}
