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

        // Giả lập giao dịch thành công và trả về một mã giao dịch
        return "FAKE-REAL-TX-" + UUID.randomUUID().toString();
    }
}