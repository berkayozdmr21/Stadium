package com.perde.application.service;

import com.perde.application.port.EventStore;
import com.perde.application.port.SeatStore;
import com.perde.application.port.SeatStore.Availability;
import com.perde.application.view.EventSummary;
import com.perde.application.view.HoldState;
import com.perde.application.view.SeatMapView;
import com.perde.application.view.SeatMapView.SeatView;
import com.perde.application.view.SeatMapView.ZoneView;
import com.perde.domain.DomainException;
import com.perde.domain.model.Event;
import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldPolicy;
import com.perde.domain.model.Seat;
import com.perde.domain.model.SeatStatus;
import com.perde.domain.model.Zone;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Etkinlik listesi ve koltuk haritası sorguları. */
@Service
public class CatalogService {

    private static final Availability EMPTY = new Availability(0, 0, 0);

    private final EventStore events;
    private final SeatStore seats;
    private final HoldLifecycle lifecycle;
    private final HoldPolicy policy;
    private final Clock clock;

    CatalogService(EventStore events, SeatStore seats, HoldLifecycle lifecycle, HoldPolicy policy, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.lifecycle = lifecycle;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventSummary> listEvents() {
        Map<Long, Availability> availability = seats.availabilityByEvent();
        return events.findAllOrderByStart().stream()
                .map(e -> summarize(e, availability.getOrDefault(e.id(), EMPTY)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SeatMapView seatMap(String slug, String token) {
        Instant now = clock.instant();
        Event event = events.findBySlug(slug).orElseThrow(DomainException::eventNotFound);
        List<Seat> all = seats.findByEvent(event.id());
        Optional<Hold> hold = lifecycle.peekUsable(event, token, now);
        Long myHoldId = hold.map(Hold::id).orElse(null);

        Map<Zone, Long> zonePrices = new EnumMap<>(Zone.class);
        int available = 0;
        long minPrice = Long.MAX_VALUE;
        for (Seat s : all) {
            zonePrices.merge(s.zone(), s.priceKurus(), Math::min);
            minPrice = Math.min(minPrice, s.priceKurus());
            if (s.status() == SeatStatus.AVAILABLE) {
                available++;
            }
        }

        List<SeatView> seatViews = all.stream()
                .map(s -> new SeatView(s.id(), s.rowLabel(), s.rowIndex(), s.number(), s.zone(), s.priceKurus(),
                        s.status(), s.isHeldBy(myHoldId)))
                .toList();
        List<ZoneView> zones = zonePrices.entrySet().stream()
                .map(z -> new ZoneView(z.getKey(), z.getKey().label(), z.getValue()))
                .toList();
        HoldState holdState = hold.map(h -> lifecycle.stateOf(h, null, now)).orElseGet(() -> HoldState.none(now));

        return new SeatMapView(
                summarize(event, new Availability(all.size(), available, all.isEmpty() ? 0 : minPrice)),
                seatViews,
                zones,
                holdState,
                (int) policy.duration().toSeconds(),
                policy.maxSeats());
    }

    private static EventSummary summarize(Event e, Availability a) {
        return new EventSummary(e.slug(), e.title(), e.category(), e.venue(), e.city(), e.startsAt(),
                e.description(), e.hue(), a.total(), a.available(), a.minPriceKurus());
    }
}
