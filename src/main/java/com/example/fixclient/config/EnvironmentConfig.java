package com.example.fixclient.config;

import java.util.List;

public record EnvironmentConfig(ConnectionConfig connection, List<InitiatorConfig> initiators) {

    public record ConnectionConfig(String address, int port) {
    }

    public record InitiatorConfig(String senderCompId, String keystorePassword, Boolean enabled) {
        public boolean isEnabled() {
            return enabled == null || enabled;
        }
    }
}
