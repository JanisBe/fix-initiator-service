package com.example.fixclient.service;

import com.example.fixclient.config.EnvironmentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    private final String ENV_DEV = "dev";
    private final String ENV_PROD = "prod";
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        Map<String, EnvironmentConfig> configMap = new HashMap<>();

        // Dev logic
        EnvironmentConfig.ConnectionConfig devConn = new EnvironmentConfig.ConnectionConfig("127.0.0.1", 9876);
        EnvironmentConfig.InitiatorConfig devInit1 = new EnvironmentConfig.InitiatorConfig("INITIATOR1", "pass1", true);
        EnvironmentConfig.InitiatorConfig devInit2 = new EnvironmentConfig.InitiatorConfig("DISALBED_INIT", "pass2", false);
        configMap.put(ENV_DEV, new EnvironmentConfig(devConn, List.of(devInit1, devInit2)));

        // Prod logic (empty initiators for test)
        EnvironmentConfig.ConnectionConfig prodConn = new EnvironmentConfig.ConnectionConfig("192.168.1.1", 5000);
        configMap.put(ENV_PROD, new EnvironmentConfig(prodConn, Collections.emptyList()));

        configService = new ConfigService(configMap);
    }

    @Test
    void testGetAddress() {
        assertEquals("127.0.0.1", configService.getAddress(ENV_DEV));
        assertEquals("192.168.1.1", configService.getAddress(ENV_PROD));
        assertNull(configService.getAddress("unknown"));
    }

    @Test
    void testGetPort() {
        assertEquals(9876, configService.getPort(ENV_DEV));
        assertEquals(5000, configService.getPort(ENV_PROD));
        assertEquals(0, configService.getPort("unknown"));
    }

    @Test
    void testGetPassword() {
        assertEquals("pass1", configService.getPassword(ENV_DEV, "INITIATOR1"));
        assertEquals("pass2", configService.getPassword(ENV_DEV, "DISALBED_INIT"));
        assertNull(configService.getPassword(ENV_DEV, "UNKNOWN_INIT"));
        assertNull(configService.getPassword(ENV_PROD, "INITIATOR1"));
    }

    @Test
    void testIsValid() {
        // Valid if environment exists, initiator exists and is enabled
        assertTrue(configService.isValid(ENV_DEV, "TARGET", "INITIATOR1"));

        // Invalid if initiator disabled
        assertFalse(configService.isValid(ENV_DEV, "TARGET", "DISALBED_INIT"));

        // Invalid if initiator unknown
        assertFalse(configService.isValid(ENV_DEV, "TARGET", "UNKNOWN"));

        // Invalid if env unknown
        assertFalse(configService.isValid("unknown", "TARGET", "INITIATOR1"));
    }
}
