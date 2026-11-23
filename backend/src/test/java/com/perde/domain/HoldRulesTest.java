package com.perde.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perde.domain.model.Event;
import com.perde.domain.model.Hold;
import com.perde.domain.model.HoldPolicy;
import com.perde.domain.model.HoldStatus;
import com.perde.domain.model.Seat;
import com.perde.domain.model.SeatStatus;
import com.perde.domain.model.Zone;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Çerçeve gerektirmeyen saf kural testleri. */
class HoldRulesTest {

    private static final Instant T0 = Instant.parse("2030-01-01T12:00:00Z");

    @Test
    void holdIsUsableOnlyWhileActiveAndBeforeExpiry() {
        Hold hold = new Hold(1L, 1L, HoldStatus.ACTIVE, T0, T0.plusSeconds(300));

        assertThat(hold.isUsable(T0.plusSeconds(299))).isTrue();
        assertThat(hold.isUsable(T0.plusSeconds(300))).as("bitiş anı dahil değildir").isFalse();
        assertThat(hold.isOverdue(T0.plusSeconds(300))).isTrue();
        assertThat(hold.isOverdue(T0.plusSeconds(299))).isFalse();
    }

    @Test
    void closedHoldIsNeitherUsableNorOverdue() {
        for (HoldStatus status : new HoldStatus[]{HoldStatus.CONFIRMED, HoldStatus.RELEASED, HoldStatus.EXPIRED}) {
            Hold hold = new Hold(1L, 1L, status, T0, T0.plusSeconds(300));
            assertThat(hold.isUsable(T0)).isFalse();
            assertThat(hold.isOverdue(T0.plusSeconds(999))).as("kapalı tutma yeniden kapatılmaz").isFalse();
        }
    }

    @Test
    void policyLimitsSeatCount() {
        HoldPolicy policy = new HoldPolicy(Duration.ofMinutes(5), 6);

        assertThatCode(() -> policy.ensureCanAdd(5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureCanAdd(6))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.MAX_SEATS_EXCEEDED);
        assertThat(policy.expiryFrom(T0)).isEqualTo(T0.plusSeconds(300));
    }

    @Test
    void policyRejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new HoldPolicy(Duration.ZERO, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HoldPolicy(Duration.ofMinutes(5), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void salesCloseWhenEventStarts() {
        Event event = new Event(1L, "e", "E", "Test", "Salon", "Şehir", T0.plusSeconds(60), "", 0);

        assertThat(event.isOnSale(T0)).isTrue();
        assertThat(event.isOnSale(T0.plusSeconds(60))).isFalse();
    }

    @Test
    void seatKnowsItsHolder() {
        Seat held = new Seat(1L, 1L, "A", 0, 1, Zone.VIP, 100, SeatStatus.HELD, 7L, null);
        Seat sold = new Seat(2L, 1L, "A", 0, 2, Zone.VIP, 100, SeatStatus.SOLD, 7L, 3L);

        assertThat(held.isHeldBy(7L)).isTrue();
        assertThat(held.isHeldBy(8L)).isFalse();
        assertThat(held.isHeldBy(null)).isFalse();
        assertThat(sold.isHeldBy(7L)).as("satılmış koltuk artık tutulmuyor sayılır").isFalse();
    }
}
