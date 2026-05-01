package com.unihub.backend.controller;

import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.entity.User;
import com.unihub.backend.exception.DuplicateRegistrationException;
import com.unihub.backend.exception.GlobalExceptionHandler;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.UserRepository;
import com.unihub.backend.service.RegistrationService;
import com.unihub.backend.service.WorkshopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StudentWorkshopControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WorkshopService workshopService;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        StudentWorkshopController controller = new StudentWorkshopController(
                workshopService,
                registrationService,
                userRepository,
                "seatDecrScript",
                "seatReserveScript"
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ────────────── GET /api/v1/students/workshops ──────────────

    @Test
    void listWorkshops_returnsAllWorkshops() throws Exception {
        WorkshopResponse w1 = WorkshopResponse.builder()
                .id(1L).title("Workshop A").totalSlots(60).remainingSlots(40)
                .price(BigDecimal.ZERO)
                .startTime(LocalDateTime.of(2026, 5, 10, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 10, 12, 0))
                .build();
        WorkshopResponse w2 = WorkshopResponse.builder()
                .id(2L).title("Workshop B").totalSlots(30).remainingSlots(30)
                .price(new BigDecimal("50000"))
                .startTime(LocalDateTime.of(2026, 6, 1, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 17, 0))
                .build();

        when(workshopService.getAllWorkshops()).thenReturn(List.of(w1, w2));

        mockMvc.perform(get("/api/v1/students/workshops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("Workshop A")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].title", is("Workshop B")));
    }

    @Test
    void listWorkshops_emptyList_returns200WithEmptyArray() throws Exception {
        when(workshopService.getAllWorkshops()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/students/workshops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ────────────── GET /api/v1/students/workshops/{id} ──────────────

    @Test
    void getWorkshop_existingId_returns200() throws Exception {
        WorkshopResponse w = WorkshopResponse.builder()
                .id(1L).title("Workshop A").description("Desc")
                .totalSlots(60).remainingSlots(42)
                .price(BigDecimal.ZERO)
                .startTime(LocalDateTime.of(2026, 5, 10, 8, 0))
                .endTime(LocalDateTime.of(2026, 5, 10, 12, 0))
                .build();

        when(workshopService.getWorkshopById(1L)).thenReturn(w);

        mockMvc.perform(get("/api/v1/students/workshops/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Workshop A")))
                .andExpect(jsonPath("$.remainingSlots", is(42)));
    }

    @Test
    void getWorkshop_nonExistentId_returns404() throws Exception {
        when(workshopService.getWorkshopById(999L))
                .thenThrow(new ResourceNotFoundException("Workshop not found with id: 999"));

        mockMvc.perform(get("/api/v1/students/workshops/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Workshop not found")));
    }

    // ────────────── POST /api/v1/students/workshops/{id}/register ──────────────

    @Test
    void registerWorkshop_success_returns201() throws Exception {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(registrationService.registerFree(any(), any(), anyString()))
                .thenReturn(new RegistrationResponse(100L, "qr-uuid-123", "SUCCESS"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                10L, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(post("/api/v1/students/workshops/1/register")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationId", is(100)))
                .andExpect(jsonPath("$.qrCode", is("qr-uuid-123")))
                .andExpect(jsonPath("$.status", is("SUCCESS")));
    }

    @Test
    void registerWorkshop_noSeats_returns409() throws Exception {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(registrationService.registerFree(any(), any(), anyString()))
                .thenThrow(new InsufficientSeatsException("Workshop này đã hết chỗ"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                10L, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(post("/api/v1/students/workshops/1/register")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Workshop này đã hết chỗ")));
    }

    @Test
    void registerWorkshop_alreadyRegistered_returns409() throws Exception {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(registrationService.registerFree(any(), any(), anyString()))
                .thenThrow(new DuplicateRegistrationException("Bạn đã đăng ký workshop này rồi"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                10L, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(post("/api/v1/students/workshops/1/register")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Bạn đã đăng ký workshop này rồi")));
    }

    // ────────────── POST /api/v1/students/workshops/{id}/register-paid ──────────────

    @Test
    void registerPaidWorkshop_success_returns201() throws Exception {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(registrationService.initiatePaid(any(), any(), anyString()))
                .thenReturn(new RegistrationResponse(101L, null, "PENDING"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                10L, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(post("/api/v1/students/workshops/1/register-paid")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationId", is(101)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void registerPaidWorkshop_noSeats_returns409() throws Exception {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(registrationService.initiatePaid(any(), any(), anyString()))
                .thenThrow(new InsufficientSeatsException("Workshop này đã hết chỗ"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                10L, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(post("/api/v1/students/workshops/1/register-paid")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Workshop này đã hết chỗ")));
    }
}
