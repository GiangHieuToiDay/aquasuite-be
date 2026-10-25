package com.aquasuite.ims.module.aiadvisor.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "ai_chat_sessions",
        indexes = {
                @Index(name = "idx_ai_chat_sessions_customer_id", columnList = "customer_id"),
                @Index(name = "idx_ai_chat_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_ai_chat_sessions_started_at", columnList = "started_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSession {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_type", nullable = false, length = 50)
    private String sessionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> messages;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    public void prePersist() {
        if (this.sessionId == null) {
            this.sessionId = UUID.randomUUID();
        }
        if (this.startedAt == null) {
            this.startedAt = OffsetDateTime.now();
        }
    }
}
