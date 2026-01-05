package com.example.fixclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.Message;
import quickfix.SessionID;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BatchMessageSenderService {

    private static final Logger log = LoggerFactory.getLogger(BatchMessageSenderService.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> currentTask = new AtomicReference<>();

    private final FixSessionGateway sessionGateway;

    public BatchMessageSenderService(FixSessionGateway sessionGateway) {
        this.sessionGateway = sessionGateway;
    }

    // For backward compatibility or testing if needed, though strictly we should use constructor injection now.
    // We'll rely on Spring creating the bean with constructor injection.

    /**
     * Starts batch sending of messages at specified interval.
     * Only one batch sender can run at a time.
     *
     * @param noOfMessages number of messages to send per interval
     * @param intervalMs   interval between batches in milliseconds
     * @param senderCompId sender comp ID to identify the session
     * @param fixMessage   FIX message template in pipe-delimited format
     * @return true if started successfully, false if already running
     */
    public boolean startSending(int noOfMessages, long intervalMs, String senderCompId, String fixMessage) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Batch sender already running, cannot start another");
            return false;
        }

        log.info("Starting batch sender: {} messages every {}ms for session {}", noOfMessages, intervalMs, senderCompId);

        String sanitizedMessage = sanitizeMessage(fixMessage);

        Runnable sendTask = () -> {
            try {
                // Use non-validating parsing to allow inputs without BodyLength/Checksum
                // The Session.sendToTarget will recalculate them correctly.
                Message message = new Message();
                message.fromString(sanitizedMessage, null, false);
                
                String target = message.getHeader().getString(56);
                SessionID sessionId = new SessionID("FIX.4.4", senderCompId, target);

                if (!sessionGateway.doesSessionExist(sessionId)) {
                    log.warn("Session {} does not exist, skipping batch send", sessionId);
                    return;
                }

                for (int i = 0; i < noOfMessages; i++) {
                    Message msgCopy = new Message();
                    msgCopy.fromString(sanitizedMessage, null, false);
                    boolean sent = sessionGateway.sendToTarget(msgCopy, sessionId);
                    if (sent) {
                        log.debug("Batch message {} sent successfully", i + 1);
                    } else {
                        log.warn("Failed to send batch message {}", i + 1);
                    }
                }
                log.info("Batch of {} messages sent to session {}", noOfMessages, sessionId);
            } catch (Exception e) {
                log.error("Error sending batch messages", e);
            }
        };

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(sendTask, 0, intervalMs, TimeUnit.MILLISECONDS);
        currentTask.set(future);

        return true;
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
