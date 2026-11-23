package com.stadyum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stadyum.domain.model.Zone;
import com.stadyum.domain.stadium.BlockSpec;
import com.stadyum.domain.stadium.StadiumGeometry;
import com.stadyum.domain.stadium.StadiumLayout;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StadiumLayoutTest {

    private final StadiumLayout layout = StadiumLayout.standard();

    @Test
    void capacityMatchesTheStadiumWithinAFewPercent() {
        // Kaynaklarda kapasite 41.903 ile 42.590 arasında geçiyor; düzen bu aralığın yakınında olmalı
        assertThat(layout.totalSeats()).isBetween(41_000, 44_000);
        int lower = layout.blocks().stream().filter(b -> b.tier().code().equals("A")).mapToInt(BlockSpec::seatCount).sum();
        int upper = layout.blocks().stream().filter(b -> b.tier().code().equals("B")).mapToInt(BlockSpec::seatCount).sum();
        assertThat(upper).as("üst kat daha uzun çevrede ve daha çok sırada, alt kattan büyüktür").isGreaterThan(lower);
        assertThat(lower + upper).isEqualTo(layout.totalSeats());
    }

    @Test
    void hasTwoTiersWithTheDocumentedBlockCounts() {
        long lower = layout.blocks().stream().filter(b -> b.tier().code().equals("A") || b.code().equals("VIP")).count();
        long upper = layout.blocks().stream().filter(b -> b.tier().code().equals("B")).count();

        assertThat(lower).as("alt kat: 24 standart + 1 VIP").isEqualTo(25);
        assertThat(upper).isEqualTo(30);
        assertThat(layout.blocks().stream().filter(b -> b.zone() == Zone.VIP)).hasSize(1);
    }

    @Test
    void blockCodesAreUnique() {
        Set<String> codes = layout.blocks().stream().map(BlockSpec::code).collect(Collectors.toSet());
        assertThat(codes).hasSameSizeAs(layout.blocks());
        assertThat(layout.block("VIP")).isPresent();
        assertThat(layout.block("A01")).isPresent();
        assertThat(layout.block("B30")).isPresent();
        assertThat(layout.block("YOK")).isEmpty();
    }

    @Test
    void blocksTileEachTierWithoutGapsOrOverlaps() {
        for (String tier : new String[]{"A", "B"}) {
            var blocks = layout.blocks().stream()
                    .filter(b -> b.tier().code().equals(tier)).toList();
            double covered = 0;
            for (int i = 0; i < blocks.size(); i++) {
                BlockSpec b = blocks.get(i);
                assertThat(b.u1()).isGreaterThan(b.u0());
                covered += b.u1() - b.u0();
                if (i > 0) {
                    assertThat(b.u0()).as("blok " + b.code() + " öncekinin bittiği yerde başlar")
                            .isCloseTo(blocks.get(i - 1).u1(), org.assertj.core.data.Offset.offset(1e-9));
                }
            }
            assertThat(covered).as("tüm çevre kaplanır").isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void rowsGrowTowardsTheBackBecauseThePerimeterGrows() {
        BlockSpec side = layout.block("B15").orElseThrow();
        int[] seats = side.seatsPerRow();

        assertThat(seats[seats.length - 1]).isGreaterThan(seats[0]);
        assertThat(side.rows()).isEqualTo(25);
    }

    @Test
    void pricesFollowThePositionOfTheBlock() {
        assertThat(layout.block("VIP").orElseThrow().zone()).isEqualTo(Zone.VIP);
        Set<Zone> zones = layout.blocks().stream().map(BlockSpec::zone).collect(Collectors.toSet());
        assertThat(zones).containsExactlyInAnyOrder(Zone.VIP, Zone.KAT1, Zone.KAT2, Zone.KAT3);
        // Köşe blokları her zaman en ucuz kategoridir
        assertThat(layout.blocks().stream().filter(b -> b.stand().endsWith("Köşe")))
                .allMatch(b -> b.zone() == Zone.KAT3);
    }

    @Test
    void standNamesAreGeometricAndNotOfficialNames() {
        assertThat(layout.blocks().stream().map(BlockSpec::stand).collect(Collectors.toSet()))
                .contains("Güney", "Kuzey", "Doğu", "Batı", "Güneydoğu Köşe", "Kuzeybatı Köşe");
        assertThat(layout.block("VIP").orElseThrow().stand()).isEqualTo("Güney");
    }

    @Test
    void geometryHasTheExpectedShape() {
        // Saha kenarında çevre, saha çevresinden biraz uzundur
        assertThat(StadiumGeometry.perimeter(0)).isBetween(270.0, 330.0);
        // u = 0 doğu ucun ortasıdır, u = 0.25 civarı güney kenarın ortasıdır
        var east = StadiumGeometry.pointAt(10, 0);
        assertThat(east.x()).isGreaterThan(60);
        assertThat(Math.abs(east.z())).isLessThan(1.0);
        var south = StadiumGeometry.pointAt(10, 0.25);
        assertThat(south.z()).isGreaterThan(30);
        assertThat(StadiumGeometry.pointAt(10, 1.0).x()).as("çevre kapanır: u = 1, u = 0 ile aynı noktadır").isCloseTo(east.x(), org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void compactLayoutIsSmallButStructurallyTheSame() {
        StadiumLayout compact = StadiumLayout.compact();

        assertThat(compact.blocks()).hasSizeGreaterThan(8);
        assertThat(compact.totalSeats()).isBetween(1_000, 10_000);
        assertThat(compact.blocks()).allMatch(b -> b.rows() == 4);
    }
}
