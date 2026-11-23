package com.perde.application.service;

import com.perde.application.port.HoldStore;
import com.perde.application.port.SeatChangePublisher;
import com.perde.application.port.SeatStore;
import com.perde.application.port.Secrets;
import com.perde.application.view.HoldState;
import com.perde.application.view.SeatChange;
import com.perde.domain.model.Event;
import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldStatus;
import com.perde.domain.model.Seat;
import com.perde.domain.model.SeatStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tutmaların ortak yaşam döngüsü adımları. Yalnızca açık bir işlem (transaction) içinde çağrılır. */
@Component
class HoldLifecycle {

    /** Bundan uzun anahtar üretmiyoruz; daha uzunu geçersiz sayılır ve özetlenmez. */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final HoldStore holds;
    private final SeatStore seats;
    private final SeatChangePublisher publisher;
    private final Secrets secrets;

    HoldLifecycle(HoldStore holds, SeatStore seats, SeatChangePublisher publisher, Secrets secrets) {
        this.holds = holds;
        this.seats = seats;
        this.publisher = publisher;
        this.secrets = secrets;
    }

    /**
     * Ziyaretçinin bu etkinlikteki tutmasını kilitleyerek getirir. Süresi geçmişse hemen kapatır
     * (temizlik işini beklemeden) ve boş döner.
     */
    Optional<Hold> lockUsable(Event event, String token, Instant now) {
        if (!isPlausible(token)) {
            return Optional.empty();
        }
        Optional<Hold> found = holds.lockByTokenHash(secrets.hash(token))
                .filter(h -> h.eventId().equals(event.id()));
        if (found.isPresent() && found.get().isOverdue(now)) {
            close(found.get(), event.slug(), HoldStatus.EXPIRED);
            return Optional.empty();
        }
        return found.filter(h -> h.isUsable(now));
    }

    /** Kilitlemeden okur; yalnızca görüntüleme için. */
    Optional<Hold> peekUsable(Event event, String token, Instant now) {
        if (!isPlausible(token)) {
            return Optional.empty();
        }
        return holds.findByTokenHash(secrets.hash(token))
                .filter(h -> h.eventId().equals(event.id()) && h.isUsable(now));
    }

    /** Tutmayı kapatır ve koltuklarını satışa geri açar. Çağıran, tutma satırını kilitlemiş olmalıdır. */
    void close(Hold hold, String eventSlug, HoldStatus finalStatus) {
        holds.updateStatus(hold.id(), finalStatus);
        List<Long> freed = seats.releaseAll(hold.id());
        if (!freed.isEmpty()) {
            publisher.publish(new SeatChange(eventSlug, freed, SeatStatus.AVAILABLE));
        }
    }

    HoldState stateOf(Hold hold, String newToken, Instant now) {
        List<Seat> held = seats.findByHold(hold.id());
        return new HoldState(
                true,
                newToken,
                hold.expiresAt(),
                now,
                held.stream().map(Seat::id).toList(),
                held.stream().mapToLong(Seat::priceKurus).sum());
    }

    private static boolean isPlausible(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }
}
