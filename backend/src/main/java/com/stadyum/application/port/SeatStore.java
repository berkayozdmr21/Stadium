package com.stadyum.application.port;

import com.stadyum.domain.model.Seat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Koltuk deposu. Durum değiştiren işlemler veritabanında tek bir koşullu UPDATE olarak çalışır;
 * böylece aynı koltuk için yarışan iki istekten yalnızca biri başarılı olur.
 */
public interface SeatStore {

    /** Bir bloğun koltukları, sıra ve koltuk numarasına göre. */
    List<Seat> findByBlock(Long eventId, String blockCode);

    Optional<Seat> findById(Long seatId);

    List<Seat> findByHold(Long holdId);

    List<Seat> findByBooking(Long bookingId);

    int countByHold(Long holdId);

    /** Koltuk satıştaysa tutar. Başkası aldıysa false döner, hiçbir şey değişmez. */
    boolean tryHold(Long seatId, Long eventId, Long holdId);

    /** Koltuk bu tutmaya aitse bırakır. */
    boolean release(Long seatId, Long holdId);

    /** Tutmanın tüm koltuklarını bırakır ve bırakılan koltukları (bırakılmadan önceki hâliyle) döner. */
    List<Seat> releaseAll(Long holdId);

    /** Tutmanın tüm koltuklarını satıldı yapar; etkilenen koltuk sayısını döner. */
    int markSold(Long holdId, Long bookingId);

    /** Bir maçın blok bazında doluluk özeti. */
    Map<String, BlockAvailability> availabilityByBlock(Long eventId);

    /** Etkinlik bazında doluluk özeti. */
    Map<Long, Availability> availabilityByEvent();

    record Availability(int total, int available, long minPriceKurus) {
    }

    record BlockAvailability(int total, int available) {
    }
}
