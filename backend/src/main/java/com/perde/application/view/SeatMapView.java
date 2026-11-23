package com.perde.application.view;

import com.perde.domain.model.SeatStatus;
import com.perde.domain.model.Zone;
import java.util.List;

public record SeatMapView(
        EventSummary event,
        List<SeatView> seats,
        List<ZoneView> zones,
        HoldState hold,
        int holdSeconds,
        int maxSeats) {

    /** @param mine koltuk isteği yapan ziyaretçinin tutmasına aitse true */
    public record SeatView(
            long id,
            String row,
            int rowIndex,
            int number,
            Zone zone,
            long priceKurus,
            SeatStatus status,
            boolean mine) {
    }

    public record ZoneView(Zone code, String label, long priceKurus) {
    }
}
