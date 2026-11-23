package com.perde.application.service;

import com.perde.application.port.EventStore;
import com.perde.application.port.HoldStore;
import com.perde.application.port.SeatChangePublisher;
import com.perde.application.port.SeatStore;
import com.perde.application.port.Secrets;
import com.perde.application.view.HoldState;
import com.perde.application.view.SeatChange;
import com.perde.domain.DomainException;
import com.perde.domain.model.Event;
import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldPolicy;
import com.perde.domain.model.HoldStatus;
import com.perde.domain.model.Seat;
import com.perde.domain.model.SeatStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Koltuk tutma kullanım senaryoları.
 *
 * <p>Eşzamanlılık iki katmanda çözülür: (1) aynı ziyaretçinin istekleri, tutma satırı kilitlenerek
 * sıraya sokulur; (2) farklı ziyaretçilerin aynı koltuk için yarışı, veritabanındaki tek bir koşullu
 * UPDATE ile sonuçlanır ve yalnızca biri kazanır.
 */
@Service
public class HoldService {

    private final EventStore events;
    private final SeatStore seats;
    private final HoldStore holds;
    private final SeatChangePublisher publisher;
    private final Secrets secrets;
    private final HoldLifecycle lifecycle;
    private final HoldPolicy policy;
    private final Clock clock;

    HoldService(EventStore events, SeatStore seats, HoldStore holds, SeatChangePublisher publisher,
                Secrets secrets, HoldLifecycle lifecycle, HoldPolicy policy, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.holds = holds;
        this.publisher = publisher;
        this.secrets = secrets;
        this.lifecycle = lifecycle;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Koltuğu ziyaretçinin tutmasına ekler; tutması yoksa (veya süresi dolduysa) yenisini açar.
     * Aynı koltuk için tekrar çağrılması zararsızdır.
     */
    @Transactional
    public HoldState addSeat(String slug, long seatId, String token) {
        Instant now = clock.instant();
        Event event = requireEvent(slug);
        if (!event.isOnSale(now)) {
            throw DomainException.salesClosed();
        }
        Seat seat = seats.findById(seatId)
                .filter(s -> s.eventId().equals(event.id()))
                .orElseThrow(DomainException::seatNotFound);

        String newToken = null;
        Hold hold = lifecycle.lockUsable(event, token, now).orElse(null);
        if (hold == null) {
            newToken = secrets.newHoldToken();
            hold = holds.create(event.id(), secrets.hash(newToken), now, policy.expiryFrom(now));
        }

        if (!seat.isHeldBy(hold.id())) {
            policy.ensureCanAdd(seats.countByHold(hold.id()));
            if (!seats.tryHold(seatId, event.id(), hold.id())) {
                // İşlem geri alınır; yeni açılmış bir tutma varsa o da kaybolur.
                throw DomainException.seatUnavailable();
            }
            publisher.publish(SeatChange.of(event.slug(), seatId, SeatStatus.HELD));
        }
        return lifecycle.stateOf(hold, newToken, now);
    }

    /** Koltuğu tutmadan çıkarır. Son koltuk da çıkarsa tutma kapanır. */
    @Transactional
    public HoldState removeSeat(String slug, long seatId, String token) {
        Instant now = clock.instant();
        Event event = requireEvent(slug);
        Hold hold = lifecycle.lockUsable(event, token, now).orElseThrow(DomainException::holdExpired);

        if (seats.release(seatId, hold.id())) {
            publisher.publish(SeatChange.of(event.slug(), seatId, SeatStatus.AVAILABLE));
        }
        if (seats.countByHold(hold.id()) == 0) {
            holds.updateStatus(hold.id(), HoldStatus.RELEASED);
            return HoldState.none(now);
        }
        return lifecycle.stateOf(hold, null, now);
    }

    /** Tutmayı tümüyle bırakır. Tutma zaten yoksa sessizce başarılı olur. */
    @Transactional
    public HoldState releaseAll(String slug, String token) {
        Instant now = clock.instant();
        Event event = requireEvent(slug);
        lifecycle.lockUsable(event, token, now)
                .ifPresent(hold -> lifecycle.close(hold, event.slug(), HoldStatus.RELEASED));
        return HoldState.none(now);
    }

    @Transactional(readOnly = true)
    public HoldState current(String slug, String token) {
        Instant now = clock.instant();
        Event event = requireEvent(slug);
        return lifecycle.peekUsable(event, token, now)
                .map(hold -> lifecycle.stateOf(hold, null, now))
                .orElseGet(() -> HoldState.none(now));
    }

    private Event requireEvent(String slug) {
        return events.findBySlug(slug).orElseThrow(DomainException::eventNotFound);
    }
}
