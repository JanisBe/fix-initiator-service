package com.example.fixclient.model;

public record StartSessionRequest(String senderCompId, String targetCompId, String environment) {
}