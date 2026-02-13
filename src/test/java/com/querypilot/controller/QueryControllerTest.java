package com.querypilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querypilot.model.dto.QueryRequest;
import com.querypilot.model.dto.QueryResponse;
import com.querypilot.security.JwtTokenProvider;
import com.querypilot.service.AiQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private AiQueryService aiQueryService;

    @Test
    @DisplayName("POST /api/v1/query/ask returns query result for authenticated user")
    void testAskQuerySuccess() throws Exception {
        String token = tokenProvider.generateToken(1L, "admin", "ADMIN");

        QueryResponse response = QueryResponse.builder()
                .sql("SELECT * FROM users LIMIT 100")
                .result(List.of(Map.of("id", 1, "name", "Alice")))
                .safetyCheck("PASSED")
                .build();

        when(aiQueryService.processQuery(eq(1L), any(QueryRequest.class))).thenReturn(response);

        QueryRequest request = new QueryRequest(5L, "Show me all users");

        mockMvc.perform(post("/api/v1/query/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT * FROM users LIMIT 100"))
                .andExpect(jsonPath("$.safetyCheck").value("PASSED"))
                .andExpect(jsonPath("$.result").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/query/ask returns 401 without token")
    void testAskQueryUnauthorized() throws Exception {
        QueryRequest request = new QueryRequest(5L, "Show me all users");

        mockMvc.perform(post("/api/v1/query/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/query/ask returns 400 for missing question")
    void testAskQueryValidation() throws Exception {
        String token = tokenProvider.generateToken(1L, "admin", "ADMIN");

        QueryRequest request = new QueryRequest(5L, ""); // blank question

        mockMvc.perform(post("/api/v1/query/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
