package com.example.fixclient.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import quickfix.*;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TestAcceptorService {

    private SocketAcceptor acceptor;

    private static @NonNull SessionSettings getSessionSettings() {
        SessionSettings settings = new SessionSettings();

        // Default configuration for the acceptor
        Map<Object, Object> defaults = getObjectObjectMap();

        settings.set(defaults);

        SessionID sess1 = new SessionID("FIX.4.1", "ACCEPTOR_A", "INITIATOR1");
        settings.setString(sess1, "SenderCompID", "ACCEPTOR_A");
        settings.setString(sess1, "TargetCompID", "INITIATOR1");

        SessionID sess2 = new SessionID("FIX.4.1", "ACCEPTOR_A", "INITIATOR2");
        settings.setString(sess2, "SenderCompID", "ACCEPTOR_A");
        settings.setString(sess2, "TargetCompID", "INITIATOR2");
        return settings;
    }

    private static @NonNull Map<Object, Object> getObjectObjectMap() {
        Map<Object, Object> defaults = new HashMap<>();
        defaults.put("ConnectionType", "acceptor");
        defaults.put("SocketAcceptPort", "9876");
        defaults.put("StartTime", "00:00:00");
        defaults.put("EndTime", "00:00:00");
        defaults.put("HeartBtInt", "30");
        defaults.put("UseDataDictionary", "Y");
        defaults.put("DataDictionary", "FIX41.xml");
        defaults.put("FileStorePath", "store/acceptor");
        defaults.put("FileLogPath", "acceptor_log");

        defaults.put("SocketUseSSL", "Y");
        defaults.put("SocketKeyStore", "certs/INITIATOR1.p12"); // Re-using for simplicity/testing
        defaults.put("SocketKeyStorePassword", "password");
        return defaults;
    }

    @PostConstruct
    public void startAcceptor() {
        try {
            log.info("Starting Embedded Test Acceptor on port 9876...");
            SessionSettings settings = getSessionSettings();

            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();

            Application application = new Application() {
                @Override
                public void onCreate(SessionID sessionId) {
                    log.info("Acceptor Session Created: {}", sessionId);
                }

                @Override
                public void onLogon(SessionID sessionId) {
                    log.info("Acceptor Session Logon: {}", sessionId);
                }

                @Override
                public void onLogout(SessionID sessionId) {
                    log.info("Acceptor Session Logout: {}", sessionId);
                }

                @Override
                public void toAdmin(Message message, SessionID sessionId) {
                }

                @Override
                public void fromAdmin(Message message, SessionID sessionId) {
                }

                @Override
                public void toApp(Message message, SessionID sessionId) {
                }

                @Override
                public void fromApp(Message message, SessionID sessionId) {
                    log.info("Acceptor Received: {}", message);
                }
            };

            acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
            acceptor.start();
            log.info("Test Acceptor Started.");

        } catch (Exception e) {
            log.error("Failed to start Test Acceptor", e);
        }
    }

    @PreDestroy
    public void stopAcceptor() {
        if (acceptor != null) {
            acceptor.stop();
        }
    }
}
