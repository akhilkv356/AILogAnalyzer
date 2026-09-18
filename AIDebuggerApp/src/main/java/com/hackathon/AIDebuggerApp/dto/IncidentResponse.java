package com.hackathon.AIDebuggerApp.dto;

import java.time.Instant;

public record IncidentResponse(Long id, String applicationName, Instant receivedAt) {
}
