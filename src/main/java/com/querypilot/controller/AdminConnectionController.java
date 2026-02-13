package com.querypilot.controller;

import com.querypilot.model.dto.ConnectionRequest;
import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.service.ConnectionManagerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/connections")
public class AdminConnectionController {

    private final ConnectionManagerService connectionManagerService;

    public AdminConnectionController(ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }

    @PostMapping
    public ResponseEntity<DataSourceConfig> addConnection(@Valid @RequestBody ConnectionRequest request) {
        DataSourceConfig config = connectionManagerService.addConnection(request);
        return ResponseEntity.ok(config);
    }

    @GetMapping
    public ResponseEntity<List<DataSourceConfig>> listConnections() {
        return ResponseEntity.ok(connectionManagerService.listConnections());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceConfig> getConnection(@PathVariable Long id) {
        return ResponseEntity.ok(connectionManagerService.getConnection(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable Long id) {
        connectionManagerService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh-schema")
    public ResponseEntity<DataSourceConfig> refreshSchema(@PathVariable Long id) {
        return ResponseEntity.ok(connectionManagerService.refreshSchema(id));
    }
}
