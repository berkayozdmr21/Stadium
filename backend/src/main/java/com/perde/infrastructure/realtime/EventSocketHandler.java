package com.perde.infrastructure.realtime;

import com.perde.infrastructure.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Etkinlik başına bir "oda": o etkinliğin koltuk haritasına bakan herkes aynı odadadır.
 * Sunucu yalnızca yayın yapar; istemciden gelen iletiler yok sayılır (yazma işlemleri REST üzerindendir).
 */
@Component
public class EventSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventSocketHandler.class);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,80}");
    private static final String ROOM_ATTR = "room";
    private static final int SEND_TIME_LIMIT_MS = 2_000;
    private static final int SEND_BUFFER_BYTES = 64 * 1024;
    /** İstemciden veri beklenmiyor; büyük ileti gönderen bağlantı kapatılır. */
    private static final int MAX_INBOUND_BYTES = 1024;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final int maxConnections;

    EventSocketHandler(AppProperties props) {
        this.maxConnections = props.realtime().maxConnections();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String slug = slugOf(session.getUri());
        if (slug == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (connections.incrementAndGet() > maxConnections) {
            connections.decrementAndGet();
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        session.setTextMessageSizeLimit(MAX_INBOUND_BYTES);
        session.setBinaryMessageSizeLimit(MAX_INBOUND_BYTES);
        // Yavaş bir istemci diğerlerine yayını bekletmesin: süre/arabellek aşılırsa bağlantısı kesilir.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        session.getAttributes().put(ROOM_ATTR, slug);
        decorated.put(session.getId(), safe);
        rooms.computeIfAbsent(slug, k -> ConcurrentHashMap.newKeySet()).add(safe);
        broadcastViewers(slug);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketSession safe = decorated.remove(session.getId());
        Object slug = session.getAttributes().get(ROOM_ATTR);
        if (safe == null || !(slug instanceof String room)) {
            return;
        }
        connections.decrementAndGet();
        Set<WebSocketSession> members = rooms.get(room);
        if (members != null) {
            members.remove(safe);
            broadcastViewers(room);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // İstemciden veri kabul edilmez.
    }

    /** Odadaki herkese gönderir. Gönderilemeyen bağlantı kapatılır, diğerleri etkilenmez. */
    public void broadcast(String slug, String json) {
        Set<WebSocketSession> members = rooms.get(slug);
        if (members == null || members.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession member : members) {
            try {
                if (member.isOpen()) {
                    member.sendMessage(message);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Yayın gönderilemedi, bağlantı kapatılıyor: {}", e.getMessage());
                closeQuietly(member);
            }
        }
    }

    public int viewers(String slug) {
        Set<WebSocketSession> members = rooms.get(slug);
        return members == null ? 0 : members.size();
    }

    private void broadcastViewers(String slug) {
        broadcast(slug, "{\"type\":\"viewers\",\"count\":" + viewers(slug) + "}");
    }

    private static String slugOf(URI uri) {
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        String path = uri.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        return SLUG.matcher(last).matches() ? last : null;
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException ignored) {
            // zaten kapanmış
        }
    }
}
