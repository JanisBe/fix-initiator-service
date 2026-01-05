package com.example.fixclient.service;

import com.example.fixclient.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.field.MsgType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixApplicationImpl implements Application {

    private static final Logger log = LoggerFactory.getLogger(FixApplicationImpl.class);
    private static final int CERT_FIELD = 9479;
    
    private final CertificateService certificateService;
    private final Map<SessionID, SessionStatus> sessionStatuses = new ConcurrentHashMap<>();

    public FixApplicationImpl(CertificateService certificateService) {
        this.certificateService = certificateService;
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
        sessionStatuses.put(sessionID, SessionStatus.DISCONNECTED);
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
    public void fromAdmin(Message message, SessionID sessionID) {}

    @Override
    public void toApp(Message message, SessionID sessionID) {
        log.info("[INITIATOR][ToApp] {} {}", sessionID, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) {
        log.info("[INITIATOR][FromApp] {}: {}", sessionID, message);
    }
}
