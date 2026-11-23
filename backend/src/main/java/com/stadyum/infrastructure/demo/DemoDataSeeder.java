package com.stadyum.infrastructure.demo;

import com.stadyum.domain.model.Event;
import com.stadyum.domain.stadium.StadiumLayout;
import com.stadyum.infrastructure.persistence.EventCatalogWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Boş veritabanına örnek maçlar yükler. Maçlar, rakip takımlar ve tarihler kurgusaldır; bu bir portfolyo
 * demosudur ve gerçek bir fikstürü ya da gerçek bilet satışını temsil etmez. Tarihler çalıştırma anına göre
 * hesaplanır, böylece demo her zaman "yaklaşan" maçlar gösterir.
 */
@Component
@ConditionalOnProperty(prefix = "stadyum.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String VENUE = "Tüpraş Stadyumu";

    private final EventCatalogWriter writer;
    private final StadiumLayout layout;
    private final Clock clock;

    DemoDataSeeder(EventCatalogWriter writer, StadiumLayout layout, Clock clock) {
        this.writer = writer;
        this.layout = layout;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!writer.isEmpty()) {
            return;
        }
        writer.create(match("ornek-mac-marmara", "Beşiktaş – Marmara Gençlik", "Örnek maç", 5,
                "Kurgusal bir lig maçı. Takım adları ve tarih örnektir."), layout);
        writer.create(match("ornek-mac-kuzey", "Beşiktaş – Kuzey Yıldızı", "Örnek maç", 12,
                "Kurgusal bir lig maçı. Takım adları ve tarih örnektir."), layout);
        writer.create(match("ornek-mac-anadolu", "Beşiktaş – Anadolu Birliği", "Örnek maç", 19,
                "Kurgusal bir kupa maçı. Takım adları ve tarih örnektir."), layout);
        log.info("Örnek maçlar yüklendi ({} koltuk/maç)", layout.totalSeats());
    }

    private Event match(String slug, String title, String category, int daysFromNow, String description) {
        // 16:00 UTC = Türkiye saatiyle 19:00
        Instant startsAt = clock.instant().truncatedTo(ChronoUnit.DAYS)
                .plus(Duration.ofDays(daysFromNow)).plus(Duration.ofHours(16));
        return new Event(null, slug, title, category, VENUE, "İstanbul", startsAt, description, 0);
    }
}
