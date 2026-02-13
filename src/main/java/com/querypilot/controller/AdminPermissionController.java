package com.querypilot.controller;

import com.querypilot.model.dto.PermissionRequest;
import com.querypilot.model.entity.Permission;
import com.querypilot.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/permissions")
public class AdminPermissionController {

    private final PermissionService permissionService;

    public AdminPermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    public ResponseEntity<Permission> grantPermission(@Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.grantPermission(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> revokePermission(@RequestParam Long userId, @RequestParam Long connectionId) {
        permissionService.revokePermission(userId, connectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Permission>> getPermissionsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionService.getPermissionsByUser(userId));
    }

    @GetMapping("/connection/{connectionId}")
    public ResponseEntity<List<Permission>> getPermissionsByConnection(@PathVariable Long connectionId) {
        return ResponseEntity.ok(permissionService.getPermissionsByConnection(connectionId));
    }
}
