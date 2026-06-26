package com.example.account.controller;

import com.example.account.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> validation(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(MDC.get("traceId"), status.value(), error, message, Instant.now()));
    }
}
