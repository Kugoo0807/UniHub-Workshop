package com.unihub.backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IdempotencyResult {
    private String status;
    private String transactionId;
    private String message;
}
