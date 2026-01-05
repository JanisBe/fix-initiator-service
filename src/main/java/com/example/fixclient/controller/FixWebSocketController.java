package com.example.fixclient.controller;

import com.example.fixclient.exception.BatchAlreadyRunningException;
import com.example.fixclient.model.MessageRequestDto;
import com.example.fixclient.model.StartSessionRequest;
import com.example.fixclient.service.BatchMessageSenderService;
import com.example.fixclient.service.FixSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class FixWebSocketController {

    private final FixSessionManager sessionManager;
    private final BatchMessageSenderService batchSender;

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
    public void sendMessage(@Payload MessageRequestDto request, SimpMessageHeaderAccessor headerAccessor) {
        if (request.repeatCount() > 1 && request.interval() > 0) {
            if (!batchSender.startSending(request, headerAccessor.getSessionId())) {
                throw new BatchAlreadyRunningException("Batch sender is already running");
            }
        } else {
            batchSender.sendOnce(request, headerAccessor.getSessionId());
        }
    }

    @MessageMapping("/stopSendingBulkMessages")
    public void stopBatchMessages() {
        batchSender.stopSending();
    }
}
