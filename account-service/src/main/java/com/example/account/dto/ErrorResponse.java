package com.example.account.dto;

import java.time.Instant;

public record ErrorResponse(String traceId, int status, String error, String message, Instant timestamp) {
}
