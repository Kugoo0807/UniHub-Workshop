package com.unihub.backend.service;

import com.unihub.backend.dto.IdempotencyResult;
import com.unihub.backend.dto.PaymentResultResponse;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.enums.IdempotencyState;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.exception.SeatUnavailableException;
import com.unihub.backend.exception.PaymentGatewayUnavailableException;
import com.unihub.backend.exception.PaymentFailedException;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
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
 * These tests focus on business logic only and mock all infrastructure dependencies
 * (repositories, payment gateway, seat locking, idempotency). They cover both
 * free and paid registration flows, validation errors, seat reservation behavior,
 * and idempotent payment processing scenarios.
 */
class RegistrationServiceTest {

    @Mock WorkshopRepository workshopRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock SeatLockingService seatLockingService;
    @Mock IdempotencyService idempotencyService;
    @Mock PaymentGatewayClient paymentGatewayClient;

    @InjectMocks RegistrationService registrationService;

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
    // Scenario: free workshop; seat reserved successfully and registration completes immediately
    void register_freeWorkshop_success() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopId(user.getId(), workshop.getId())).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(true);

        Registration saved = Registration.builder().id(1L).qrCode("QR-STATIC").status("SUCCESS").build();
        when(registrationRepository.save(any(Registration.class))).thenReturn(saved);

        RegistrationResponse resp = registrationService.register(workshop.getId(), user.getId());
        assertFalse(resp.isPaidFlow());
        assertEquals("QR-STATIC", resp.getQrCode());
        verify(workshopRepository).save(any());
    }

    @Test
    // Scenario: paid workshop; registration created and pending payment (idempotency key returned)
    void register_paidWorkshop_returnsPending() {
        workshop.setPrice(5000L);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopId(user.getId(), workshop.getId())).thenReturn(false);
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
        verify(paymentRepository).save(any());
    }

    @Test
    // Scenario: user already registered -> should raise a ConflictException
    void register_duplicate_throwsConflict() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopId(user.getId(), workshop.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Scenario: registration outside allowed window -> should raise a ConflictException
    void register_outsideWindow_throwsConflict() {
        workshop.setRegistrationStartTime(LocalDateTime.now().plusDays(1));
        workshop.setRegistrationEndTime(LocalDateTime.now().plusDays(2));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Scenario: workshop not published -> registration disallowed
    void register_notPublished_throwsConflict() {
        workshop.setStatus("DRAFT");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ConflictException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Scenario: seat locking service indicates no seats available -> SeatUnavailableException
    void register_noSeats_throwsSeatUnavailable() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));
        when(registrationRepository.existsByUserIdAndWorkshopId(user.getId(), workshop.getId())).thenReturn(false);
        when(seatLockingService.reserveSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId())))
                .thenReturn(false);

        assertThrows(SeatUnavailableException.class, () -> registrationService.register(workshop.getId(), user.getId()));
    }

    @Test
    // Scenario: idempotency key marked as IN_FLIGHT -> reject concurrent payment
    void processPayment_inFlight_throwsConflict() {
        String key = UUID.randomUUID().toString();
        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.IN_FLIGHT);

        assertThrows(ConflictException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));
    }

    @Test
    // Scenario: idempotency state SUCCESS -> return stored transaction and QR
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
    }

    @Test
    // Scenario: idempotency key unknown but payment record missing -> release seat and throw
    void processPayment_missingPayment_releasesSeat_and_throwsNotFound() {
        String key = "missing-key";
        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.NOT_FOUND);
        doNothing().when(idempotencyService).markInFlight(eq(key), any(Duration.class));
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // ensure workshop found so releaseSeat is called
        when(workshopRepository.findById(workshop.getId())).thenReturn(Optional.of(workshop));

        assertThrows(ResourceNotFoundException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));

        verify(seatLockingService).releaseSeat(String.valueOf(workshop.getId()), String.valueOf(user.getId()));
        verify(workshopRepository).save(any());
    }

    @Test
    // Scenario: payment gateway returns success -> persist payment & registration updates, store idempotency
    void processPayment_gatewaySuccess_updatesEntities_and_storesIdempotency() {
        String key = "key-ok";
        Registration reg = Registration.builder().id(30L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(40L).registration(reg).amount(1234L).idempotencyKey(key).status("PENDING").build();

        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.NOT_FOUND);
        doNothing().when(idempotencyService).markInFlight(eq(key), any(Duration.class));
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(1234L)).thenReturn(com.unihub.backend.service.PaymentGatewayResult.builder()
                .success(true).transactionId("tx-999").build());

        PaymentResultResponse resp = registrationService.processPayment(workshop.getId(), user.getId(), key);
        assertTrue(resp.isSuccess());
        assertEquals("tx-999", resp.getTransactionId());
        verify(paymentRepository).save(argThat(pay -> "COMPLETED".equals(pay.getStatus())));
        verify(registrationRepository).save(argThat(r -> "SUCCESS".equals(r.getStatus()) && r.getQrCode() != null));
        verify(idempotencyService).storeResult(eq(key), any(IdempotencyResult.class), any(Duration.class));
    }

    @Test
    // Scenario: payment gateway failure -> mark payment failed, update registration, release seat, store failure
    void processPayment_gatewayFailure_handlesFailure_and_releasesSeat() {
        String key = "key-fail";
        Registration reg = Registration.builder().id(50L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(60L).registration(reg).amount(2000L).idempotencyKey(key).status("PENDING").build();

        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.NOT_FOUND);
        doNothing().when(idempotencyService).markInFlight(eq(key), any(Duration.class));
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(2000L)).thenReturn(com.unihub.backend.service.PaymentGatewayResult.builder()
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
    // Scenario: payment gateway temporarily unavailable -> throw PaymentGatewayUnavailableException and store gateway_error
    void processPayment_gatewayUnavailable_throwsAndStoresGatewayError() {
        String key = "key-unavailable";
        Registration reg = Registration.builder().id(70L).status("PENDING").workshop(workshop).build();
        Payment p = Payment.builder().id(80L).registration(reg).amount(3000L).idempotencyKey(key).status("PENDING").build();

        when(idempotencyService.getState(key)).thenReturn(IdempotencyState.NOT_FOUND);
        doNothing().when(idempotencyService).markInFlight(eq(key), any(Duration.class));
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(p));
        when(paymentGatewayClient.charge(3000L)).thenReturn(com.unihub.backend.service.PaymentGatewayResult.builder()
                .success(false).failureReason("payment_gateway_unavailable").build());

        assertThrows(PaymentGatewayUnavailableException.class,
                () -> registrationService.processPayment(workshop.getId(), user.getId(), key));

        verify(idempotencyService, never()).storeResult(any(), any(), any(Duration.class));
        verify(seatLockingService, never()).releaseSeat(anyString(), anyString());
        verify(paymentRepository, never()).save(argThat(pay -> "FAILED".equals(pay.getStatus()) || "COMPLETED".equals(pay.getStatus())));
    }
}
