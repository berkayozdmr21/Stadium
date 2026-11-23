package com.stadyum.support;

import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.Zone;
import com.stadyum.domain.stadium.BlockSpec;
import com.stadyum.domain.stadium.StadiumLayout;
import com.stadyum.domain.stadium.Tier;
import com.stadyum.infrastructure.persistence.EventCatalogWriter;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
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
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    public static final long PRICE = Zone.KAT2.priceKurus();
    /** Test maçlarının tek bloğu; küçük düzende de vardır, böylece blok sorguları geçerli sayılır. */
    public static final String BLOCK = "A01";

    private static EmbeddedPostgres postgres;

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        /** Testler yaklaşık 42.500 koltukluk gerçek düzen yerine küçük bir düzenle çalışır. */
        @Bean
        @Primary
        StadiumLayout testLayout() {
            return StadiumLayout.compact();
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

    /** Tek bloklu ({@value #BLOCK}), {@code rows} sıralı ve her sırası {@code seatsPerRow} koltuklu, 10 gün sonraki maç. */
    protected Event newEvent(String slug, int rows, int seatsPerRow) {
        Tier tier = new Tier("A", "Alt Tribün", rows, 0.85, 23.4, 7.0, 1.2);
        int[] perRow = new int[rows];
        Arrays.fill(perRow, seatsPerRow);
        BlockSpec block = new BlockSpec(BLOCK, "Test Bloğu", "Güney", tier, Zone.KAT2, 0, 1, perRow);
        StadiumLayout layout = new StadiumLayout(tier, tier, List.of(block));
        Event draft = new Event(null, slug, "Test " + slug, "Test", "Stadyum", "Şehir",
                clock.instant().plus(Duration.ofDays(10)), "Açıklama", 0);
        return writer.create(draft, layout);
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
