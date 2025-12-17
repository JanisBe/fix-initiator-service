package com.example.fixclient.service;

import com.example.fixclient.model.FixSessionKey;
import com.example.fixclient.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixSessionManager {

    private static final Logger log = LoggerFactory.getLogger(FixSessionManager.class);

    private final ConfigService configService;
    private final FixApplicationImpl application;
    private final DynamicSettingsBuilder settingsBuilder;
    private final Map<FixSessionKey, SocketInitiator> initiators = new ConcurrentHashMap<>();

    public FixSessionManager(ConfigService configService, FixApplicationImpl application, DynamicSettingsBuilder settingsBuilder) {
        this.configService = configService;
        this.application = application;
        this.settingsBuilder = settingsBuilder;
    }

    public void startSession(String sender, String target, String env) throws ConfigError {
        FixSessionKey key = new FixSessionKey(sender, target, env);
        if (initiators.containsKey(key)) {
            log.info("Session already active for {}", key);
            return;
        }

        log.info("Starting session for {}", key);
        
        SessionSettings settings = settingsBuilder.buildSettings(sender, target, env);
        
        FileStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        SocketInitiator initiator = new SocketInitiator(application, storeFactory, settings, logFactory, messageFactory);
        initiator.start();
        
        initiators.put(key, initiator);
    }

    public void stopSession(String sender, String target, String env) {
        FixSessionKey key = new FixSessionKey(sender, target, env);
        SocketInitiator initiator = initiators.remove(key);
        if (initiator != null) {
            initiator.stop();
            log.info("Stopped session for {}", key);
        }
    }

    public SessionStatus getStatus(String sender, String target, String env) {
        SessionID sessionId = new SessionID("FIX.4.4", sender, target);
        
        if (!initiators.containsKey(new FixSessionKey(sender, target, env))) {
            return SessionStatus.DISCONNECTED;
        }
        
        return application.getStatus(sessionId);
    }
}
