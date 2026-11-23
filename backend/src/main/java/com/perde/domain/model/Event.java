package com.perde.domain.model;

import java.time.Instant;

/**
 * Bilet satılan etkinlik.
 *
 * @param hue afiş renginin tohumu (0-359); arayüz afişi bundan üretir, görsel dosyası tutulmaz
 */
public record Event(
        Long id,
        String slug,
        String title,
        String category,
        String venue,
        String city,
        Instant startsAt,
        String description,
        int hue) {

    /** Etkinlik başladıktan sonra bilet satılmaz. */
    public boolean isOnSale(Instant now) {
        return now.isBefore(startsAt);
    }
}
