package com.hackathon.AIDebuggerApp;

import com.hackathon.AIDebuggerApp.repository.IncidentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentIngestionTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private IncidentRepository repository;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void acceptsMessageFieldAndDoesNotRequireAiConfiguration() throws Exception {
        mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationName":"transaction-app","exceptionType":"java.lang.IllegalStateException",
                                 "message":"Synthetic error","stackTrace":"at Example.run(Example.java:1)"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void disabledAiReturnsServiceUnavailable() throws Exception {
        mvc.perform(post("/api/incidents/1/analyze"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void storesIncidentAndReturnsCreated() throws Exception {
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationName": "transaction-app",
                                  "exceptionType": "java.lang.ArithmeticException",
                                  "errorMessage": "/ by zero",
                                  "stackTrace": "java.lang.ArithmeticException: / by zero\\n\\tat com.example.TransactionService.process(TransactionService.java:42)",
                                  "requestPath": "/transactions"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.applicationName").value("transaction-app"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findAll()).singleElement().satisfies(incident -> {
            assertThat(incident.getId()).isPositive();
           // assertThat(incident.getApplicationName()).isEqualTo("transaction-app");
            assertThat(incident.getExceptionType()).isEqualTo("java.lang.ArithmeticException");
            assertThat(incident.getErrorMessage()).isEqualTo("/ by zero");
            assertThat(incident.getStackTrace()).isEqualTo(
                    "java.lang.ArithmeticException: / by zero\n\tat com.example.TransactionService.process(TransactionService.java:42)");
            //assertThat(incident.getRequestPath()).isEqualTo("/transactions");
            //assertThat(incident.getReceivedAt()).isNotNull();
        });
    }

    @Test
    void acceptsLongStackTraceAndOptionalRequestPath() throws Exception {
        String stackTrace = "at com.example.TransactionService.process(TransactionService.java:42)".repeat(100);
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationName": "transaction-app",
                                  "exceptionType": "java.lang.IllegalStateException",
                                  "errorMessage": "Transaction failed",
                                  "stackTrace": "%s"
                                }
                                """.formatted(stackTrace)))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findAll()).singleElement().satisfies(incident -> {
            assertThat(incident.getStackTrace()).isEqualTo(stackTrace);
            //assertThat(incident.getRequestPath()).isNull();
        });
    }

    @Test
    void rejectsMissingRequiredFieldsWithoutStoringIncident() throws Exception {
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationName": " "}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsOversizedApplicationNameWithoutStoringIncident() throws Exception {
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationName": "%s",
                                  "exceptionType": "java.lang.IllegalStateException",
                                  "errorMessage": "Transaction failed",
                                  "stackTrace": "java.lang.IllegalStateException: Transaction failed"
                                }
                                """.formatted("x".repeat(101))))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsMalformedJsonWithoutStoringIncident() throws Exception {
        mvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void doesNotExposePullIngestionEndpoint() throws Exception {
        mvc.perform(get("/api/incidents/fetch"))
                .andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }
}
