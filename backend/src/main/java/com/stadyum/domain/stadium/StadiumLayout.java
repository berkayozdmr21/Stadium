package com.stadyum.domain.stadium;

import com.stadyum.domain.model.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stadyumun koltuk düzeni: iki katlı tribün, her katta bloklar, her blokta sıralar ve koltuklar.
 *
 * <p><b>Bu düzen yaklaşıktır.</b> Alt kat 25 blok (24 standart + 1 VIP), üst kat 30 blok olarak kuruldu;
 * toplam kapasite yaklaşık 42.500 koltuktur. Blok sınırları ve koltuk sayıları resmî plandan değil,
 * yazılı kaynaklardaki özetlerden çıkarıldı. Gerçek plan bilindiğinde yalnızca bu sınıf değişir.
 */
public record StadiumLayout(Tier lower, Tier upper, List<BlockSpec> blocks) {

    /** Koltuklar arası aralık (metre): koltuk genişliği artı kolçak. */
    static final double SEAT_PITCH = 0.55;

    private static final double END_WEIGHT = 20.0;
    private static final double SIDE_WEIGHT = 14.5;
    private static final double CORNER_WEIGHT = 24.0;

    public StadiumLayout {
        blocks = List.copyOf(blocks);
    }

    public int totalSeats() {
        return blocks.stream().mapToInt(BlockSpec::seatCount).sum();
    }

    public Optional<BlockSpec> block(String code) {
        return blocks.stream().filter(b -> b.code().equals(code)).findFirst();
    }

    public Map<String, BlockSpec> byCode() {
        return blocks.stream().collect(Collectors.toMap(BlockSpec::code, Function.identity()));
    }

    // ---------------------------------------------------------------------------------------------

    private enum Kind { END, SIDE, CORNER }

    private record Slot(Kind kind, int index, int count, boolean vip) {
    }

    /** Standart düzen: alt kat 22 sıra, üst kat 25 sıra. */
    public static StadiumLayout standard() {
        Tier lower = new Tier("A", "Alt Tribün", 22, 0.85, 23.4, 7.0, 1.2);
        Tier upper = new Tier("B", "Üst Tribün", 25, 0.80, 38.7, lower.endOffset() + 7.0, lower.endHeight() + 4.0);
        List<BlockSpec> blocks = new ArrayList<>();
        // Alt kat: uçlar 3'er blok, güney kenarda ortada VIP olacak şekilde 8 yuva, kuzey kenarda 7 blok
        blocks.addAll(buildTier(lower, 3, 7, 8, true));
        blocks.addAll(buildTier(upper, 4, 9, 9, false));
        return new StadiumLayout(lower, upper, blocks);
    }

    /**
     * Test ve hızlı denemeler için küçük düzen: tek katlı, birkaç blok, az koltuk.
     * Koltuk sayıları gerçek düzenle aynı yöntemle hesaplanır.
     */
    public static StadiumLayout compact() {
        Tier lower = new Tier("A", "Alt Tribün", 4, 0.85, 23.4, 7.0, 1.2);
        Tier upper = new Tier("B", "Üst Tribün", 4, 0.80, 38.7, lower.endOffset() + 7.0, lower.endHeight() + 4.0);
        List<BlockSpec> blocks = new ArrayList<>();
        blocks.addAll(buildTier(lower, 1, 2, 2, false));
        blocks.addAll(buildTier(upper, 1, 2, 2, false));
        return new StadiumLayout(lower, upper, blocks);
    }

    /**
     * @param endBlocks  her ucun blok sayısı
     * @param northSide  kuzey kenardaki blok sayısı
     * @param southSlots güney kenardaki yuva sayısı (VIP varsa biri VIP'e ayrılır)
     */
    private static List<BlockSpec> buildTier(Tier tier, int endBlocks, int northSide, int southSlots, boolean vipInSouth) {
        // Saat yönünün tersine, doğu ucun ortasından başlayarak: uç, köşe, güney kenar, köşe, uç, köşe, kuzey kenar, köşe
        List<Slot> plan = new ArrayList<>();
        for (int i = 0; i < endBlocks; i++) plan.add(new Slot(Kind.END, i, endBlocks, false));
        plan.add(new Slot(Kind.CORNER, 0, 1, false));
        int vipSlot = vipInSouth ? southSlots / 2 - 1 : -1;
        for (int i = 0; i < southSlots; i++) plan.add(new Slot(Kind.SIDE, i, southSlots, i == vipSlot));
        plan.add(new Slot(Kind.CORNER, 0, 1, false));
        for (int i = 0; i < endBlocks; i++) plan.add(new Slot(Kind.END, i, endBlocks, false));
        plan.add(new Slot(Kind.CORNER, 0, 1, false));
        for (int i = 0; i < northSide; i++) plan.add(new Slot(Kind.SIDE, i, northSide, false));
        plan.add(new Slot(Kind.CORNER, 0, 1, false));

        double total = plan.stream().mapToDouble(s -> weight(s.kind())).sum();
        double u = -(endBlocks * END_WEIGHT) / 2.0 / total;       // doğu ucun ortası u = 0 olsun

        List<BlockSpec> result = new ArrayList<>();
        int number = 0;
        for (Slot slot : plan) {
            double span = weight(slot.kind()) / total;
            double u0 = u;
            double u1 = u + span;
            u = u1;

            String code;
            if (slot.vip()) {
                code = "VIP";
            } else {
                number++;
                code = tier.code() + String.format("%02d", number);
            }
            double midOffset = tier.offsetOf(tier.rows() / 2);
            StadiumGeometry.Point mid = StadiumGeometry.pointAt(midOffset, (u0 + u1) / 2);
            String stand = standOf(slot.kind(), mid);
            Zone zone = zoneOf(tier, slot);
            String name = slot.vip() ? "VIP Locası" : stand + " " + tier.label() + " · Blok " + code;

            int[] seats = new int[tier.rows()];
            for (int r = 0; r < seats.length; r++) {
                double perimeter = StadiumGeometry.perimeter(tier.offsetOf(r) + tier.rowDepth() / 2);
                seats[r] = Math.max(4, (int) Math.round(perimeter * (u1 - u0) / SEAT_PITCH));
            }
            result.add(new BlockSpec(code, name, stand, tier, zone, u0, u1, seats));
        }
        return result;
    }

    private static double weight(Kind kind) {
        return switch (kind) {
            case END -> END_WEIGHT;
            case SIDE -> SIDE_WEIGHT;
            case CORNER -> CORNER_WEIGHT;
        };
    }

    private static String standOf(Kind kind, StadiumGeometry.Point mid) {
        boolean south = mid.z() > 0;
        boolean east = mid.x() > 0;
        return switch (kind) {
            case END -> east ? "Doğu" : "Batı";
            case SIDE -> south ? "Güney" : "Kuzey";
            case CORNER -> (south ? "Güney" : "Kuzey") + (east ? "doğu" : "batı") + " Köşe";
        };
    }

    private static Zone zoneOf(Tier tier, Slot slot) {
        boolean lowerTier = tier.code().equals("A");
        if (slot.vip()) return Zone.VIP;
        return switch (slot.kind()) {
            case CORNER -> Zone.KAT3;
            case END -> lowerTier ? Zone.KAT2 : Zone.KAT3;
            case SIDE -> {
                double centrality = Math.abs(slot.index() - (slot.count() - 1) / 2.0);
                if (lowerTier) yield centrality <= 1.6 ? Zone.KAT1 : Zone.KAT2;
                yield centrality <= 2.6 ? Zone.KAT2 : Zone.KAT3;
            }
        };
    }
}
