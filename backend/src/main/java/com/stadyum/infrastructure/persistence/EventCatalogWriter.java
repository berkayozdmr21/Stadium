package com.stadyum.infrastructure.persistence;

import com.stadyum.domain.model.Event;
import com.stadyum.domain.model.SeatStatus;
import com.stadyum.domain.stadium.BlockSpec;
import com.stadyum.domain.stadium.StadiumLayout;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maç ve koltuklarını oluşturur. Bir maçın yaklaşık 42.500 koltuğu vardır; JPA ile tek tek eklemek yavaş
 * olacağından koltuklar toplu SQL (batch) ile yazılır.
 */
@Component
public class EventCatalogWriter {

    private static final String INSERT_SEAT = """
            insert into event_seats (event_id, block_code, row_label, row_index, seat_no, zone, price_kurus, status)
            values (?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final int BATCH = 5_000;

    private final EventJpaRepository events;
    private final JdbcTemplate jdbc;

    EventCatalogWriter(EventJpaRepository events, JdbcTemplate jdbc) {
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return events.count() == 0;
    }

    @Transactional
    public Event create(Event draft, StadiumLayout layout) {
        EventEntity saved = events.saveAndFlush(EventEntity.from(draft));

        List<Object[]> rows = new ArrayList<>(BATCH);
        for (BlockSpec block : layout.blocks()) {
            int[] perRow = block.seatsPerRow();
            for (int r = 0; r < perRow.length; r++) {
                for (int no = 1; no <= perRow[r]; no++) {
                    rows.add(new Object[]{saved.getId(), block.code(), String.valueOf(r + 1), r, no,
                            block.zone().name(), block.zone().priceKurus(), SeatStatus.AVAILABLE.name()});
                    if (rows.size() == BATCH) {
                        flush(rows);
                    }
                }
            }
        }
        flush(rows);
        return saved.toDomain();
    }

    private void flush(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_SEAT, rows, rows.size(), (PreparedStatement ps, Object[] row) -> {
            for (int i = 0; i < row.length; i++) {
                setValue(ps, i + 1, row[i]);
            }
        });
        rows.clear();
    }

    private static void setValue(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof Long l) {
            ps.setLong(index, l);
        } else if (value instanceof Integer n) {
            ps.setInt(index, n);
        } else {
            ps.setString(index, (String) value);
        }
    }
}
