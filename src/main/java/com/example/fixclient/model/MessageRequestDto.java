package com.example.fixclient.model;

import java.util.List;

public record MessageRequestDto(int repeatCount, int interval, String senderCompId, List<String> fixMessages) {
}
