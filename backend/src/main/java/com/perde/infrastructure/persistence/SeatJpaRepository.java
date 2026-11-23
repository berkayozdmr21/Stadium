package com.perde.infrastructure.persistence;

import com.perde.domain.model.SeatStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeatJpaRepository extends JpaRepository<EventSeatEntity, Long> {

    List<EventSeatEntity> findByEventIdOrderByRowIndexAscSeatNoAsc(Long eventId);

    List<EventSeatEntity> findByHoldIdAndStatusOrderByRowIndexAscSeatNoAsc(Long holdId, SeatStatus status);

    List<EventSeatEntity> findByBookingIdOrderByRowIndexAscSeatNoAsc(Long bookingId);

    long countByHoldIdAndStatus(Long holdId, SeatStatus status);

    @Query("select s.id from EventSeatEntity s where s.holdId = :holdId and s.status = :held")
    List<Long> findIdsByHold(@Param("holdId") Long holdId, @Param("held") SeatStatus held);

    /**
     * Çift satışı önleyen çekirdek sorgu: koşul ve güncelleme veritabanında tek adımdır.
     * İki istek aynı koltuk için yarışırsa ikincisi satır kilidini bekler, sonra koşulu yeniden
     * değerlendirir ve 0 satır günceller.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EventSeatEntity s set s.status = :held, s.holdId = :holdId
            where s.id = :seatId and s.eventId = :eventId and s.status = :available""")
    int tryHold(@Param("seatId") Long seatId, @Param("eventId") Long eventId, @Param("holdId") Long holdId,
                @Param("available") SeatStatus available, @Param("held") SeatStatus held);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EventSeatEntity s set s.status = :available, s.holdId = null
            where s.id = :seatId and s.holdId = :holdId and s.status = :held""")
    int release(@Param("seatId") Long seatId, @Param("holdId") Long holdId,
                @Param("available") SeatStatus available, @Param("held") SeatStatus held);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EventSeatEntity s set s.status = :available, s.holdId = null
            where s.holdId = :holdId and s.status = :held""")
    int releaseAll(@Param("holdId") Long holdId,
                   @Param("available") SeatStatus available, @Param("held") SeatStatus held);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EventSeatEntity s set s.status = :sold, s.bookingId = :bookingId
            where s.holdId = :holdId and s.status = :held""")
    int markSold(@Param("holdId") Long holdId, @Param("bookingId") Long bookingId,
                 @Param("held") SeatStatus held, @Param("sold") SeatStatus sold);

    /** Satır: [eventId, status, adet, en düşük fiyat]. */
    @Query("""
            select s.eventId, s.status, count(s), min(s.priceKurus)
            from EventSeatEntity s group by s.eventId, s.status""")
    List<Object[]> availabilityRows();
}
