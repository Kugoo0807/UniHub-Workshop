package com.unihub.backend.service;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.exception.IdempotencyKeyException;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.exception.PaymentServiceUnavailableException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.service.gateway.PaymentGatewayClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RegistrationRepository registrationRepository;
    private final StringRedisTemplate redisTemplate;
    private final WorkshopRepository workshopRepository;
    private final PaymentGatewayClient paymentGatewayClient;

    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:idem:";

    @Transactional
    public Payment processPayment(PaymentRequest req, String seatReserveScript) {
        // 1. Idempotency: check Redis first (TTL 24h)
        String idemKey = IDEMPOTENCY_KEY_PREFIX + req.getIdempotencyKey();
        String stored = redisTemplate.opsForValue().get(idemKey);
        if (stored != null) {
            if (stored.startsWith("no_seat")) {
                throw new InsufficientSeatsException("Workshop này đã hết chỗ");
            }
            return paymentRepository.findByIdempotencyKey(req.getIdempotencyKey())
                    .orElseThrow(() -> new IdempotencyKeyException("Idempotency key present but payment missing"));
        }

        Registration registration = registrationRepository.findById(req.getRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        // 2. Seat Contention: Reserve seat atomically before calling external payment gateway
        String slotKey = String.format("workshop:%d:slots", registration.getWorkshop().getId());
        String reservationKey = String.format("workshop:%d:reservation:%d", registration.getWorkshop().getId(), registration.getId());
        String token = String.valueOf(registration.getId());

        String existing = redisTemplate.opsForValue().get(reservationKey);
        if (existing == null || !existing.equals(token)) {
            long ttlMs = 15 * 60 * 1000;
            Long r = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) (connection) -> {
                Object o = connection.scriptingCommands().eval(
                        seatReserveScript.getBytes(),
                        org.springframework.data.redis.connection.ReturnType.INTEGER,
                        2,
                        slotKey.getBytes(),
                        reservationKey.getBytes(),
                        token.getBytes(),
                        String.valueOf(ttlMs).getBytes()
                );
                return (Long) o;
            });

            if (r == null || r.longValue() < 0) {
                // Mark idempotency key as out-of-seats to prevent further retries
                redisTemplate.opsForValue().set(idemKey, "no_seat", Duration.ofHours(24));
                throw new InsufficientSeatsException("Workshop này đã hết chỗ");
            }
        }

        // 3. Process payment via external gateway AFTER successfully reserving seat
        String transactionId;
        try {
            transactionId = paymentGatewayClient.callGateway(req);
        } catch (CallNotPermittedException cbnpe) {
            // Circuit is open: return 503 and keep registration PENDING for retry.
            throw new PaymentServiceUnavailableException("Dịch vụ thanh toán tạm thời không khả dụng");
        } catch (PaymentServiceUnavailableException psue) {
            // Temporary upstream issue (timeout/network): keep PENDING for retry.
            throw psue;
        } catch (PaymentFailedException pfe) {
            handleFailedPayment(registration); // Rollback reserved seat[cite: 1]
            throw pfe;
        } catch (Exception ex) {
            handleFailedPayment(registration); // Rollback reserved seat on unexpected error[cite: 1]
            throw new PaymentFailedException("Payment gateway error");
        }

        if (transactionId == null) {
            handleFailedPayment(registration); // Rollback reserved seat[cite: 1]
            throw new PaymentFailedException("Payment failed");
        }

        // 4. Success: persist payment and confirm registration[cite: 1]
        Payment payment = Payment.builder()
                .registration(registration)
                .amount(req.getAmount())
                .idempotencyKey(req.getIdempotencyKey())
                .transactionId(transactionId)
                .status("COMPLETED")
                .build();

        payment = paymentRepository.save(payment);
        registration.setStatus("SUCCESS");
        registrationRepository.save(registration);
        com.unihub.backend.entity.Workshop workshop = registration.getWorkshop();
        int currentRemaining = workshop.getRemainingSlots() == null ? 0 : workshop.getRemainingSlots();
        if (currentRemaining <= 0) {
            handleFailedPayment(registration);
            throw new InsufficientSeatsException("Workshop này đã hết chỗ");
        }
        workshop.setRemainingSlots(currentRemaining - 1);
        workshopRepository.save(workshop);

        try {
            redisTemplate.delete(reservationKey);
        } catch (Exception ignore) {}


        redisTemplate.opsForValue().set(idemKey, "tx:" + payment.getTransactionId(), Duration.ofHours(24));

        return payment;
    }

    private void handleFailedPayment(Registration registration) {
        try {
            String slotKey = String.format("workshop:%d:slots", registration.getWorkshop().getId());
            String reservationKey = String.format("workshop:%d:reservation:%d", registration.getWorkshop().getId(), registration.getId());
            redisTemplate.delete(reservationKey);
            redisTemplate.opsForValue().increment(slotKey);
        } catch (Exception ignore) {}
    }
}