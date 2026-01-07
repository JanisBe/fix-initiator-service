package com.example.fixclient.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.io.File;

@Service
@Slf4j
public class DynamicSettingsBuilder {

    private final ConfigService configService;

    public DynamicSettingsBuilder(ConfigService configService) {
        this.configService = configService;
    }

    public SessionSettings buildSettings(String sender, String target, String envName) {
        if (!configService.isValid(envName, target, sender)) {
            // Maybe log WARN but proceed, or throw.
        }

        SessionSettings settings;
        try {
            settings = new SessionSettings("src/main/resources/initiator.cfg");
        } catch (quickfix.ConfigError e) {
            log.error("Failed to load initiator.cfg", e);
            throw new RuntimeException(e);
        }

        SessionID sessionID = new SessionID("FIX.4.1", sender, target);

        // Basic Connection Info
        settings.setString(sessionID, "SenderCompID", sender);
        settings.setString(sessionID, "TargetCompID", target);

        String address = configService.getAddress(envName);
        int port = configService.getPort(envName);

        settings.setString(sessionID, "SocketConnectHost", address != null ? address : "127.0.0.1");
        settings.setLong(sessionID, "SocketConnectPort", port);

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
