package com.unihub.backend.service;

import com.unihub.backend.dto.WorkshopRequest;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.dto.WorkshopStatsResponse;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkshopServiceTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    // Mock the SeatLockingService which is an external integration (Redis).
    // WorkshopService calls into this for slot initialization/removal and
    // to fetch accurate remaining slots. In unit tests we mock it to avoid
    // requiring a running Redis instance.
    @Mock
    private com.unihub.backend.service.SeatLockingService seatLockingService;

    @InjectMocks
    private WorkshopService workshopService;

    /**
     * Prepare lenient stubs for external/side-effecting integrations used by
     * WorkshopService. This avoids NPEs in unit tests and removes the need to
     * run Redis during local unit test execution.
     */
    @BeforeEach
    void initMocks() {
        // stub void methods to do nothing
        doNothing().when(seatLockingService).initSlots(anyString(), anyInt(), anyLong());
        doNothing().when(seatLockingService).removeSlots(anyString());

        // Provide a dynamic remaining-slots answer so tests that stub the
        // repository remainingSlots value still see the expected value from
        // the mocked SeatLockingService. The key passed by WorkshopService is
        // typically in the form "ws-{id}", so parse the id and query the
        // mocked repository; fallback to 60 when parsing fails.
        lenient().when(seatLockingService.getRemainingSlots(anyString())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof String) {
                String key = (String) arg;
                // Try numeric id first (WorkshopService passes String.valueOf(id))
                try {
                    Long id = Long.parseLong(key);
                    Optional<Workshop> w = workshopRepository.findById(id);
                    if (w.isPresent()) return w.get().getRemainingSlots();
                } catch (NumberFormatException ignored) {
                    // Fallback: support keys like "ws-{id}" if encountered
                    if (key.startsWith("ws-")) {
                        String idStr = key.substring(3);
                        try {
                            Long id = Long.parseLong(idStr);
                            Optional<Workshop> w = workshopRepository.findById(id);
                            if (w.isPresent()) return w.get().getRemainingSlots();
                        } catch (NumberFormatException ignored2) {
                        }
                    }
                }
            }
            return 60;
        });
    }
    // WM-UT-01: Create valid workshop

        // Verify successful Workshop creation with valid data
    @Test
    void createWorkshop_validRequest_savesAndReturnsCreated() {
        WorkshopRequest request = validRequest();
        Workshop saved = workshopFromRequest(request, 1L);

        when(workshopRepository.save(any(Workshop.class))).thenReturn(saved);

        WorkshopResponse response = workshopService.createWorkshop(request);

        ArgumentCaptor<Workshop> captor = ArgumentCaptor.forClass(Workshop.class);
        verify(workshopRepository).save(captor.capture());
        Workshop captured = captor.getValue();

        assertEquals(request.title(), captured.getTitle());
        assertEquals(request.description(), captured.getDescription());
        assertEquals(request.totalSlots(), captured.getTotalSlots());
        assertEquals(request.totalSlots(), captured.getRemainingSlots());
        assertEquals(request.price(), captured.getPrice());
        assertEquals(request.startTime(), captured.getStartTime());
        assertEquals(request.endTime(), captured.getEndTime());

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(request.title(), response.getTitle());
        assertEquals(request.totalSlots(), response.getRemainingSlots());
    }

        // Verify Workshop creation with null price defaults to 0
    @Test
    void createWorkshop_nullPrice_defaultsToZero() {
        WorkshopRequest request = new WorkshopRequest(
                "Test Workshop", null, 50, null,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));
        Workshop saved = workshopFromRequest(request, 2L);
        saved.setPrice(0L);

        when(workshopRepository.save(any(Workshop.class))).thenReturn(saved);

        workshopService.createWorkshop(request);

        ArgumentCaptor<Workshop> captor = ArgumentCaptor.forClass(Workshop.class);
        verify(workshopRepository).save(captor.capture());
        assertEquals(0L, captor.getValue().getPrice());
    }

        // Verify Workshop creation allows null description
    @Test
    void createWorkshop_nullDescription_allowsNull() {
        WorkshopRequest request = new WorkshopRequest(
                "Workshop No Desc", null, 30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));
        Workshop saved = workshopFromRequest(request, 3L);

        when(workshopRepository.save(any(Workshop.class))).thenReturn(saved);

        WorkshopResponse response = workshopService.createWorkshop(request);

        assertNull(response.getDescription());
    }

    // WM-UT-02: Create with end_time before start_time

        // Verify Workshop creation throws error when end time is before start time
    @Test
    void createWorkshop_endTimeBeforeStartTime_throwsIllegalArgument() {
        WorkshopRequest request = new WorkshopRequest(
                "Bad Times", null, 30, 0L,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 8, 0)
        );

        IllegalArgumentException ex =
        assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("end_time must be after start_time"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

        // Verify Workshop creation throws error when end time equals start time
    @Test
    void createWorkshop_endTimeEqualsStartTime_throwsIllegalArgument() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 5, 10, 10, 0);
        WorkshopRequest request = new WorkshopRequest(
                "Same Times", null, 30, 0L, sameTime, sameTime);

        assertThrows(IllegalArgumentException.class, () -> workshopService.createWorkshop(request));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // WM-UT-04: Delete workshop with SUCCESS registrations


        // Verify Workshop deletion throws Conflict if there are successful registrations
    @Test
    void deleteWorkshop_hasSuccessRegistrations_throwsConflict() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(true);

        ConflictException ex =
        assertThrows(
                ConflictException.class,
                () ->
        workshopService.deleteWorkshop(1L));
        assertTrue(ex.getMessage().contains("successful registrations"));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    // WM-UT-05: Delete workshop with no registrations

        // Verify successful Workshop deletion when there are no registrations
    @Test
    void deleteWorkshop_noRegistrations_deletesSuccessfully() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(false);

        workshopService.deleteWorkshop(1L);

        verify(workshopRepository).delete(existing);
    }

        // Verify Workshop deletion throws Not Found for non-existent ID
    @Test
    void deleteWorkshop_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
        workshopService.deleteWorkshop(999L));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    // WM-UT-06: Update total_slots when there are registrations

        // Verify Workshop update throws Conflict when changing total slots with existing registrations
    @Test
    void updateWorkshop_changeTotalSlotsWithRegistrations_throwsConflict() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title", null, 100, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopId(1L)).thenReturn(true);

        ConflictException ex =
        assertThrows(
                ConflictException.class,
                () -> workshopService.updateWorkshop(1L, request));
        assertTrue(ex.getMessage().contains("Cannot change total_slots"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

        // Verify successful Workshop update and total slots change when there are no registrations
    @Test
    void updateWorkshop_changeTotalSlotsWithoutRegistrations_succeeds() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title", "New desc", 100, 10L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopId(1L)).thenReturn(false);
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        // `WorkshopService.updateWorkshop` registers an after-commit callback
        // via TransactionSynchronizationManager. In plain unit tests there is
        // no active transaction, so enable a test synchronization scope to
        // allow registration of the callback and avoid IllegalStateException.
        TransactionSynchronizationManager.initSynchronization();
        try {
            WorkshopResponse response = workshopService.updateWorkshop(1L, request);

            verify(workshopRepository).save(existing);
            assertEquals(100, existing.getTotalSlots());
            assertEquals(100, existing.getRemainingSlots());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

        // Verify successful Workshop update when keeping total slots unchanged (even with registrations)
    @Test
    void updateWorkshop_sameTotalSlotsWithRegistrations_succeeds() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title Only", null, 60, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.updateWorkshop(1L, request);

        verify(workshopRepository).save(existing);
        assertEquals("Updated Title Only", existing.getTitle());
    }

        // Verify Workshop update throws Not Found for non-existent ID
    @Test
    void updateWorkshop_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> workshopService.updateWorkshop(999L, validRequest()));
    }

        // Verify Workshop update throws error when end time is before start time
    @Test
    void updateWorkshop_endTimeBeforeStartTime_throwsIllegalArgument() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        WorkshopRequest request = new WorkshopRequest(
                "Bad Update", null, 60, 0L,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 8, 0));

        assertThrows(IllegalArgumentException.class, () -> workshopService.updateWorkshop(1L, request));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // WM-UT-07: Get details of non-existent workshop

        // Verify getting Workshop details throws Not Found for non-existent ID
    @Test
    void getWorkshopById_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex =
        assertThrows(
                ResourceNotFoundException.class,
                () -> workshopService.getWorkshopById(999L));
        assertTrue(ex.getMessage().contains("Workshop not found"));
    }

        // Verify getting Workshop details successfully returns correct data
    @Test
    void getWorkshopById_found_returnsResponse() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        WorkshopResponse response = workshopService.getWorkshopById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(existing.getTitle(), response.getTitle());
        assertEquals(existing.getRemainingSlots(), response.getRemainingSlots());
    }

    // getAllWorkshops

        // Verify getting all Workshops successfully
    @Test
    void getAllWorkshops_returnsAllMappedToResponse() {
        Workshop w1 = baseWorkshop(1L);
        Workshop w2 = baseWorkshop(2L);
        w2.setTitle("Second Workshop");

        when(workshopRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(w1, w2));

        List<WorkshopResponse> result = workshopService.getAllWorkshops();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals("Second Workshop", result.get(1).getTitle());
    }

        // Verify getting all Workshops returns empty array when no data
    @Test
    void getAllWorkshops_emptyList_returnsEmpty() {
        when(workshopRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());

        List<WorkshopResponse> result = workshopService.getAllWorkshops();

        assertTrue(result.isEmpty());
    }

    // getWorkshopStats

        // Verify getting Workshop stats successfully and calculates correct fill rate
    @Test
    void getWorkshopStats_validId_returnsCorrectStats() {
        Workshop workshop = baseWorkshop(1L);
        workshop.setTotalSlots(60);
        workshop.setRemainingSlots(42);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(workshop));
        when(registrationRepository.countByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(18L);

        WorkshopStatsResponse stats = workshopService.getWorkshopStats(1L);

        assertEquals(1L, stats.getWorkshopId());
        assertEquals(workshop.getTitle(), stats.getTitle());
        assertEquals(60, stats.getTotalSlots());
        assertEquals(42, stats.getRemainingSlots());
        assertEquals(18L, stats.getRegisteredCount());
        assertEquals(30.0, stats.getFillRate());
    }

        // Verify getting Workshop stats returns 100% fill rate when full
    @Test
    void getWorkshopStats_fullWorkshop_fillRate100() {
        Workshop workshop = baseWorkshop(1L);
        workshop.setTotalSlots(10);
        workshop.setRemainingSlots(0);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(workshop));
        when(registrationRepository.countByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(10L);

        WorkshopStatsResponse stats = workshopService.getWorkshopStats(1L);

        assertEquals(0, stats.getRemainingSlots());
        assertEquals(100.0, stats.getFillRate());
    }

        // Verify getting Workshop stats returns 0% fill rate when no registrations
    @Test
    void getWorkshopStats_emptyWorkshop_fillRate0() {
        Workshop workshop = baseWorkshop(1L);
        workshop.setTotalSlots(60);
        workshop.setRemainingSlots(60);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(workshop));
        when(registrationRepository.countByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(0L);

        WorkshopStatsResponse stats = workshopService.getWorkshopStats(1L);

        assertEquals(60, stats.getRemainingSlots());
        assertEquals(0L, stats.getRegisteredCount());
        assertEquals(0.0, stats.getFillRate());
    }

        // Verify getting Workshop stats throws Not Found for non-existent ID
    @Test
    void getWorkshopStats_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workshopService.getWorkshopStats(999L));
    }

    // Edge cases

        // Verify successful Workshop deletion when there are only Pending/Cancelled registrations (no Success)
    @Test
    void deleteWorkshop_hasPendingButNoSuccessRegistrations_deletesSuccessfully() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(false);

        workshopService.deleteWorkshop(1L);

        verify(workshopRepository).delete(existing);
    }

        // Verify Workshop update persists valid fields correctly
    @Test
    void updateWorkshop_validFieldsUpdated_persists() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "New Title", "New Description", 60, 50000L,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 17, 0));

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.updateWorkshop(1L, request);

        assertEquals("New Title", existing.getTitle());
        assertEquals("New Description", existing.getDescription());
        // `Workshop.price` is stored as Long in the entity; assert the Long value.
        assertEquals(50000L, existing.getPrice());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 0), existing.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 1, 17, 0), existing.getEndTime());
    }

    // Test Helpers

    private WorkshopRequest validRequest() {
        return new WorkshopRequest(
                "Workshop: Clean Code với Java",
                "Mô tả workshop về Clean Code.",
                60,
                0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0));
    }

    private Workshop baseWorkshop(Long id) {
        return Workshop.builder()
                .id(id)
                .title("Test Workshop")
                .description(null)
                .totalSlots(60)
                .remainingSlots(60)
                .price(0L)
                .startTime(LocalDateTime.of(2026, 5, 10, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 10, 12, 0))
                .build();
    }

    private Workshop workshopFromRequest(WorkshopRequest request, Long id) {
        return Workshop.builder()
                .id(id)
                .title(request.title())
                .description(request.description())
                .totalSlots(request.totalSlots())
                .remainingSlots(request.totalSlots())
                .price(request.price() != null ? request.price() : 0L)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();
    }
}
