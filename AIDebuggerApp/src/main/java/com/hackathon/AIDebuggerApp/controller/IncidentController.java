package com.hackathon.AIDebuggerApp.controller;

import com.hackathon.AIDebuggerApp.dto.CreateIncidentRequest;
import com.hackathon.AIDebuggerApp.dto.IncidentResponse;
import com.hackathon.AIDebuggerApp.dto.IncidentDetails;
import com.hackathon.AIDebuggerApp.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request) {
        return incidentService.create(request);
    }

    @GetMapping("/{id:\\d+}")
    public IncidentDetails get(@PathVariable Long id) {
        return incidentService.get(id);
    }
    @GetMapping("/")
    public List<IncidentDetails> get(){
        return incidentService.getAll();

    }
}
