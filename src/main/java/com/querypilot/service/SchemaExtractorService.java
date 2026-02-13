package com.querypilot.service;

import com.querypilot.model.enums.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemaExtractorService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractorService.class);

    /**
     * Extracts schema DDL from the target database using information_schema.
     * Returns a DDL-like text representation of all tables and columns.
     */
    public String extractSchema(JdbcTemplate jdbcTemplate, DatabaseType type, String url) {
        String dbName = extractDatabaseName(url, type);
        log.info("Extracting schema for database: {} (type: {})", dbName, type);

        String query = buildSchemaQuery(type);
        List<Map<String, Object>> rows;

        if (type == DatabaseType.H2) {
            rows = jdbcTemplate.queryForList(query);
        } else {
            rows = jdbcTemplate.queryForList(query, dbName);
        }

        return buildDdlFromRows(rows);
    }

    private String buildSchemaQuery(DatabaseType type) {
        return switch (type) {
            case MYSQL -> """
                    SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = ?
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """;
            case POSTGRESQL -> """
                    SELECT table_name AS TABLE_NAME, column_name AS COLUMN_NAME,
                           data_type AS DATA_TYPE, is_nullable AS IS_NULLABLE,
                           '' AS COLUMN_KEY
                    FROM information_schema.columns
                    WHERE table_catalog = ? AND table_schema = 'public'
                    ORDER BY table_name, ordinal_position
                    """;
            case H2 -> """
                    SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE,
                           '' AS COLUMN_KEY
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """;
        };
    }

    private String buildDdlFromRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "-- No tables found";
        }

        // Group columns by table
        Map<String, StringBuilder> tables = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String tableName = (String) row.get("TABLE_NAME");
            String columnName = (String) row.get("COLUMN_NAME");
            String dataType = String.valueOf(row.get("DATA_TYPE"));
            String nullable = String.valueOf(row.get("IS_NULLABLE"));
            String key = String.valueOf(row.get("COLUMN_KEY"));

            tables.computeIfAbsent(tableName, k -> new StringBuilder("CREATE TABLE " + k + " (\n"));

            StringBuilder sb = tables.get(tableName);
            if (!sb.toString().endsWith("(\n")) {
                sb.append(",\n");
            }
            sb.append("  ").append(columnName).append(" ").append(dataType);
            if ("NO".equalsIgnoreCase(nullable)) {
                sb.append(" NOT NULL");
            }
            if ("PRI".equalsIgnoreCase(key)) {
                sb.append(" PRIMARY KEY");
            }
        }

        StringBuilder ddl = new StringBuilder();
        for (Map.Entry<String, StringBuilder> entry : tables.entrySet()) {
            ddl.append(entry.getValue()).append("\n);\n\n");
        }

        return ddl.toString().trim();
    }

    private String extractDatabaseName(String url, DatabaseType type) {
        try {
            return switch (type) {
                case MYSQL -> {
                    // jdbc:mysql://host:port/dbname?params
                    String afterSlash = url.substring(url.lastIndexOf("/") + 1);
                    yield afterSlash.contains("?") ? afterSlash.substring(0, afterSlash.indexOf("?")) : afterSlash;
                }
                case POSTGRESQL -> {
                    // jdbc:postgresql://host:port/dbname?params
                    String afterSlash = url.substring(url.lastIndexOf("/") + 1);
                    yield afterSlash.contains("?") ? afterSlash.substring(0, afterSlash.indexOf("?")) : afterSlash;
                }
                case H2 -> "PUBLIC";
            };
        } catch (StringIndexOutOfBoundsException | NullPointerException e) {
            log.warn("Could not extract database name from URL: {}", url);
            return "";
        }
    }
}
