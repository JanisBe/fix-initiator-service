package com.example.fixclient.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketErrorHandler.class);

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ProblemDetail validExceptionHandler(Exception ex) {
        log.error("WebSocket Error: ", ex);
        ProblemDetail problem = ProblemDetail.forStatus(500);
        problem.setTitle("WebSocket Error");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
