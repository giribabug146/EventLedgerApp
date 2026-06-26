package com.example.gateway.observability;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.MDC;

public final class StructuredLogger {
    private static final String SERVICE = "gateway-service";

    private StructuredLogger() {
    }

    public static void info(Logger logger, String eventId, String message) {
        logger.info(json("INFO", eventId, message));
    }

    public static void warn(Logger logger, String eventId, String message) {
        logger.warn(json("WARN", eventId, message));
    }

    private static String json(String level, String eventId, String message) {
        return """
                {"service":"%s","traceId":"%s","eventId":"%s","timestamp":"%s","level":"%s","message":"%s"}"""
                .formatted(SERVICE, value(MDC.get("traceId")), value(eventId), Instant.now(), level, escape(message));
    }

    private static String value(String value) {
        return value == null ? "" : escape(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
