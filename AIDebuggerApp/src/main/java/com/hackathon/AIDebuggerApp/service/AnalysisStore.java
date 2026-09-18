package com.hackathon.AIDebuggerApp.service;

import com.hackathon.AIDebuggerApp.config.AiProperties;
import com.hackathon.AIDebuggerApp.dto.AiSuggestion;
import com.hackathon.AIDebuggerApp.dto.AnalysisResponse;
import com.hackathon.AIDebuggerApp.entity.AnalysisStatus;
import com.hackathon.AIDebuggerApp.entity.Incident;
import com.hackathon.AIDebuggerApp.entity.IncidentAnalysis;
import com.hackathon.AIDebuggerApp.repository.IncidentAnalysisRepository;
import com.hackathon.AIDebuggerApp.repository.IncidentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AnalysisStore {
    private final IncidentRepository incidents;
    private final IncidentAnalysisRepository analyses;
    private final AiProperties properties;

    public AnalysisStore(IncidentRepository incidents, IncidentAnalysisRepository analyses, AiProperties properties) {
        this.incidents = incidents;
        this.analyses = analyses;
        this.properties = properties;
    }

    public record AnalysisInput(Long analysisId, String exceptionType, String message, String stackTrace) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisInput start(Long incidentId) {
        // Serialize the "check then insert" across application instances, not just threads.
        Incident incident = incidents.findForAnalysis(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        if (analyses.existsByIncidentIdAndStatus(incidentId, AnalysisStatus.ANALYZING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Analysis already in progress");
        }
        IncidentAnalysis analysis = new IncidentAnalysis();
        analysis.setIncident(incident);
        analysis.setStatus(AnalysisStatus.ANALYZING);
        analysis.setModel(properties.model());
        analysis.setStartedAt(Instant.now());
        analyses.saveAndFlush(analysis);
        return new AnalysisInput(analysis.getId(), incident.getExceptionType(),
                incident.getErrorMessage(), incident.getStackTrace());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisResponse succeed(Long id, AiSuggestion suggestion) {
        IncidentAnalysis analysis = requireRunning(id);
        analysis.setRootCause(suggestion.rootCause());
        analysis.setSuggestedFix(suggestion.suggestedFix());
        analysis.setAffectedFile(suggestion.affectedFile());
        analysis.setAffectedClass(suggestion.affectedClass());
        analysis.setAffectedMethod(suggestion.affectedMethod());
        analysis.setAffectedLine(suggestion.affectedLine());
        analysis.setExampleCode(suggestion.exampleCode());
        analysis.setStatus(AnalysisStatus.SUCCEEDED);
        analysis.setCompletedAt(Instant.now());
        return AnalysisResponse.from(analysis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisResponse fail(Long id, String safeReason) {
        IncidentAnalysis analysis = requireRunning(id);
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setFailureReason(safeReason);
        analysis.setCompletedAt(Instant.now());
        return AnalysisResponse.from(analysis);
    }

    private IncidentAnalysis requireRunning(Long id) {
        IncidentAnalysis analysis = analyses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found"));
        if (analysis.getStatus() != AnalysisStatus.ANALYZING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Analysis already completed");
        }
        return analysis;
    }

    @Transactional(readOnly = true)
    public Page<AnalysisResponse> list(Long incidentId, int page, int size) {
        if (!incidents.existsById(incidentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return analyses.findByIncidentIdOrderByIdDesc(incidentId, PageRequest.of(page, size))
                .map(AnalysisResponse::from);
    }
}
