package com.unihub.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.backend.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealPaymentGatewayClient implements PaymentGatewayClient {

    private final ObjectMapper objectMapper;

    // Hard-coded payment gateway URL as requested (do not read from env/config)
    // Using 127.0.0.1 instead of localhost to avoid potential IPv6/DNS issues
    private final String paymentGatewayUrl = "http://127.0.0.1:8080/api/v1/payments/process";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public PaymentGatewayResult charge(Long amount) {
        try {
            String payload = objectMapper.writeValueAsString(new PaymentRequest(amount));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paymentGatewayUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentGatewayResult.builder()
                        .success(false)
                        .failureReason("gateway_http_" + response.statusCode())
                        .build();
            }

            JsonNode body = objectMapper.readTree(response.body());
            String txId = body.path("transactionId").asText(null);
            boolean success = body.path("success").asBoolean(false);

            if (!success) {
                String failureReason = body.path("failureReason").asText("payment_failed");
                return PaymentGatewayResult.builder()
                        .success(false)
                        .failureReason(failureReason)
                        .build();
            }

            if (txId == null || txId.isBlank()) {
                txId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            return PaymentGatewayResult.builder()
                    .success(true)
                    .transactionId(txId)
                    .build();
        } catch (Exception ex) {
            return PaymentGatewayResult.builder()
                    .success(false)
                    .failureReason("payment_gateway_unavailable")
                    .build();
        }
    }

}
