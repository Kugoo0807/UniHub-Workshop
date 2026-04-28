package com.unihub.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class AiServiceException extends RuntimeException {
    public AiServiceException(String message) {
        super(message);
    }
}