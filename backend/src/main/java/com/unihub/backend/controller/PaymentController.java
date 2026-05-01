package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // TODO: Bạn cần inject cái script Lua "seatReserveScript" vào đây
    // giống hệt như cách bạn đang làm bên RegistrationController
    private final String seatReserveScript = "LUA_SCRIPT_CUA_BAN_O_DAY";

    @PostMapping
    public ResponseEntity<Payment> processPayment(@RequestBody PaymentRequest request) {
        // Gọi hàm processPayment từ service
        Payment payment = paymentService.processPayment(request, seatReserveScript);
        return ResponseEntity.ok(payment);
    }
}