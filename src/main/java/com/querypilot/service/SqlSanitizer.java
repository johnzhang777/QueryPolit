package com.querypilot.service;

import com.querypilot.exception.SqlSafetyException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class SqlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(SqlSanitizer.class);

    // Pattern to match SQL single-line comments (-- ...)
    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("--[^\\n]*", Pattern.MULTILINE);

    // Pattern to match SQL multi-line comments (/* ... */)
    private static final Pattern MULTI_LINE_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    // Pattern to detect LIMIT clause (case-insensitive)
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+\\d+", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitizes AI-generated SQL:
     * 1. Strips comments to prevent injection hiding
     * 2. Parses into AST and verifies it is a SELECT statement
     * 3. Appends LIMIT 100 if no LIMIT is present
     *
     * @param sql the raw SQL from AI
     * @return sanitized, safe SQL
     * @throws SqlSafetyException if the SQL is not a SELECT or cannot be parsed
     */
    public String sanitize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlSafetyException("SQL cannot be empty");
        }

        log.debug("Sanitizing SQL: {}", sql);

        // Step 1: Strip comments
        String cleaned = stripComments(sql).trim();

        if (cleaned.isEmpty()) {
            throw new SqlSafetyException("SQL is empty after removing comments");
        }

        // Step 2: Parse and validate statement type
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(cleaned);
        } catch (JSQLParserException e) {
            throw new SqlSafetyException("Failed to parse SQL: " + e.getMessage(), e);
        }

        validateStatementType(statement);

        // Step 3: Ensure LIMIT is present
        String sanitized = ensureLimit(cleaned);

        log.info("SQL sanitized successfully: {}", sanitized);
        return sanitized;
    }

    /**
     * Strips single-line and multi-line SQL comments.
     */
    String stripComments(String sql) {
        String result = MULTI_LINE_COMMENT.matcher(sql).replaceAll("");
        result = SINGLE_LINE_COMMENT.matcher(result).replaceAll("");
        return result;
    }

    /**
     * Validates that the statement is a SELECT. Throws SqlSafetyException for all other types.
     */
    private void validateStatementType(Statement statement) {
        if (statement == null) {
            throw new SqlSafetyException("SQL statement is null");
        }
        if (statement instanceof Select) {
            return; // Safe
        }

        String blockedType;
        if (statement instanceof Delete) {
            blockedType = "DELETE";
        } else if (statement instanceof Update) {
            blockedType = "UPDATE";
        } else if (statement instanceof Insert) {
            blockedType = "INSERT";
        } else if (statement instanceof Drop) {
            blockedType = "DROP";
        } else if (statement instanceof Truncate) {
            blockedType = "TRUNCATE";
        } else if (statement instanceof Alter) {
            blockedType = "ALTER";
        } else if (statement instanceof Grant) {
            blockedType = "GRANT";
        } else if (statement instanceof Execute) {
            blockedType = "EXECUTE";
        } else if (statement instanceof CreateTable) {
            blockedType = "CREATE";
        } else {
            blockedType = statement.getClass().getSimpleName().toUpperCase();
        }

        throw new SqlSafetyException(
                "SQL Safety Violation: " + blockedType + " statements are not allowed. Only SELECT is permitted.");
    }

    /**
     * Appends LIMIT 100 if the SQL does not already contain a LIMIT clause.
     */
    String ensureLimit(String sql) {
        if (LIMIT_PATTERN.matcher(sql).find()) {
            return sql;
        }

        // Remove trailing semicolon if present, then append LIMIT
        String trimmed = sql.stripTrailing();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }

        return trimmed + " LIMIT 100";
    }
}
