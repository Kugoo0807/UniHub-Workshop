package com.unihub.backend.service;

public interface PaymentGatewayClient {
    PaymentGatewayResult charge(Long amount);
}
