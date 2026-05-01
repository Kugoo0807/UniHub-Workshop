package com.unihub.backend.exception;

public class IdempotencyKeyException extends RuntimeException {
    public IdempotencyKeyException(String message) {
        super(message);
    }
}
