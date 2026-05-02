package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fake-gateway")
public class MockGatewayController {

    @PostMapping("/pay")
    public String processFakePayment(@RequestBody PaymentRequest req) throws InterruptedException {
        Thread.sleep(2000);

        // Simulate a successful transaction and return a transaction ID
        return "FAKE-REAL-TX-" + UUID.randomUUID().toString();
    }
}