package com.stadyum.domain.stadium;

/**
 * Tribün katı: sıra sayısı, sıra derinliği ve basamak eğimi.
 *
 * <p>Kaynaklarda alt katın yaklaşık 23,4°, üst katın yaklaşık 38,7° eğimli olduğu yazıyor;
 * diğer ölçüler yaklaşıktır (fotoğraf veya resmî plan olmadan çıkarılmadı).
 *
 * @param startOffset  ilk sıranın saha kenarından uzaklığı (metre)
 * @param startHeight  ilk sıranın zeminden yüksekliği (metre)
 */
public record Tier(String code, String label, int rows, double rowDepth, double riseDegrees,
                   double startOffset, double startHeight) {

    public double rise() {
        return rowDepth * Math.tan(Math.toRadians(riseDegrees));
    }

    /** {@code row} numaralı sıranın saha kenarından uzaklığı. */
    public double offsetOf(int row) {
        return startOffset + row * rowDepth;
    }

    public double heightOf(int row) {
        return startHeight + row * rise();
    }

    public double endOffset() {
        return offsetOf(rows);
    }

    public double endHeight() {
        return heightOf(rows);
    }
}
