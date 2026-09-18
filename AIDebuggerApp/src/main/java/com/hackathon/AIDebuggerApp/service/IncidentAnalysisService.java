package com.hackathon.AIDebuggerApp.service;

import com.hackathon.AIDebuggerApp.dto.AiSuggestion;
import com.hackathon.AIDebuggerApp.dto.AnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentAnalysisService {
    private final AnalysisStore store;
    private final AiSuggestionService ai;

    public IncidentAnalysisService(AnalysisStore store, AiSuggestionService ai) {
        this.store = store;
        this.ai = ai;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AnalysisResponse analyze(Long incidentId) {
        ai.requireConfigured();
        AnalysisStore.AnalysisInput input = store.start(incidentId);
        AiSuggestion suggestion;
        try {
            suggestion = ai.suggest(input.exceptionType(), input.message(), input.stackTrace());
        } catch (AiProviderException ex) {
            return store.fail(input.analysisId(), ex.getMessage());
        }
        return store.succeed(input.analysisId(), suggestion);
    }
}
