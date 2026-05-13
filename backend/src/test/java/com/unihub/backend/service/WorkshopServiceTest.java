package com.unihub.backend.service;

import com.unihub.backend.dto.WorkshopRequest;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.dto.WorkshopStatsResponse;
import com.unihub.backend.entity.Room;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.RoomRepository;
import com.unihub.backend.repository.WorkshopRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkshopServiceTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private SeatLockingService seatLockingService;

    @InjectMocks
    private WorkshopService workshopService;

    private Room defaultRoom;

    @BeforeEach
    void initMocks() {
        // Prepare a default room for tests
        defaultRoom = Room.builder()
                .id(1L)
                .name("Hall A")
                .capacity(100)
                .build();

        // Stub void methods
        doNothing().when(seatLockingService).initSlots(anyString(), anyInt(), anyLong());
        doNothing().when(seatLockingService).removeSlots(anyString());

        // Default: Redis returns -1 (key missing) → fallback to DB remaining_slots
        lenient().when(seatLockingService.getRemainingSlots(anyString())).thenReturn(-1);

        // Default: Room exists
        lenient().when(roomRepository.findById(1L)).thenReturn(Optional.of(defaultRoom));
    }

    // ──────────── WM-UT-01: Create valid workshop ────────────

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
        assertEquals(request.registrationStartTime(), captured.getRegistrationStartTime());
        assertEquals(request.registrationEndTime(), captured.getRegistrationEndTime());
        assertEquals("DRAFT", captured.getStatus());
        assertSame(defaultRoom, captured.getRoom());

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(request.title(), response.getTitle());
        assertEquals("Hall A", response.getRoomName());
        assertEquals("DRAFT", response.getStatus());
    }

    @Test
    void createWorkshop_nullPrice_defaultsToZero() {
        WorkshopRequest request = new WorkshopRequest(
                "Test Workshop", null, 1L, "Speaker A",
                50, null,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0)); // 1 day and 1 hour before
        Workshop saved = workshopFromRequest(request, 2L);
        saved.setPrice(0L);

        when(workshopRepository.save(any(Workshop.class))).thenReturn(saved);

        workshopService.createWorkshop(request);

        ArgumentCaptor<Workshop> captor = ArgumentCaptor.forClass(Workshop.class);
        verify(workshopRepository).save(captor.capture());
        assertEquals(0L, captor.getValue().getPrice());
    }

    @Test
    void createWorkshop_nullDescription_allowsNull() {
        WorkshopRequest request = new WorkshopRequest(
                "Workshop No Desc", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));
        Workshop saved = workshopFromRequest(request, 3L);

        when(workshopRepository.save(any(Workshop.class))).thenReturn(saved);

        WorkshopResponse response = workshopService.createWorkshop(request);

        assertNull(response.getDescription());
    }

    // ──────────── WM-UT-02: Create with end_time before start_time ────────────

    @Test
    void createWorkshop_endTimeBeforeStartTime_throwsIllegalArgument() {
        WorkshopRequest request = new WorkshopRequest(
                "Bad Times", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("end_time must be after start_time"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // ──────────── WM-UT-03: Create with total_slots = 0 ────────────

    @Test
    void createWorkshop_totalSlotsZero_failsBeanValidation() {
        // WM-UT-03: total_slots = 0 should be rejected by @Positive constraint
        WorkshopRequest request = new WorkshopRequest(
                "Workshop Zero Slots", null, 1L, "Speaker A",
                0, 0L, // total_slots = 0
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<WorkshopRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Expected validation error for total_slots = 0");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("totalSlots")),
                "Expected violation on totalSlots field");
    }

    @Test
    void createWorkshop_totalSlotsNegative_failsBeanValidation() {
        // Supplementary to WM-UT-03: negative total_slots
        WorkshopRequest request = new WorkshopRequest(
                "Workshop Negative Slots", null, 1L, "Speaker A",
                -5, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<WorkshopRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("totalSlots")));
    }

    @Test
    void createWorkshop_endTimeEqualsStartTime_throwsIllegalArgument() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 5, 10, 10, 0);
        WorkshopRequest request = new WorkshopRequest(
                "Same Times", null, 1L, "Speaker A",
                30, 0L, sameTime, sameTime,
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 10, 9, 30));

        assertThrows(IllegalArgumentException.class, () -> workshopService.createWorkshop(request));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // ──────────── G24: Same-day constraint ────────────

    @Test
    void createWorkshop_startEndDifferentDays_throwsIllegalArgument() {
        WorkshopRequest request = new WorkshopRequest(
                "Multi Day", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 11, 8, 0),  // different day
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("same day"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // ──────────── G5: Registration time validation ────────────

    @Test
    void createWorkshop_regEndBeforeRegStart_throwsIllegalArgument() {
        WorkshopRequest request = new WorkshopRequest(
                "Bad Reg Times", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 7, 0),   // reg start AFTER reg end
                LocalDateTime.of(2026, 5, 5, 8, 0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("registration_end_time must be after"));
    }

    @Test
    void createWorkshop_regStartAfterWorkshopStart_throwsIllegalArgument() {
        WorkshopRequest request = new WorkshopRequest(
                "Late Reg", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 9, 0),   // reg start AFTER workshop start
                LocalDateTime.of(2026, 5, 10, 11, 0));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("registration_start_time must be before"));
    }

    @Test
    void createWorkshop_regEndLessThannOneDayBeforeWorkshopStart_throwsIllegalArgument() {
        // New rule: registrationEndTime <= startTime - 1 day
        WorkshopRequest request = new WorkshopRequest(
                "Late Reg End", null, 1L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),  // Start: 10th
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 23, 0)); // End: 9th 23:00 (less than 24h before 10th 08:00)

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("at least 1 day before"));
    }

    // ──────────── G6: Room capacity validation ────────────

    @Test
    void createWorkshop_totalSlotsExceedsRoomCapacity_throwsConflict() {
        WorkshopRequest request = new WorkshopRequest(
                "Too Many Slots", null, 1L, "Speaker A",
                150, 0L,  // room capacity is 100
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.createWorkshop(request));
        assertTrue(ex.getMessage().contains("exceeds room capacity"));
    }

    @Test
    void createWorkshop_roomNotFound_throwsResourceNotFound() {
        WorkshopRequest request = new WorkshopRequest(
                "No Room", null, 999L, "Speaker A",
                30, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workshopService.createWorkshop(request));
    }

    // ──────────── WM-UT-04/WM-UT-05/G10: Delete workshop ────────────

    @Test
    void deleteWorkshop_draftStatus_deletesSuccessfully() {
        // WM-UT-05: Delete workshop without registrations → DELETE DB, DEL Redis key
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("DRAFT");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        workshopService.deleteWorkshop(1L);

        verify(workshopRepository).delete(existing);
        verify(seatLockingService).removeSlots("1");
    }

    @Test
    void deleteWorkshop_publishedStatus_throwsConflict() {
        // WM-UT-04: Published workshop (may have SUCCESS registrations) → ConflictException
        // Implementation checks DRAFT status rather than registrations directly,
        // which is stricter: non-DRAFT workshops cannot be deleted regardless of registrations.
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("PUBLISHED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.deleteWorkshop(1L));
        assertTrue(ex.getMessage().contains("Only DRAFT"));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    @Test
    void deleteWorkshop_completedStatus_throwsConflict() {
        // WM-UT-04 supplementary: COMPLETED workshop cannot be deleted
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("COMPLETED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.deleteWorkshop(1L));
        assertTrue(ex.getMessage().contains("Only DRAFT"));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    @Test
    void deleteWorkshop_cancelledStatus_throwsConflict() {
        // WM-UT-04 supplementary: CANCELLED workshop cannot be deleted
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("CANCELLED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.deleteWorkshop(1L));
        assertTrue(ex.getMessage().contains("Only DRAFT"));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    @Test
    void deleteWorkshop_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                workshopService.deleteWorkshop(999L));
        verify(workshopRepository, never()).delete(any(Workshop.class));
    }

    // ──────────── G8/G9: Cancel workshop ────────────

    @Test
    void cancelWorkshop_draftStatus_cancelledSuccessfully() {
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("DRAFT");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.cancelWorkshop(1L);

        assertEquals("CANCELLED", existing.getStatus());
        verify(workshopRepository).save(existing);
        verify(seatLockingService).removeSlots("1");
    }

    @Test
    void cancelWorkshop_publishedStatus_cancelledSuccessfully() {
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("PUBLISHED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.cancelWorkshop(1L);

        assertEquals("CANCELLED", existing.getStatus());
    }

    @Test
    void cancelWorkshop_alreadyCancelled_throwsIllegalArgument() {
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("CANCELLED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> workshopService.cancelWorkshop(1L));
        assertTrue(ex.getMessage().contains("already CANCELLED"));
    }

    @Test
    void cancelWorkshop_completed_throwsIllegalArgument() {
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("COMPLETED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> workshopService.cancelWorkshop(1L));
    }

    @Test
    void cancelWorkshop_withSuccessfulRegistrations_throwsConflict() {
        Workshop existing = baseWorkshop(1L);
        existing.setStatus("PUBLISHED");
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopIdAndStatus(1L, "SUCCESS")).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.cancelWorkshop(1L));
        assertTrue(ex.getMessage().contains("Cannot cancel workshop"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // ──────────── WM-UT-06: Update total_slots with registrations ────────────

    @Test
    void updateWorkshop_changeTotalSlotsWithRegistrations_throwsConflict() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title", null, 1L, "Speaker A",
                100, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopId(1L)).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> workshopService.updateWorkshop(1L, request));
        assertTrue(ex.getMessage().contains("Cannot change total_slots"));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    @Test
    void updateWorkshop_changeTotalSlotsWithoutRegistrations_succeeds() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title", "New desc", 1L, "Speaker A",
                80, 10L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByWorkshopId(1L)).thenReturn(false);
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        try {
            WorkshopResponse response = workshopService.updateWorkshop(1L, request);
            verify(workshopRepository).save(existing);
            assertEquals(80, existing.getTotalSlots());
            assertEquals(80, existing.getRemainingSlots());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateWorkshop_sameTotalSlotsWithRegistrations_succeeds() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "Updated Title Only", null, 1L, "Speaker A",
                60, 0L,
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.updateWorkshop(1L, request);

        verify(workshopRepository).save(existing);
        assertEquals("Updated Title Only", existing.getTitle());
    }

    @Test
    void updateWorkshop_notFound_throwsResourceNotFound() {
        when(workshopRepository.findByIdWithRoom(999L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> workshopService.updateWorkshop(999L, validRequest()));
    }

    @Test
    void updateWorkshop_endTimeBeforeStartTime_throwsIllegalArgument() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));

        WorkshopRequest request = new WorkshopRequest(
                "Bad Update", null, 1L, "Speaker A",
                60, 0L,
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),
                LocalDateTime.of(2026, 5, 9, 7, 0));

        assertThrows(IllegalArgumentException.class, () -> workshopService.updateWorkshop(1L, request));
        verify(workshopRepository, never()).save(any(Workshop.class));
    }

    // ──────────── WM-UT-07: Get details of non-existent workshop ────────────

    @Test
    void getWorkshopById_notFound_throwsResourceNotFound() {
        when(workshopRepository.findByIdWithRoom(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> workshopService.getWorkshopById(999L));
        assertTrue(ex.getMessage().contains("Workshop not found"));
    }

    @Test
    void getWorkshopById_found_returnsResponse() {
        Workshop existing = baseWorkshop(1L);
        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));

        WorkshopResponse response = workshopService.getWorkshopById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(existing.getTitle(), response.getTitle());
        assertEquals("Hall A", response.getRoomName());
        assertEquals(100, response.getRoomCapacity());
        assertEquals("DRAFT", response.getStatus());
        assertEquals("Speaker A", response.getSpeaker());
    }

    // ──────────── getAllWorkshops ────────────

    @Test
    void getAllWorkshops_returnsAllMappedToResponse() {
        Workshop w1 = baseWorkshop(1L);
        Workshop w2 = baseWorkshop(2L);
        w2.setTitle("Second Workshop");

        when(workshopRepository.findAllWithRoom()).thenReturn(List.of(w1, w2));

        List<WorkshopResponse> result = workshopService.getAllWorkshops();

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals("Second Workshop", result.get(1).getTitle());
    }

    @Test
    void getAllWorkshops_emptyList_returnsEmpty() {
        when(workshopRepository.findAllWithRoom()).thenReturn(List.of());

        List<WorkshopResponse> result = workshopService.getAllWorkshops();

        assertTrue(result.isEmpty());
    }

    // ──────────── getWorkshopStats ────────────

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

    @Test
    void getWorkshopStats_notFound_throwsResourceNotFound() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workshopService.getWorkshopStats(999L));
    }

    // ──────────── Edge cases ────────────

    @Test
    void updateWorkshop_validFieldsUpdated_persists() {
        Workshop existing = baseWorkshop(1L);
        existing.setTotalSlots(60);

        WorkshopRequest request = new WorkshopRequest(
                "New Title", "New Description", 1L, "New Speaker",
                60, 50000L,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 17, 0),
                LocalDateTime.of(2026, 5, 25, 8, 0),
                LocalDateTime.of(2026, 5, 31, 8, 30));

        when(workshopRepository.findByIdWithRoom(1L)).thenReturn(Optional.of(existing));
        when(workshopRepository.save(any(Workshop.class))).thenReturn(existing);

        workshopService.updateWorkshop(1L, request);

        assertEquals("New Title", existing.getTitle());
        assertEquals("New Description", existing.getDescription());
        assertEquals("New Speaker", existing.getSpeaker());
        // G21/Q5: price is Long — assert Long value, not BigDecimal
        assertEquals(50000L, existing.getPrice());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 0), existing.getStartTime());
        assertEquals(LocalDateTime.of(2026, 6, 1, 17, 0), existing.getEndTime());
        assertSame(defaultRoom, existing.getRoom());
    }

    // ──────────── Test Helpers ────────────

    private WorkshopRequest validRequest() {
        return new WorkshopRequest(
                "Workshop: Clean Code với Java",
                "Mô tả workshop về Clean Code.",
                1L,         // roomId
                "Speaker A", // speaker
                60,         // totalSlots
                0L,         // price
                LocalDateTime.of(2026, 5, 10, 8, 0),
                LocalDateTime.of(2026, 5, 10, 12, 0),
                LocalDateTime.of(2026, 5, 5, 8, 0),       // registrationStartTime
                LocalDateTime.of(2026, 5, 9, 7, 0));     // registrationEndTime
    }

    private Workshop baseWorkshop(Long id) {
        return Workshop.builder()
                .id(id)
                .title("Test Workshop")
                .description(null)
                .room(defaultRoom)
                .speaker("Speaker A")
                .status("DRAFT")
                .totalSlots(60)
                .remainingSlots(60)
                .price(0L)
                .startTime(LocalDateTime.of(2026, 5, 10, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 10, 12, 0))
                .registrationStartTime(LocalDateTime.of(2026, 5, 5, 8, 0))
                .registrationEndTime(LocalDateTime.of(2026, 5, 9, 7, 0))
                .build();
    }

    private Workshop workshopFromRequest(WorkshopRequest request, Long id) {
        return Workshop.builder()
                .id(id)
                .title(request.title())
                .description(request.description())
                .room(defaultRoom)
                .speaker(request.speaker())
                .status("DRAFT")
                .totalSlots(request.totalSlots())
                .remainingSlots(request.totalSlots())
                .price(request.price() != null ? request.price() : 0L)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .registrationStartTime(request.registrationStartTime())
                .registrationEndTime(request.registrationEndTime())
                .build();
    }
}
