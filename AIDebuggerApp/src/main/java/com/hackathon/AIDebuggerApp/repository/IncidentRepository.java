package com.hackathon.AIDebuggerApp.repository;

import com.hackathon.AIDebuggerApp.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findForAnalysis(@Param("id") Long id);
}
