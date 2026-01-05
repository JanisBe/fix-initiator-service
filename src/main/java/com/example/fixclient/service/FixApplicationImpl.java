package com.example.fixclient.service;

import com.example.fixclient.model.SessionStatus;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import quickfix.Application;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FixApplicationImpl implements Application {

    private static final int CERT_FIELD = 9479;

    private final CertificateService certificateService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final Map<SessionID, SessionStatus> sessionStatuses = new ConcurrentHashMap<>();

    @Setter
    private FixSessionManager sessionManager;

    public FixApplicationImpl(CertificateService certificateService, SimpMessageSendingOperations messagingTemplate) {
        this.certificateService = certificateService;
        this.messagingTemplate = messagingTemplate;
    }

    public SessionStatus getStatus(SessionID sessionID) {
        return sessionStatuses.getOrDefault(sessionID, SessionStatus.DISCONNECTED);
    }

    @Override
    public void onCreate(SessionID sessionID) {
        log.info("Session created: {}", sessionID);
        sessionStatuses.put(sessionID, SessionStatus.DISCONNECTED);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        log.info("Logon: {}", sessionID);
        sessionStatuses.put(sessionID, SessionStatus.CONNECTED);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        log.info("Logout: {}", sessionID);
        // Don't overwrite LOGON_REJECTED status
        if (sessionStatuses.get(sessionID) != SessionStatus.LOGON_REJECTED) {
            sessionStatuses.put(sessionID, SessionStatus.DISCONNECTED);
        }
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.LOGON.equals(msgType)) {
                String senderCompId = sessionID.getSenderCompID();
                String certBase64 = certificateService.getCertificateBase64(senderCompId);
                if (certBase64 != null) {
                    message.setString(CERT_FIELD, certBase64);
                    log.info("Injected certificate for {} into Logon message (field {})", senderCompId, CERT_FIELD);
                } else {
                    log.warn("No certificate found for senderCompId: {}", senderCompId);
                }
            }
        } catch (FieldNotFound e) {
            log.error("Error reading MsgType from admin message", e);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.LOGOUT.equals(msgType)) {
                handleLogoutMessage(message, sessionID);
            }
        } catch (FieldNotFound e) {
            log.error("Error reading MsgType from admin message", e);
        }
    }

    /**
     * Handles incoming Logout message from acceptor.
     * If session was never fully connected (logon rejected), stops the initiator.
     */
    void handleLogoutMessage(Message message, SessionID sessionID) {
        String reason = "No reason provided";
        try {
            if (message.isSetField(Text.FIELD)) {
                reason = message.getString(Text.FIELD);
            }
        } catch (FieldNotFound e) {
            // Ignore, use default reason
        }

        log.warn("Received Logout from acceptor for session {}: {}", sessionID, reason);

        SessionStatus currentStatus = sessionStatuses.get(sessionID);
        // If session was never connected, this is a logon rejection
        if (currentStatus != SessionStatus.CONNECTED) {
            log.info("Logon rejected by acceptor - stopping initiator to prevent reconnection");
            sessionStatuses.put(sessionID, SessionStatus.LOGON_REJECTED);

            if (sessionManager != null) {
                // Stop in a separate thread to avoid blocking QuickFIX/J callback
                String sender = sessionID.getSenderCompID();
                String target = sessionID.getTargetCompID();
                new Thread(() -> {
                    try {
                        // Small delay to let QuickFIX/J finish processing
                        Thread.sleep(100);
                        sessionManager.stopSessionByIds(sender, target);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "initiator-stop-" + sender).start();
            } else {
                log.warn("SessionManager not set - cannot stop initiator automatically");
            }
        }
    }

    @Override
    public void toApp(Message message, SessionID sessionID) {
        log.info("[INITIATOR][ToApp] {} {}", sessionID, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) {
        log.info("[INITIATOR][FromApp] {}: {}", sessionID, message);

        if (sessionManager != null) {
            String wsSessionId = sessionManager.getOwner(sessionID);
            if (wsSessionId != null) {
                messagingTemplate.convertAndSendToUser(wsSessionId, "/topic/fixMessages", message.toString());
            }
        }
    }

}
