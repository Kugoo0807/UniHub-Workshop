package com.unihub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RegistrationResponse {
    private boolean paidFlow;
    private String qrCode;
    private String idempotencyKey;
    private Long amount;

    public static RegistrationResponse success(String qrCode) {
        return RegistrationResponse.builder().paidFlow(false).qrCode(qrCode).build();
    }

    public static RegistrationResponse pending(String idempotencyKey, Long amount) {
        return RegistrationResponse.builder().paidFlow(true).idempotencyKey(idempotencyKey).amount(amount).build();
    }
}
