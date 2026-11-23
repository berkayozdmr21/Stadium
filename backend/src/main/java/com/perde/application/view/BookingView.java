package com.perde.application.view;

import java.time.Instant;
import java.util.List;

public record BookingView(
        String code,
        String eventSlug,
        String eventTitle,
        String category,
        String venue,
        String city,
        Instant startsAt,
        int hue,
        List<TicketSeat> seats,
        long totalKurus,
        String customerName,
        Instant createdAt) {

    public record TicketSeat(String row, int number, String zone, long priceKurus) {
    }
}
