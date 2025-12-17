package com.example.fixclient.model;

public record FixSessionKey(String senderCompId, String targetCompId, String environment) {
}
