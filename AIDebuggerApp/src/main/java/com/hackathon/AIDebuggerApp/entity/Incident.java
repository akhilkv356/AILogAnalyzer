package com.hackathon.AIDebuggerApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@Entity
@Table(name = "DEBUG_INCIDENTS")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "APPLICATION_NAME", length = 100)
    private String applicationName;

    @Column(name = "EXCEPTION_TYPE", length = 500)
    private String exceptionType;

    @Column(name = "ERROR_MESSAGE", length = 10000)
    private String errorMessage;

    @Lob
    @Column(name = "STACK_TRACE")
    private String stackTrace;

    @Column(name = "REQUEST_PATH", length = 2048)
    private String requestPath;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public Instant getReceivedAt() {
        return createdAt == null ? null : createdAt.toInstant(ZoneOffset.UTC);
    }
}
