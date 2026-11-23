package com.perde.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perde.application.service.BookingService;
import com.perde.application.service.HoldService;
import com.perde.domain.DomainException;
import com.perde.domain.model.Event;
import com.perde.support.IntegrationTest;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Gerçek bir sunucu portuna gerçek bir WebSocket istemcisiyle bağlanır. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RealtimeTest extends IntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @LocalServerPort
    int port;
    @Autowired
    HoldService holds;
    @Autowired
    BookingService bookings;

    @Test
    void watchersSeeSeatChangesAndViewerCountLive() throws Exception {
        Event event = newEvent("canli", 1, 4);
        List<Long> seats = seatIds(event);
        Listener first = new Listener();
        Listener second = new Listener();

        WebSocketSession s1 = connect("canli", ALLOWED_ORIGIN, first);
        assertThat(first.next()).isEqualTo("{\"type\":\"viewers\",\"count\":1}");
        WebSocketSession s2 = connect("canli", ALLOWED_ORIGIN, second);
        assertThat(first.next()).isEqualTo("{\"type\":\"viewers\",\"count\":2}");
        assertThat(second.next()).isEqualTo("{\"type\":\"viewers\",\"count\":2}");

        String token = holds.addSeat("canli", seats.get(0), null).token();
        String held = "{\"type\":\"seats\",\"status\":\"HELD\",\"ids\":[" + seats.get(0) + "]}";
        assertThat(first.next()).isEqualTo(held);
        assertThat(second.next()).isEqualTo(held);

        bookings.confirm("canli", token, "Ada Yılmaz", "ada@example.com");
        assertThat(second.next()).isEqualTo("{\"type\":\"seats\",\"status\":\"SOLD\",\"ids\":[" + seats.get(0) + "]}");
        assertThat(first.next()).contains("SOLD");

        s2.close();
        assertThat(first.next()).isEqualTo("{\"type\":\"viewers\",\"count\":1}");
        s1.close();
    }

    @Test
    void aFailedAttemptIsNeverBroadcast() throws Exception {
        Event event = newEvent("sessiz", 1, 2);
        long seat = seatIds(event).get(0);
        holds.addSeat("sessiz", seat, null);
        Listener listener = new Listener();
        WebSocketSession session = connect("sessiz", ALLOWED_ORIGIN, listener);
        assertThat(listener.next()).contains("viewers");

        assertThatThrownBy(() -> holds.addSeat("sessiz", seat, null)).isInstanceOf(DomainException.class);

        assertThat(listener.poll(700)).as("geri alınan işlem yayınlanmaz").isNull();
        session.close();
    }

    @Test
    void watchersOfOtherEventsAreNotDisturbed() throws Exception {
        newEvent("oda-a", 1, 2);
        Event b = newEvent("oda-b", 1, 2);
        Listener onA = new Listener();
        WebSocketSession session = connect("oda-a", ALLOWED_ORIGIN, onA);
        assertThat(onA.next()).contains("viewers");

        holds.addSeat("oda-b", seatIds(b).get(0), null);

        assertThat(onA.poll(700)).isNull();
        session.close();
    }

    @Test
    void connectionsFromUnknownOriginsAreRefused() {
        newEvent("kapali", 1, 2);

        assertThatThrownBy(() -> connect("kapali", "https://kotu.example", new Listener()))
                .isInstanceOf(ExecutionException.class);
    }

    private WebSocketSession connect(String slug, String origin, Listener listener) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return new StandardWebSocketClient()
                .execute(listener, headers, URI.create("ws://localhost:" + port + "/ws/events/" + slug))
                .get(5, TimeUnit.SECONDS);
    }

    private static class Listener extends TextWebSocketHandler {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("5 saniye içinde ileti gelmedi").isNotNull();
            return message;
        }

        String poll(long millis) throws InterruptedException {
            return messages.poll(millis, TimeUnit.MILLISECONDS);
        }
    }
}
