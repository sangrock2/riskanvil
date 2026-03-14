package com.sw103302.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresFlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("stock_ai")
            .withUsername("postgres")
            .withPassword("postgres");

    @Test
    void shouldApplyPostgresBaselineAndAlignmentMigrations() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("2")
                .locations("classpath:db/migration-postgres")
                .load();

        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertColumnType(connection, "analysis_runs", "request_json", "text");
            assertColumnType(connection, "market_cache", "report_text", "text");

            assertConstraint(connection, "uk_portfolios_user_name", "u");
            assertConstraint(connection, "uk_portfolio_positions", "u");
            assertConstraint(connection, "uk_paper_accounts_user_market", "u");
            assertConstraint(connection, "uk_paper_positions_account_ticker", "u");
            assertConstraint(connection, "fk_portfolios_user", "f");
            assertConstraint(connection, "fk_portfolio_positions_portfolio", "f");
            assertCascadeDelete(connection, "fk_portfolios_user");
            assertCascadeDelete(connection, "fk_portfolio_positions_portfolio");

            assertIndex(connection, "idx_watchlist_user_test_created");
            assertIndex(connection, "idx_price_alerts_user_enabled");
            assertIndex(connection, "idx_user_id");
        }
    }

    private void assertConstraint(Connection connection, String constraintName, String type) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select contype
                from pg_constraint
                where conname = ?
                """)) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("contype")).isEqualTo(type);
            }
        }
    }

    private void assertCascadeDelete(Connection connection, String constraintName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select confdeltype
                from pg_constraint
                where conname = ?
                """)) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("confdeltype")).isEqualTo("c");
            }
        }
    }

    private void assertIndex(Connection connection, String indexName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select indexname
                from pg_indexes
                where schemaname = 'public' and indexname = ?
                """)) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("indexname")).isEqualTo(indexName);
            }
        }
    }

    private void assertColumnType(Connection connection, String tableName, String columnName, String dataType) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select data_type
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                  and column_name = ?
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("data_type")).isEqualTo(dataType);
            }
        }
    }
}
