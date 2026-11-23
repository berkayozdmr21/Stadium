package com.perde.infrastructure.persistence;

import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "seat_holds")
class HoldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private HoldStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected HoldEntity() {
    }

    static HoldEntity active(Long eventId, String tokenHash, Instant createdAt, Instant expiresAt) {
        HoldEntity entity = new HoldEntity();
        entity.eventId = eventId;
        entity.tokenHash = tokenHash;
        entity.status = HoldStatus.ACTIVE;
        entity.createdAt = createdAt;
        entity.expiresAt = expiresAt;
        return entity;
    }

    Hold toDomain() {
        return new Hold(id, eventId, status, createdAt, expiresAt);
    }
}
