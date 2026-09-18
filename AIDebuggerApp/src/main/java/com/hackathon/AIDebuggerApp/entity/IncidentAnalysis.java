package com.hackathon.AIDebuggerApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "INCIDENT_ANALYSES", indexes = {
        @Index(name = "IX_INCIDENT_ANALYSES_INCIDENT", columnList = "INCIDENT_ID, ID")
})
public class IncidentAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "INCIDENT_ID", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "MODEL", nullable = false, length = 200)
    private String model;

    @Column(name = "STARTED_AT", nullable = false)
    private Instant startedAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Lob
    @Column(name = "ROOT_CAUSE")
    private String rootCause;

    @Lob
    @Column(name = "SUGGESTED_FIX")
    private String suggestedFix;

    @Column(name = "AFFECTED_FILE", length = 1000)
    private String affectedFile;

    @Column(name = "AFFECTED_CLASS", length = 500)
    private String affectedClass;

    @Column(name = "AFFECTED_METHOD", length = 500)
    private String affectedMethod;

    @Column(name = "AFFECTED_LINE")
    private Integer affectedLine;

    @Lob
    @Column(name = "EXAMPLE_CODE")
    private String exampleCode;
}
