package com.example.fixclient.controller;

import com.example.fixclient.model.BatchMessageRequest;
import com.example.fixclient.model.SessionStatus;
import com.example.fixclient.model.StartSessionRequest;
import com.example.fixclient.service.BatchMessageSenderService;
import com.example.fixclient.service.FixSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

@RestController
@RequestMapping("/api/fix")
public class FixController {

    private static final Logger log = LoggerFactory.getLogger(FixController.class);
    private final FixSessionManager sessionManager;
    private final BatchMessageSenderService batchSender;

    public FixController(FixSessionManager sessionManager, BatchMessageSenderService batchSender) {
        this.sessionManager = sessionManager;
        this.batchSender = batchSender;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startSession(@RequestBody StartSessionRequest request) {
        try {
            sessionManager.startSession(request.senderCompId(), request.targetCompId(), request.environment());
            return ResponseEntity.ok("Initiator started");
        } catch (Exception e) {
            log.error("Error starting session", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopSession(@RequestBody StartSessionRequest request) {
        sessionManager.stopSession(request.senderCompId(), request.targetCompId(), request.environment());
        return ResponseEntity.ok("Initiator stopped");
    }

    @GetMapping("/status")
    public ResponseEntity<SessionStatus> getStatus(@RequestParam String sender, @RequestParam String target, @RequestParam String env) {
        return ResponseEntity.ok(sessionManager.getStatus(sender, target, env));
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody String fixMessage) {
        try {
            String sanitizedMessage = sanitizeMessage(fixMessage);
            Message message = new Message(sanitizedMessage);

            String sender = message.getHeader().getString(49); 
            String target = message.getHeader().getString(56);
            
            SessionID sessionId = new SessionID("FIX.4.4", sender, target);
            if (!Session.doesSessionExist(sessionId)) {
                return ResponseEntity.badRequest().body("Session not found (or not logged on)");
            }
            
            boolean sent = Session.sendToTarget(message, sessionId);
            if (sent) return ResponseEntity.ok("Message queued/sent");
            else return ResponseEntity.status(503).body("Failed to send (Logon required)");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid Message: " + e.getMessage());
        }
    }

    private String sanitizeMessage(String rawInput) {
        String message = rawInput.replace('|', '\u0001');

        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }
        
        if (!message.endsWith("\u0001")) {
            message += "\u0001";
        }

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

    @PostMapping("/batch/start")
    public ResponseEntity<String> startBatchMessages(@RequestBody BatchMessageRequest request) {
        boolean started = batchSender.startSending(
            request.noOfMessages(),
            request.interval(),
            request.senderCompId(),
            request.fixMessage()
        );
        
        if (started) {
            return ResponseEntity.ok("Batch sender started");
        } else {
            return ResponseEntity.status(409).body("Batch sender already running");
        }
    }

    @PostMapping("/batch/stop")
    public ResponseEntity<String> stopBatchMessages() {
        batchSender.stopSending();
        return ResponseEntity.ok("Batch sender stopped");
    }
}
