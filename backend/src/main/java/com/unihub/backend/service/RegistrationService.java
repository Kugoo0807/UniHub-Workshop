package com.unihub.backend.service;

import com.unihub.backend.dto.IdempotencyResult;
import com.unihub.backend.dto.PaymentResultResponse;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.dto.UserRegistrationResponse;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.exception.*;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j

public class RegistrationService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SeatLockingService seatLockingService;
    private final IdempotencyService idempotencyService;
    private final PaymentGatewayClient paymentGatewayClient;

    private static final Duration HOLD_TTL = Duration.ofMinutes(10);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Transactional
    public RegistrationResponse register(Long workshopId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("User not found"));

        Workshop w = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));

        log.info("🔍 Workshop {} status: {} | now: {}", workshopId, w.getStatus(), LocalDateTime.now());

        if ("CANCELLED".equals(w.getStatus())) {
            log.warn("❌ Workshop {} has been cancelled.", workshopId);
            throw new ConflictException("Workshop has been cancelled and is no longer accepting registrations");
        }

        if (!"PUBLISHED".equals(w.getStatus())) {
            log.warn("❌ Workshop {} not published. Status: {}", workshopId, w.getStatus());
            throw new ConflictException("Workshop is not published");
        }

        // Validate registration time window
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ Registration time: {} - {} (now: {})", w.getRegistrationStartTime(), w.getRegistrationEndTime(),
                now);
        if (now.isBefore(w.getRegistrationStartTime()) || now.isAfter(w.getRegistrationEndTime())) {
            log.warn("❌ Outside registration window for workshop {}", workshopId);
            throw new ConflictException(
                    "Registration period has ended or not yet started");
        }

        // Prevent duplicate registration
        boolean alreadyRegistered = registrationRepository.existsByUserIdAndWorkshopId(userId, workshopId);
        log.info("📋 User {} already registered: {}", userId, alreadyRegistered);
        if (alreadyRegistered) {
            log.warn("❌ User {} already registered for workshop {}", userId, workshopId);
            throw new ConflictException("User already registered for this workshop");
        }

        boolean reserved = seatLockingService.reserveSeat(String.valueOf(workshopId), String.valueOf(userId));
        log.info("🪑 Seat reservation for user {} in workshop {}: {}", userId, workshopId, reserved);
        if (!reserved) {
            log.warn("❌ No slots available for workshop {}", workshopId);
            throw new SeatUnavailableException("No slots available");
        }

        decrementRemainingSlots(w);
        workshopRepository.save(w);

        try {
            Registration reg = Registration.builder()
                    .user(user)
                    .workshop(w)
                    .status(w.getPrice() == 0L ? "SUCCESS" : "PENDING")
                    .qrCode(w.getPrice() == 0L ? generateQrCode() : "PENDING-" + UUID.randomUUID())
                    .build();

            Registration saved = registrationRepository.save(reg);

            if (w.getPrice() == 0L) {
                return RegistrationResponse.success(saved.getQrCode());
            } else {
                String suggestedIdempotencyKey = UUID.randomUUID().toString();
                Payment p = Payment.builder()
                        .registration(saved)
                        .amount(w.getPrice())
                        .idempotencyKey(suggestedIdempotencyKey)
                        .status("PENDING")
                        .build();
                paymentRepository.save(p);
                return RegistrationResponse.pending(suggestedIdempotencyKey, w.getPrice());
            }
        } catch (RuntimeException ex) {
            seatLockingService.releaseSeat(String.valueOf(workshopId), String.valueOf(userId));
            incrementRemainingSlots(w);
            workshopRepository.save(w);
            throw ex;
        }
    }

    @Transactional
    public PaymentResultResponse processPayment(Long workshopId, Long userId, String idempotencyKey) {
        // Idempotency check
        IdempotencyState state = idempotencyService.getState(idempotencyKey);
        if (state == IdempotencyState.SUCCESS) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Registration reg = existing.get().getRegistration();
                return PaymentResultResponse.success(existing.get().getTransactionId(), reg.getQrCode());
            }
        }

        if (state == IdempotencyState.IN_FLIGHT) {
            throw new ConflictException("Payment is currently being processed");
        }

        idempotencyService.markInFlight(idempotencyKey, Duration.ofMinutes(5));

        try {
            Optional<Payment> pOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (pOpt.isEmpty()) {
                workshopRepository.findById(workshopId).ifPresent(workshop -> {
                    seatLockingService.releaseSeat(String.valueOf(workshopId), String.valueOf(userId));
                    incrementRemainingSlots(workshop);
                    workshopRepository.save(workshop);
                });
                throw new ResourceNotFoundException("Payment record not found for idempotency key");
            }

            Payment p = pOpt.get();
            Workshop w = p.getRegistration().getWorkshop();

            if ("CANCELLED".equals(w.getStatus())) {
                log.warn("❌ Payment blocked: Workshop {} has been cancelled.", workshopId);
                p.setStatus("FAILED");
                paymentRepository.save(p);
                
                Registration reg = p.getRegistration();
                reg.setStatus("FAILED");
                registrationRepository.save(reg);
                
                seatLockingService.releaseSeat(String.valueOf(workshopId), String.valueOf(userId));
                incrementRemainingSlots(w);
                workshopRepository.save(w);
                
                idempotencyService.storeResult(idempotencyKey,
                        IdempotencyResult.builder().status("FAILED").message("Workshop was cancelled").build(),
                        IDEMPOTENCY_TTL);
                
                throw new ConflictException("Workshop has been cancelled. Payment cannot be processed.");
            }

            var result = paymentGatewayClient.charge(p.getAmount());

            if (result.isSuccess()) {
                p.setStatus("COMPLETED");
                p.setTransactionId(result.getTransactionId());
                paymentRepository.save(p);

                Registration reg = p.getRegistration();
                reg.setStatus("SUCCESS");
                reg.setQrCode(generateQrCode());
                registrationRepository.save(reg);

                idempotencyService.storeResult(idempotencyKey,
                        IdempotencyResult.builder().status("SUCCESS").transactionId(result.getTransactionId()).build(),
                        IDEMPOTENCY_TTL);
                return PaymentResultResponse.success(result.getTransactionId(), reg.getQrCode());
            } else {
                String reason = result.getFailureReason() != null ? result.getFailureReason() : "payment_failed";

                // Distinguish between card decline (definitive) and gateway/network errors (transient)
                boolean gatewayUnavailable = reason.contains("payment_gateway_unavailable")
                        || reason.startsWith("gateway_http_5");

                if (gatewayUnavailable) {
                    // For transient gateway errors, do NOT mark payment/registration as FAILED.
                    // Keep registration in PENDING so client can retry with same Idempotency-Key.
                    // Do not store final idempotency result.
                    throw new PaymentGatewayUnavailableException("Payment gateway unavailable");
                }

                // Definitive payment failure (card declined, insufficient funds)
                p.setStatus("FAILED");
                paymentRepository.save(p);

                Registration reg = p.getRegistration();
                reg.setStatus("FAILED");
                registrationRepository.save(reg);

                seatLockingService.releaseSeat(String.valueOf(workshopId), String.valueOf(userId));
                incrementRemainingSlots(reg.getWorkshop());
                workshopRepository.save(reg.getWorkshop());

                idempotencyService.storeResult(idempotencyKey,
                        IdempotencyResult.builder().status("FAILED").message(result.getFailureReason()).build(),
                        IDEMPOTENCY_TTL);

                throw new PaymentFailedException("Payment failed: " + result.getFailureReason());
            }
        } catch (RuntimeException ex) {
            // If the error is a transient payment gateway unavailability, do NOT store
            // a final FAILED idempotency result so the client can retry with the
            // same Idempotency-Key. Only store final FAILED for definitive errors.
            if (ex instanceof PaymentGatewayUnavailableException || ex instanceof PaymentFailedException) {
                throw ex;
            }

            idempotencyService.storeResult(idempotencyKey,
                    IdempotencyResult.builder().status("FAILED").message("gateway_error").build(), IDEMPOTENCY_TTL);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<UserRegistrationResponse> getUserRegistrations(Long userId) {
        return registrationRepository.findAllByUserIdWithWorkshop(userId).stream()
                .map(registration -> UserRegistrationResponse.builder()
                        .registrationId(registration.getId())
                        .workshopId(registration.getWorkshop().getId())
                        .title(registration.getWorkshop().getTitle())
                        .status(registration.getStatus())
                        .qrCode(registration.getQrCode())
                        .createdAt(registration.getCreatedAt())
                        .price(registration.getWorkshop().getPrice())
                        .build())
                .toList();
    }

    private String generateQrCode() {
        return "QR-" + UUID.randomUUID().toString();
    }

    private void decrementRemainingSlots(Workshop workshop) {
        int currentSlots = workshop.getRemainingSlots() != null
                ? workshop.getRemainingSlots()
                : workshop.getTotalSlots();
        workshop.setRemainingSlots(Math.max(0, currentSlots - 1));
    }

    private void incrementRemainingSlots(Workshop workshop) {
        int currentSlots = workshop.getRemainingSlots() != null
                ? workshop.getRemainingSlots()
                : 0;
        int totalSlots = workshop.getTotalSlots() != null ? workshop.getTotalSlots() : currentSlots;
        workshop.setRemainingSlots(Math.min(totalSlots, currentSlots + 1));
    }
}
