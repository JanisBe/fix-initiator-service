package com.example.fixclient.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.io.File;

@Service
public class DynamicSettingsBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicSettingsBuilder.class);

    private final ConfigService configService;

    public DynamicSettingsBuilder(ConfigService configService) {
        this.configService = configService;
    }

    public SessionSettings buildSettings(String sender, String target, String envName) {
        if (!configService.isValid(envName, target, sender)) {
            // Maybe log WARN but proceed, or throw.
        }

        SessionSettings settings = new SessionSettings();
        SessionID sessionID = new SessionID("FIX.4.4", sender, target);

        // Basic Connection Info
        settings.setString(sessionID, "ConnectionType", "initiator");
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", sender);
        settings.setString(sessionID, "TargetCompID", target);

        String address = configService.getAddress(envName);
        int port = configService.getPort(envName);

        settings.setString(sessionID, "SocketConnectHost", address != null ? address : "127.0.0.1");
        settings.setLong(sessionID, "SocketConnectPort", port);

        settings.setString(sessionID, "HeartBtInt", "30");
        settings.setString(sessionID, "ValidateUserDefinedFields", "N");
        settings.setString(sessionID, "ReconnectInterval", "5");
        settings.setString(sessionID, "StartTime", "00:00:00");
        settings.setString(sessionID, "EndTime", "00:00:00");
        settings.setString(sessionID, "UseDataDictionary", "Y");
        settings.setString(sessionID, "DataDictionary", "FIX44.xml");
        settings.setString(sessionID, "FileStorePath", "store/initiator");
        settings.setString(sessionID, "FileLogPath", "acceptor_log");

        String password = configService.getPassword(envName, sender);
        String certPath = "certs/" + sender + ".p12";
        if (new File(certPath).exists()) {
            settings.setString(sessionID, "SocketUseSSL", "Y");
            settings.setString(sessionID, "SocketKeyStore", certPath);

            if (password != null) {
                settings.setString(sessionID, "SocketKeyStorePassword", password);
            } else {
                log.warn("No password found for {}, SSL might fail if password required", sender);
            }
        }

        return settings;
    }
}
