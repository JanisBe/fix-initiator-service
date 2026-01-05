package com.example.fixclient.controller;

import com.example.fixclient.exception.BatchAlreadyRunningException;
import com.example.fixclient.model.MessageRequestDto;
import com.example.fixclient.model.StartSessionRequest;
import com.example.fixclient.service.BatchMessageSenderService;
import com.example.fixclient.service.FixSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
        StartSessionRequest request = new StartSessionRequest("SENDER", "TARGET", "ENV");
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("ws-123");

        controller.startSession(request, headerAccessor);

        verify(sessionManager).startSession("SENDER", "TARGET", "ENV", "ws-123");
    }

    @Test
    void testStopSession_CallsSessionManager() {
        StartSessionRequest request = new StartSessionRequest("SENDER", "TARGET", "ENV");
        controller.stopSession(request);
        verify(sessionManager).stopSession("SENDER", "TARGET", "ENV");
    }

    @Test
    void testSendMessage_DelegatesToSendOnce_WhenRepeatIsOne() {
        MessageRequestDto request = new MessageRequestDto(1, 1000, "SENDER", List.of("MSG"));
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("ws-123");

        controller.sendMessage(request, headerAccessor);

        verify(batchSender).sendOnce(request, "ws-123");
        verify(batchSender, never()).startSending(any(), any());
    }

    @Test
    void testSendMessage_DelegatesToStartSending_WhenRepeatIsGreaterThanOneAndIntervalPositive() {
        MessageRequestDto request = new MessageRequestDto(2, 1000, "SENDER", List.of("MSG"));
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("ws-123");

        when(batchSender.startSending(request, "ws-123")).thenReturn(true);

        controller.sendMessage(request, headerAccessor);

        verify(batchSender).startSending(request, "ws-123");
        verify(batchSender, never()).sendOnce(any(), any());
    }

    @Test
    void testSendMessage_ThrowsException_WhenBatchAlreadyRunning() {
        MessageRequestDto request = new MessageRequestDto(2, 1000, "SENDER", List.of("MSG"));
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("ws-123");

        when(batchSender.startSending(request, "ws-123")).thenReturn(false);

        assertThrows(BatchAlreadyRunningException.class, () ->
                controller.sendMessage(request, headerAccessor)
        );
    }

    @Test
    void testStopBatchMessages_CallsBatchSender() {
        controller.stopBatchMessages();
        verify(batchSender).stopSending();
    }
}
