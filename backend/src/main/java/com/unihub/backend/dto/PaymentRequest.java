package com.unihub.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private Long registrationId;
    private BigDecimal amount;
    private String idempotencyKey;
}
