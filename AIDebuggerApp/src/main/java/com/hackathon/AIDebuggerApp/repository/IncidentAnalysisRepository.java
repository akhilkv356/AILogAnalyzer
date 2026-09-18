package com.hackathon.AIDebuggerApp.repository;

import com.hackathon.AIDebuggerApp.entity.AnalysisStatus;
import com.hackathon.AIDebuggerApp.entity.IncidentAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, Long> {
    boolean existsByIncidentIdAndStatus(Long incidentId, AnalysisStatus status);
    Page<IncidentAnalysis> findByIncidentIdOrderByIdDesc(Long incidentId, Pageable pageable);
}
