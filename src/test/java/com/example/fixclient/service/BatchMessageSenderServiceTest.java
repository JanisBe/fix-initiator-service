package com.example.fixclient.service;

import com.example.fixclient.exception.SessionLogonRequiredException;
import com.example.fixclient.exception.SessionNotFoundException;
import com.example.fixclient.model.MessageRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.util.List;

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
        String rawMessage = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=TEST|";

        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.sendToTarget(any(Message.class), any(SessionID.class))).thenReturn(true);

        // Act
        boolean started = service.startSending(new MessageRequestDto(2, 50, senderCompId, List.of(rawMessage)), "ws-session-id");

        // Assert
        assertTrue(started);
        assertTrue(service.isRunning());

        // Wait for execution
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            verify(sessionGateway, atLeast(2)).sendToTarget(any(Message.class), any(SessionID.class));
            verify(messagingTemplate, atLeast(2)).convertAndSendToUser(eq("ws-session-id"), eq("/topic/progress"), any());
        } catch (SessionNotFound e) {
            fail("Should not throw exception");
        }
    }

    @Test
    void testStartSending_PreventsConcurrentRuns() {
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        boolean first = service.startSending(new MessageRequestDto(1, 1000, "INIT1", List.of("8=FIX.4.1|35=D|56=T1|")), "ws1");
        boolean second = service.startSending(new MessageRequestDto(1, 1000, "INIT2", List.of("8=FIX.4.1|35=D|56=T2|")), "ws2");
        assertTrue(first);
        assertFalse(second);
    }

    @Test
    void testStopSending() {
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        service.startSending(new MessageRequestDto(1, 100, "INIT", List.of("8=FIX.4.1|35=D|56=T|")), "ws1");
        assertTrue(service.isRunning());

        service.stopSending();

        assertFalse(service.isRunning());
    }

    @Test
    void testSanitizeMessage_FixesDelimitersAndChecksum() {
        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        service.startSending(new MessageRequestDto(1, 1000, "INIT", List.of("8=FIX.4.1|35=D|56=T|55=TEST|")), "ws1");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            verify(sessionGateway, atLeast(1)).sendToTarget(
                    argThat(msg -> msg.toString().contains("10=")), any(SessionID.class)
            );
        } catch (SessionNotFound e) {
            fail(e);
        }
    }

    @Test
    void testSendOnce_SendsImmediately() throws SessionNotFound {
        String senderCompId = "INITIATOR";
        String rawMessage = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=TEST|";

        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.sendToTarget(any(Message.class), any(SessionID.class))).thenReturn(true);

        service.sendOnce(new MessageRequestDto(1, 0, senderCompId, List.of(rawMessage)), "ws-session-id");

        verify(sessionGateway, times(1)).sendToTarget(any(Message.class), any(SessionID.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("ws-session-id"), eq("/topic/progress"), any());
    }

    @Test
    void testSendOnce_MultipleMessages() throws SessionNotFound {
        String senderCompId = "INITIATOR";
        String rawMessage1 = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=MSG1|";
        String rawMessage2 = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=MSG2|";

        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.sendToTarget(any(Message.class), any(SessionID.class))).thenReturn(true);

        service.sendOnce(new MessageRequestDto(1, 0, senderCompId, List.of(rawMessage1, rawMessage2)), "ws-session-id");

        verify(sessionGateway, times(2)).sendToTarget(any(Message.class), any(SessionID.class));
    }

    @Test
    void testSendOnce_ThrowsSessionNotFound() {
        String senderCompId = "INITIATOR";
        String rawMessage = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=TEST|";

        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(false);

        assertThrows(SessionNotFoundException.class, () ->
                service.sendOnce(new MessageRequestDto(1, 0, senderCompId, List.of(rawMessage)), "ws-session-id")
        );
    }

    @Test
    void testSendOnce_ThrowsSessionLogonRequired() throws SessionNotFound {
        String senderCompId = "INITIATOR";
        String rawMessage = "8=FIX.4.1|35=D|49=INITIATOR|56=ACCEPTOR|55=TEST|";

        when(sessionGateway.doesSessionExist(any(SessionID.class))).thenReturn(true);
        when(sessionGateway.sendToTarget(any(Message.class), any(SessionID.class))).thenReturn(false);

        assertThrows(SessionLogonRequiredException.class, () ->
                service.sendOnce(new MessageRequestDto(1, 0, senderCompId, List.of(rawMessage)), "ws-session-id")
        );
    }
}
