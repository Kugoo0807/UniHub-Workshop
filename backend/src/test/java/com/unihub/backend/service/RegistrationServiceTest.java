package com.unihub.backend.service;

import com.unihub.backend.dto.*;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.event.WorkshopRegistrationSuccessEvent;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.exception.SeatUnavailableException;
import com.unihub.backend.exception.PaymentGatewayUnavailableException;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.exception.UnauthorizedException;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * Unit tests for `RegistrationService`.
 *
 * These tests focus on business logic only and mock all infrastructure
 * dependencies
 * (repositories, payment gateway, seat locking, idempotency). They cover both
 * free and paid registration flows, validation errors, seat reservation
 * behavior,
 * and idempotent payment processing scenarios.
 *
 * Blueprint: blueprint/specs/registration.md.
 */
class RegistrationServiceTest {

    @Mock
    WorkshopRepository workshopRepository;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    SeatLockingService seatLockingService;
    @Mock
    IdempotencyService idempotencyService;
    @Mock
    PaymentGatewayClient paymentGatewayClient;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    RegistrationService registrationService;

    private User user;
    private Workshop workshop;

    @BeforeEach
    void setup() {
        user = User.builder().id(10L).fullName("U").email("u@x").role("STUDENT").status("ACTIVE").build();
        workshop = Workshop.builder()
                .id(5L)
                .title("W")
                .status("PUBLISHED")
                .price(0L)
                .totalSlots(10)
                .remainingSlots(10)
                .registrationStartTime(LocalDateTime.now().minusHours(1))
                .registrationEndTime(LocalDateTime.now().plusHours(2))
                .build();
    }

