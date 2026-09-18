package com.hackathon.AIDebuggerApp.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.hackathon.AIDebuggerApp.dto.CreateIncidentRequest;
import com.hackathon.AIDebuggerApp.dto.IncidentResponse;
import com.hackathon.AIDebuggerApp.dto.IncidentDetails;
import com.hackathon.AIDebuggerApp.entity.Incident;
import com.hackathon.AIDebuggerApp.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public IncidentResponse create(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setApplicationName(request.applicationName());
        incident.setExceptionType(request.exceptionType());
        incident.setErrorMessage(request.message());
        incident.setStackTrace(request.stackTrace());
        incident.setRequestPath(request.requestPath());
        incident.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        Incident saved = incidentRepository.save(incident);
        return new IncidentResponse(saved.getId(), saved.getApplicationName(), saved.getReceivedAt());
    }
//testing
    @Transactional(readOnly = true)
    public IncidentDetails get(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        return new IncidentDetails(incident.getId(), incident.getApplicationName(),
                incident.getExceptionType(), incident.getErrorMessage(), incident.getStackTrace(),
                incident.getRequestPath(), incident.getReceivedAt());
    }

    public List<IncidentDetails> getAll() {
        return incidentRepository.findAll().stream()
                .map(incident -> new IncidentDetails(incident.getId(), incident.getApplicationName(),
                        incident.getExceptionType(), incident.getErrorMessage(), incident.getStackTrace(),
                        incident.getRequestPath(), incident.getReceivedAt()))
                .toList();
    }
}
