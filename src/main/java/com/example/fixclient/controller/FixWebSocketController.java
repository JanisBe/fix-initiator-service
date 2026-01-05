package com.example.fixclient.controller;

import com.example.fixclient.model.BatchMessageRequest;
import com.example.fixclient.model.StartSessionRequest;
import com.example.fixclient.service.BatchMessageSenderService;
import com.example.fixclient.service.FixSessionManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

@Controller
public class FixWebSocketController {

    private final FixSessionManager sessionManager;
    private final BatchMessageSenderService batchSender;

    public FixWebSocketController(FixSessionManager sessionManager, BatchMessageSenderService batchSender) {
        this.sessionManager = sessionManager;
        this.batchSender = batchSender;
    }

    @MessageMapping("/startInitiator")
    public void startSession(@Payload StartSessionRequest request, SimpMessageHeaderAccessor headerAccessor) throws Exception {
        String wsSessionId = headerAccessor.getSessionId();
        sessionManager.startSession(request.senderCompId(), request.targetCompId(), request.environment(), wsSessionId);
    }

    @MessageMapping("/stopInitiator")
    public void stopSession(@Payload StartSessionRequest request) {
        sessionManager.stopSession(request.senderCompId(), request.targetCompId(), request.environment());
    }

    @MessageMapping("/sendFixMessages")
    public void sendMessage(@Payload BatchMessageRequest request, SimpMessageHeaderAccessor headerAccessor) throws Exception {
        if (request.noOfMessages() > 1) {
            batchSender.startSending(request, headerAccessor.getSessionId());
        } else {
            String sanitizedMessage = sanitizeMessage(request.fixMessage());
            Message message = new Message(sanitizedMessage);

            String sender = message.getHeader().getString(49);
            String target = message.getHeader().getString(56);

            SessionID sessionId = new SessionID("FIX.4.4", sender, target);
            if (!Session.doesSessionExist(sessionId)) {
                throw new RuntimeException("Session not found (or not logged on)");
            }

            boolean sent = Session.sendToTarget(message, sessionId);
            if (!sent) {
                throw new RuntimeException("Failed to send (Logon required)");
            }
        }
    }

    @MessageMapping("/stopSendingBulkMessages")
    public void stopBatchMessages() {
        batchSender.stopSending();
    }

    // Copying sanitize logic from old controller as requested to keep logic same
    private String sanitizeMessage(String rawInput) {
        String message = rawInput.replace('|', '\u0001');

        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }

        if (!message.endsWith("\u0001")) {
            message += "\u0001";
        }

        // Logic to remove existing checksum if present to recalculate it??
        // The old controller had this logic:
        int checksumIndex = message.lastIndexOf("\u000110=");
        if (checksumIndex != -1) {
            message = message.substring(0, checksumIndex + 1);
        } else if (message.startsWith("10=")) {
            message = "";
        }

        int checksum = 0;
        for (int i = 0; i < message.length(); i++) {
            checksum += message.charAt(i);
        }
        checksum = checksum % 256;

        String checksumStr = String.format("%03d", checksum);

        return message + "10=" + checksumStr + "\u0001";
    }
}
