package com.example.gateway.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(String accountId, BigDecimal balance, String currency) {
}
