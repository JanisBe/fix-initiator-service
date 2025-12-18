package com.example.fixclient.model;

public record BatchMessageRequest(int noOfMessages, int interval, String senderCompId, String fixMessage) {}
