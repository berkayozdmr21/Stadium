package com.perde.domain.model;

/**
 * Bir etkinliğe ait tek koltuk. Fiyat kuruş cinsindendir (kayan nokta hatası olmasın diye).
 *
 * @param rowIndex sahneye en yakın sıra 0
 */
public record Seat(
        Long id,
        Long eventId,
        String rowLabel,
        int rowIndex,
        int number,
        Zone zone,
        long priceKurus,
        SeatStatus status,
        Long holdId,
        Long bookingId) {

    public boolean isHeldBy(Long otherHoldId) {
        return status == SeatStatus.HELD && holdId != null && holdId.equals(otherHoldId);
    }
}
