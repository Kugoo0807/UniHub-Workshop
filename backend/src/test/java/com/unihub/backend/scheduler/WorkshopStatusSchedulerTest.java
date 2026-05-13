package com.unihub.backend.scheduler;

import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.WorkshopRepository;
import com.unihub.backend.service.SeatLockingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopStatusSchedulerTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private SeatLockingService seatLockingService;

    @InjectMocks
    private WorkshopStatusScheduler scheduler;

    @Test
    void autoUpdateWorkshopStatus_NoEndedWorkshops_ReturnsEarly() {
        // Given
        when(workshopRepository.findByStatusAndEndTimeBefore(eq("PUBLISHED"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // When
        scheduler.autoUpdateWorkshopStatus();

        // Then
        verify(workshopRepository).findByStatusAndEndTimeBefore(eq("PUBLISHED"), any(LocalDateTime.class));
        verify(workshopRepository, never()).save(any(Workshop.class));
        verify(seatLockingService, never()).removeSlots(any(String.class));
    }

    @Test
    void autoUpdateWorkshopStatus_EndedWorkshop_UpdatesAndRemovesSlots() {
        // Given
        Workshop workshop = workshop(31L, "PUBLISHED");
        when(workshopRepository.findByStatusAndEndTimeBefore(eq("PUBLISHED"), any(LocalDateTime.class)))
                .thenReturn(List.of(workshop));

        // When
        scheduler.autoUpdateWorkshopStatus();

        // Then
        assertThat(workshop.getStatus()).isEqualTo("COMPLETED");
        verify(workshopRepository).save(workshop);
        verify(seatLockingService).removeSlots("31");
    }

    @Test
    void autoUpdateWorkshopStatus_FirstSaveThrows_SecondStillProcessed() {
        // Given
        Workshop first = workshop(41L, "PUBLISHED");
        Workshop second = workshop(42L, "PUBLISHED");
        when(workshopRepository.findByStatusAndEndTimeBefore(eq("PUBLISHED"), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("db error")).when(workshopRepository).save(first);

        // When
        scheduler.autoUpdateWorkshopStatus();

        // Then
        verify(workshopRepository).save(first);
        verify(workshopRepository).save(second);
        assertThat(second.getStatus()).isEqualTo("COMPLETED");
        verify(seatLockingService, never()).removeSlots("41");
        verify(seatLockingService).removeSlots("42");
    }

    private static Workshop workshop(Long id, String status) {
        return Workshop.builder()
                .id(id)
                .title("Workshop " + id)
                .status(status)
                .totalSlots(10)
                .remainingSlots(10)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .registrationStartTime(LocalDateTime.now().minusDays(1))
                .registrationEndTime(LocalDateTime.now().minusHours(3))
                .build();
    }
}
