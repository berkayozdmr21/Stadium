package com.stadyum.domain.stadium;

/**
 * Tribün çevresinin geometrisi. Sahanın etrafındaki her sıra, köşeleri yuvarlatılmış bir dikdörtgendir
 * (süper elips): {@code |x/a|^n + |z/b|^n = 1}. Sıra saha kenarından {@code d} metre uzaktaysa
 * {@code a = 52,5 + d}, {@code b = 34 + d}.
 *
 * <p>Çevre üzerindeki konum, yay uzunluğuna göre 0-1 arası bir {@code u} ile verilir; u = 0 doğu ucun
 * ortasıdır (+x) ve artarken güneye (+z) doğru ilerler. Arayüz aynı formülü kullanır.
 */
public final class StadiumGeometry {

    public static final double PITCH_LENGTH = 105.0;
    public static final double PITCH_WIDTH = 68.0;
    /** Köşelerin yuvarlaklığı: 2 = elips, büyüdükçe dikdörtgene yaklaşır. */
    public static final double EXPONENT = 3.0;

    private static final int SAMPLES = 4096;

    private StadiumGeometry() {
    }

    public record Point(double x, double z) {
    }

    private static double halfX(double d) {
        return PITCH_LENGTH / 2 + d;
    }

    private static double halfZ(double d) {
        return PITCH_WIDTH / 2 + d;
    }

    /** Açı parametresi {@code t} için çevre noktası. */
    static Point at(double d, double t) {
        double c = Math.cos(t);
        double s = Math.sin(t);
        double p = 2.0 / EXPONENT;
        return new Point(halfX(d) * Math.signum(c) * Math.pow(Math.abs(c), p),
                halfZ(d) * Math.signum(s) * Math.pow(Math.abs(s), p));
    }

    /** Çevrenin uzunluğu (metre). */
    public static double perimeter(double d) {
        return cumulative(d)[SAMPLES];
    }

    /** Yay uzunluğunun {@code u} oranındaki nokta. {@code u} 1'e göre kalan alınır (negatif olabilir). */
    public static Point pointAt(double d, double u) {
        double[] cum = cumulative(d);
        double target = (u - Math.floor(u)) * cum[SAMPLES];
        int lo = 0;
        int hi = SAMPLES;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        int i = Math.max(1, lo);
        double span = cum[i] - cum[i - 1];
        double f = span == 0 ? 0 : (target - cum[i - 1]) / span;
        double t = (i - 1 + f) * (2 * Math.PI / SAMPLES);
        return at(d, t);
    }

    private static double[] cumulative(double d) {
        double[] cum = new double[SAMPLES + 1];
        Point prev = at(d, 0);
        for (int i = 1; i <= SAMPLES; i++) {
            Point cur = at(d, i * 2 * Math.PI / SAMPLES);
            cum[i] = cum[i - 1] + Math.hypot(cur.x() - prev.x(), cur.z() - prev.z());
            prev = cur;
        }
        return cum;
    }
}
