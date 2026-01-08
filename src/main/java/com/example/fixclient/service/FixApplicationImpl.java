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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import quickfix.Session;

@Service
@Slf4j
public class FixApplicationImpl implements Application {

    private static final int CERT_FIELD = 9479;
    private static final Pattern SEQ_NUM_EXPECTED_PATTERN = Pattern.compile("expected \\[(\\d+)\\]");

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

        if (tryHandlingSeqNumMismatch(sessionID, reason)) {
            return;
        }

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

        // Add Timestamp (9481)
        message.setString(9481, LocalDateTime.now().toString());

        // Add Signature (9489)
        String signature = certificateService.signMessage(message);
        if (signature != null) {
            message.setString(9489, signature);
        } else {
            log.warn("Failed to sign message for session {}", sessionID);
        }
    }

    private boolean tryHandlingSeqNumMismatch(SessionID sessionID, String reason) {
        if (reason.contains("sequence number") && reason.contains("less than the one we expected")) {
            Matcher matcher = SEQ_NUM_EXPECTED_PATTERN.matcher(reason);
            if (matcher.find()) {
                try {
                    int expectedSeqNum = Integer.parseInt(matcher.group(1));
                    log.info(
                            "Detected sequence number mismatch for {}. Acceptor expects {}. Updating local session state.",
                            sessionID, expectedSeqNum);

                    Session session = getSession(sessionID);
                    if (session != null) {
                        session.setNextSenderMsgSeqNum(expectedSeqNum);
                        log.info("Updated next sender sequence number for {} to {}", sessionID, expectedSeqNum);
                        // We return true so that handleLogoutMessage returns early and doesn't stop the
                        // initiator
                        return true;
                    } else {
                        log.warn("Could not find session {} to update sequence number", sessionID);
                    }
                } catch (Exception e) {
                    log.error("Failed to update sequence number after mismatch detection", e);
                }
            }
        }
        return false;
    }

    protected Session getSession(SessionID sessionID) {
        return Session.lookupSession(sessionID);
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
