package com.perde.infrastructure.persistence;

import com.perde.domain.model.Event;
import com.perde.domain.model.Zone;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Etkinlik ve salon düzeni oluşturur. Demo verisi ve testler tarafından kullanılır. */
@Component
public class EventCatalogWriter {

    /** Salondaki bir sıra: etiketi, koltuk sayısı, fiyat bölgesi ve koltuk fiyatı. */
    public record RowSpec(String label, int seats, Zone zone, long priceKurus) {
    }

    private final EventJpaRepository events;
    private final SeatJpaRepository seats;

    EventCatalogWriter(EventJpaRepository events, SeatJpaRepository seats) {
        this.events = events;
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return events.count() == 0;
    }

    @Transactional
    public Event create(Event draft, List<RowSpec> rows) {
        EventEntity saved = events.save(EventEntity.from(draft));
        List<EventSeatEntity> all = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            RowSpec row = rows.get(rowIndex);
            for (int no = 1; no <= row.seats(); no++) {
                all.add(EventSeatEntity.available(saved.getId(), row.label(), rowIndex, no, row.zone(), row.priceKurus()));
            }
        }
        seats.saveAll(all);
        return saved.toDomain();
    }
}
