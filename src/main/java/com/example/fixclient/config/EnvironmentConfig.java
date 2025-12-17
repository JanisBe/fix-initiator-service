package com.example.fixclient.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class EnvironmentConfig {
    private ConnectionConfig connection;
    private List<InitiatorConfig> initiators;

    @Setter
    @Getter
    public static class ConnectionConfig {
        private String address;
        private int port;

    }

    @Setter
    @Getter
    public static class InitiatorConfig {
        private String senderCompId;
        private String keystorePassword;
        private boolean enabled = true;

    }
}
