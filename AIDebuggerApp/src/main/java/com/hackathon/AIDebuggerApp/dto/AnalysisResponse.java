package com.hackathon.AIDebuggerApp.dto;

import com.hackathon.AIDebuggerApp.entity.AnalysisStatus;
import com.hackathon.AIDebuggerApp.entity.IncidentAnalysis;

import java.time.Instant;

public record AnalysisResponse(
        Long id, Long incidentId, AnalysisStatus status, String model,
        Instant startedAt, Instant completedAt, String failureReason,
        AiSuggestion suggestion) {
    public static AnalysisResponse from(IncidentAnalysis analysis) {
        AiSuggestion suggestion = analysis.getStatus() == AnalysisStatus.SUCCEEDED
                ? new AiSuggestion(analysis.getRootCause(), analysis.getSuggestedFix(),
                analysis.getAffectedFile(), analysis.getAffectedClass(), analysis.getAffectedMethod(),
                analysis.getAffectedLine(), analysis.getExampleCode())
                : null;
        return new AnalysisResponse(analysis.getId(), analysis.getIncident().getId(),
                analysis.getStatus(), analysis.getModel(), analysis.getStartedAt(),
                analysis.getCompletedAt(), analysis.getFailureReason(), suggestion);
    }
}
