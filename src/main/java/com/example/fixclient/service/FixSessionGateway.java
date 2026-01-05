package com.example.fixclient.service;

import org.springframework.stereotype.Component;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;

/**
 * Gateway to wrap static QuickFIX/J Session calls for testability.
 */
@Component
public class FixSessionGateway {

    public boolean doesSessionExist(SessionID sessionID) {
        return Session.doesSessionExist(sessionID);
    }

    public boolean sendToTarget(Message message, SessionID sessionID) throws SessionNotFound {
        return Session.sendToTarget(message, sessionID);
    }
}
