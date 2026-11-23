package com.stadyum.infrastructure.persistence;

import com.stadyum.domain.model.Seat;
import com.stadyum.domain.model.SeatStatus;
import com.stadyum.domain.model.Zone;
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

/**
 * Koltuk satırı. Koltuklar toplu SQL ile eklenir ({@link EventCatalogWriter}); JPA yalnızca okur ve
 * koşullu güncellemeleri {@code SeatJpaRepository} üzerinden yapar.
 */
@Entity
@Table(name = "event_seats")
class EventSeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "block_code", nullable = false, length = 8)
    private String blockCode;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Zone zone;

    @Column(name = "price_kurus", nullable = false)
    private long priceKurus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "booking_id")
    private Long bookingId;

    protected EventSeatEntity() {
    }

    Seat toDomain() {
        return new Seat(id, eventId, blockCode, rowLabel, rowIndex, seatNo, zone, priceKurus, status, holdId, bookingId);
    }
}
