package com.stadyum.application.view;

import com.stadyum.domain.model.Zone;
import com.stadyum.domain.stadium.StadiumGeometry;
import com.stadyum.domain.stadium.StadiumLayout;
import com.stadyum.domain.stadium.Tier;
import java.util.List;

/**
 * Arayüzün 3B modeli kurması için stadyumun yapısı. Tek doğruluk kaynağı backend'dir: koltuk sayıları ve blok
 * sınırları burada, geometri ölçüleri de buradadır; arayüz kendi başına sayı uydurmaz.
 */
public record LayoutView(
        Pitch pitch,
        double exponent,
        List<TierView> tiers,
        List<BlockView> blocks,
        int totalSeats,
        String note) {

    public record Pitch(double length, double width) {
    }

    public record TierView(String code, String label, int rows, double rowDepth, double riseDegrees,
                           double startOffset, double startHeight) {
    }

    public record BlockView(String code, String name, String stand, String tier, Zone zone, long priceKurus,
                            double u0, double u1, int[] seatsPerRow, int totalSeats) {
    }

    public static LayoutView of(StadiumLayout layout) {
        return new LayoutView(
                new Pitch(StadiumGeometry.PITCH_LENGTH, StadiumGeometry.PITCH_WIDTH),
                StadiumGeometry.EXPONENT,
                List.of(tier(layout.lower()), tier(layout.upper())),
                layout.blocks().stream()
                        .map(b -> new BlockView(b.code(), b.name(), b.stand(), b.tier().code(), b.zone(),
                                b.zone().priceKurus(), b.u0(), b.u1(), b.seatsPerRow(), b.seatCount()))
                        .toList(),
                layout.totalSeats(),
                "Blok sınırları ve numaraları yaklaşıktır; resmî stadyum planı değildir.");
    }

    private static TierView tier(Tier t) {
        return new TierView(t.code(), t.label(), t.rows(), t.rowDepth(), t.riseDegrees(), t.startOffset(), t.startHeight());
    }
}
