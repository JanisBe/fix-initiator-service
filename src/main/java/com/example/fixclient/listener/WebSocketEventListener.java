package com.example.fixclient.listener;

import com.example.fixclient.service.FixSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
public class WebSocketEventListener {

    private final FixSessionManager sessionManager;

    public WebSocketEventListener(FixSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        log.info("WebSocket Disconnected. Session ID: {}", sessionId);

        sessionManager.stopSessionsByWsId(sessionId);
    }
}
