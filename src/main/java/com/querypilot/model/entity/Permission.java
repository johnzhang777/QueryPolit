package com.querypilot.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permission", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "connection_id"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getConnectionId() { return connectionId; }
    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
}
