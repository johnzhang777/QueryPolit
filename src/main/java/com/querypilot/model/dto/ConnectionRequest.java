package com.querypilot.model.dto;

import com.querypilot.model.enums.DatabaseType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRequest {

    @NotBlank(message = "Connection name is required")
    private String name;

    @NotNull(message = "Database type is required")
    private DatabaseType type;

    @NotBlank(message = "Database URL is required")
    private String url;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    public String getName() { return name; }
    public DatabaseType getType() { return type; }
    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setName(String name) { this.name = name; }
    public void setType(DatabaseType type) { this.type = type; }
    public void setUrl(String url) { this.url = url; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
}
