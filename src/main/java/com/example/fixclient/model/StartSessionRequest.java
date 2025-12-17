package com.example.fixclient.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StartSessionRequest {
    private String senderCompId;
    private String targetCompId;
    private String environment;

}
