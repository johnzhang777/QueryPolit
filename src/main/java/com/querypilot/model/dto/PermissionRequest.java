package com.querypilot.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Connection ID is required")
    private Long connectionId;

    public Long getUserId() { return userId; }
    public Long getConnectionId() { return connectionId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
}