    @Test
    // Spec happy path: free workshop returns QR immediately (HTTP 201).
    void register_freeWorkshop_success() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopIdAndStatusIn(user.getId(), workshop.getId(),
                java.util.List.of("PENDING", "SUCCESS"))).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(true);

        Registration saved = Registration.builder().id(1L).qrCode("QR-STATIC").status("SUCCESS").build();
        when(registrationRepository.save(any(Registration.class))).thenReturn(saved);

        RegistrationResponse resp = registrationService.register(workshop.getId(), user.getId());
        assertFalse(resp.isPaidFlow());
        assertEquals("QR-STATIC", resp.getQrCode());
        verify(workshopRepository).save(any());
        verify(eventPublisher).publishEvent(any(WorkshopRegistrationSuccessEvent.class));
    }

    @Test
    // Spec happy path: paid workshop creates PENDING registration and returns
    // payment info (HTTP 202).
    void register_paidWorkshop_returnsPending() {
        workshop.setPrice(5000L);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopIdAndStatusIn(user.getId(), workshop.getId(),
                java.util.List.of("PENDING", "SUCCESS"))).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(true);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrationResponse resp = registrationService.register(workshop.getId(), user.getId());
        assertTrue(resp.isPaidFlow());
        assertEquals(5000L, resp.getAmount());
        assertNotNull(resp.getIdempotencyKey());

        ArgumentCaptor<Registration> registrationCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(registrationCaptor.capture());
        assertEquals("PENDING", registrationCaptor.getValue().getStatus());
        assertTrue(registrationCaptor.getValue().getQrCode().startsWith("PENDING-"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals("PENDING", paymentCaptor.getValue().getStatus());
        assertEquals(resp.getIdempotencyKey(), paymentCaptor.getValue().getIdempotencyKey());
    }

    @Test
    // Spec error 401 Unauthorized: user/JWT cannot be resolved.
    void register_userNotFound_throwsUnauthorized() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> registrationService.register(workshop.getId(), user.getId()));

        verifyNoInteractions(workshopRepository, registrationRepository, seatLockingService, paymentRepository);
    }

    @Test
    // Spec/API error 404 Not Found: workshop does not exist.
    void register_workshopNotFound_throwsResourceNotFound() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> registrationService.register(workshop.getId(), user.getId()));

        verifyNoInteractions(registrationRepository, seatLockingService, paymentRepository);
    }

    @Test
    // Spec error 409 Conflict: cancelled workshop must not accept new
    // registrations.
    void register_cancelledWorkshop_throwsConflict() {
        workshop.setStatus("CANCELLED");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> registrationService.register(workshop.getId(), user.getId()));
        assertTrue(ex.getMessage().contains("cancelled"));

        verify(seatLockingService, never()).reserveSeat(anyString(), anyString());
    }

    @Test
    // Spec error 409 Conflict: student already has PENDING/SUCCESS registration for
    // this workshop.
    void register_duplicate_throwsConflict() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopIdAndStatusIn(user.getId(), workshop.getId(),
                java.util.List.of("PENDING", "SUCCESS"))).thenReturn(true);

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Spec error 409 Conflict: registration window has not started yet.
    void register_beforeWindow_throwsConflict() {
        workshop.setRegistrationStartTime(LocalDateTime.now().plusDays(1));
        workshop.setRegistrationEndTime(LocalDateTime.now().plusDays(2));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Spec error 409 Conflict: registration window already ended.
    void register_afterWindow_throwsConflict() {
        workshop.setRegistrationStartTime(LocalDateTime.now().minusDays(2));
        workshop.setRegistrationEndTime(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Spec error 409 Conflict: workshop must be PUBLISHED.
    void register_notPublished_throwsConflict() {
        workshop.setStatus("DRAFT");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Spec error 409 Conflict: no seats available (reserveSeat returns false).
    void register_noSeats_throwsSeatUnavailable() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopIdAndStatusIn(user.getId(), workshop.getId(),
                java.util.List.of("PENDING", "SUCCESS"))).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(false);

        assertThrows(SeatUnavailableException.class,
                () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Spec consistency rule: if DB save fails after seat reservation, release seat
    // and restore slot count.
    void register_saveRegistrationFails_releasesSeatAndRestoresSlot() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopIdAndStatusIn(
                user.getId(), workshop.getId(), List.of("PENDING", "SUCCESS"))).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(true);
        when(registrationRepository.save(any(Registration.class))).thenThrow(new RuntimeException("db down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.register(workshop.getId(), user.getId()));
        assertEquals("db down", ex.getMessage());

        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(workshopRepository, times(2)).save(workshop);
        assertEquals(10, workshop.getRemainingSlots());
    }

    @Test
    // Spec payment error HTTP 202: Idempotency-Key is already IN_FLIGHT.
    void processPayment_inFlight_throwsConflict() {
        String key = UUID.randomUUID().toString();
        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.IN_FLIGHT);

        assertThrows(ConflictException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));
    }

    @Test
    // Spec idempotency: same Idempotency-Key returns previous result and does not
    // call gateway again.
    void processPayment_stateSuccess_returnsStoredResult() {
        String key = "k-success";
        Registration reg = Registration.builder().id(11L).qrCode("QR-OLD").build();
        Payment p = Payment.builder().id(22L).registration(reg).transactionId("tx-1").status("COMPLETED").build();

        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));

        PaymentResultResponse resp = registrationService.processPayment(workshop.getId(), user.getId(), key);
        assertTrue(resp.isSuccess());
        assertEquals("tx-1", resp.getTransactionId());
        assertEquals("QR-OLD", resp.getQrCode());
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    // Spec/API error 404 Not Found: Idempotency-Key has no matching payment record.
    void processPayment_missingPayment_releasesSeat_and_throwsNotFound() {
        String key = "missing-key";
        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // ensure workshop found so releaseSeat is called
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ResourceNotFoundException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));

        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(workshopRepository).save(any());
    }

    @Test
    // Spec happy path: payment succeeds -> registration SUCCESS, QR returned,
    // idempotency stored.
    void processPayment_gatewaySuccess_updatesEntities_and_storesIdempotency() {
        String key = "key-ok";
        Registration reg = Registration.builder().id(30L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(40L).registration(reg).amount(1234L).idempotencyKey(key).status("PENDING")
                .build();

        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(1234L)).thenReturn(PaymentGatewayResult.builder()
                .success(true).transactionId("tx-999").build());

        PaymentResultResponse resp = registrationService.processPayment(workshop.getId(), user.getId(), key);
        assertTrue(resp.isSuccess());
        assertEquals("tx-999", resp.getTransactionId());
        verify(paymentRepository).save(argThat(pay -> "COMPLETED".equals(pay.getStatus())));
        verify(registrationRepository).save(argThat(r -> "SUCCESS".equals(r.getStatus()) && r.getQrCode() != null));
        verify(idempotencyService).storeResult(eq(key), any(IdempotencyResult.class), any(Duration.class));
        verify(eventPublisher).publishEvent(any(WorkshopRegistrationSuccessEvent.class));
    }

    @Test
    // Spec error 402 Payment Required: definitive gateway failure marks FAILED and
    // releases seat.
    void processPayment_gatewayFailure_handlesFailure_and_releasesSeat() {
        String key = "key-fail";
        Registration reg = Registration.builder().id(50L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(60L).registration(reg).amount(2000L).idempotencyKey(key).status("PENDING")
                .build();

        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(2000L)).thenReturn(PaymentGatewayResult.builder()
                .success(false).failureReason("card_declined").build());

        PaymentFailedException ex = assertThrows(PaymentFailedException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));
        assertTrue(ex.getMessage().contains("card_declined"));

        verify(paymentRepository).save(argThat(pay -> "FAILED".equals(pay.getStatus())));
        verify(registrationRepository).save(argThat(r -> "FAILED".equals(r.getStatus())));
        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(idempotencyService).storeResult(eq(key), any(IdempotencyResult.class), any(Duration.class));
    }

    @Test
    // Spec error 503 Service Unavailable: timeout/unavailable keeps registration
    // PENDING for retry.
    void processPayment_gatewayUnavailable_keepsPendingForRetry() {
        String key = "key-unavailable";
        Registration reg = Registration.builder().id(70L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(80L).registration(reg).amount(3000L).idempotencyKey(key).status("PENDING")
                .build();

        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(3000L)).thenReturn(PaymentGatewayResult.builder()
                .success(false).failureReason("payment_gateway_unavailable").build());

        assertThrows(PaymentGatewayUnavailableException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));

        verify(idempotencyService, never()).storeResult(any(), any(), any(Duration.class));
        verify(seatLockingService, never()).releaseSeat(anyString(), anyString());
        verify(paymentRepository, never())
                .save(argThat(pay -> "FAILED".equals(pay.getStatus()) || "COMPLETED".equals(pay.getStatus())));
        assertEquals("PENDING", p.getStatus());
        assertEquals("PENDING", reg.getStatus());
    }

    @Test
    // Spec error 503 Service Unavailable: gateway 5xx/circuit-open style result is
    // transient.
    void processPayment_gatewayHttp5_keepsPendingForRetry() {
        String key = "key-http-5";
        Registration reg = Registration.builder().id(71L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(81L).registration(reg).amount(3000L).idempotencyKey(key).status("PENDING")
                .build();

        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(3000L)).thenReturn(PaymentGatewayResult.builder()
                .success(false).failureReason("gateway_http_503").build());

        assertThrows(PaymentGatewayUnavailableException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));

        verify(idempotencyService, never()).storeResult(any(), any(), any(Duration.class));
        verify(seatLockingService, never()).releaseSeat(anyString(), anyString());
        assertEquals("PENDING", p.getStatus());
        assertEquals("PENDING", reg.getStatus());
    }

    @Test
    // Spec error 409 Conflict: workshop cancelled before payment is processed.
    void processPayment_cancelledWorkshop_marksFailedReleasesSeatAndStoresFailure() {
        String key = "key-cancelled";
        workshop.setStatus("CANCELLED");
        Registration reg = Registration.builder().id(72L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(82L).registration(reg).amount(3000L).idempotencyKey(key).status("PENDING")
                .build();

        when(idempotencyService.tryMarkInFlight(eq(key), any(Duration.class))).thenReturn(true);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));
        assertTrue(ex.getMessage().contains("cancelled"));

        verify(paymentRepository).save(argThat(pay -> "FAILED".equals(pay.getStatus())));
        verify(registrationRepository).save(argThat(r -> "FAILED".equals(r.getStatus())));
        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(workshopRepository).save(workshop);
        verify(idempotencyService, atLeastOnce()).storeResult(eq(key), any(IdempotencyResult.class),
                any(Duration.class));
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    // Spec UI action: pending registration can be cancelled to release the seat.
    void cancelRegistration_success() {
        Registration reg = Registration.builder().id(90L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(100L).registration(reg).amount(4000L).status("PENDING").build();

        when(registrationRepository.findByUserIdAndWorkshopIdAndStatus(user.getId(), workshop.getId(), "PENDING"))
                .thenReturn(Optional.of(reg));
        when(paymentRepository.findByRegistrationId(reg.getId())).thenReturn(Optional.of(p));

        registrationService.cancelRegistration(workshop.getId(), user.getId());

        verify(registrationRepository).save(argThat(r -> "CANCELLED".equals(r.getStatus())));
        verify(paymentRepository).save(argThat(pay -> "CANCELLED".equals(pay.getStatus())));
        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(workshopRepository).save(workshop);
    }

    @Test
    // Spec/API error 404 Not Found: cancelling requires an existing PENDING
    // registration.
    void cancelRegistration_missingPending_throwsResourceNotFound() {
        when(registrationRepository.findByUserIdAndWorkshopIdAndStatus(user.getId(), workshop.getId(), "PENDING"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> registrationService.cancelRegistration(workshop.getId(), user.getId()));

        verifyNoInteractions(paymentRepository, seatLockingService);
        verify(workshopRepository, never()).save(any());
    }

    @Test
    // Spec acceptance: my-workshops exposes QR only for SUCCESS and includes
    // payment key for PENDING.
    void getUserRegistrations_successOnlyQr_andPendingPaymentKey() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);
        LocalDateTime createdSuccess = LocalDateTime.now().minusMinutes(10);
        LocalDateTime createdPending = LocalDateTime.now().minusMinutes(5);

        Workshop freeWorkshop = Workshop.builder()
                .id(101L)
                .title("Free")
                .price(0L)
                .startTime(start)
                .endTime(end)
                .build();
        Workshop paidWorkshop = Workshop.builder()
                .id(102L)
                .title("Paid")
                .price(9000L)
                .startTime(start.plusDays(1))
                .endTime(end.plusDays(1))
                .build();
        Registration success = Registration.builder()
                .id(201L)
                .workshop(freeWorkshop)
                .status("SUCCESS")
                .qrCode("QR-SUCCESS")
                .createdAt(createdSuccess)
                .build();
        Registration pending = Registration.builder()
                .id(202L)
                .workshop(paidWorkshop)
                .status("PENDING")
                .qrCode("PENDING-SECRET")
                .createdAt(createdPending)
                .build();
        Payment pendingPayment = Payment.builder()
                .id(301L)
                .registration(pending)
                .idempotencyKey("idem-pending")
                .status("PENDING")
                .build();

        when(registrationRepository.findAllByUserIdWithWorkshop(user.getId()))
                .thenReturn(List.of(success, pending));
        when(paymentRepository.findByRegistrationIdIn(List.of(201L, 202L)))
                .thenReturn(List.of(pendingPayment));

        List<UserRegistrationResponse> responses = registrationService.getUserRegistrations(user.getId());

        assertEquals(2, responses.size());
        assertEquals("QR-SUCCESS", responses.get(0).getQrCode());
        assertNull(responses.get(1).getQrCode());
        assertEquals("idem-pending", responses.get(1).getPaymentIdempotencyKey());
        assertEquals(start, responses.get(0).getStartTime());
        assertEquals(end, responses.get(0).getEndTime());
        assertEquals(createdSuccess, responses.get(0).getCreatedAt());
    }

    @Test
    // Spec acceptance: paginated my-workshops keeps metadata and hides QR for
    // non-SUCCESS rows.
    void getUserRegistrations_paginated_mapsContentAndPageMetadata() {
        Registration failed = Registration.builder()
                .id(203L)
                .workshop(Workshop.builder()
                        .id(103L)
                        .title("Failed paid")
                        .price(12000L)
                        .startTime(LocalDateTime.now().plusDays(2))
                        .endTime(LocalDateTime.now().plusDays(2).plusHours(2))
                        .build())
                .status("FAILED")
                .qrCode("QR-SHOULD-NOT-LEAK")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(registrationRepository.findAllByUserIdWithWorkshop(eq(user.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failed), PageRequest.of(0, 12), 1));
        when(paymentRepository.findByRegistrationIdIn(List.of(203L))).thenReturn(List.of());

        PageResponse<UserRegistrationResponse> page = registrationService.getUserRegistrations(user.getId(), 0, 12);

        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals("FAILED", page.getContent().get(0).getStatus());
        assertNull(page.getContent().get(0).getQrCode());
    }
}