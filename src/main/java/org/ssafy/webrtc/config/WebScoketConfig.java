package org.ssafy.webrtc.config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.ssafy.webrtc.SignalingHandler;

import java.util.logging.SocketHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebScoketConfig implements WebSocketConfigurer
{

    private final SignalingHandler signalingHandler ;


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler,"/ws/signal")
                .setAllowedOrigins("*");
    }
}
