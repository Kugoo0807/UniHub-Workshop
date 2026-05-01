package com.unihub.backend.service;

import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.DuplicateRegistrationException;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RegistrationService registrationService;

    private Workshop workshop;
    private User user;
    private RegistrationRequest request;

    @BeforeEach
    void setup() {
        workshop = Workshop.builder()
                .id(1L)
                .title("Redis Workshop")
                .description("Seat contention")
                .totalSlots(10)
                .remainingSlots(10)
                .price(BigDecimal.ZERO)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();

        user = User.builder().id(100L).build();

        request = new RegistrationRequest();
        request.setWorkshopId(1L);

        when(workshopRepository.findById(1L)).thenReturn(Optional.of(workshop));
    }

    @Test
    void registerFree_success_returnsQrAndSuccess() {
        when(registrationRepository.findByUserAndWorkshop(user, workshop)).thenReturn(Optional.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> {
            Registration reg = invocation.getArgument(0);
            reg.setId(11L);
            return reg;
        });

        RegistrationResponse out = registrationService.registerFree(request, user, "script");

        assertEquals("SUCCESS", out.getStatus());
        assertNotNull(out.getQrCode());
        assertEquals(11L, out.getRegistrationId());
    }

    @Test
    void registerFree_duplicate_shouldThrowConflict() {
        when(registrationRepository.findByUserAndWorkshop(user, workshop))
                .thenReturn(Optional.of(Registration.builder().id(1L).build()));

        assertThrows(DuplicateRegistrationException.class,
                () -> registrationService.registerFree(request, user, "script"));

        verify(redisTemplate, never()).execute(any(RedisCallback.class));
    }

    @Test
    void registerFree_noSeat_shouldThrowConflict() {
        when(registrationRepository.findByUserAndWorkshop(user, workshop)).thenReturn(Optional.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(-1L);

        assertThrows(InsufficientSeatsException.class,
                () -> registrationService.registerFree(request, user, "script"));

        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    void initiatePaid_success_returnsPendingWithoutQr() {
        workshop.setPrice(new BigDecimal("100000"));

        when(registrationRepository.findByUserAndWorkshop(user, workshop)).thenReturn(Optional.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> {
            Registration reg = invocation.getArgument(0);
            reg.setId(22L);
            return reg;
        });
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);

        RegistrationResponse out = registrationService.initiatePaid(request, user, "script");

        assertEquals("PENDING", out.getStatus());
        assertNull(out.getQrCode());
        assertEquals(22L, out.getRegistrationId());
    }

    @Test
    void initiatePaid_noSeat_shouldDeletePendingRegistration() {
        when(registrationRepository.findByUserAndWorkshop(user, workshop)).thenReturn(Optional.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        Registration pending = Registration.builder().id(30L).build();
        when(registrationRepository.save(any(Registration.class))).thenReturn(pending);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(-1L);

        assertThrows(InsufficientSeatsException.class,
                () -> registrationService.initiatePaid(request, user, "script"));

        verify(registrationRepository).delete(pending);
    }

    @Test
    void registerFree_dbSaveFails_shouldRollbackRedisSeat() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(registrationRepository.findByUserAndWorkshop(user, workshop)).thenReturn(Optional.empty());
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(1L);
        when(registrationRepository.save(any(Registration.class))).thenThrow(new RuntimeException("db error"));

        assertThrows(RuntimeException.class,
                () -> registrationService.registerFree(request, user, "script"));

        verify(valueOps).increment("workshop:1:slots");
    }
}
