package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Qualifier("seatReserveScript")
    private final String seatReserveScript;

    @PostMapping
    public ResponseEntity<Payment> processPayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.processPayment(request, seatReserveScript);
        return ResponseEntity.ok(payment);
    }
}