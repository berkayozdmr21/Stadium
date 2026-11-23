package com.perde.application.port;

import com.perde.domain.model.Seat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Koltuk deposu. Durum değiştiren işlemler veritabanında tek bir koşullu UPDATE olarak çalışır;
 * böylece aynı koltuk için yarışan iki istekten yalnızca biri başarılı olur.
 */
public interface SeatStore {

    List<Seat> findByEvent(Long eventId);

    Optional<Seat> findById(Long seatId);

    List<Seat> findByHold(Long holdId);

    List<Seat> findByBooking(Long bookingId);

    int countByHold(Long holdId);

    /** Koltuk satıştaysa tutar. Başkası aldıysa false döner, hiçbir şey değişmez. */
    boolean tryHold(Long seatId, Long eventId, Long holdId);

    /** Koltuk bu tutmaya aitse bırakır. */
    boolean release(Long seatId, Long holdId);

    /** Tutmanın tüm koltuklarını bırakır ve bırakılan koltuk kimliklerini döner. */
    List<Long> releaseAll(Long holdId);

    /** Tutmanın tüm koltuklarını satıldı yapar; etkilenen koltuk sayısını döner. */
    int markSold(Long holdId, Long bookingId);

    /** Etkinlik bazında doluluk özeti. */
    Map<Long, Availability> availabilityByEvent();

    record Availability(int total, int available, long minPriceKurus) {
    }
}
