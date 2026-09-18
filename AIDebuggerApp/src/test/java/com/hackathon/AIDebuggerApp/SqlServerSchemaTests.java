package com.hackathon.AIDebuggerApp;

import com.hackathon.AIDebuggerApp.entity.Incident;
import com.hackathon.AIDebuggerApp.entity.IncidentAnalysis;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerSchemaTests {
    @Test
    void generatesTwoTablesAndLargeTextColumnsForSqlServerWithoutConnecting() {
        StringWriter script = new StringWriter();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script)
                .build();
        try {
            try (var factory = new MetadataSources(registry)
                    .addAnnotatedClass(Incident.class)
                    .addAnnotatedClass(IncidentAnalysis.class)
                    .buildMetadata().buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();
                assertThat(script.toString())
                        .contains("DEBUG_INCIDENTS", "INCIDENT_ANALYSES",
                                "ERROR_MESSAGE varchar(max)", "STACK_TRACE varchar(max)",
                                "ROOT_CAUSE varchar(max)", "SUGGESTED_FIX varchar(max)",
                                "STARTED_AT datetimeoffset(7)", "COMPLETED_AT datetimeoffset(7)",
                                "foreign key (INCIDENT_ID)");
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
