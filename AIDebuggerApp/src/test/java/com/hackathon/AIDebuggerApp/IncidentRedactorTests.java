package com.hackathon.AIDebuggerApp;

import com.hackathon.AIDebuggerApp.service.IncidentRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentRedactorTests {
    private final IncidentRedactor redactor = new IncidentRedactor();

    @Test
    void masksCommonCredentialsAndEmailWithoutRemovingStackFrames() {
        String result = redactor.redact("""
                Authorization: Bearer synthetic-token
                password=synthetic-password; api_key="synthetic-key"
                {"password": "synthetic json password"}
                contact=synthetic@example.com
                https://user:synthetic-password@example.com/path
                -----BEGIN PRIVATE KEY-----
                synthetic-key-material
                -----END PRIVATE KEY-----
                at com.example.TransactionService.run(TransactionService.java:42)
                """);
        assertThat(result).doesNotContain("synthetic")
                .contains("TransactionService.java:42", "[REDACTED]");
    }
}
