package com.aquasuite.ims.module.audit.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    @Id @Column(name = "audit_id", nullable = false) private UUID auditId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "entity_type", nullable = false, length = 100) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    @Column(name = "action", nullable = false, length = 100) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "old_data", columnDefinition = "jsonb") private Map<String, Object> oldData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_data", columnDefinition = "jsonb") private Map<String, Object> newData;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
