package com.stadyum.application.view;

import java.time.Instant;
import java.util.List;

/**
 * Ziyaretçinin tutmasının anlık durumu.
 *
 * @param token      yalnızca tutma yeni oluşturulduğunda dolu gelir; istemci saklar ve sonraki isteklerde gönderir
 * @param serverTime istemcinin geri sayımı kendi saatine değil sunucu saatine göre yapabilmesi için
 * @param seats      tutulan koltukların ayrıntısı (farklı bloklardan olabilir)
 */
public record HoldState(
        boolean active,
        String token,
        Instant expiresAt,
        Instant serverTime,
        List<Long> seatIds,
        List<HeldSeat> seats,
        long totalKurus) {

    public record HeldSeat(long id, String block, String blockName, String row, int number, String zone, long priceKurus) {
    }

    public static HoldState none(Instant now) {
        return new HoldState(false, null, null, now, List.of(), List.of(), 0);
    }
}
