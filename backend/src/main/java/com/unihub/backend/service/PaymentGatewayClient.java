package com.unihub.backend.service;

import com.unihub.backend.dto.PaymentGatewayResult;

public interface PaymentGatewayClient {
    PaymentGatewayResult charge(Long amount);
}
