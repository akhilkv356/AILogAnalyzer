package com.hackathon.AIDebuggerApp.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        @NotNull URI responsesUrl,
        @NotBlank @Size(max = 200) String model,
        String apiKey,
        @Min(1) @Max(60) int connectTimeoutSeconds,
        @Min(1) @Max(300) int readTimeoutSeconds,
        @Min(1000) @Max(100000) int maxInputChars,
        @Min(100) @Max(16000) int maxOutputTokens) {
}
