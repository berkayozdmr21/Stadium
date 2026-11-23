package com.perde.infrastructure.persistence;

import com.perde.application.port.BookingStore;
import com.perde.application.port.EventStore;
import com.perde.application.port.HoldStore;
import com.perde.application.port.SeatStore;
import com.perde.domain.model.Booking;
import com.perde.domain.model.Event;
import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldStatus;
import com.perde.domain.model.Seat;
import com.perde.domain.model.SeatStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** Uygulama katmanındaki depo arayüzlerinin JPA ile gerçekleştirimleri. */
final class JpaStores {

    private JpaStores() {
    }

    @Repository
    static class JpaEventStore implements EventStore {

        private final EventJpaRepository repo;

        JpaEventStore(EventJpaRepository repo) {
            this.repo = repo;
        }

        @Override
        public List<Event> findAllOrderByStart() {
            return repo.findAllByOrderByStartsAtAsc().stream().map(EventEntity::toDomain).toList();
        }

        @Override
        public Optional<Event> findBySlug(String slug) {
            return repo.findBySlug(slug).map(EventEntity::toDomain);
        }

        @Override
        public Optional<Event> findById(Long id) {
            return repo.findById(id).map(EventEntity::toDomain);
        }
    }

    @Repository
    static class JpaSeatStore implements SeatStore {

        private final SeatJpaRepository repo;

        JpaSeatStore(SeatJpaRepository repo) {
            this.repo = repo;
        }

        @Override
        public List<Seat> findByEvent(Long eventId) {
            return repo.findByEventIdOrderByRowIndexAscSeatNoAsc(eventId).stream().map(EventSeatEntity::toDomain).toList();
        }

        @Override
        public Optional<Seat> findById(Long seatId) {
            return repo.findById(seatId).map(EventSeatEntity::toDomain);
        }

        @Override
        public List<Seat> findByHold(Long holdId) {
            return repo.findByHoldIdAndStatusOrderByRowIndexAscSeatNoAsc(holdId, SeatStatus.HELD).stream()
                    .map(EventSeatEntity::toDomain).toList();
        }

        @Override
        public List<Seat> findByBooking(Long bookingId) {
            return repo.findByBookingIdOrderByRowIndexAscSeatNoAsc(bookingId).stream()
                    .map(EventSeatEntity::toDomain).toList();
        }

        @Override
        public int countByHold(Long holdId) {
            return (int) repo.countByHoldIdAndStatus(holdId, SeatStatus.HELD);
        }

        @Override
        public boolean tryHold(Long seatId, Long eventId, Long holdId) {
            return repo.tryHold(seatId, eventId, holdId, SeatStatus.AVAILABLE, SeatStatus.HELD) == 1;
        }

        @Override
        public boolean release(Long seatId, Long holdId) {
            return repo.release(seatId, holdId, SeatStatus.AVAILABLE, SeatStatus.HELD) == 1;
        }

        @Override
        public List<Long> releaseAll(Long holdId) {
            // Tutma satırı çağıran tarafından kilitlendiği için iki sorgu arasında liste değişemez.
            List<Long> ids = repo.findIdsByHold(holdId, SeatStatus.HELD);
            if (!ids.isEmpty()) {
                repo.releaseAll(holdId, SeatStatus.AVAILABLE, SeatStatus.HELD);
            }
            return ids;
        }

        @Override
        public int markSold(Long holdId, Long bookingId) {
            return repo.markSold(holdId, bookingId, SeatStatus.HELD, SeatStatus.SOLD);
        }

        @Override
        public Map<Long, Availability> availabilityByEvent() {
            Map<Long, int[]> counts = new HashMap<>();      // [toplam, satışta]
            Map<Long, Long> minPrices = new HashMap<>();
            for (Object[] row : repo.availabilityRows()) {
                Long eventId = (Long) row[0];
                SeatStatus status = (SeatStatus) row[1];
                int count = ((Number) row[2]).intValue();
                long minPrice = ((Number) row[3]).longValue();
                int[] c = counts.computeIfAbsent(eventId, k -> new int[2]);
                c[0] += count;
                if (status == SeatStatus.AVAILABLE) {
                    c[1] += count;
                }
                minPrices.merge(eventId, minPrice, Math::min);
            }
            Map<Long, Availability> result = new HashMap<>();
            counts.forEach((eventId, c) -> result.put(eventId, new Availability(c[0], c[1], minPrices.get(eventId))));
            return result;
        }
    }

    @Repository
    static class JpaHoldStore implements HoldStore {

        private final HoldJpaRepository repo;

        JpaHoldStore(HoldJpaRepository repo) {
            this.repo = repo;
        }

        @Override
        public Hold create(Long eventId, String tokenHash, Instant now, Instant expiresAt) {
            return repo.saveAndFlush(HoldEntity.active(eventId, tokenHash, now, expiresAt)).toDomain();
        }

        @Override
        public Optional<Hold> findByTokenHash(String tokenHash) {
            return repo.findByTokenHash(tokenHash).map(HoldEntity::toDomain);
        }

        @Override
        public Optional<Hold> lockByTokenHash(String tokenHash) {
            return repo.lockByTokenHash(tokenHash).map(HoldEntity::toDomain);
        }

        @Override
        public Optional<Hold> lockById(Long holdId) {
            return repo.lockById(holdId).map(HoldEntity::toDomain);
        }

        @Override
        public void updateStatus(Long holdId, HoldStatus status) {
            repo.updateStatus(holdId, status);
        }

        @Override
        public List<Long> findOverdueIds(Instant now, int limit) {
            return repo.findDueIds(HoldStatus.ACTIVE, now, PageRequest.of(0, limit));
        }
    }

    @Repository
    static class JpaBookingStore implements BookingStore {

        private final BookingJpaRepository repo;

        JpaBookingStore(BookingJpaRepository repo) {
            this.repo = repo;
        }

        @Override
        public Booking save(Booking booking) {
            return repo.saveAndFlush(BookingEntity.from(booking)).toDomain();
        }

        @Override
        public Optional<Booking> findByCode(String code) {
            return repo.findByCode(code).map(BookingEntity::toDomain);
        }
    }
}
