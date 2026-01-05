package com.example.fixclient.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class WebSocketErrorHandler {


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
