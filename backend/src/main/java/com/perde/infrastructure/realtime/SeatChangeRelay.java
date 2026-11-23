package com.perde.infrastructure.realtime;

import com.perde.application.port.SeatChangePublisher;
import com.perde.application.view.SeatChange;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Koltuk değişikliklerini WebSocket'e taşır. Yayın, veritabanı işlemi başarıyla tamamlandıktan SONRA
 * yapılır; geri alınan bir işlemin değişikliği izleyenlere hiç gitmez.
 */
@Component
class SeatChangeRelay implements SeatChangePublisher {

    private final ApplicationEventPublisher events;
    private final EventSocketHandler sockets;

    SeatChangeRelay(ApplicationEventPublisher events, EventSocketHandler sockets) {
        this.events = events;
        this.sockets = sockets;
    }

    @Override
    public void publish(SeatChange change) {
        events.publishEvent(change);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void afterCommit(SeatChange change) {
        // Yalnızca sayı ve sabit adlar içerdiği için elle kurulan JSON güvenlidir.
        String ids = change.seatIds().stream().map(String::valueOf).collect(Collectors.joining(","));
        sockets.broadcast(change.eventSlug(),
                "{\"type\":\"seats\",\"status\":\"" + change.status().name() + "\",\"ids\":[" + ids + "]}");
    }
}
