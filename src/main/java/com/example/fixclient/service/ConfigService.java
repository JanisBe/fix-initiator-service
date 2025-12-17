package com.example.fixclient.service;

import com.example.fixclient.config.EnvironmentConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
public class ConfigService {

    private Map<String, EnvironmentConfig> configData;

    @PostConstruct
    public void loadConfig() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        File configFile = new File("configuration.json");
        try {
            if (configFile.exists()) {
                configData = mapper.readValue(configFile, new TypeReference<>() {
                });
            } else {
                System.out.println("configuration.json not found, starting with empty config.");
                configData = Collections.emptyMap();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration.json", e);
        }
    }

    public String getAddress(String env) {
        return getEnv(env).map(e -> e.connection().address()).orElse(null);
    }

    public int getPort(String env) {
        return getEnv(env).map(e -> e.connection().port()).orElse(0);
    }

    public String getPassword(String env, String senderCompId) {
        return getEnv(env)
                .map(EnvironmentConfig::initiators)
                .orElse(Collections.emptyList())
                .stream()
                .filter(i -> i.senderCompId().equals(senderCompId))
                .findFirst()
                .map(EnvironmentConfig.InitiatorConfig::keystorePassword)
                .orElse(null);
    }
    
    public boolean isValid(String env, String target, String sender) {
        if (!configData.containsKey(env)) return false;
        
        return getEnv(env)
                .map(EnvironmentConfig::initiators)
                .orElse(Collections.emptyList())
                .stream()
                .anyMatch(i -> i.senderCompId().equals(sender) && i.isEnabled());
    }
    
    private Optional<EnvironmentConfig> getEnv(String env) {
        return Optional.ofNullable(configData.get(env));
    }
}
