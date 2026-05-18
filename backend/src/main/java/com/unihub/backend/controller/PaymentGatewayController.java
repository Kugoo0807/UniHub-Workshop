package com.unihub.backend.controller;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.dto.ErrorResponse;
import com.unihub.backend.dto.PaymentGatewayResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@Tag(name = "Payments", description = "Payment gateway simulation endpoints.")
@Profile("!prod")
public class PaymentGatewayController {

    @PostMapping("/process")
    @Operation(summary = "Process a payment", description = "Simulates a payment gateway transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment processed"),
            @ApiResponse(responseCode = "400", description = "Invalid payment amount",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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