package com.example.fixclient.exception;

public class BatchAlreadyRunningException extends RuntimeException {
    public BatchAlreadyRunningException(String message) {
        super(message);
    }
}
