package com.unihub.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.backend.dto.PaymentGatewayResult;
import com.unihub.backend.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.payment-gateway.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    private final ObjectMapper objectMapper;
    private final PaymentGatewayCircuitBreaker circuitBreaker;

    @Value("${app.payment-gateway.url}")
    private final String paymentGatewayUrl = "http://127.0.0.1:8080/api/v1/payments/process";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public PaymentGatewayResult charge(Long amount) {
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("Payment gateway circuit is OPEN; failing fast");
            return gatewayUnavailable();
        }

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
                String failureReason = "gateway_http_" + response.statusCode();
                recordGatewayResult(failureReason);
                return failed(failureReason);
            }

            JsonNode body = objectMapper.readTree(response.body());
            String txId = body.path("transactionId").asText(null);
            boolean success = body.path("success").asBoolean(false);

            if (!success) {
                String failureReason = body.path("failureReason").asText("payment_failed");
                recordGatewayResult(failureReason);
                return failed(failureReason);
            }

            if (txId == null || txId.isBlank()) {
                txId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            circuitBreaker.recordSuccess();
            return PaymentGatewayResult.builder()
                    .success(true)
                    .transactionId(txId)
                    .build();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            return gatewayUnavailable();
        }
    }

    private void recordGatewayResult(String failureReason) {
        if (isTransientGatewayFailure(failureReason)) {
            circuitBreaker.recordFailure();
        } else {
            // Gateway responded normally; business failures must not open the circuit.
            circuitBreaker.recordSuccess();
        }
    }

    private boolean isTransientGatewayFailure(String failureReason) {
        return failureReason != null
                && (failureReason.contains("payment_gateway_unavailable")
                        || failureReason.startsWith("gateway_http_5"));
    }

    private PaymentGatewayResult failed(String failureReason) {
        return PaymentGatewayResult.builder()
                .success(false)
                .failureReason(failureReason)
                .build();
    }

    private PaymentGatewayResult gatewayUnavailable() {
        return failed("payment_gateway_unavailable");
    }

}
