package com.example.fixclient.service;

import com.example.fixclient.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixApplicationImpl implements Application {

    private static final Logger log = LoggerFactory.getLogger(FixApplicationImpl.class);
    private final Map<SessionID, SessionStatus> sessionStatuses = new ConcurrentHashMap<>();
    
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
    public void toAdmin(Message message, SessionID sessionID) {}

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {}

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.info("Received message from {}: {}", sessionID, message);
    }
}
