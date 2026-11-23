package com.perde.infrastructure.demo;

import com.perde.domain.model.Event;
import com.perde.domain.model.Zone;
import com.perde.infrastructure.persistence.EventCatalogWriter;
import com.perde.infrastructure.persistence.EventCatalogWriter.RowSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Boş veritabanına örnek etkinlikleri yükler. Etkinlik, sanatçı ve mekân adlarının tümü kurgusaldır.
 * Tarihler çalıştırma anına göre hesaplanır, böylece demo her zaman "yaklaşan" etkinlikler gösterir.
 */
@Component
@ConditionalOnProperty(prefix = "perde.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final EventCatalogWriter writer;
    private final Clock clock;

    DemoDataSeeder(EventCatalogWriter writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!writer.isEmpty()) {
            return;
        }
        writer.create(event("pazartesi-sendromu", "Pazartesi Sendromu", "Stand-up", "Köşe Sahne", "Bursa", 2, 140,
                        "Haftanın en zor gününe adanmış tek kişilik gösteri. Kahvenizi bitirmeden gelmeyin."),
                club(45_000, 30_000));
        writer.create(event("anadolu-caz-gecesi", "Anadolu Caz Gecesi", "Caz", "Tünel Sahne", "İstanbul", 4, 28,
                        "Bağlama ile kontrbasın aynı sahnede buluştuğu, doğaçlaması bol bir gece."),
                club(60_000, 42_000));
        writer.create(event("sessiz-film-canli-piyano", "Sessiz Film Gecesi", "Sinema", "Açık Hava Sineması", "İzmir", 6, 190,
                        "Perdede sessiz bir klasik, sahnede ona eşlik eden canlı piyano."),
                theatre(38_000, 26_000, 18_000));
        writer.create(event("gece-yarisi-senfonisi", "Gece Yarısı Senfonisi", "Klasik", "Lale Konser Salonu", "İstanbul", 9, 262,
                        "Seksen kişilik orkestra, geç saatte başlayan ve ışıkların yavaşça söndüğü bir program."),
                grandHall(120_000, 75_000, 45_000));
        writer.create(event("kirmizi-perde", "Kırmızı Perde", "Tiyatro", "Eski Şehir Tiyatrosu", "Ankara", 14, 350,
                        "Bir prova gecesinde kaybolan başrol oyuncusu ve perdeyi yine de açmak zorunda kalan ekip."),
                theatre(55_000, 38_000, 24_000));
        writer.create(event("elektronik-ruya", "Elektronik Rüya", "Elektronik", "Depo 34", "İstanbul", 21, 300,
                        "Eski bir depoda sabaha kadar süren ışık ve ses yerleştirmesi."),
                grandHall(90_000, 60_000, 35_000));
        log.info("Örnek etkinlikler yüklendi");
    }

    private Event event(String slug, String title, String category, String venue, String city,
                        int daysFromNow, int hue, String description) {
        // 17:30 UTC = Türkiye saatiyle 20:30
        Instant startsAt = clock.instant().truncatedTo(ChronoUnit.DAYS)
                .plus(Duration.ofDays(daysFromNow)).plus(Duration.ofMinutes(17 * 60 + 30));
        return new Event(null, slug, title, category, venue, city, startsAt, description, hue);
    }

    /** 7 sıra, 94 koltuk: küçük sahne. */
    private static List<RowSpec> club(long vip, long salon) {
        return rows(new int[]{10, 12, 12, 14, 14, 16, 16}, 2, 7, vip, salon, 0);
    }

    /** 10 sıra, 166 koltuk. */
    private static List<RowSpec> theatre(long vip, long salon, long balkon) {
        return rows(new int[]{12, 14, 14, 16, 16, 18, 18, 18, 20, 20}, 2, 7, vip, salon, balkon);
    }

    /** 14 sıra, 312 koltuk. */
    private static List<RowSpec> grandHall(long vip, long salon, long balkon) {
        return rows(new int[]{16, 18, 18, 20, 20, 22, 22, 24, 24, 24, 26, 26, 26, 26}, 3, 10, vip, salon, balkon);
    }

    /**
     * @param vipRows   ilk kaç sıra VIP
     * @param salonEnd  bu sıra numarasına kadar (hariç) Salon, sonrası Balkon
     */
    private static List<RowSpec> rows(int[] seatsPerRow, int vipRows, int salonEnd, long vip, long salon, long balkon) {
        List<RowSpec> rows = new ArrayList<>();
        for (int i = 0; i < seatsPerRow.length; i++) {
            String label = String.valueOf((char) ('A' + i));
            if (i < vipRows) {
                rows.add(new RowSpec(label, seatsPerRow[i], Zone.VIP, vip));
            } else if (i < salonEnd) {
                rows.add(new RowSpec(label, seatsPerRow[i], Zone.SALON, salon));
            } else {
                rows.add(new RowSpec(label, seatsPerRow[i], Zone.BALKON, balkon));
            }
        }
        return rows;
    }
}
