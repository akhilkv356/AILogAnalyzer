package com.hackathon.AIDebuggerApp;

import com.hackathon.AIDebuggerApp.dto.CreateIncidentRequest;
import com.hackathon.AIDebuggerApp.entity.AnalysisStatus;
import com.hackathon.AIDebuggerApp.repository.IncidentAnalysisRepository;
import com.hackathon.AIDebuggerApp.repository.IncidentRepository;
import com.hackathon.AIDebuggerApp.service.AnalysisStore;
import com.hackathon.AIDebuggerApp.service.IncidentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "ai.enabled=true", "ai.api-key=synthetic-test-key",
        "ai.responses-url=https://example.invalid/openai/v1/responses"
})
@ActiveProfiles("test")
class IncidentAnalysisTests {
    private static MockRestServiceServer server;
    private static final String URL = "https://example.invalid/openai/v1/responses";
    private static final String SUGGESTION = """
            {
              "rootCause": "Probable zero divisor, based on the arithmetic exception.",
              "suggestedFix": "Validate count; confirm the intended zero-count business behavior.",
              "affectedFile": "TransactionService.java",
              "affectedClass": "com.example.TransactionService",
              "affectedMethod": "calculateAverage",
              "affectedLine": 42,
              "exampleCode": null
            }
            """;

    @TestBean(methodName = "mockAiClient")
    private RestClient aiRestClient;

    static RestClient mockAiClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    @Autowired private WebApplicationContext context;
    @Autowired private IncidentService incidents;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentAnalysisRepository analyses;
    @Autowired private AnalysisStore store;
    @Autowired private ObjectMapper mapper;
    private MockMvc mvc;
    private Long incidentId;

    @BeforeEach
    void setUp() {
        server.reset();
        analyses.deleteAll();
        incidentRepository.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        incidentId = incidents.create(new CreateIncidentRequest("transaction-app",
                "java.lang.ArithmeticException", "/ by zero password=synthetic-password",
                "at com.example.TransactionService.calculateAverage(TransactionService.java:42)",
                "/transactions")).id();
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    void ingestionDoesNotCallAiAndIncidentCanBeRetrieved() throws Exception {
        assertThat(analyses.count()).isZero();
        mvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId))
                .andExpect(jsonPath("$.applicationName").value("transaction-app"));
    }

    @Test
    void persistsStructuredSuggestionSeparatelyAndSupportsRepeatedAttempts() throws Exception {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "synthetic-test-key"))
                .andExpect(request -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(analyses.findAll()).singleElement()
                            .satisfies(a -> assertThat(a.getStatus()).isEqualTo(AnalysisStatus.ANALYZING));
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode json = mapper.readTree(body);
                    assertThat(json.path("model").asText()).isEqualTo("gpt-6-astra");
                    assertThat(json.path("store").asBoolean()).isFalse();
                    assertThat(json.at("/text/format/type").asText()).isEqualTo("json_schema");
                    assertThat(body).doesNotContain("synthetic-password").contains("[REDACTED]");
                }).andRespond(withSuccess(envelope(SUGGESTION), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.suggestion.affectedLine").value(42))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertThat(incidentRepository.findById(incidentId).orElseThrow().getErrorMessage())
                .contains("synthetic-password");
        assertThat(analyses.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getRootCause()).startsWith("Probable");
            assertThat(a.getSuggestedFix()).contains("business");
        });
        server.verify();
        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope(SUGGESTION), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId)).andExpect(status().isCreated());
        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(analyses.count()).isEqualTo(2);
        mvc.perform(get("/api/incidents/{id}/analyses", incidentId).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].suggestion.affectedMethod").value("calculateAverage"));
    }

    @Test
    void failedProviderCallIsPersistedAndDoesNotExposeProviderBody() throws Exception {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("sensitive upstream diagnostic"));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("AI provider returned HTTP 429"))
                .andExpect(jsonPath("$.suggestion").doesNotExist());
        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(analyses.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AnalysisStatus.FAILED);
            assertThat(a.getCompletedAt()).isNotNull();
            assertThat(a.getFailureReason()).doesNotContain("sensitive");
        });
    }

    @Test
    void transportFailureIsPersisted() throws Exception {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("synthetic timeout")));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("AI provider request failed or timed out"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "null",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}"})
    void invalidIncompleteOrRefusedResponsesAreNotSuccess(String body) throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(analyses.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getRootCause()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]",
            "{\"rootCause\":\"cause\",\"suggestedFix\":\"fix\",\"affectedFile\":null,\"affectedClass\":null,\"affectedMethod\":null,\"affectedLine\":\"42\",\"exampleCode\":null}",
            "{\"rootCause\":\"\",\"suggestedFix\":\"fix\",\"affectedFile\":null,\"affectedClass\":null,\"affectedMethod\":null,\"affectedLine\":-1,\"exampleCode\":null}"})
    void rejectsInvalidSuggestionSchema(String suggestion) throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope(suggestion), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void rejectsDuplicateActiveAnalysisWithoutCallingProvider() throws Exception {
        store.start(incidentId);
        mvc.perform(post("/api/incidents/{id}/analyze", incidentId))
                .andExpect(status().isConflict());
        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void concurrentStartsAllowOnlyOneActiveAttempt() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt = () -> {
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent start was not released");
                }
                store.start(incidentId);
                return true;
            } catch (ResponseStatusException ex) {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                return false;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        };
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(attempt);
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(attempt);
        start.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void marksTruncatedInputAndAcceptsUnknownSourceLocations() throws Exception {
        Long largeId = incidents.create(new CreateIncidentRequest("transaction-app",
                "java.lang.IllegalStateException", "Synthetic exception",
                "x".repeat(20000), null)).id();
        server.expect(requestTo(URL)).andExpect(request -> {
            JsonNode json = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertThat(json.path("input").asText()).hasSize(16000).contains("[INPUT TRUNCATED:");
        }).andRespond(withSuccess(envelope("""
                {"rootCause":"Insufficient context.","suggestedFix":"Supply the missing source context.",
                 "affectedFile":null,"affectedClass":null,"affectedMethod":null,"affectedLine":null,"exampleCode":null}
                """), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/incidents/{id}/analyze", largeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestion.affectedLine").doesNotExist());
    }

    @Test
    void rejectsMissingIncidentAndInvalidPagination() throws Exception {
        mvc.perform(post("/api/incidents/999999/analyze")).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/999999/analyses")).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/{id}/analyses", incidentId).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents/{id}/analyses", incidentId).param("page", "-1"))
                .andExpect(status().isBadRequest());
        assertThat(analyses.count()).isZero();
    }

    private String envelope(String suggestion) {
        return mapper.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("type", "reasoning", "summary", List.of()),
                Map.of("type", "message", "content", List.of(
                        Map.of("type", "output_text", "text", suggestion))))));
    }
}
