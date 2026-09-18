package com.hackathon.AIDebuggerApp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AiSuggestion(
        @NotBlank @Size(max = 10000) String rootCause,
        @NotBlank @Size(max = 20000) String suggestedFix,
        @Size(max = 1000) String affectedFile,
        @Size(max = 500) String affectedClass,
        @Size(max = 500) String affectedMethod,
        @Positive Integer affectedLine,
        @Size(max = 20000) String exampleCode) {
}
