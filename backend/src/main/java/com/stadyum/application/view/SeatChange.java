package com.stadyum.application.view;

import com.stadyum.domain.model.Seat;
import com.stadyum.domain.model.SeatStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir maçta durumu değişen koltuklar. Kimin tuttuğu bilgisi bilerek yer almaz.
 *
 * @param blockCounts blok koduna göre etkilenen koltuk sayısı; arayüz blok doluluk sayacını buradan günceller
 */
public record SeatChange(String eventSlug, List<Long> seatIds, SeatStatus status, Map<String, Integer> blockCounts) {

    public SeatChange {
        seatIds = List.copyOf(seatIds);
        blockCounts = Map.copyOf(blockCounts);
    }

    public static SeatChange of(String eventSlug, Collection<Seat> seats, SeatStatus status) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Seat seat : seats) {
            counts.merge(seat.blockCode(), 1, Integer::sum);
        }
        return new SeatChange(eventSlug, seats.stream().map(Seat::id).toList(), status, counts);
    }
}
