package com.example.fixclient.service;

import com.example.fixclient.exception.SessionLogonRequiredException;
import com.example.fixclient.exception.SessionNotFoundException;
import com.example.fixclient.model.MessageRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.TargetCompID;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class BatchMessageSenderService {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> currentTask = new AtomicReference<>();

    private final FixSessionGateway sessionGateway;
    private final SimpMessageSendingOperations messagingTemplate;

    public BatchMessageSenderService(FixSessionGateway sessionGateway, SimpMessageSendingOperations messagingTemplate) {
        this.sessionGateway = sessionGateway;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Starts batch sending of messages at specified interval.
     * Only one batch sender can run at a time.
     *
     * @param request     a MessageRequestDto
     * @param wsSessionId WebSocket session ID of the user requesting the batch
     * @return true if started successfully, false if already running
     */
    public boolean startSending(MessageRequestDto request, String wsSessionId) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Batch sender already running, cannot start another");
            return false;
        }

        log.info("Starting batch sender: {} repeats every {}ms for session {}", request.repeatCount(), request.interval(), request.senderCompId());

        Runnable sendTask = () -> {
            try {
                processMessageBatch(request, wsSessionId, false);
            } catch (Exception e) {
                log.error("Error sending batch messages", e);
            }
        };

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(sendTask, 0, request.interval(), TimeUnit.MILLISECONDS);
        currentTask.set(future);

        return true;
    }

    /**
     * Sends the messages once immediately.
     *
     * @param request     a MessageRequestDto
     * @param wsSessionId WebSocket session ID
     */
    public void sendOnce(MessageRequestDto request, String wsSessionId) {
        log.info("Sending messages once for session {}", request.senderCompId());
        processMessageBatch(request, wsSessionId, true);
    }

    private void processMessageBatch(MessageRequestDto request, String wsSessionId, boolean throwOnError) {
        for (int i = 0; i < request.repeatCount(); i++) {
            int messageIndex = 0;
            for (String rawMsg : request.fixMessages()) {
                messageIndex++;
                try {
                    String sanitizedMessage = sanitizeMessage(rawMsg);

                    // Use non-validating parsing
                    Message message = new Message();
                    message.fromString(sanitizedMessage, null, false);

                    String target = message.getHeader().getString(TargetCompID.FIELD);
                    SessionID sessionId = new SessionID("FIX.4.1", request.senderCompId(), target);

                    if (!sessionGateway.doesSessionExist(sessionId)) {
                        String err = String.format("Session %s does not exist", sessionId);
                        log.warn(err);
                        if (throwOnError) throw new SessionNotFoundException(err);
                        continue;
                    }

                    Message msgToSend = new Message();
                    msgToSend.fromString(sanitizedMessage, null, false);

                    boolean sent = sessionGateway.sendToTarget(msgToSend, sessionId);
                    if (sent) {
                        log.debug("Message {}/{} (iteration {}) sent successfully", messageIndex, request.fixMessages().size(), i + 1);
                        messagingTemplate.convertAndSendToUser(wsSessionId, "/topic/progress", "Sent batch " + (i + 1));
                    } else {
                        String err = "Failed to send message (Logon required)";
                        log.warn(err);
                        if (throwOnError) throw new SessionLogonRequiredException(err);
                    }
                } catch (RuntimeException e) {
                    // Rethrow custom runtime exceptions
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing message", e);
                    if (throwOnError) throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Stops the currently running batch sender.
     */
    public void stopSending() {
        ScheduledFuture<?> task = currentTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
            log.info("Batch sender stopped");
        }
        running.set(false);
    }

    /**
     * Returns whether the batch sender is currently running.
     */
    public boolean isRunning() {
        return running.get();
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
}
