package com.example.fixclient.service;

import com.example.fixclient.model.BatchMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class BatchMessageSenderServiceTest {

    private BatchMessageSenderService service;

    @Mock
    private FixSessionGateway sessionGateway;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new BatchMessageSenderService(sessionGateway, messagingTemplate);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        service.stopSending();
    }

    @Test
    void testStartSending_SendsMessages() throws SessionNotFound {
        // Arrange
        String senderCompId = "INITIATOR";
        // Header fields (8, 35, 49, 56) MUST come before Body fields (55)
        String rawMessage = "8=FIX.4.4|35=D|49=INITIATOR|56=ACCEPTOR|55=TEST|";

        // Mock Gateway behavior
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.sendToTarget(any(Message.class), any(SessionID.class))).thenReturn(true);

        // Act
        boolean started = service.startSending(new BatchMessageRequest(2, 50, senderCompId, rawMessage), "ws-session-id");

        // Assert
        assertTrue(started);
        assertTrue(service.isRunning());

        // Wait for at least one execution
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Verify sendToTarget called
        try {
            verify(sessionGateway, atLeast(2)).sendToTarget(any(Message.class), any(SessionID.class));
            verify(messagingTemplate, atLeast(2)).convertAndSendToUser(eq("ws-session-id"), eq("/topic/progress"), any());
        } catch (SessionNotFound e) {
            fail("Should not throw exception");
        }
    }

    @Test
    void testStartSending_PreventsConcurrentRuns() {
        // Arrange
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);

        // Act
        boolean first = service.startSending(new BatchMessageRequest(1, 1000, "INIT1", "8=FIX.4.4|35=D|56=T1|"), "ws1");
        boolean second = service.startSending(new BatchMessageRequest(1, 1000, "INIT2", "8=FIX.4.4|35=D|56=T2|"), "ws2");

        // Assert
        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void testStopSending() {
        // Arrange
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        service.startSending(new BatchMessageRequest(1, 100, "INIT", "8=FIX.4.4|35=D|56=T|"), "ws1");
        assertTrue(service.isRunning());

        // Act
        service.stopSending();

        // Assert
        assertFalse(service.isRunning());
    }

    @Test
    void testSanitizeMessage_FixesDelimitersAndChecksum() {
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);

        // Ensure TargetCompID (56) is in header/before body in case logic parses it
        service.startSending(new BatchMessageRequest(1, 1000, "INIT", "8=FIX.4.4|35=D|56=T|55=TEST|"), "ws1");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            verify(sessionGateway, atLeast(1)).sendToTarget(argThat(msg -> {
                return msg.toString().contains("10="); // simplified check
            }), any(SessionID.class));
        } catch (SessionNotFound e) {
            fail(e);
        }
    }
}
