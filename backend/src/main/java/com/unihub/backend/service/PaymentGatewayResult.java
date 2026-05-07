package com.unihub.backend.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentGatewayResult {
    private boolean success;
    private String transactionId;
    private String failureReason;
}
