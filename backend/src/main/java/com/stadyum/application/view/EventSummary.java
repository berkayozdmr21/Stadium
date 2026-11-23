package com.stadyum.application.view;

import java.time.Instant;

public record EventSummary(
        String slug,
        String title,
        String category,
        String venue,
        String city,
        Instant startsAt,
        String description,
        int hue,
        int totalSeats,
        int availableSeats,
        long minPriceKurus) {
}
