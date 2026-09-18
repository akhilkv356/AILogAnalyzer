package com.hackathon.AIDebuggerApp.dto;

import java.time.Instant;

public record IncidentDetails(
        Long id, String applicationName, String exceptionType, String message,
        String stackTrace, String requestPath, Instant receivedAt) {
}
