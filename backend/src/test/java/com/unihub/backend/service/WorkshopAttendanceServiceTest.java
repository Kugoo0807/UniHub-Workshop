package com.unihub.backend.service;

import com.unihub.backend.dto.PageResponse;
import com.unihub.backend.dto.WorkshopAttendanceResponse;
import com.unihub.backend.entity.CheckinRecord;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Room;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.PaymentRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.RoomRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkshopService#getWorkshopAttendances(Long, int, int)}.
 *
 * Tất cả kịch bản chỉ test registrations có status = 'SUCCESS'.
 * Kết quả trả về kiểu {@link PageResponse}.
 *
 * Test ID convention: WA-UT-<số>
 *
 * Coverage:
 *   WA-UT-01  Workshop không tồn tại → ResourceNotFoundException
 *   WA-UT-02  Workshop tồn tại, không có đăng ký SUCCESS → page rỗng
 *   WA-UT-03  Đăng ký SUCCESS chưa check-in → checkedIn = false, checkedInAt = null
 *   WA-UT-04  Đăng ký SUCCESS đã check-in → checkedIn = true, checkedInAt đúng timestamp
 *   WA-UT-05  Danh sách hỗn hợp (có / không check-in) → trả đủ tất cả records trong page
 *   WA-UT-06  Mapping DTO đầy đủ tất cả fields
 *   WA-UT-07  Delegation đúng: gọi repository với đúng workshopId và Pageable
 *   WA-UT-08  Phân trang: PageResponse.page, size, totalElements, totalPages, last đúng
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkshopAttendanceServiceTest {

    @Mock private WorkshopRepository    workshopRepository;
    @Mock private RegistrationRepository registrationRepository;
    @Mock private PaymentRepository     paymentRepository;
    @Mock private RoomRepository        roomRepository;
    @Mock private SeatLockingService    seatLockingService;

    @InjectMocks
    private WorkshopService workshopService;

    private Workshop defaultWorkshop;

    @BeforeEach
    void setUp() {
        Room room = Room.builder().id(1L).name("Hall A").capacity(100).build();

        defaultWorkshop = Workshop.builder()
                .id(1L).title("Test Workshop").room(room).speaker("Speaker A")
                .status("PUBLISHED").totalSlots(60).remainingSlots(55).price(0L)
                .startTime(LocalDateTime.of(2026, 5, 20, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 20, 12, 0))
                .registrationStartTime(LocalDateTime.of(2026, 5, 14, 8, 0))
                .registrationEndTime(LocalDateTime.of(2026, 5, 19, 8, 0))
                .build();

        lenient().when(seatLockingService.getRemainingSlots(anyString())).thenReturn(-1);
    }

    // ──────────── WA-UT-01 ────────────

    /**
     * WA-UT-01: Workshop không tồn tại → throw ResourceNotFoundException ngay,
     * repository không được gọi.
     */
    @Test
    void getWorkshopAttendances_workshopNotFound_throwsResourceNotFoundException() {
        when(workshopRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workshopService.getWorkshopAttendances(999L, 0, 5));

        verify(registrationRepository, never()).findAttendancesByWorkshopId(anyLong(), any());
    }

    // ──────────── WA-UT-02 ────────────

    /**
     * WA-UT-02: Workshop tồn tại nhưng không có đăng ký SUCCESS nào
     * → trả PageResponse với content rỗng và totalElements = 0.
     */
    @Test
    void getWorkshopAttendances_noSuccessRegistrations_returnsEmptyPage() {
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<WorkshopAttendanceResponse> result =
                workshopService.getWorkshopAttendances(1L, 0, 5);

        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    // ──────────── WA-UT-03 ────────────

    /**
     * WA-UT-03: Sinh viên đăng ký SUCCESS nhưng chưa quét QR
     * → checkedIn = false, checkedInAt = null.
     */
    @Test
    void getWorkshopAttendances_successNotCheckedIn_checkedInFalse() {
        User student = buildUser(7L, "SV007", "Tran Van B", "tvb@example.com", "0901111111");
        Registration reg = buildRegistration(42L, student, null);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reg)));

        PageResponse<WorkshopAttendanceResponse> result =
                workshopService.getWorkshopAttendances(1L, 0, 5);

        assertEquals(1, result.getContent().size());
        WorkshopAttendanceResponse dto = result.getContent().get(0);
        assertFalse(dto.getCheckedIn());
        assertNull(dto.getCheckedInAt());
    }

    // ──────────── WA-UT-04 ────────────

    /**
     * WA-UT-04: Sinh viên đăng ký SUCCESS và đã quét QR
     * → checkedIn = true, checkedInAt = timestamp quét.
     */
    @Test
    void getWorkshopAttendances_successCheckedIn_checkedInTrue() {
        LocalDateTime scanTime = LocalDateTime.of(2026, 5, 20, 9, 15);
        User student = buildUser(8L, "SV008", "Le Thi C", "ltc@example.com", "0902222222");
        Registration reg = buildRegistration(43L, student, scanTime);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reg)));

        PageResponse<WorkshopAttendanceResponse> result =
                workshopService.getWorkshopAttendances(1L, 0, 5);

        WorkshopAttendanceResponse dto = result.getContent().get(0);
        assertTrue(dto.getCheckedIn());
        assertEquals(scanTime, dto.getCheckedInAt());
    }

    // ──────────── WA-UT-05 ────────────

    /**
     * WA-UT-05: Danh sách hỗn hợp — một số đã check-in, một số chưa
     * → trả đủ tất cả records trong page.
     */
    @Test
    void getWorkshopAttendances_mixedCheckinStatus_returnsAllRecords() {
        LocalDateTime scanTime = LocalDateTime.of(2026, 5, 20, 9, 30);
        Registration r1 = buildRegistration(10L, buildUser(1L, "SV001", "A", "a@e.com", "01"), scanTime);
        Registration r2 = buildRegistration(11L, buildUser(2L, "SV002", "B", "b@e.com", "02"), null);
        Registration r3 = buildRegistration(12L, buildUser(3L, "SV003", "C", "c@e.com", "03"), null);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1, r2, r3)));

        PageResponse<WorkshopAttendanceResponse> result =
                workshopService.getWorkshopAttendances(1L, 0, 5);

        assertEquals(3, result.getContent().size());
        assertTrue(result.getContent().get(0).getCheckedIn());
        assertFalse(result.getContent().get(1).getCheckedIn());
        assertFalse(result.getContent().get(2).getCheckedIn());
    }

    // ──────────── WA-UT-06 ────────────

    /**
     * WA-UT-06: Tất cả fields trong DTO được map đúng từ entity.
     */
    @Test
    void getWorkshopAttendances_dtoFieldsMappedCorrectly() {
        LocalDateTime registeredAt = LocalDateTime.of(2026, 5, 14, 10, 0);
        LocalDateTime scanTime     = LocalDateTime.of(2026, 5, 20, 8, 45);

        User student = buildUser(5L, "SV005", "Dao Thi F", "dtf@example.com", "0909999999");
        Registration reg = buildRegistrationWithDate(50L, student, scanTime, registeredAt);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reg)));

        WorkshopAttendanceResponse dto =
                workshopService.getWorkshopAttendances(1L, 0, 5).getContent().get(0);

        assertAll(
            () -> assertEquals(50L,            dto.getRegistrationId()),
            () -> assertEquals(5L,             dto.getUserId()),
            () -> assertEquals("SV005",        dto.getStudentCode()),
            () -> assertEquals("Dao Thi F",    dto.getFullName()),
            () -> assertEquals("dtf@example.com", dto.getEmail()),
            () -> assertEquals("0909999999",   dto.getPhoneNumber()),
            () -> assertEquals("SUCCESS",      dto.getRegistrationStatus()),
            () -> assertEquals(registeredAt,   dto.getRegisteredAt()),
            () -> assertTrue(dto.getCheckedIn()),
            () -> assertEquals(scanTime,       dto.getCheckedInAt())
        );
    }

    // ──────────── WA-UT-07 ────────────

    /**
     * WA-UT-07: Service phải gọi repository đúng 1 lần với đúng workshopId.
     */
    @Test
    void getWorkshopAttendances_delegatesToRepositoryWithCorrectId() {
        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        workshopService.getWorkshopAttendances(1L, 0, 5);

        verify(registrationRepository, times(1))
                .findAttendancesByWorkshopId(eq(1L), any(Pageable.class));
    }

    // ──────────── WA-UT-08 ────────────

    /**
     * WA-UT-08: PageResponse phải có đúng các meta-field:
     * page, size, totalElements, totalPages, last.
     */
    @Test
    void getWorkshopAttendances_pageResponseMetaIsCorrect() {
        // Simulate: 12 total elements, page 1, size 5 → page 2 of 3, not last
        List<Registration> pageContent = List.of(
                buildRegistration(20L, buildUser(20L, "SV020", "X", "x@e.com", "0"), null),
                buildRegistration(21L, buildUser(21L, "SV021", "Y", "y@e.com", "1"), null),
                buildRegistration(22L, buildUser(22L, "SV022", "Z", "z@e.com", "2"), null),
                buildRegistration(23L, buildUser(23L, "SV023", "W", "w@e.com", "3"), null),
                buildRegistration(24L, buildUser(24L, "SV024", "V", "v@e.com", "4"), null)
        );

        org.springframework.data.domain.Page<Registration> springPage =
                new PageImpl<>(pageContent, PageRequest.of(1, 5), 12);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(defaultWorkshop));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(registrationRepository.findAttendancesByWorkshopId(eq(1L), pageableCaptor.capture()))
                .thenReturn(springPage);

        PageResponse<WorkshopAttendanceResponse> result =
                workshopService.getWorkshopAttendances(1L, 1, 5);

        // Verify Pageable passed correctly
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(5, pageableCaptor.getValue().getPageSize());

        // Verify PageResponse meta
        assertEquals(1,  result.getPage());
        assertEquals(5,  result.getSize());
        assertEquals(12, result.getTotalElements());
        assertEquals(3,  result.getTotalPages());
        assertFalse(result.isLast());
        assertEquals(5,  result.getContent().size());
    }

    // ──────────── Helpers ────────────

    private User buildUser(Long id, String code, String name, String email, String phone) {
        return User.builder()
                .id(id).studentCode(code).fullName(name)
                .email(email).phoneNumber(phone)
                .role("STUDENT").status("ACTIVE")
                .build();
    }

    private Registration buildRegistration(Long regId, User user, LocalDateTime scanTime) {
        return buildRegistrationWithDate(regId, user, scanTime,
                LocalDateTime.of(2026, 5, 14, 9, 0));
    }

    private Registration buildRegistrationWithDate(Long regId, User user,
                                                   LocalDateTime scanTime,
                                                   LocalDateTime createdAt) {
        CheckinRecord checkin = null;
        if (scanTime != null) {
            checkin = CheckinRecord.builder()
                    .registrationId(regId)
                    .scannedAt(scanTime)
                    .syncedAt(scanTime)
                    .build();
        }

        Registration reg = Registration.builder()
                .id(regId).user(user).workshop(defaultWorkshop)
                .qrCode("QR-" + regId).status("SUCCESS")
                .checkinRecord(checkin)
                .build();

        try {
            var field = Registration.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(reg, createdAt);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set createdAt via reflection", e);
        }

        return reg;
    }
}
