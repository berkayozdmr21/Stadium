package com.stadyum.domain.stadium;

import com.stadyum.domain.model.Zone;
import java.util.Arrays;

/**
 * Tribündeki bir blok: çevrede {@code [u0, u1)} aralığını kaplar, her sırasında {@code seatsPerRow[sıra]} koltuk vardır.
 *
 * @param stand tribünün adı ("Güney", "Doğu"...). Resmî tribün adları değildir, yön adlarıdır.
 */
public record BlockSpec(String code, String name, String stand, Tier tier, Zone zone, double u0, double u1,
                        int[] seatsPerRow) {

    public BlockSpec {
        seatsPerRow = seatsPerRow.clone();
    }

    public int rows() {
        return seatsPerRow.length;
    }

    public int seatCount() {
        return Arrays.stream(seatsPerRow).sum();
    }

    @Override
    public int[] seatsPerRow() {
        return seatsPerRow.clone();
    }
}
