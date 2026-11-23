package com.stadyum.application.view;

import com.stadyum.domain.model.SeatStatus;
import com.stadyum.domain.model.Zone;
import java.util.List;

/** Bir maçın stadyum görünümü: blok bazında doluluk, fiyatlar ve ziyaretçinin tutması. */
public record StadiumView(
        EventSummary event,
        List<BlockView> blocks,
        List<ZoneView> zones,
        HoldState hold,
        int holdSeconds,
        int maxSeats) {

    public record BlockView(String code, int totalSeats, int availableSeats) {
    }

    public record ZoneView(Zone code, String label, long priceKurus) {
    }

    /** Bir bloğun koltukları. */
    public record BlockSeats(String block, List<SeatView> seats) {
    }

    /** @param mine koltuk isteği yapan ziyaretçinin tutmasına aitse true */
    public record SeatView(long id, int rowIndex, String row, int number, SeatStatus status, boolean mine) {
    }
}
