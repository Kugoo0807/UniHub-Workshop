package com.unihub.backend.scheduler;

import com.unihub.backend.entity.Payment;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.SeatLockingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingRegistrationCancellationSchedulerTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private SeatLockingService seatLockingService;

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PendingRegistrationCancellationScheduler scheduler;

    @Test
    void cancelStalePendingRegistrations_NoStaleRegistrations_ReturnsEarly() {
        // Given
        when(registrationRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // When
        scheduler.cancelStalePendingRegistrations();

        // Then
        verify(registrationRepository).findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class));
        verify(seatLockingService, never()).releaseSeat(any(String.class), any(String.class));
        verify(workshopRepository, never()).save(any(Workshop.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void cancelStalePendingRegistrations_StalePendingWithoutPayment_CancelsAndReleasesSeatAndIncrementsSlots() {
        // Given
        Registration registration = registration(1L, "PENDING", user(11L), workshop(21L, 5, 2));
        when(registrationRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(registration));
        when(paymentRepository.findByRegistrationId(1L)).thenReturn(Optional.empty());

        // When
        scheduler.cancelStalePendingRegistrations();

        // Then
        assertThat(registration.getStatus()).isEqualTo("CANCELLED");
        verify(registrationRepository).save(registration);
        verify(paymentRepository).findByRegistrationId(1L);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(seatLockingService).releaseSeat("21", "11");

        ArgumentCaptor<Workshop> workshopCaptor = ArgumentCaptor.forClass(Workshop.class);
        verify(workshopRepository).save(workshopCaptor.capture());
        Workshop savedWorkshop = workshopCaptor.getValue();
        assertThat(savedWorkshop.getRemainingSlots()).isEqualTo(3);
    }

    @Test
    void cancelStalePendingRegistrations_StalePendingWithPendingPayment_CancelsRegistrationAndPaymentAndReleasesSeat() {
        // Given
        Registration registration = registration(2L, "PENDING", user(12L), workshop(22L, 10, 9));
        Payment payment = payment(100L, registration, "PENDING");
        when(registrationRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(registration));
        when(paymentRepository.findByRegistrationId(2L)).thenReturn(Optional.of(payment));

        // When
        scheduler.cancelStalePendingRegistrations();

        // Then
        assertThat(registration.getStatus()).isEqualTo("CANCELLED");
        verify(registrationRepository).save(registration);
        verify(paymentRepository).findByRegistrationId(2L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("CANCELLED");

        verify(seatLockingService).releaseSeat("22", "12");
        verify(workshopRepository).save(any(Workshop.class));
    }

    @Test
    void cancelStalePendingRegistrations_FirstRegistrationThrows_SecondStillProcessed() {
        // Given
        Registration first = registration(3L, "PENDING", user(13L), workshop(23L, 5, 4));
        Registration second = registration(4L, "PENDING", user(14L), workshop(24L, 5, 1));
        when(registrationRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("db down")).when(registrationRepository).save(first);
        when(paymentRepository.findByRegistrationId(4L)).thenReturn(Optional.empty());

        // When
        scheduler.cancelStalePendingRegistrations();

        // Then
        verify(registrationRepository).save(first);
        verify(registrationRepository).save(second);
        verify(seatLockingService, never()).releaseSeat("23", "13");
        verify(seatLockingService).releaseSeat("24", "14");
        verify(workshopRepository).save(second.getWorkshop());
    }

    private static User user(Long id) {
        return User.builder().id(id).build();
    }

    private static Workshop workshop(Long id, Integer totalSlots, Integer remainingSlots) {
        return Workshop.builder()
                .id(id)
                .totalSlots(totalSlots)
                .remainingSlots(remainingSlots)
                .build();
    }

    private static Registration registration(Long id, String status, User user, Workshop workshop) {
        return Registration.builder()
                .id(id)
                .status(status)
                .user(user)
                .workshop(workshop)
                .build();
    }

    private static Payment payment(Long id, Registration registration, String status) {
        return Payment.builder()
                .id(id)
                .registration(registration)
                .status(status)
                .amount(1000L)
                .idempotencyKey("idem-" + id)
                .build();
    }
}
