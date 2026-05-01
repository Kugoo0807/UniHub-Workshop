package com.unihub.backend.service.gateway;

import com.unihub.backend.dto.PaymentRequest;

public interface PaymentGatewayClient {
    String callGateway(PaymentRequest req);
}