package com.querypilot.model.entity;

import com.querypilot.model.enums.DatabaseType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "data_source_config")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DatabaseType type;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String encryptedPassword;

    @Column(columnDefinition = "TEXT")
    private String schemaDdl;

    public Long getId() { return id; }
    public String getName() { return name; }
    public DatabaseType getType() { return type; }
    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getSchemaDdl() { return schemaDdl; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(DatabaseType type) { this.type = type; }
    public void setUrl(String url) { this.url = url; }
    public void setUsername(String username) { this.username = username; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public void setSchemaDdl(String schemaDdl) { this.schemaDdl = schemaDdl; }
}
