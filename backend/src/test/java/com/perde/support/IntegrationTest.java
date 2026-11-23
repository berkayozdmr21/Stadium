package com.perde.support;

import com.perde.domain.model.Event;
import com.perde.domain.model.Zone;
import com.perde.infrastructure.persistence.EventCatalogWriter;
import com.perde.infrastructure.persistence.EventCatalogWriter.RowSpec;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gerçek veritabanıyla çalışan testlerin ortak tabanı.
 *
 * <p>Varsayılan veritabanı bellek içi H2'dir. {@code -Dtestdb=postgres} verilirse aynı testler gömülü
 * gerçek bir PostgreSQL üzerinde koşar; kilitleme davranışı veritabanına göre değiştiği için
 * eşzamanlılık testlerinin asıl kanıtı odur.
 */
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    public static final long PRICE = 10_000;

    private static EmbeddedPostgres postgres;

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        if (!"postgres".equalsIgnoreCase(System.getProperty("testdb"))) {
            return;
        }
        synchronized (IntegrationTest.class) {
            if (postgres == null) {
                postgres = EmbeddedPostgres.builder().start();
            }
        }
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    protected EventCatalogWriter writer;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from event_seats");
        jdbc.update("delete from bookings");
        jdbc.update("delete from seat_holds");
        jdbc.update("delete from events");
        clock.reset();
    }

    /** Her sırası {@code seatsPerRow} koltuklu, 10 gün sonra başlayacak bir etkinlik kurar. */
    protected Event newEvent(String slug, int rows, int seatsPerRow) {
        List<RowSpec> specs = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            specs.add(new RowSpec(String.valueOf((char) ('A' + i)), seatsPerRow, Zone.SALON, PRICE));
        }
        Clock c = clock;
        Event draft = new Event(null, slug, "Test " + slug, "Test", "Salon", "Şehir",
                c.instant().plus(Duration.ofDays(10)), "Açıklama", 200);
        return writer.create(draft, specs);
    }

    protected List<Long> seatIds(Event event) {
        return jdbc.queryForList("select id from event_seats where event_id = ? order by row_index, seat_no",
                Long.class, event.id());
    }

    protected String seatStatus(long seatId) {
        return jdbc.queryForObject("select status from event_seats where id = ?", String.class, seatId);
    }

    protected int countSeats(Event event, String status) {
        Integer n = jdbc.queryForObject("select count(*) from event_seats where event_id = ? and status = ?",
                Integer.class, event.id(), status);
        return n == null ? 0 : n;
    }
}
