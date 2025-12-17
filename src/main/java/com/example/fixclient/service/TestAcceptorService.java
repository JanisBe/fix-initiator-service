package com.example.fixclient.service;

import lombok.extern.slf4j.Slf4j; // Can't use lombok, careful
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class TestAcceptorService {
    
    private static final Logger log = LoggerFactory.getLogger(TestAcceptorService.class);
    private SocketAcceptor acceptor;

    @PostConstruct
    public void startAcceptor() {
        try {
            log.info("Starting Embedded Test Acceptor on port 9876...");
            SessionSettings settings = getSessionSettings();

            // And maybe a catch-all if we want dynamic? 
            // settings.setString(SessionID.NOT_APPLICABLE, "AcceptorTemplate", "Y"); (Not standard prop, checking documentation)
            // QuickFIX/J supports [Session] with defaults inherited.
            // For now explicit sessions are safer.

            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            LogFactory logFactory = new ScreenLogFactory(settings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            
            Application application = new Application() {
                @Override public void onCreate(SessionID sessionId) { log.info("Acceptor Session Created: " + sessionId); }
                @Override public void onLogon(SessionID sessionId) { log.info("Acceptor Session Logon: " + sessionId); }
                @Override public void onLogout(SessionID sessionId) { log.info("Acceptor Session Logout: " + sessionId); }
                @Override public void toAdmin(Message message, SessionID sessionId) {}
                @Override public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}
                @Override public void toApp(Message message, SessionID sessionId) throws DoNotSend {}
                @Override public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
                    log.info("Acceptor Received: " + message);
                }
            };

            acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
            acceptor.start();
            log.info("Test Acceptor Started.");
            
        } catch (Exception e) {
            log.error("Failed to start Test Acceptor", e);
        }
    }

    private static @NonNull SessionSettings getSessionSettings() {
        SessionSettings settings = new SessionSettings();

        // Default configuration for the acceptor
        Map<Object, Object> defaults = getObjectObjectMap();

        settings.set(defaults);

        SessionID sess1 = new SessionID("FIX.4.4", "ACCEPTOR_A", "INITIATOR1");
        settings.setString(sess1, "SenderCompID", "ACCEPTOR_A");
        settings.setString(sess1, "TargetCompID", "INITIATOR1");

        SessionID sess2 = new SessionID("FIX.4.4", "ACCEPTOR_A", "INITIATOR2");
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
        defaults.put("DataDictionary", "FIX44.xml");
        defaults.put("FileStorePath", "acceptor_store");
        defaults.put("FileLogPath", "acceptor_log");

        defaults.put("SocketUseSSL", "Y");
        defaults.put("SocketKeyStore", "certs/INITIATOR1.p12"); // Re-using for simplicity/testing
        defaults.put("SocketKeyStorePassword", "password");
        return defaults;
    }

    @PreDestroy
    public void stopAcceptor() {
        if (acceptor != null) {
            acceptor.stop();
        }
    }
}
