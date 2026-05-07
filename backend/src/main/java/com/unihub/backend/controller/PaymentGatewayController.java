package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.service.PaymentGatewayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentGatewayController {

    @PostMapping("/process")
    public ResponseEntity<PaymentGatewayResult> process(@RequestBody PaymentRequest request) {
        long amount = request != null && request.getAmount() != null ? request.getAmount() : 0L;

        if (amount <= 0) {
            return ResponseEntity.badRequest().body(PaymentGatewayResult.builder()
                    .success(false)
                    .failureReason("invalid_amount")
                    .build());
        }

        String txId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return ResponseEntity.ok(PaymentGatewayResult.builder()
                .success(true)
                .transactionId(txId)
                .build());
    }
}