package com.stadyum.infrastructure.persistence;

import com.stadyum.domain.model.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bookings")
class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "hold_id", nullable = false)
    private Long holdId;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "total_kurus", nullable = false)
    private long totalKurus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookingEntity() {
    }

    static BookingEntity from(Booking b) {
        BookingEntity entity = new BookingEntity();
        entity.id = b.id();
        entity.code = b.code();
        entity.eventId = b.eventId();
        entity.holdId = b.holdId();
        entity.customerName = b.customerName();
        entity.customerEmail = b.customerEmail();
        entity.totalKurus = b.totalKurus();
        entity.createdAt = b.createdAt();
        return entity;
    }

    Booking toDomain() {
        return new Booking(id, code, eventId, holdId, customerName, customerEmail, totalKurus, createdAt);
    }
}
