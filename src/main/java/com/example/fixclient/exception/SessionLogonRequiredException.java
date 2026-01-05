package com.example.fixclient.exception;

public class SessionLogonRequiredException extends RuntimeException {
    public SessionLogonRequiredException(String message) {
        super(message);
    }
}
