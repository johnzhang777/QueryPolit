package com.querypilot.service;

import com.querypilot.model.entity.DataSourceConfig;
import com.querypilot.model.enums.DatabaseType;
import com.querypilot.repository.DataSourceConfigRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicConnectionFactory.class);

    private final DataSourceConfigRepository configRepository;
    private final EncryptionService encryptionService;
    private final ConcurrentHashMap<Long, JdbcTemplate> cache = new ConcurrentHashMap<>();

    public DynamicConnectionFactory(DataSourceConfigRepository configRepository,
                                    EncryptionService encryptionService) {
        this.configRepository = configRepository;
        this.encryptionService = encryptionService;
    }

    /**
     * Returns a JdbcTemplate for the given connection ID, creating one if not cached.
     */
    public JdbcTemplate getJdbcTemplate(Long connectionId) {
        return cache.computeIfAbsent(connectionId, this::createJdbcTemplate);
    }

    /**
     * Creates a temporary JdbcTemplate for testing a connection (not cached).
     */
    public JdbcTemplate createTempJdbcTemplate(String url, String username, String password, DatabaseType type) {
        DataSource ds = createDataSource(url, username, password, type, "temp-test");
        return new JdbcTemplate(ds);
    }

    /**
     * Evicts a connection from the cache and closes its DataSource.
     */
    public void evict(Long connectionId) {
        JdbcTemplate removed = cache.remove(connectionId);
        if (removed != null) {
            DataSource ds = removed.getDataSource();
            if (ds instanceof HikariDataSource hikariDs) {
                hikariDs.close();
                log.info("Closed and evicted DataSource for connection ID: {}", connectionId);
            }
        }
    }

    private JdbcTemplate createJdbcTemplate(Long connectionId) {
        DataSourceConfig config = configRepository.findById(connectionId)
                .orElseThrow(() -> new RuntimeException("Connection config not found: " + connectionId));

        String decryptedPassword = encryptionService.decrypt(config.getEncryptedPassword());
        String poolName = "qp-pool-" + connectionId;

        DataSource ds = createDataSource(config.getUrl(), config.getUsername(), decryptedPassword,
                config.getType(), poolName);

        log.info("Created dynamic DataSource for connection: {} ({})", config.getName(), connectionId);
        return new JdbcTemplate(ds);
    }

    private DataSource createDataSource(String url, String username, String password,
                                        DatabaseType type, String poolName) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setPoolName(poolName);
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setDriverClassName(getDriverClass(type));

        return new HikariDataSource(hikariConfig);
    }

    private String getDriverClass(DatabaseType type) {
        return switch (type) {
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            case H2 -> "org.h2.Driver";
        };
    }
}
