package com.example.fixclient.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct; // Spring Boot 3
import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class ConfigService {

    @Getter
    private Map<String, Map<String, String>> configData;

    @PostConstruct
    public void loadConfig() {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File("configuration.json");
        try {
            if (configFile.exists()) {
                configData = mapper.readValue(configFile, new TypeReference<>() {});
            } else {
                System.out.println("configuration.json not found, starting with empty config.");
                configData = Map.of();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration.json", e);
        }
    }

    public String getAddress(String env) {
        return configData.getOrDefault(env, Map.of()).get("address");
    }

    public int getPort(String env) {
        String p = configData.getOrDefault(env, Map.of()).get("port");
        return p != null ? Integer.parseInt(p) : 0;
    }

    public String getPassword(String env, String compId) {
        return configData.getOrDefault(env, Map.of()).get(compId);
    }
    
    public boolean isValid(String env, String target, String sender) {
        if (!configData.containsKey(env)) return false;
        Map<String, String> envProps = configData.get(env);
        // User said: "List of targetCompId and senderCompId is limited... For each environment are known: all allowed..."
        // The JSON contains keys for CompIDs with passwords. 
        // We assume existence of key implies validity for at least one side (likely Sender, as getting password for it).
        // The logical layout often is: We are "Sender", we authenticate to "Acceptor".
        // The password is for US (Sender).
        return envProps.containsKey(sender);
    }
}
