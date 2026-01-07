package com.example.fixclient.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.SessionID;
import quickfix.SessionSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicSettingsBuilderTest {

    @Mock
    private ConfigService configService;

    private DynamicSettingsBuilder builder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        builder = new DynamicSettingsBuilder(configService);
    }

    @Test
    void buildSettings_LoadsFromFileAndAppliesDynamicValues() throws Exception {
        // Arrange
        String sender = "TEST_SENDER";
        String target = "TEST_TARGET";
        String env = "DEV";

        when(configService.isValid(env, target, sender)).thenReturn(true);
        when(configService.getAddress(env)).thenReturn("127.0.0.1");
        when(configService.getPort(env)).thenReturn(9876);
        when(configService.getPassword(env, sender)).thenReturn("secret");

        // Act
        SessionSettings settings = builder.buildSettings(sender, target, env);
        SessionID sessionID = new SessionID("FIX.4.1", sender, target);

        // Assert
        // Check values from file
        assertEquals("initiator", settings.getString(sessionID, "ConnectionType"));
        assertEquals("90", settings.getString(sessionID, "HeartBtInt"));
        assertEquals("log/initiator", settings.getString(sessionID, "FileLogPath"));
        assertEquals("TLSv1.2", settings.getString(sessionID, "EnabledProtocols"));

        // Check dynamic values
        assertEquals(sender, settings.getString(sessionID, "SenderCompID"));
        assertEquals(target, settings.getString(sessionID, "TargetCompID"));
        assertEquals("127.0.0.1", settings.getString(sessionID, "SocketConnectHost"));
        assertEquals("9876", settings.getString(sessionID, "SocketConnectPort"));

    }
}
