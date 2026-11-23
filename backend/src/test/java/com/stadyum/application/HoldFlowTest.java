package com.stadyum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stadyum.application.service.BookingService;
import com.stadyum.application.service.CatalogService;
import com.stadyum.application.service.HoldExpiryService;
import com.stadyum.application.service.HoldService;
import com.stadyum.application.view.BookingView;
import com.stadyum.application.view.HoldState;
import com.stadyum.application.view.StadiumView;
import com.stadyum.domain.DomainException;
import com.stadyum.domain.ErrorCode;
import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.SeatStatus;
import com.stadyum.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HoldFlowTest extends IntegrationTest {

    private static final String SLUG = "akis";

    @Autowired
    HoldService holds;
    @Autowired
    BookingService bookings;
    @Autowired
    HoldExpiryService expiry;
    @Autowired
    CatalogService catalog;

    Event event;
    List<Long> seats;

    @BeforeEach
    void setUp() {
        event = newEvent(SLUG, 2, 5);
        seats = seatIds(event);
    }

    // ---------- koltuk tutma ----------

    @Test
    void firstSeatOpensAHoldAndReturnsItsSecretOnce() {
        HoldState first = holds.addSeat(SLUG, seats.get(0), null);

        assertThat(first.active()).isTrue();
        assertThat(first.token()).isNotBlank();
        assertThat(first.seatIds()).containsExactly(seats.get(0));
        assertThat(first.totalKurus()).isEqualTo(PRICE);
        assertThat(first.expiresAt()).isEqualTo(clock.instant().plusSeconds(300));
        assertThat(seatStatus(seats.get(0))).isEqualTo("HELD");

        HoldState second = holds.addSeat(SLUG, seats.get(1), first.token());
        assertThat(second.token()).as("anahtar yalnızca ilk seferde döner").isNull();
        assertThat(second.seatIds()).containsExactly(seats.get(0), seats.get(1));
        assertThat(second.expiresAt()).as("koltuk eklemek süreyi uzatmaz").isEqualTo(first.expiresAt());
    }

    @Test
    void secretIsStoredOnlyAsHash() {
        HoldState state = holds.addSeat(SLUG, seats.get(0), null);

        String stored = jdbc.queryForObject("select token_hash from seat_holds", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(state.token());
    }

    @Test
    void aSeatHeldByOneVisitorIsUnavailableToAnother() {
        holds.addSeat(SLUG, seats.get(0), null);

        assertDomainError(() -> holds.addSeat(SLUG, seats.get(0), null), ErrorCode.SEAT_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select count(*) from seat_holds", Integer.class))
                .as("başarısız denemenin açtığı tutma geri alınır").isEqualTo(1);
    }

    @Test
    void addingTheSameSeatTwiceIsHarmless() {
        HoldState first = holds.addSeat(SLUG, seats.get(0), null);

        HoldState again = holds.addSeat(SLUG, seats.get(0), first.token());

        assertThat(again.seatIds()).containsExactly(seats.get(0));
    }

    @Test
    void seatLimitIsEnforced() {
        Event big = newEvent("buyuk", 1, 10);
        List<Long> ids = seatIds(big);
        String token = holds.addSeat("buyuk", ids.get(0), null).token();
        for (int i = 1; i < 6; i++) {
            holds.addSeat("buyuk", ids.get(i), token);
        }

        assertDomainError(() -> holds.addSeat("buyuk", ids.get(6), token), ErrorCode.MAX_SEATS_EXCEEDED);
        assertThat(countSeats(big, "HELD")).isEqualTo(6);
    }

    @Test
    void unknownSecretCannotHijackAndSimplyStartsANewHold() {
        HoldState mine = holds.addSeat(SLUG, seats.get(0), null);

        HoldState stranger = holds.addSeat(SLUG, seats.get(1), "uydurma-anahtar");

        assertThat(stranger.token()).isNotNull().isNotEqualTo(mine.token());
        assertThat(stranger.seatIds()).containsExactly(seats.get(1));
    }

    @Test
    void oneVisitorCannotReleaseAnothersSeat() {
        HoldState a = holds.addSeat(SLUG, seats.get(0), null);
        HoldState b = holds.addSeat(SLUG, seats.get(1), null);

        holds.removeSeat(SLUG, seats.get(0), b.token());

        assertThat(seatStatus(seats.get(0))).as("A'nın koltuğu yerinde durur").isEqualTo("HELD");
        assertThat(holds.current(SLUG, a.token()).seatIds()).containsExactly(seats.get(0));
    }

    @Test
    void secretOfOneEventDoesNotWorkOnAnother() {
        Event other = newEvent("diger", 1, 3);
        String token = holds.addSeat(SLUG, seats.get(0), null).token();

        HoldState onOther = holds.addSeat("diger", seatIds(other).get(0), token);

        assertThat(onOther.token()).as("diğer etkinlikte ayrı bir tutma açılır").isNotNull();
        assertThat(holds.current(SLUG, token).seatIds()).containsExactly(seats.get(0));
    }

    @Test
    void seatOfAnotherEventIsNotFound() {
        Event other = newEvent("diger", 1, 3);

        assertDomainError(() -> holds.addSeat(SLUG, seatIds(other).get(0), null), ErrorCode.SEAT_NOT_FOUND);
        assertDomainError(() -> holds.addSeat("yok-boyle-etkinlik", seats.get(0), null), ErrorCode.EVENT_NOT_FOUND);
    }

    // ---------- bırakma ----------

    @Test
    void removingASeatFreesItAndRemovingTheLastEndsTheHold() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        holds.addSeat(SLUG, seats.get(1), token);

        HoldState afterOne = holds.removeSeat(SLUG, seats.get(0), token);
        assertThat(afterOne.active()).isTrue();
        assertThat(afterOne.seatIds()).containsExactly(seats.get(1));
        assertThat(seatStatus(seats.get(0))).isEqualTo("AVAILABLE");

        HoldState afterLast = holds.removeSeat(SLUG, seats.get(1), token);
        assertThat(afterLast.active()).isFalse();
        assertThat(jdbc.queryForObject("select status from seat_holds", String.class)).isEqualTo("RELEASED");
        assertDomainError(() -> holds.removeSeat(SLUG, seats.get(1), token), ErrorCode.HOLD_EXPIRED);
    }

    @Test
    void releaseAllFreesEverythingAndIsRepeatable() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        holds.addSeat(SLUG, seats.get(1), token);

        assertThat(holds.releaseAll(SLUG, token).active()).isFalse();
        assertThat(holds.releaseAll(SLUG, token).active()).as("ikinci çağrı hata vermez").isFalse();

        assertThat(countSeats(event, "AVAILABLE")).isEqualTo(10);
    }

    // ---------- süre dolumu ----------

    @Test
    void expiredHoldIsClosedByTheCleanupAndSeatsReturnToSale() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        holds.addSeat(SLUG, seats.get(1), token);

        clock.advance(Duration.ofSeconds(299));
        assertThat(expiry.findOverdue(10)).as("süre dolmadan dokunulmaz").isEmpty();

        clock.advance(Duration.ofSeconds(1));
        List<Long> overdue = expiry.findOverdue(10);
        assertThat(overdue).hasSize(1);
        assertThat(expiry.expire(overdue.get(0))).isTrue();
        assertThat(expiry.expire(overdue.get(0))).as("ikinci kez kapatılmaz").isFalse();

        assertThat(countSeats(event, "AVAILABLE")).isEqualTo(10);
        assertThat(jdbc.queryForObject("select status from seat_holds", String.class)).isEqualTo("EXPIRED");
        assertThat(holds.current(SLUG, token).active()).isFalse();
    }

    @Test
    void expiredHoldCannotBeConfirmedEvenBeforeTheCleanupRuns() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        clock.advance(Duration.ofSeconds(300));

        assertDomainError(() -> bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com"), ErrorCode.HOLD_EXPIRED);
        assertThat(countSeats(event, "SOLD")).isZero();
    }

    @Test
    void usingAnExpiredSecretFreesItsSeatsImmediatelyAndStartsFresh() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        clock.advance(Duration.ofSeconds(301));

        HoldState fresh = holds.addSeat(SLUG, seats.get(1), token);

        assertThat(fresh.token()).as("yeni tutma, yeni anahtar").isNotNull().isNotEqualTo(token);
        assertThat(fresh.seatIds()).containsExactly(seats.get(1));
        assertThat(seatStatus(seats.get(0))).as("eski koltuk temizlik beklenmeden serbest kaldı").isEqualTo("AVAILABLE");
    }

    @Test
    void cleanupLeavesAConfirmedHoldAlone() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com");
        Long holdId = jdbc.queryForObject("select id from seat_holds", Long.class);

        clock.advance(Duration.ofHours(1));

        assertThat(expiry.findOverdue(10)).isEmpty();
        assertThat(expiry.expire(holdId)).isFalse();
        assertThat(seatStatus(seats.get(0))).isEqualTo("SOLD");
    }

    // ---------- satın alma ----------

    @Test
    void confirmSellsTheSeatsAndIssuesATicket() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        holds.addSeat(SLUG, seats.get(1), token);

        BookingView ticket = bookings.confirm(SLUG, token, "  Ada   Yılmaz ", " ADA@Example.com ");

        assertThat(ticket.code()).matches("PRD-[A-Z2-9]{8}");
        assertThat(ticket.totalKurus()).isEqualTo(2 * PRICE);
        assertThat(ticket.customerName()).isEqualTo("Ada Yılmaz");
        assertThat(ticket.seats()).extracting(BookingView.TicketSeat::block, BookingView.TicketSeat::row, BookingView.TicketSeat::number)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BLOCK, "1", 1), org.assertj.core.groups.Tuple.tuple(BLOCK, "1", 2));
        assertThat(countSeats(event, "SOLD")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select customer_email from bookings", String.class)).isEqualTo("ada@example.com");

        assertThat(bookings.findByCode(ticket.code().toLowerCase()).code()).isEqualTo(ticket.code());
        assertDomainError(() -> bookings.findByCode("PRD-YOKYOKYO"), ErrorCode.BOOKING_NOT_FOUND);
    }

    @Test
    void aHoldCanBeConfirmedOnlyOnce() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com");

        assertDomainError(() -> bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com"), ErrorCode.HOLD_EXPIRED);
        assertThat(jdbc.queryForObject("select count(*) from bookings", Integer.class)).isEqualTo(1);
    }

    @Test
    void soldSeatsCannotBeHeldOrReleased() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com");

        assertDomainError(() -> holds.addSeat(SLUG, seats.get(0), null), ErrorCode.SEAT_UNAVAILABLE);
        holds.releaseAll(SLUG, token);
        assertThat(seatStatus(seats.get(0))).isEqualTo("SOLD");
    }

    @Test
    void salesCloseOnceTheEventStarts() {
        String token = holds.addSeat(SLUG, seats.get(0), null).token();
        clock.advance(Duration.ofDays(11));

        assertDomainError(() -> holds.addSeat(SLUG, seats.get(1), null), ErrorCode.SALES_CLOSED);
        assertDomainError(() -> bookings.confirm(SLUG, token, "Ada Yılmaz", "ada@example.com"), ErrorCode.SALES_CLOSED);
    }

    // ---------- görünüm ----------

    @Test
    void blockViewMarksOnlyTheCallersOwnSeatsAsMine() {
        String mine = holds.addSeat(SLUG, seats.get(0), null).token();
        holds.addSeat(SLUG, seats.get(1), null);

        StadiumView asMe = catalog.stadium(SLUG, mine);
        StadiumView asGuest = catalog.stadium(SLUG, null);
        StadiumView.BlockSeats seatsAsMe = catalog.blockSeats(SLUG, BLOCK, mine);
        StadiumView.BlockSeats seatsAsGuest = catalog.blockSeats(SLUG, BLOCK, null);

        assertThat(seatsAsMe.seats()).filteredOn(StadiumView.SeatView::mine)
                .extracting(StadiumView.SeatView::id).containsExactly(seats.get(0));
        assertThat(seatsAsMe.seats()).filteredOn(s -> s.status() == SeatStatus.HELD).hasSize(2);
        assertThat(seatsAsMe.seats()).hasSize(10);
        assertThat(asMe.hold().active()).isTrue();
        assertThat(asMe.hold().seats()).singleElement().satisfies(s -> {
            assertThat(s.block()).isEqualTo(BLOCK);
            assertThat(s.priceKurus()).isEqualTo(PRICE);
        });
        assertThat(asMe.event().availableSeats()).isEqualTo(8);
        assertThat(asMe.blocks()).filteredOn(b -> b.code().equals(BLOCK)).singleElement().satisfies(b -> {
            assertThat(b.totalSeats()).isEqualTo(10);
            assertThat(b.availableSeats()).isEqualTo(8);
        });
        assertThat(seatsAsGuest.seats()).noneMatch(StadiumView.SeatView::mine);
        assertThat(asGuest.hold().active()).isFalse();
        assertThat(asGuest.holdSeconds()).isEqualTo(300);
        assertThat(asGuest.maxSeats()).isEqualTo(6);
        assertThat(asGuest.zones()).hasSize(4);
    }

    @Test
    void unknownBlockIsNotFound() {
        assertDomainError(() -> catalog.blockSeats(SLUG, "Z99", null), ErrorCode.BLOCK_NOT_FOUND);
        assertDomainError(() -> catalog.blockSeats("yok-boyle-mac", BLOCK, null), ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void eventListReportsLiveAvailability() {
        holds.addSeat(SLUG, seats.get(0), null);

        assertThat(catalog.listEvents()).singleElement().satisfies(e -> {
            assertThat(e.totalSeats()).isEqualTo(10);
            assertThat(e.availableSeats()).isEqualTo(9);
            assertThat(e.minPriceKurus()).isEqualTo(PRICE);
        });
    }

    private static void assertDomainError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(expected);
    }
}
