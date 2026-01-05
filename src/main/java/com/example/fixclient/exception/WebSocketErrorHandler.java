package com.example.fixclient.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class WebSocketErrorHandler {

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ProblemDetail handleException(Exception ex) {
        log.error("WebSocket Error: ", ex);

        if (ex instanceof SessionNotFoundException) {
            return createProblemDetail(HttpStatus.NOT_FOUND, "Session Not Found", ex.getMessage());
        } else if (ex instanceof SessionLogonRequiredException) {
            return createProblemDetail(HttpStatus.BAD_REQUEST, "Logon Required", ex.getMessage());
        } else if (ex instanceof BatchAlreadyRunningException) {
            return createProblemDetail(HttpStatus.CONFLICT, "Batch Already Running", ex.getMessage());
        } else if (ex instanceof ConfigurationException) {
            return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Configuration Error", ex.getMessage());
        } else {
            return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "WebSocket Error", ex.getMessage());
        }
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }
}
