package com.perde.domain.model;

import java.time.Instant;

/** Bir ziyaretçinin bir etkinlikteki süreli koltuk tutması. */
public record Hold(Long id, Long eventId, HoldStatus status, Instant createdAt, Instant expiresAt) {

    /** Hâlâ kullanılabilir mi: aktif ve süresi dolmamış. */
    public boolean isUsable(Instant now) {
        return status == HoldStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** Aktif görünüyor ama süresi geçmiş: temizlenmeyi bekliyor. */
    public boolean isOverdue(Instant now) {
        return status == HoldStatus.ACTIVE && !now.isBefore(expiresAt);
    }
}
