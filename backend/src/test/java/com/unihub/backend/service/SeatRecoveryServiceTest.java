package com.unihub.backend.service;

import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatRecoveryServiceTest {

    @Mock
    RegistrationRepository registrationRepository;

    @Mock
    WorkshopRepository workshopRepository;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @InjectMocks
    SeatRecoveryService seatRecoveryService;

    @BeforeEach
    void setup() {
        // leniency to avoid UnnecessaryStubbingException if a test doesn't use it
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void releaseExpiredReservations_shouldReleaseSeats() {
        // GIVEN
        Workshop workshop = Workshop.builder().id(1L).build();
        Registration reg1 = Registration.builder().id(10L).workshop(workshop).status("PENDING").build();
        Registration reg2 = Registration.builder().id(11L).workshop(workshop).status("PENDING").build();

        when(registrationRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(reg1, reg2));

        // WHEN
        seatRecoveryService.releaseExpiredReservations();

        // THEN
        verify(registrationRepository, times(2)).save(any(Registration.class));
        verify(valueOps, times(2)).increment("workshop:1:slots");
    }
}
