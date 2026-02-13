package com.querypilot.controller;

import com.querypilot.model.dto.QueryRequest;
import com.querypilot.model.dto.QueryResponse;
import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.model.entity.Permission;
import com.querypilot.model.entity.User;
import com.querypilot.model.enums.UserRole;
import com.querypilot.repository.DataSourceConfigRepository;
import com.querypilot.repository.PermissionRepository;
import com.querypilot.repository.UserRepository;
import com.querypilot.service.AiQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    private final AiQueryService aiQueryService;
    private final PermissionRepository permissionRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final UserRepository userRepository;

    public QueryController(AiQueryService aiQueryService,
                           PermissionRepository permissionRepository,
                           DataSourceConfigRepository dataSourceConfigRepository,
                           UserRepository userRepository) {
        this.aiQueryService = aiQueryService;
        this.permissionRepository = permissionRepository;
        this.dataSourceConfigRepository = dataSourceConfigRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns the list of database connections the current user has access to.
     * ADMINs see all connections; ANALYSTs see only their permitted connections.
     */
    @GetMapping("/connections")
    public ResponseEntity<List<DataSourceConfig>> getMyConnections(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<DataSourceConfig> connections;
        if (user.getRole() == UserRole.ADMIN) {
            connections = dataSourceConfigRepository.findAll();
        } else {
            List<Long> connectionIds = permissionRepository.findByUserId(userId).stream()
                    .map(Permission::getConnectionId)
                    .toList();
            connections = dataSourceConfigRepository.findAllById(connectionIds);
        }
        return ResponseEntity.ok(connections);
    }

    /**
     * Main endpoint: Natural language to SQL query execution.
     *
     * The authentication token's credentials hold the userId (set in JwtAuthenticationFilter).
     */
    @PostMapping("/ask")
    public ResponseEntity<QueryResponse> askQuery(
            @Valid @RequestBody QueryRequest request,
            Authentication authentication) {

        // userId is stored as credentials in the JwtAuthenticationFilter
        Long userId = (Long) authentication.getCredentials();

        QueryResponse response = aiQueryService.processQuery(userId, request);
        return ResponseEntity.ok(response);
    }
}
