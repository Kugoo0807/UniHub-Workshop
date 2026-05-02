package com.unihub.backend.service;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.exception.PaymentServiceUnavailableException;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.gateway.PaymentGatewayClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisCallback;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    RegistrationRepository registrationRepository;

    @Mock
    WorkshopRepository workshopRepository;

    @Mock
    PaymentGatewayClient paymentGatewayClient;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @InjectMocks
    PaymentService paymentService;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void processPayment_success_withReservationFirst() {
        // GIVEN
        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(10L);
        req.setAmount(new BigDecimal("100000.00"));
        req.setIdempotencyKey("idem-1");

        Registration reg = Registration.builder()
                .id(10L)
                .workshop(Workshop.builder().id(5L).remainingSlots(100).build())
                .status("PENDING")
                .build();

        when(valueOps.get("payment:idem:idem-1")).thenReturn(null);
        when(registrationRepository.findById(10L)).thenReturn(Optional.of(reg));

        // Simulate a successful seat reservation (r = 1)
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);

        // Simulate the gateway returning a transaction ID after a seat is reserved
        when(paymentGatewayClient.callGateway(any())).thenReturn("tx-123");
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        Payment out = paymentService.processPayment(req, "seatReserveScript");

        // THEN
        assertNotNull(out);
        verify(redisTemplate).execute(any(RedisCallback.class)); // Verify reservation happens first
        verify(paymentGatewayClient).callGateway(req); // Verify payment happens after reservation
        verify(paymentRepository).save(any());
        verify(valueOps).set(eq("payment:idem:idem-1"), eq("tx:tx-123"), any(Duration.class));
    }

    @Test
    void processPayment_noSeat_shouldThrowImmediately_andNotCallGateway() {
        // GIVEN
        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(11L);
        req.setIdempotencyKey("idem-2");

        Registration reg = Registration.builder()
                .id(11L)
                .workshop(Workshop.builder().id(7L).build())
                .build();

        when(valueOps.get("payment:idem:idem-2")).thenReturn(null);
        when(registrationRepository.findById(11L)).thenReturn(Optional.of(reg));

        // Simulate Redis reporting sold out (r = -1)
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(-1L);

        // WHEN & THEN
        assertThrows(InsufficientSeatsException.class, () ->
                paymentService.processPayment(req, "seatReserveScript")
        );

        // IMPORTANT: the gateway must never be called when the workshop is sold out
        verify(paymentGatewayClient, never()).callGateway(any());
        verify(valueOps).set(eq("payment:idem:idem-2"), eq("no_seat"), any(Duration.class));
    }

    @Test
    void processPayment_paymentFails_shouldReleaseSeat() {
        // GIVEN
        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(12L);
        req.setIdempotencyKey("idem-3");

        Registration reg = Registration.builder()
                .id(12L)
                .workshop(Workshop.builder().id(9L).build())
                .status("PENDING")
                .build();

        when(valueOps.get("payment:idem:idem-3")).thenReturn(null);
        when(registrationRepository.findById(12L)).thenReturn(Optional.of(reg));

        // Step 1: Reserve a seat successfully
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);

        // Step 2: Payment fails
        when(paymentGatewayClient.callGateway(any())).thenThrow(new PaymentFailedException("Declined"));

        // WHEN & THEN
        assertThrows(PaymentFailedException.class, () ->
                paymentService.processPayment(req, "seatReserveScript")
        );

        // Step 3: Release the reserved seat
        verify(redisTemplate).delete(contains("reservation:12"));
        verify(valueOps).increment(contains("slots"));
        assertEquals("PENDING", reg.getStatus());
    }

    @Test
    void processPayment_idempotency_returnsExisting() {
        PaymentRequest req = new PaymentRequest();
        req.setIdempotencyKey("idem-4");

        Payment existing = Payment.builder().transactionId("tx-exist").build();
        when(valueOps.get("payment:idem:idem-4")).thenReturn("tx:tx-exist");
        when(paymentRepository.findByIdempotencyKey("idem-4")).thenReturn(Optional.of(existing));

        Payment out = paymentService.processPayment(req, "script");

        assertEquals("tx-exist", out.getTransactionId());
        verify(paymentGatewayClient, never()).callGateway(any());
    }

        @Test
        void processPayment_idempotency_noSeatMarker_shouldThrowConflict_withoutGatewayCall() {
                PaymentRequest req = new PaymentRequest();
                req.setIdempotencyKey("idem-7");

                when(valueOps.get("payment:idem:idem-7")).thenReturn("no_seat:tx-any");

                assertThrows(InsufficientSeatsException.class, () -> paymentService.processPayment(req, "script"));

                verify(paymentGatewayClient, never()).callGateway(any());
        }

    @Test
    void processPayment_circuitOpen_shouldKeepPending_andNotReleaseSeat() {
        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(13L);
        req.setIdempotencyKey("idem-5");

        Registration reg = Registration.builder()
                .id(13L)
                .workshop(Workshop.builder().id(11L).build())
                .status("PENDING")
                .build();

        when(valueOps.get("payment:idem:idem-5")).thenReturn(null);
        when(registrationRepository.findById(13L)).thenReturn(Optional.of(reg));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
        when(paymentGatewayClient.callGateway(any()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("paymentGateway")));

        assertThrows(PaymentServiceUnavailableException.class, () -> paymentService.processPayment(req, "seatReserveScript"));

        assertEquals("PENDING", reg.getStatus());
        verify(registrationRepository, never()).save(argThat(r -> "FAILED".equals(r.getStatus())));
        verify(redisTemplate, never()).delete(contains("reservation:13"));
        verify(valueOps, never()).increment(contains("slots"));
    }

    @Test
    void processPayment_gatewayUnavailable_shouldKeepPending_andNotReleaseSeat() {
        PaymentRequest req = new PaymentRequest();
        req.setRegistrationId(14L);
        req.setIdempotencyKey("idem-6");

        Registration reg = Registration.builder()
                .id(14L)
                .workshop(Workshop.builder().id(12L).build())
                .status("PENDING")
                .build();

        when(valueOps.get("payment:idem:idem-6")).thenReturn(null);
        when(registrationRepository.findById(14L)).thenReturn(Optional.of(reg));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
        when(paymentGatewayClient.callGateway(any()))
                .thenThrow(new PaymentServiceUnavailableException("The payment service is temporarily unavailable"));

        assertThrows(PaymentServiceUnavailableException.class, () -> paymentService.processPayment(req, "seatReserveScript"));

        assertEquals("PENDING", reg.getStatus());
        verify(registrationRepository, never()).save(argThat(r -> "FAILED".equals(r.getStatus())));
        verify(redisTemplate, never()).delete(contains("reservation:14"));
        verify(valueOps, never()).increment(contains("slots"));
    }
}