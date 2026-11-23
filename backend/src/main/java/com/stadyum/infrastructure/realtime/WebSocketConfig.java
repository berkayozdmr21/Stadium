package com.stadyum.infrastructure.realtime;

import com.stadyum.infrastructure.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final EventSocketHandler handler;
    private final AppProperties props;

    WebSocketConfig(EventSocketHandler handler, AppProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Yalnızca izin verilen kaynaklardan (arayüzün adresi) bağlantı kabul edilir.
        registry.addHandler(handler, "/ws/events/*")
                .setAllowedOrigins(props.allowedOrigins().toArray(String[]::new));
    }
}
