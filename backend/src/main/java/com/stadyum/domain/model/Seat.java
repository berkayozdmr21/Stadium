package com.stadyum.domain.model;

/**
 * Bir maça ait tek koltuk. Fiyat kuruş cinsindendir (kayan nokta hatası olmasın diye).
 *
 * @param blockCode koltuğun bulunduğu blok ("A07", "B12", "VIP")
 * @param rowIndex  sahaya en yakın sıra 0
 * @param rowLabel  sıra numarası, gösterim için ("1", "2"...)
 */
public record Seat(
        Long id,
        Long eventId,
        String blockCode,
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
