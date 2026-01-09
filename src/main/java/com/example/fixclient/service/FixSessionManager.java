package com.example.fixclient.service;

import com.example.fixclient.model.FixSessionKey;
import com.example.fixclient.model.SessionStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FixSessionManager {

    private final FixApplicationImpl application;
    private final DynamicSettingsBuilder settingsBuilder;
    private final Map<FixSessionKey, SocketInitiator> initiators = new ConcurrentHashMap<>();

    // Maps WebSocket Session ID -> Set of FIX Session Keys started by that WS
    // session
    private final Map<String, Set<FixSessionKey>> wsToFixSessions = new ConcurrentHashMap<>();

    // Maps FIX Session ID -> WebSocket Session ID (owner)
    // Used to route incoming messages back to the correct user
    private final Map<SessionID, String> fixSessionOwners = new ConcurrentHashMap<>();

    public FixSessionManager(FixApplicationImpl application, DynamicSettingsBuilder settingsBuilder) {
        this.application = application;
        this.settingsBuilder = settingsBuilder;
    }

    @PostConstruct
    public void init() {
        // Wire callback to avoid circular constructor dependency
        application.setSessionManager(this);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Spring context is shutting down, stopping all FIX sessions...");
        initiators.keySet().forEach(this::stopSessionByKey);
    }

    public void startSession(String sender, String target, String env, String wsSessionId) throws ConfigError {
        FixSessionKey key = new FixSessionKey(sender, target, env);
        if (initiators.containsKey(key)) {
            log.info("Session already active for {}", key);
            return;
        }

        log.info("Starting session for {} (WS Owner: {})", key, wsSessionId);

        SessionSettings settings = settingsBuilder.buildSettings(sender, target, env);

        FileStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        SocketInitiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory,
                messageFactory);
        initiator.start();

        initiators.put(key, initiator);

        // Register owner
        wsToFixSessions.computeIfAbsent(wsSessionId, k -> ConcurrentHashMap.newKeySet()).add(key);
        fixSessionOwners.put(new SessionID("FIX.4.1", sender, target), wsSessionId);
    }

    public void stopSession(String sender, String target, String env) {
        FixSessionKey key = new FixSessionKey(sender, target, env);
        stopSessionByKey(key);
    }

    private void stopSessionByKey(FixSessionKey key) {
        SocketInitiator initiator = initiators.remove(key);
        if (initiator != null) {
            initiator.stop(true);
            log.info("Stopped session for {} (forced)", key);

            // Remove from ownership and reverse maps
            // Note: This is a bit expensive to reverse-lookup from wsToFixSessions if we
            // don't know the WS ID,
            // but usually we stop by WS ID or we can cleanup lazily.
            // Better to cleanup strictly.

            SessionID sessionId = new SessionID("FIX.4.1", key.senderCompId(), key.targetCompId());
            String wsOwner = fixSessionOwners.remove(sessionId);

            if (wsOwner != null) {
                Set<FixSessionKey> sessions = wsToFixSessions.get(wsOwner);
                if (sessions != null) {
                    sessions.remove(key);
                    if (sessions.isEmpty()) {
                        wsToFixSessions.remove(wsOwner);
                    }
                }
            }
        }
    }

    /**
     * Stops all sessions owned by the specific WebSocket session.
     */
    public void stopSessionsByWsId(String wsSessionId) {
        Set<FixSessionKey> ownedSessions = wsToFixSessions.remove(wsSessionId);
        if (ownedSessions != null) {
            log.info("Stopping all sessions for WS Owner: {}", wsSessionId);
            for (FixSessionKey key : ownedSessions) {
                // We call the internal stop that also cleans up maps,
                // but we already removed the set from wsToFixSessions to prevent concurrent
                // modification issues
                // if we iterated directly. However, we need to be careful.
                // The efficient way is to just stop the initiators and remove from maps.

                SocketInitiator initiator = initiators.remove(key);
                if (initiator != null) {
                    initiator.stop(true);
                    log.info("Stopped session {} (forced)", key);
                }

                SessionID sessionId = new SessionID("FIX.4.1", key.senderCompId(), key.targetCompId());
                fixSessionOwners.remove(sessionId);
            }
        }
    }

    /**
     * Stops all sessions matching the given sender and target (any environment).
     * Used by FixApplicationImpl when logon is rejected.
     */
    public void stopSessionByIds(String sender, String target) {
        // Iterate to find keys matching sender/target
        // This is safe because we are iterating the entry set of the map
        initiators.keySet().forEach(key -> {
            if (key.senderCompId().equals(sender) && key.targetCompId().equals(target)) {
                stopSessionByKey(key);
            }
        });
    }

    public SessionStatus getStatus(String sender, String target, String env) {
        SessionID sessionId = new SessionID("FIX.4.1", sender, target);

        if (!initiators.containsKey(new FixSessionKey(sender, target, env))) {
            return SessionStatus.DISCONNECTED;
        }

        return application.getStatus(sessionId);
    }

    public String getOwner(SessionID sessionId) {
        return fixSessionOwners.get(sessionId);
    }
}
