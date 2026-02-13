package com.querypilot.service;

import com.querypilot.exception.AccessDeniedException;
import com.querypilot.model.dto.QueryRequest;
import com.querypilot.model.dto.QueryResponse;
import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.model.enums.DatabaseType;
import com.querypilot.repository.DataSourceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiQueryServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    @Mock
    private SqlSanitizer sqlSanitizer;

    @Mock
    private DynamicConnectionFactory connectionFactory;

    @Mock
    private DataSourceConfigRepository configRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AiQueryService aiQueryService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiQueryService = new AiQueryService(chatClientBuilder, sqlSanitizer, connectionFactory,
                configRepository, permissionService);
    }

    @Test
    @DisplayName("Successful query returns result")
    void testSuccessfulQuery() {
        Long userId = 1L;
        Long connectionId = 5L;
        QueryRequest request = new QueryRequest(connectionId, "Show all users");

        DataSourceConfig config = new DataSourceConfig(connectionId, "Test DB", DatabaseType.MYSQL,
                "jdbc:mysql://localhost:3306/test", "testuser", "encrypted", "CREATE TABLE users (id INT, name VARCHAR(255));");

        String aiResponse = "{\"sql\": \"SELECT * FROM users\"}";
        String sanitizedSql = "SELECT * FROM users LIMIT 100";
        List<Map<String, Object>> queryResult = List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );

        // Mock the chain
        doNothing().when(permissionService).validateAccess(userId, connectionId);
        when(configRepository.findById(connectionId)).thenReturn(Optional.of(config));
        when(chatClient.prompt(any(Prompt.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(aiResponse);
        when(sqlSanitizer.sanitize("SELECT * FROM users")).thenReturn(sanitizedSql);
        when(connectionFactory.getJdbcTemplate(connectionId)).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForList(sanitizedSql)).thenReturn(queryResult);

        QueryResponse response = aiQueryService.processQuery(userId, request);

        assertNotNull(response);
        assertEquals(sanitizedSql, response.getSql());
        assertEquals(2, response.getResult().size());
        assertEquals("PASSED", response.getSafetyCheck());

        verify(permissionService).validateAccess(userId, connectionId);
        verify(sqlSanitizer).sanitize("SELECT * FROM users");
    }

    @Test
    @DisplayName("Access denied for unauthorized user")
    void testAccessDenied() {
        Long userId = 2L;
        Long connectionId = 5L;
        QueryRequest request = new QueryRequest(connectionId, "Show all users");

        doThrow(new AccessDeniedException("User does not have permission"))
                .when(permissionService).validateAccess(userId, connectionId);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> aiQueryService.processQuery(userId, request));
        assertTrue(ex.getMessage().contains("permission"));
    }

    @Test
    @DisplayName("Connection not found throws exception")
    void testConnectionNotFound() {
        Long userId = 1L;
        Long connectionId = 999L;
        QueryRequest request = new QueryRequest(connectionId, "Show all users");

        doNothing().when(permissionService).validateAccess(userId, connectionId);
        when(configRepository.findById(connectionId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> aiQueryService.processQuery(userId, request));
        assertTrue(ex.getMessage().contains("Connection not found"));
    }
}
