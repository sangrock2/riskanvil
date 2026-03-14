package com.sw103302.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/generated/postgres-baseline.sql"
})
@ActiveProfiles("test")
class PostgresSchemaGenerationTest {

    @Test
    void shouldGeneratePostgresSchemaScript() {
        Path output = Path.of("build", "generated", "postgres-baseline.sql");
        assertThat(Files.exists(output)).isTrue();
        assertThat(read(output)).isNotBlank();
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read generated schema file", e);
        }
    }
}
