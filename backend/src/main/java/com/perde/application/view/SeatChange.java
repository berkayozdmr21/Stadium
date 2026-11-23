package com.perde.application.view;

import com.perde.domain.model.SeatStatus;
import java.util.List;

/** Bir etkinlikte durumu değişen koltuklar. Kimin tuttuğu bilgisi bilerek yer almaz. */
public record SeatChange(String eventSlug, List<Long> seatIds, SeatStatus status) {

    public SeatChange {
        seatIds = List.copyOf(seatIds);
    }

    public static SeatChange of(String eventSlug, Long seatId, SeatStatus status) {
        return new SeatChange(eventSlug, List.of(seatId), status);
    }
}
