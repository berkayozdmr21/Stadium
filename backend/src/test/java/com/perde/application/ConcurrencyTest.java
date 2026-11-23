package com.perde.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.perde.application.service.BookingService;
import com.perde.application.service.HoldService;
import com.perde.application.view.HoldState;
import com.perde.domain.DomainException;
import com.perde.domain.ErrorCode;
import com.perde.domain.model.Event;
import com.perde.support.IntegrationTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Projenin asıl iddiası: aynı koltuk iki kişiye satılamaz. Buradaki testler istekleri gerçekten aynı
 * anda (başlama kapısıyla) gönderir ve sonucu doğrudan veritabanından denetler.
 */
@SpringBootTest
class ConcurrencyTest extends IntegrationTest {

    @Autowired
    HoldService holds;
    @Autowired
    BookingService bookings;

    @Test
    void manyVisitorsRacingForOneSeat_exactlyOneWins() throws Exception {
        Event event = newEvent("yaris", 1, 3);
        long seat = seatIds(event).get(0);
        int visitors = 40;
        AtomicInteger won = new AtomicInteger();
        AtomicInteger lost = new AtomicInteger();

        runTogether(visitors, i -> () -> {
            try {
                holds.addSeat("yaris", seat, null);
                won.incrementAndGet();
            } catch (DomainException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
                lost.incrementAndGet();
            } catch (ConcurrencyFailureException e) {
                lost.incrementAndGet();   // kilit beklerken zaman aşımı: koltuğu alamadı
            }
            return null;
        });

        assertThat(won.get()).as("kazanan").isEqualTo(1);
        assertThat(lost.get()).isEqualTo(visitors - 1);
        assertThat(seatStatus(seat)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("select count(*) from seat_holds", Integer.class))
                .as("kaybedenlerin açtığı tutmalar geri alındı").isEqualTo(1);
    }

    @Test
    void crowdGrabbingAndBuyingRandomSeats_neverSellsASeatTwice() throws Exception {
        Event event = newEvent("kalabalik", 4, 6);          // 24 koltuk
        List<Long> seats = seatIds(event);
        int visitors = 30;
        Map<Long, Integer> ownerOfSeat = new ConcurrentHashMap<>();   // koltuk -> onu aldığını sanan ziyaretçi
        List<String> doubleClaims = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger soldSeats = new AtomicInteger();
        AtomicInteger revenue = new AtomicInteger();

        runTogether(visitors, i -> () -> {
            Random random = new Random(i);
            String token = null;
            int mine = 0;
            for (int attempt = 0; attempt < 4; attempt++) {
                long seat = seats.get(random.nextInt(seats.size()));
                try {
                    HoldState state = holds.addSeat("kalabalik", seat, token);
                    if (state.token() != null) {
                        token = state.token();
                    }
                    if (state.seatIds().size() > mine) {     // gerçekten yeni bir koltuk aldı
                        mine = state.seatIds().size();
                        Integer previous = ownerOfSeat.put(seat, i);
                        if (previous != null && previous != i) {
                            doubleClaims.add("koltuk " + seat + ": ziyaretçi " + previous + " ve " + i);
                        }
                    }
                } catch (DomainException | ConcurrencyFailureException e) {
                    // başkası aldı; sıradaki koltuğu dener
                }
            }
            if (token != null && i % 2 == 0) {
                var ticket = bookings.confirm("kalabalik", token, "Ziyaretçi " + i, "z" + i + "@example.com");
                soldSeats.addAndGet(ticket.seats().size());
                revenue.addAndGet((int) ticket.totalKurus());
            }
            return null;
        });

        assertThat(doubleClaims).as("aynı koltuğu iki ziyaretçi aldı").isEmpty();
        int held = countSeats(event, "HELD");
        int sold = countSeats(event, "SOLD");
        assertThat(held + sold).as("alındığı sanılan koltuk sayısı veritabanıyla aynı").isEqualTo(ownerOfSeat.size());
        assertThat(sold).isEqualTo(soldSeats.get());
        assertThat(jdbc.queryForObject("select coalesce(sum(total_kurus), 0) from bookings", Long.class))
                .as("tahsil edilen tutar satılan koltuklarla tutarlı")
                .isEqualTo((long) revenue.get())
                .isEqualTo(sold * PRICE);
        assertThat(jdbc.queryForObject(
                "select count(*) from event_seats s join seat_holds h on h.id = s.hold_id "
                        + "where s.status = 'SOLD' and h.status <> 'CONFIRMED'", Integer.class))
                .as("satılan her koltuk onaylanmış bir tutmaya bağlı").isZero();
    }

