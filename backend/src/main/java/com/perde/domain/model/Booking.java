package com.perde.domain.model;

import java.time.Instant;

/** Tamamlanmış satın alma. */
public record Booking(
        Long id,
        String code,
        Long eventId,
        Long holdId,
        String customerName,
        String customerEmail,
        long totalKurus,
        Instant createdAt) {
}
