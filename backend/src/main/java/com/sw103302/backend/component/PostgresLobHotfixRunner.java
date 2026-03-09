package com.sw103302.backend.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PostgresLobHotfixRunner {
    private static final Logger log = LoggerFactory.getLogger(PostgresLobHotfixRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment env;

    public PostgresLobHotfixRunner(JdbcTemplate jdbcTemplate, Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (!isEnabled()) {
            return;
        }
        if (!isPostgres()) {
            return;
        }

        convertOidLobColumnToText("market_cache", "insights_json");
        convertOidLobColumnToText("market_cache", "report_text");
        convertOidLobColumnToText("market_report_history", "report_text");
    }

    private boolean isEnabled() {
        String enabled = env.getProperty("app.db.lob-hotfix.enabled", "true");
        return "true".equalsIgnoreCase(enabled);
    }

    private boolean isPostgres() {
        String url = env.getProperty("spring.datasource.url", "");
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    private void convertOidLobColumnToText(String tableName, String columnName) {
        ColumnInfo column = getColumnInfo(tableName, columnName);
        if (column == null) {
            return;
        }

        if (!"oid".equalsIgnoreCase(column.udtName())) {
            return;
        }

        String fqTable = column.tableSchema().isBlank()
                ? quoteIdent(tableName)
                : quoteIdent(column.tableSchema()) + "." + quoteIdent(tableName);
        String quotedColumn = quoteIdent(columnName);

        String primaryAlter = "ALTER TABLE " + fqTable
                + " ALTER COLUMN " + quotedColumn
                + " TYPE text USING CASE WHEN " + quotedColumn + " IS NULL THEN NULL ELSE convert_from(lo_get("
                + quotedColumn + "), 'UTF8') END";

        try {
            jdbcTemplate.execute(primaryAlter);
            log.warn("Applied Postgres LOB hotfix: {}.{} (oid -> text with lo_get)", tableName, columnName);
            return;
        } catch (DataAccessException primaryEx) {
            log.warn(
                    "Primary LOB conversion failed for {}.{} ({}). Retrying with cast fallback.",
                    tableName,
                    columnName,
                    primaryEx.getMostSpecificCause() != null ? primaryEx.getMostSpecificCause().getMessage() : primaryEx.getMessage()
            );
        }

        String fallbackAlter = "ALTER TABLE " + fqTable
                + " ALTER COLUMN " + quotedColumn
                + " TYPE text USING " + quotedColumn + "::text";

        try {
            jdbcTemplate.execute(fallbackAlter);
            log.warn("Applied Postgres LOB hotfix fallback: {}.{} (oid -> text using cast)", tableName, columnName);
        } catch (DataAccessException fallbackEx) {
            log.error(
                    "Postgres LOB hotfix failed for {}.{}: {}",
                    tableName,
                    columnName,
                    fallbackEx.getMostSpecificCause() != null ? fallbackEx.getMostSpecificCause().getMessage() : fallbackEx.getMessage()
            );
        }
    }

    private ColumnInfo getColumnInfo(String tableName, String columnName) {
        String sql = """
                SELECT table_schema, data_type, udt_name
                FROM information_schema.columns
                WHERE table_name = ?
                  AND column_name = ?
                  AND table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY CASE WHEN table_schema = current_schema() THEN 0 ELSE 1 END
                LIMIT 1
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableName, columnName);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        String tableSchema = safeToString(row.get("table_schema"));
        String udtName = safeToString(row.get("udt_name"));

        return new ColumnInfo(tableSchema, udtName);
    }

    private static String safeToString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private record ColumnInfo(String tableSchema, String udtName) {}
}
