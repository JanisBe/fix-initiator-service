package com.example.fixclient.controller;

import com.example.fixclient.model.StartSessionRequest;
import com.example.fixclient.service.BatchMessageSenderService;
import com.example.fixclient.service.FixSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import static org.mockito.Mockito.verify;

class FixWebSocketControllerTest {

    @Mock
    private FixSessionManager sessionManager;

    @Mock
    private BatchMessageSenderService batchSender;

    private FixWebSocketController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new FixWebSocketController(sessionManager, batchSender);
    }

    @Test
    void testStartSession_CallsSessionManager() throws Exception {
        // Arrange
        StartSessionRequest request = new StartSessionRequest("SENDER", "TARGET", "ENV");
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("ws-123");

        // Act
        controller.startSession(request, headerAccessor);

        // Assert
        verify(sessionManager).startSession("SENDER", "TARGET", "ENV", "ws-123");
    }

    @Test
    void testStopSession_CallsSessionManager() {
        // Arrange
        StartSessionRequest request = new StartSessionRequest("SENDER", "TARGET", "ENV");

        // Act
        controller.stopSession(request);

        // Assert
        verify(sessionManager).stopSession("SENDER", "TARGET", "ENV");
    }

    @Test
    void testStopBatchMessages_CallsBatchSender() {
        // Act
        controller.stopBatchMessages();

        // Assert
        verify(batchSender).stopSending();
    }
}
