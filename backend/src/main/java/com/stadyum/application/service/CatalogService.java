package com.stadyum.application.service;

import com.stadyum.application.port.EventStore;
import com.stadyum.application.port.SeatStore;
import com.stadyum.application.port.SeatStore.Availability;
import com.stadyum.application.port.SeatStore.BlockAvailability;
import com.stadyum.application.view.EventSummary;
import com.stadyum.application.view.HoldState;
import com.stadyum.application.view.LayoutView;
import com.stadyum.application.view.StadiumView;
import com.stadyum.application.view.StadiumView.BlockSeats;
import com.stadyum.application.view.StadiumView.BlockView;
import com.stadyum.application.view.StadiumView.SeatView;
import com.stadyum.application.view.StadiumView.ZoneView;
import com.stadyum.domain.DomainException;
import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.Hold;
import com.stadyum.domain.model.HoldPolicy;
import com.stadyum.domain.model.Seat;
import com.stadyum.domain.model.Zone;
import com.stadyum.domain.stadium.StadiumLayout;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maç listesi, stadyum yapısı ve blok sorguları. */
@Service
public class CatalogService {

    private static final Availability EMPTY = new Availability(0, 0, 0);

    private final EventStore events;
    private final SeatStore seats;
    private final HoldLifecycle lifecycle;
    private final HoldPolicy policy;
    private final StadiumLayout layout;
    private final Clock clock;

    CatalogService(EventStore events, SeatStore seats, HoldLifecycle lifecycle, HoldPolicy policy,
                   StadiumLayout layout, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.lifecycle = lifecycle;
        this.policy = policy;
        this.layout = layout;
        this.clock = clock;
    }

    /** Stadyumun sabit yapısı; maçtan bağımsızdır. */
    public LayoutView layout() {
        return LayoutView.of(layout);
    }

    @Transactional(readOnly = true)
    public List<EventSummary> listEvents() {
        Map<Long, Availability> availability = seats.availabilityByEvent();
        return events.findAllOrderByStart().stream()
                .map(e -> summarize(e, availability.getOrDefault(e.id(), EMPTY)))
                .toList();
    }

    /** Bir maçın blok bazında dolulukları ve ziyaretçinin tutması. Tek tek koltuklar blok açılınca istenir. */
    @Transactional(readOnly = true)
    public StadiumView stadium(String slug, String token) {
        Instant now = clock.instant();
        Event event = events.findBySlug(slug).orElseThrow(DomainException::eventNotFound);
        Map<String, BlockAvailability> perBlock = seats.availabilityByBlock(event.id());

        int total = 0;
        int available = 0;
        List<BlockView> blocks = layout.blocks().stream().map(b -> {
            BlockAvailability a = perBlock.getOrDefault(b.code(), new BlockAvailability(0, 0));
            return new BlockView(b.code(), a.total(), a.available());
        }).toList();
        for (BlockView b : blocks) {
            total += b.totalSeats();
            available += b.availableSeats();
        }

        Optional<Hold> hold = lifecycle.peekUsable(event, token, now);
        HoldState holdState = hold.map(h -> lifecycle.stateOf(h, null, now)).orElseGet(() -> HoldState.none(now));
        List<ZoneView> zones = Arrays.stream(Zone.values())
                .map(z -> new ZoneView(z, z.label(), z.priceKurus())).toList();
        long minPrice = Arrays.stream(Zone.values()).mapToLong(Zone::priceKurus).min().orElse(0);

        return new StadiumView(summarize(event, new Availability(total, available, minPrice)),
                blocks, zones, holdState, (int) policy.duration().toSeconds(), policy.maxSeats());
    }

    @Transactional(readOnly = true)
    public BlockSeats blockSeats(String slug, String blockCode, String token) {
        Instant now = clock.instant();
        Event event = events.findBySlug(slug).orElseThrow(DomainException::eventNotFound);
        if (layout.block(blockCode).isEmpty()) {
            throw DomainException.blockNotFound();
        }
        Long myHoldId = lifecycle.peekUsable(event, token, now).map(Hold::id).orElse(null);
        List<SeatView> views = seats.findByBlock(event.id(), blockCode).stream()
                .map((Seat s) -> new SeatView(s.id(), s.rowIndex(), s.rowLabel(), s.number(), s.status(), s.isHeldBy(myHoldId)))
                .toList();
        return new BlockSeats(blockCode, views);
    }

    private static EventSummary summarize(Event e, Availability a) {
        return new EventSummary(e.slug(), e.title(), e.category(), e.venue(), e.city(), e.startsAt(),
                e.description(), e.hue(), a.total(), a.available(), a.minPriceKurus());
    }
}
