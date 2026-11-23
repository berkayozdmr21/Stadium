package com.perde.application.view;

import java.time.Instant;
import java.util.List;

/**
 * Ziyaretçinin tutmasının anlık durumu.
 *
 * @param token      yalnızca tutma yeni oluşturulduğunda dolu gelir; istemci saklar ve sonraki isteklerde gönderir
 * @param serverTime istemcinin geri sayımı kendi saatine değil sunucu saatine göre yapabilmesi için
 */
public record HoldState(
        boolean active,
        String token,
        Instant expiresAt,
        Instant serverTime,
        List<Long> seatIds,
        long totalKurus) {

    public static HoldState none(Instant now) {
        return new HoldState(false, null, null, now, List.of(), 0);
    }
}
