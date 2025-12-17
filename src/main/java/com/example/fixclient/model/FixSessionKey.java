package com.example.fixclient.model;

import lombok.Getter;

import java.util.Objects;

@Getter
public class FixSessionKey {
    private String senderCompId;
    private String targetCompId;
    private String environment;

    public FixSessionKey(String senderCompId, String targetCompId, String environment) {
        this.senderCompId = senderCompId;
        this.targetCompId = targetCompId;
        this.environment = environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixSessionKey that = (FixSessionKey) o;
        return Objects.equals(senderCompId, that.senderCompId) &&
                Objects.equals(targetCompId, that.targetCompId) &&
                Objects.equals(environment, that.environment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderCompId, targetCompId, environment);
    }
    
    @Override
    public String toString() {
        return "FixSessionKey{sender='" + senderCompId + "', target='" + targetCompId + "', env='" + environment + "'}";
    }
}
