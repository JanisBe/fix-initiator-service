package com.example.fixclient.service;

import com.example.fixclient.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FixApplicationImplTest {

    @Mock
    private CertificateService certificateService;

    @Mock
    private FixSessionManager sessionManager;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private FixApplicationImpl fixApplication;
    private SessionID sessionID;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fixApplication = new FixApplicationImpl(certificateService, messagingTemplate);
        fixApplication.setSessionManager(sessionManager);
        sessionID = new SessionID("FIX.4.4", "INITIATOR", "ACCEPTOR");
    }

    @Test
    void testToAdmin_InjectsCertificate_WhenLogonMessageAndCertExists() throws FieldNotFound {
        // Arrange
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.LOGON);
        when(certificateService.getCertificateBase64("INITIATOR")).thenReturn("base64Cert");

        // Act
        fixApplication.toAdmin(message, sessionID);

        // Assert
        assertEquals("base64Cert", message.getString(9479));
        verify(certificateService).getCertificateBase64("INITIATOR");
    }

    @Test
    void testToAdmin_DoesNotInjectCertificate_WhenLogonMessageAndNoCert() {
        // Arrange
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.LOGON);
        when(certificateService.getCertificateBase64("INITIATOR")).thenReturn(null);

        // Act
        fixApplication.toAdmin(message, sessionID);

        // Assert
        // Field 9479 should not be set. QuickFIX/J Message throws FieldNotFound or returns false for isSetField
        assertFalse(message.isSetField(9479));
        verify(certificateService).getCertificateBase64("INITIATOR");
    }

    @Test
    void testToAdmin_IgnoresNonLogonMessages() {
        // Arrange
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);

        // Act
        fixApplication.toAdmin(message, sessionID);

        // Assert
        verify(certificateService, never()).getCertificateBase64(anyString());
    }

    @Test
    void testFromAdmin_StopsSession_WhenLogoutReceivedAndNotConnected() throws InterruptedException {
        // Arrange
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.LOGOUT);
        message.setString(Text.FIELD, "Session disconnect fromAdmin"); // Typical rejection message

        // Ensure session status is NOT CONNECTED (default is DISCONNECTED)
        assertEquals(SessionStatus.DISCONNECTED, fixApplication.getStatus(sessionID));

        // Act
        fixApplication.fromAdmin(message, sessionID);

        // Assert
        assertEquals(SessionStatus.LOGON_REJECTED, fixApplication.getStatus(sessionID));

        // Since stopSessionByIds is called in a separate thread, wait a bit
        Thread.sleep(200);
        verify(sessionManager).stopSessionByIds("INITIATOR", "ACCEPTOR");
    }

    @Test
    void testFromAdmin_DoesNotStopSession_WhenLogoutReceivedAndAlreadyConnected() {
        // Arrange
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.LOGOUT);

        // Simulate already connected
        fixApplication.onLogon(sessionID);
        assertEquals(SessionStatus.CONNECTED, fixApplication.getStatus(sessionID));

        // Act
        fixApplication.fromAdmin(message, sessionID);

        // Assert
        // Should NOT change to LOGON_REJECTED, but remain CONNECTED (until onLogout callback actually handles disconnection logic if needed, 
        // but fromAdmin logic specifically shouldn't trigger the stop)
        // Wait... fromAdmin doesn't change status to disconnected, onLogout does. 
        // But fromAdmin logic for REJECTION checks if (currentStatus != CONNECTED).
        // If it IS connected, it skips the stop logic.

        // Verify stopSessionByIds was NOT called
        verify(sessionManager, never()).stopSessionByIds(anyString(), anyString());
    }

    @Test
    void testOnLogout_SetsStatusToDisconnected_WhenStatusIsNotLogonRejected() {
        // Arrange
        fixApplication.onLogon(sessionID);
        assertEquals(SessionStatus.CONNECTED, fixApplication.getStatus(sessionID));

        // Act
        fixApplication.onLogout(sessionID);

        // Assert
        assertEquals(SessionStatus.DISCONNECTED, fixApplication.getStatus(sessionID));
    }

    @Test
    void testOnLogout_PreservesLogonRejectedStatus() {
        // Arrange
        // Simulate extraction of rejection
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, MsgType.LOGOUT);
        fixApplication.fromAdmin(message, sessionID);

        assertEquals(SessionStatus.LOGON_REJECTED, fixApplication.getStatus(sessionID));

        // Act 
        // QuickFIX/J will call onLogout after fromAdmin/socket disconnect
        fixApplication.onLogout(sessionID);

        // Assert
        assertEquals(SessionStatus.LOGON_REJECTED, fixApplication.getStatus(sessionID));
    }
}
