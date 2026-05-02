package com.unihub.backend.service.gateway;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.exception.PaymentServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class RealPaymentGateway implements PaymentGatewayClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    @Value("${payment.gateway.url:http://localhost:8080/api/v1/fake-gateway/pay}")
    private String paymentGatewayUrl;

    public RealPaymentGateway(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();

        // Initialize once to maintain CircuitBreaker state across multiple requests
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .build();
        this.circuitBreaker = CircuitBreaker.of("paymentGateway", cbConfig);

        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .build();
        this.timeLimiter = TimeLimiter.of(tlConfig);
    }

    @Override
    public String callGateway(PaymentRequest req) {
        Supplier<String> paymentCall = () -> webClient.post()
                .uri(paymentGatewayUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, paymentCall);
        try {
            return decorated.get();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new PaymentFailedException("Payment declined");
            }
            throw new PaymentServiceUnavailableException("The payment service is temporarily unavailable");
        } catch (WebClientRequestException ex) {
            throw new PaymentServiceUnavailableException("The payment service is temporarily unavailable");
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof TimeoutException) {
                throw new PaymentServiceUnavailableException("The payment service is temporarily unavailable");
            }
            throw ex;
        }
    }
}