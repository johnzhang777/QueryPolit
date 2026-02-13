package com.querypilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querypilot.model.dto.QueryRequest;
import com.querypilot.model.dto.QueryResponse;
import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.model.enums.DatabaseType;
import com.querypilot.repository.DataSourceConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiQueryService {

    private static final Logger log = LoggerFactory.getLogger(AiQueryService.class);

    private final ChatClient chatClient;
    private final SqlSanitizer sqlSanitizer;
    private final DynamicConnectionFactory connectionFactory;
    private final DataSourceConfigRepository configRepository;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public AiQueryService(ChatClient.Builder chatClientBuilder,
                          SqlSanitizer sqlSanitizer,
                          DynamicConnectionFactory connectionFactory,
                          DataSourceConfigRepository configRepository,
                          PermissionService permissionService) {
        this.chatClient = chatClientBuilder.build();
        this.sqlSanitizer = sqlSanitizer;
        this.connectionFactory = connectionFactory;
        this.configRepository = configRepository;
        this.permissionService = permissionService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Orchestrates the full query pipeline:
     * 1. Permission check
     * 2. Load schema DDL
     * 3. Call DeepSeek AI
     * 4. Sanitize generated SQL
     * 5. Execute against target database
     */
    public QueryResponse processQuery(Long userId, QueryRequest request) {
        Long connectionId = request.getConnectionId();

        // Step 1: Permission check
        permissionService.validateAccess(userId, connectionId);

        // Step 2: Load connection config and schema
        DataSourceConfig config = configRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connection not found: " + connectionId));

        // Step 3: Generate SQL via DeepSeek
        String rawSql = generateSql(request.getQuestion(), config);
        log.info("AI generated SQL: {}", rawSql);

        // Step 4: Sanitize SQL (safety guardrails)
        String sanitizedSql = sqlSanitizer.sanitize(rawSql);
        log.info("Sanitized SQL: {}", sanitizedSql);

        // Step 5: Execute against target database
        JdbcTemplate jdbcTemplate = connectionFactory.getJdbcTemplate(connectionId);
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sanitizedSql);

        return new QueryResponse(sanitizedSql, result, "PASSED");
    }

    /**
     * Calls DeepSeek to convert natural language to SQL.
     */
    private String generateSql(String question, DataSourceConfig config) {
        String dialect = getDialectName(config.getType());
        String schemaDdl = config.getSchemaDdl() != null ? config.getSchemaDdl() : "-- No schema available";

        String systemPrompt = String.format("""
                You are a SQL expert. The target database is **%s**.
                You MUST generate SQL that is fully compatible with %s syntax only.
                Do NOT use syntax from other databases (e.g., no PostgreSQL INTERVAL syntax for H2/MySQL, no MySQL backticks for PostgreSQL).
                
                Here is the database schema:
                %s
                
                Rules:
                1. Generate a single executable SQL query for the user's question.
                2. Do NOT allow destructive actions (no DELETE, UPDATE, INSERT, DROP, ALTER, TRUNCATE).
                3. Only generate SELECT statements.
                4. Use ONLY functions and syntax supported by %s.
                
                Output ONLY a JSON object in this exact format, with no additional text:
                {"sql": "YOUR_SQL_QUERY_HERE"}
                """, dialect, dialect, schemaDdl, dialect);

        String userPrompt = "Question: " + question;

        log.debug("System prompt: {}", systemPrompt);
        log.debug("User prompt: {}", userPrompt);

        try {
            String response = chatClient.prompt(
                    new Prompt(List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userPrompt)
                    ))
            ).call().content();

            log.debug("AI raw response: {}", response);
            return extractSqlFromResponse(response);
        } catch (Exception e) {
            log.error("Failed to generate SQL from AI: {}", e.getMessage());
            throw new RuntimeException(
                    "Could not generate SQL. Please try rephrasing your question. Error: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the SQL from the AI JSON response.
     * Handles both clean JSON and markdown-wrapped JSON.
     */
    private String extractSqlFromResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("AI returned an empty response. Please try again.");
        }

        // Strip markdown code fences if present
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has("sql")) {
                String sql = node.get("sql").asText();
                if (sql != null && !sql.isBlank()) {
                    return sql;
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Could not parse AI response as JSON, attempting raw extraction: {}", e.getMessage());
        }

        // Fallback: try to find SQL directly in the response
        if (cleaned.toUpperCase().stripLeading().startsWith("SELECT")) {
            return cleaned;
        }

        throw new RuntimeException(
                "Could not extract SQL from AI response. Please try rephrasing your question.");
    }

    private String getDialectName(DatabaseType type) {
        return switch (type) {
            case MYSQL -> "MySQL";
            case POSTGRESQL -> "PostgreSQL";
            case H2 -> "H2 Database (use DATEADD/DATEDIFF for date math, not INTERVAL)";
        };
    }
}