    @Test
    void sameVisitorFiringManyRequests_cannotExceedTheSeatLimit() throws Exception {
        Event event = newEvent("limit", 1, 12);
        List<Long> seats = seatIds(event);
        String token = holds.addSeat("limit", seats.get(0), null).token();
        AtomicInteger rejected = new AtomicInteger();

        runTogether(11, i -> () -> {
            try {
                holds.addSeat("limit", seats.get(i + 1), token);
            } catch (DomainException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.MAX_SEATS_EXCEEDED);
                rejected.incrementAndGet();
            }
            return null;
        });

        assertThat(countSeats(event, "HELD")).as("sınır 6").isEqualTo(6);
        assertThat(rejected.get()).isEqualTo(6);
    }

    @Test
    void doubleSubmitOfCheckout_createsExactlyOneBooking() throws Exception {
        Event event = newEvent("cift", 1, 3);
        String token = holds.addSeat("cift", seatIds(event).get(0), null).token();
        AtomicInteger confirmed = new AtomicInteger();

        runTogether(8, i -> () -> {
            try {
                bookings.confirm("cift", token, "Ada Yılmaz", "ada@example.com");
                confirmed.incrementAndGet();
            } catch (DomainException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.HOLD_EXPIRED);
            }
            return null;
        });

        assertThat(confirmed.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from bookings", Integer.class)).isEqualTo(1);
        assertThat(countSeats(event, "SOLD")).isEqualTo(1);
    }

    @Test
    void checkoutRacingWithCancel_endsInExactlyOneConsistentOutcome() throws Exception {
        for (int round = 0; round < 15; round++) {
            String slug = "iptal-" + round;
            Event event = newEvent(slug, 1, 2);
            long seat = seatIds(event).get(0);
            String token = holds.addSeat(slug, seat, null).token();
            AtomicInteger confirmed = new AtomicInteger();

            runTogether(2, i -> () -> {
                if (i == 0) {
                    try {
                        bookings.confirm(slug, token, "Ada Yılmaz", "ada@example.com");
                        confirmed.incrementAndGet();
                    } catch (DomainException e) {
                        assertThat(e.code()).isEqualTo(ErrorCode.HOLD_EXPIRED);
                    }
                } else {
                    holds.releaseAll(slug, token);
                }
                return null;
            });

            String status = seatStatus(seat);
            String holdStatus = jdbc.queryForObject(
                    "select status from seat_holds where event_id = ?", String.class, event.id());
            if (confirmed.get() == 1) {
                assertThat(status).isEqualTo("SOLD");
                assertThat(holdStatus).isEqualTo("CONFIRMED");
            } else {
                assertThat(status).isEqualTo("AVAILABLE");
                assertThat(holdStatus).isEqualTo("RELEASED");
            }
        }
    }

    /** {@code count} işi aynı anda başlatır; herhangi biri beklenmeyen hata verirse testi düşürür. */
    private static void runTogether(int count, java.util.function.IntFunction<Callable<Void>> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Void> task = job.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Void> f : futures) {
                f.get(60, TimeUnit.SECONDS);   // işteki assert hatası veya beklenmeyen istisna burada yüzeye çıkar
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
