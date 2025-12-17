package com.example.fixclient.controller;

import com.example.fixclient.model.SessionStatus;
import com.example.fixclient.model.StartSessionRequest;
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

    public FixController(FixSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startSession(@RequestBody StartSessionRequest request) {
        try {
            sessionManager.startSession(request.getSenderCompId(), request.getTargetCompId(), request.getEnvironment());
            return ResponseEntity.ok("Initiator started");
        } catch (Exception e) {
            log.error("Error starting session", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopSession(@RequestBody StartSessionRequest request) {
        sessionManager.stopSession(request.getSenderCompId(), request.getTargetCompId(), request.getEnvironment());
        return ResponseEntity.ok("Initiator stopped");
    }

    @GetMapping("/status")
    public ResponseEntity<SessionStatus> getStatus(@RequestParam String sender, @RequestParam String target, @RequestParam String env) {
        return ResponseEntity.ok(sessionManager.getStatus(sender, target, env));
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody String fixMessage) {
        try {
            Message message = new Message(fixMessage);
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
}
