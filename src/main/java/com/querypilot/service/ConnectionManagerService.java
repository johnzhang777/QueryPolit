package com.querypilot.service;

import com.querypilot.model.dto.ConnectionRequest;
import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.model.enums.DatabaseType;
import com.querypilot.repository.DataSourceConfigRepository;
import com.querypilot.repository.PermissionRepository;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConnectionManagerService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManagerService.class);

    private final DataSourceConfigRepository configRepository;
    private final PermissionRepository permissionRepository;
    private final EncryptionService encryptionService;
    private final SchemaExtractorService schemaExtractorService;
    private final DynamicConnectionFactory connectionFactory;

    public ConnectionManagerService(DataSourceConfigRepository configRepository,
                                    PermissionRepository permissionRepository,
                                    EncryptionService encryptionService,
                                    SchemaExtractorService schemaExtractorService,
                                    DynamicConnectionFactory connectionFactory) {
        this.configRepository = configRepository;
        this.permissionRepository = permissionRepository;
        this.encryptionService = encryptionService;
        this.schemaExtractorService = schemaExtractorService;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Add a new database connection: test it, extract schema, encrypt password, save.
     */
    public DataSourceConfig addConnection(ConnectionRequest request) {
        log.info("Adding new connection: {} ({})", request.getName(), request.getType());

        // Test connectivity first
        testConnection(request.getUrl(), request.getUsername(), request.getPassword(), request.getType());

        // Extract schema DDL
        JdbcTemplate tempJdbc = connectionFactory.createTempJdbcTemplate(
                request.getUrl(), request.getUsername(), request.getPassword(), request.getType());
        String schemaDdl = schemaExtractorService.extractSchema(tempJdbc, request.getType(), request.getUrl());

        // Encrypt password and save
        String encryptedPassword = encryptionService.encrypt(request.getPassword());

        DataSourceConfig config = new DataSourceConfig(null, request.getName(), request.getType(),
                request.getUrl(), request.getUsername(), encryptedPassword, schemaDdl);

        config = configRepository.save(config);
        log.info("Connection saved with ID: {}", config.getId());
        return config;
    }

    public List<DataSourceConfig> listConnections() {
        return configRepository.findAll();
    }

    public DataSourceConfig getConnection(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Connection not found: " + id));
    }

    @Transactional
    public void deleteConnection(Long id) {
        if (!configRepository.existsById(id)) {
            throw new RuntimeException("Connection not found: " + id);
        }
        // Evict from dynamic pool
        connectionFactory.evict(id);
        // Remove related permissions
        permissionRepository.deleteByConnectionId(id);
        // Remove config
        configRepository.deleteById(id);
        log.info("Connection deleted: {}", id);
    }

    /**
     * Refresh the cached schema DDL for a connection.
     */
    public DataSourceConfig refreshSchema(Long id) {
        DataSourceConfig config = getConnection(id);
        JdbcTemplate jdbcTemplate = connectionFactory.getJdbcTemplate(id);
        String schemaDdl = schemaExtractorService.extractSchema(jdbcTemplate, config.getType(), config.getUrl());
        config.setSchemaDdl(schemaDdl);
        return configRepository.save(config);
    }

    private void testConnection(String url, String username, String password, DatabaseType type) {
        try {
            JdbcTemplate tempJdbc = connectionFactory.createTempJdbcTemplate(url, username, password, type);
            tempJdbc.queryForObject("SELECT 1", Integer.class);
            log.info("Connection test successful for: {}", url);
        } catch (DataAccessException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }
}
