package com.unihub.backend.enums;

public enum IdempotencyState {
    IN_FLIGHT,
    SUCCESS,
    FAILED,
    NOT_FOUND
}
