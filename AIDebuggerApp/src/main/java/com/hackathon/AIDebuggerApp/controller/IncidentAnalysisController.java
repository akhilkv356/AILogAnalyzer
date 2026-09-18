package com.hackathon.AIDebuggerApp.controller;

import com.hackathon.AIDebuggerApp.dto.AnalysisResponse;
import com.hackathon.AIDebuggerApp.entity.AnalysisStatus;
import com.hackathon.AIDebuggerApp.service.AnalysisStore;
import com.hackathon.AIDebuggerApp.service.IncidentAnalysisService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents/{incidentId:\\d+}")
public class IncidentAnalysisController {
    private final IncidentAnalysisService service;
    private final AnalysisStore store;

    public IncidentAnalysisController(IncidentAnalysisService service, AnalysisStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@PathVariable Long incidentId) {
        AnalysisResponse result = service.analyze(incidentId);
        return ResponseEntity.status(result.status() == AnalysisStatus.SUCCEEDED
                ? HttpStatus.CREATED : HttpStatus.BAD_GATEWAY).body(result);
    }

    @GetMapping("/analyses")
    public Page<AnalysisResponse> list(@PathVariable Long incidentId,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return store.list(incidentId, page, size);
    }
}
