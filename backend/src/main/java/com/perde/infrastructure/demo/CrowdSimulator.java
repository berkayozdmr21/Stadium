package com.perde.infrastructure.demo;

import com.perde.application.service.BookingService;
import com.perde.application.service.CatalogService;
import com.perde.application.service.HoldService;
import com.perde.application.view.EventSummary;
import com.perde.application.view.HoldState;
import com.perde.application.view.SeatMapView;
import com.perde.application.view.SeatMapView.SeatView;
import com.perde.domain.DomainException;
import com.perde.domain.model.SeatStatus;
import com.perde.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Demo kalabalığı: tek başına bakan bir ziyaretçi de haritanın canlı değiştiğini görsün diye
 * koltuk tutan, bırakan ve satın alan sahte ziyaretçiler. Gerçek ziyaretçilerle AYNI servisleri
 * kullanır, yani aynı kurallara ve aynı yarış koşullarına tabidir. Yalnızca
 * {@code perde.crowd.enabled=true} iken çalışır; arayüz bunun simülasyon olduğunu belirtir.
 */
@Component
@ConditionalOnProperty(prefix = "perde.crowd", name = "enabled", havingValue = "true")
class CrowdSimulator {

    private static final Logger log = LoggerFactory.getLogger(CrowdSimulator.class);
    private static final String BOT_NAME = "Demo Seyirci";
    private static final String BOT_EMAIL = "demo@perde.invalid";

    private record Bot(String slug, String token, Instant decideAt) {
    }

    private final HoldService holds;
    private final BookingService bookings;
    private final CatalogService catalog;
    private final AppProperties.Crowd config;
    private final int holdSeconds;
    private final Clock clock;
    private final Random random = new Random();
    private final List<Bot> bots = new ArrayList<>();

    CrowdSimulator(HoldService holds, BookingService bookings, CatalogService catalog, AppProperties props, Clock clock) {
        this.holds = holds;
        this.bookings = bookings;
        this.catalog = catalog;
        this.config = props.crowd();
        this.holdSeconds = props.holdSeconds();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${perde.crowd.interval-ms:1500}", initialDelay = 3000)
    void tick() {
        try {
            decidePending();
            if (bots.size() < config.maxActive()) {
                arrive();
            }
        } catch (DomainException | DataAccessException e) {
            log.debug("Demo kalabalığı bu turu atladı: {}", e.getMessage());
        }
    }

    /** Bekleme süresi dolan sahte ziyaretçiler ya satın alır ya da vazgeçer. */
    private void decidePending() {
        Instant now = clock.instant();
        for (Iterator<Bot> it = bots.iterator(); it.hasNext(); ) {
            Bot bot = it.next();
            if (now.isBefore(bot.decideAt())) {
                continue;
            }
            it.remove();
            try {
                if (random.nextDouble() < 0.4 && soldRatio(bot.slug()) < config.maxSoldRatio()) {
                    bookings.confirm(bot.slug(), bot.token(), BOT_NAME, BOT_EMAIL);
                } else {
                    holds.releaseAll(bot.slug(), bot.token());
                }
            } catch (DomainException | DataAccessException e) {
                log.debug("Sahte ziyaretçi işlemini tamamlayamadı: {}", e.getMessage());
            }
        }
    }

    /** Yeni bir sahte ziyaretçi rastgele bir etkinlikte yan yana 1-3 koltuk tutar. */
    private void arrive() {
        List<EventSummary> events = catalog.listEvents();
        if (events.isEmpty()) {
            return;
        }
        String slug = events.get(random.nextInt(events.size())).slug();
        List<SeatView> free = catalog.seatMap(slug, null).seats().stream()
                .filter(s -> s.status() == SeatStatus.AVAILABLE)
                .toList();
        if (free.isEmpty()) {
            return;
        }
        SeatView first = free.get(random.nextInt(free.size()));
        int wanted = 1 + random.nextInt(3);
        List<SeatView> picks = free.stream()
                .filter(s -> s.rowIndex() == first.rowIndex()
                        && s.number() >= first.number() && s.number() < first.number() + wanted)
                .toList();

        String token = null;
        for (SeatView seat : picks) {
            try {
                HoldState state = holds.addSeat(slug, seat.id(), token);
                if (state.token() != null) {
                    token = state.token();
                }
            } catch (DomainException e) {
                break;   // koltuğu bir başkası kaptı: gerçek ziyaretçi gibi elindekiyle yetinir
            }
        }
        if (token != null) {
            // Tutma süresi dolmadan karar versin.
            long maxWait = Math.max(3, Math.min(18, holdSeconds - 5));
            long wait = 3 + random.nextLong(Math.max(1, maxWait - 2));
            bots.add(new Bot(slug, token, clock.instant().plus(Duration.ofSeconds(wait))));
        }
    }

    private double soldRatio(String slug) {
        SeatMapView map = catalog.seatMap(slug, null);
        if (map.seats().isEmpty()) {
            return 1;
        }
        long sold = map.seats().stream().filter(s -> s.status() == SeatStatus.SOLD).count();
        return (double) sold / map.seats().size();
    }
}
