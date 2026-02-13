package com.querypilot.service;

import com.querypilot.exception.AccessDeniedException;
import com.querypilot.model.dto.PermissionRequest;
import com.querypilot.model.entity.Permission;
import com.querypilot.model.entity.User;
import com.querypilot.model.enums.UserRole;
import com.querypilot.repository.DataSourceConfigRepository;
import com.querypilot.repository.PermissionRepository;
import com.querypilot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final DataSourceConfigRepository configRepository;

    public PermissionService(PermissionRepository permissionRepository,
                             UserRepository userRepository,
                             DataSourceConfigRepository configRepository) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.configRepository = configRepository;
    }

    /**
     * Validates that a user has access to a specific database connection.
     * ADMINs have access to all connections.
     * ANALYSTs must have an explicit permission entry.
     */
    public void validateAccess(Long userId, Long connectionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // ADMINs can access any connection
        if (user.getRole() == UserRole.ADMIN) {
            log.debug("Admin user {} granted access to connection {}", userId, connectionId);
            return;
        }

        // ANALYSTs need explicit permission
        if (!permissionRepository.existsByUserIdAndConnectionId(userId, connectionId)) {
            log.warn("Access denied: user {} attempted to access connection {}", userId, connectionId);
            throw new AccessDeniedException(
                    "User does not have permission to access connection: " + connectionId);
        }

        log.debug("User {} granted access to connection {}", userId, connectionId);
    }

    /**
     * Grant a user access to a database connection.
     */
    public Permission grantPermission(PermissionRequest request) {
        // Validate user and connection exist
        if (!userRepository.existsById(request.getUserId())) {
            throw new RuntimeException("User not found: " + request.getUserId());
        }
        if (!configRepository.existsById(request.getConnectionId())) {
            throw new RuntimeException("Connection not found: " + request.getConnectionId());
        }

        // Check if permission already exists
        if (permissionRepository.existsByUserIdAndConnectionId(
                request.getUserId(), request.getConnectionId())) {
            throw new RuntimeException("Permission already exists for user "
                    + request.getUserId() + " on connection " + request.getConnectionId());
        }

        Permission permission = new Permission(null, request.getUserId(), request.getConnectionId());

        permission = permissionRepository.save(permission);
        log.info("Granted user {} access to connection {}", request.getUserId(), request.getConnectionId());
        return permission;
    }

    /**
     * Revoke a user's access to a database connection.
     */
    @Transactional
    public void revokePermission(Long userId, Long connectionId) {
        Permission permission = permissionRepository.findByUserIdAndConnectionId(userId, connectionId)
                .orElseThrow(() -> new RuntimeException(
                        "Permission not found for user " + userId + " on connection " + connectionId));
        permissionRepository.delete(permission);
        log.info("Revoked user {} access to connection {}", userId, connectionId);
    }

    /**
     * List all permissions for a user.
     */
    public List<Permission> getPermissionsByUser(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    /**
     * List all permissions for a connection.
     */
    public List<Permission> getPermissionsByConnection(Long connectionId) {
        return permissionRepository.findByConnectionId(connectionId);
    }
}
