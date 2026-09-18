package com.hackathon.AIDebuggerApp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotBlank @Size(max = 100) String applicationName,
        @NotBlank @Size(max = 500) String exceptionType,
        @JsonAlias("errorMessage") @NotBlank @Size(max = 10000) String message,
        @NotBlank @Size(max = 100000) String stackTrace,
        @Size(max = 2048) String requestPath) {
}
