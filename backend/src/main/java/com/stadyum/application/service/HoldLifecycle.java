package com.stadyum.application.service;

import com.stadyum.application.port.HoldStore;
import com.stadyum.application.port.SeatChangePublisher;
import com.stadyum.application.port.SeatStore;
import com.stadyum.application.port.Secrets;
import com.stadyum.application.view.HoldState;
import com.stadyum.application.view.HoldState.HeldSeat;
import com.stadyum.application.view.SeatChange;
import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.Hold;
import com.stadyum.domain.model.HoldStatus;
import com.stadyum.domain.model.Seat;
import com.stadyum.domain.model.SeatStatus;
import com.stadyum.domain.stadium.BlockSpec;
import com.stadyum.domain.stadium.StadiumLayout;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final Map<String, BlockSpec> blocks;

    HoldLifecycle(HoldStore holds, SeatStore seats, SeatChangePublisher publisher, Secrets secrets,
                  StadiumLayout layout) {
        this.holds = holds;
        this.seats = seats;
        this.publisher = publisher;
        this.secrets = secrets;
        this.blocks = layout.byCode();
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
        List<Seat> freed = seats.releaseAll(hold.id());
        if (!freed.isEmpty()) {
            publisher.publish(SeatChange.of(eventSlug, freed, SeatStatus.AVAILABLE));
        }
    }

    HoldState stateOf(Hold hold, String newToken, Instant now) {
        List<Seat> held = seats.findByHold(hold.id()).stream()
                .sorted(Comparator.comparing(Seat::blockCode).thenComparingInt(Seat::rowIndex).thenComparingInt(Seat::number))
                .toList();
        List<HeldSeat> details = held.stream().map(this::detail).toList();
        return new HoldState(
                true,
                newToken,
                hold.expiresAt(),
                now,
                held.stream().map(Seat::id).toList(),
                details,
                held.stream().mapToLong(Seat::priceKurus).sum());
    }

    private HeldSeat detail(Seat s) {
        BlockSpec block = blocks.get(s.blockCode());
        return new HeldSeat(s.id(), s.blockCode(), block == null ? s.blockCode() : block.name(),
                s.rowLabel(), s.number(), s.zone().name(), s.priceKurus());
    }

    private static boolean isPlausible(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }
}
