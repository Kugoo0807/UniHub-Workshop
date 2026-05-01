package com.unihub.backend.service;

import com.unihub.backend.dto.PaymentRequest;
import com.unihub.backend.dto.RegistrationRequest;
import com.unihub.backend.dto.RegistrationResponse;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.User;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.DuplicateRegistrationException;
import com.unihub.backend.exception.InsufficientSeatsException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final StringRedisTemplate redisTemplate;
    private final PaymentService paymentService;

    private static final String SLOT_KEY_FMT = "workshop:%d:slots";

    private int calculateRemainingSeats(Workshop workshop) {
        long sold = registrationRepository.countByWorkshopIdAndStatus(workshop.getId(), "SUCCESS");
        return Math.max(0, workshop.getTotalSlots() - (int) sold);
    }

    private void ensureRedisSeatCounter(Workshop workshop, String slotKey) {
        Boolean exists = redisTemplate.hasKey(slotKey);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        int remainingSeats = calculateRemainingSeats(workshop);
        redisTemplate.opsForValue().set(slotKey, String.valueOf(remainingSeats));
    }

    @Transactional
    public RegistrationResponse registerFree(RegistrationRequest req, User user, String seatDecrScript) {
        Workshop workshop = workshopRepository.findById(req.getWorkshopId())
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));

        // Check existing registration to prevent double registration
        registrationRepository.findByUserAndWorkshop(user, workshop).ifPresent(r -> {
            throw new DuplicateRegistrationException("Bạn đã đăng ký workshop này rồi");
        });

        String slotKey = String.format(SLOT_KEY_FMT, workshop.getId());
        ensureRedisSeatCounter(workshop, slotKey);

        // Call Lua script to atomically decrement
        Long result = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) (connection) -> {
            Object res = connection.scriptingCommands().eval(
                    seatDecrScript.getBytes(),
                    org.springframework.data.redis.connection.ReturnType.INTEGER,
                    1,
                    slotKey.getBytes()
            );
            return (Long) res;
        });

        if (result == null || result.longValue() < 0) {
            throw new InsufficientSeatsException("Workshop này đã hết chỗ");
        }

        Registration registration = Registration.builder()
                .user(user)
                .workshop(workshop)
                .qrCode(UUID.randomUUID().toString())
                .status("SUCCESS")
                .build();

        try {
            registration = registrationRepository.save(registration);
        } catch (RuntimeException ex) {
            // rollback Redis decrement if DB save fails
            redisTemplate.opsForValue().increment(slotKey);
            throw ex;
        }

        return new RegistrationResponse(registration.getId(), registration.getQrCode(), registration.getStatus());
    }

    @Transactional
    public RegistrationResponse initiatePaid(RegistrationRequest req, User user, String seatReserveScript) {
        Workshop workshop = workshopRepository.findById(req.getWorkshopId())
            .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));

        // Check existing registration
        registrationRepository.findByUserAndWorkshop(user, workshop).ifPresent(r -> {
            throw new DuplicateRegistrationException("Bạn đã đăng ký workshop này rồi");
        });

        String slotKey = String.format(SLOT_KEY_FMT, workshop.getId());
        ensureRedisSeatCounter(workshop, slotKey);

        Registration registration = Registration.builder()
            .user(user)
            .workshop(workshop)
            .qrCode(UUID.randomUUID().toString())
            .status("PENDING")
            .build();

        registration = registrationRepository.save(registration);

        // Attempt to reserve a seat in Redis atomically and set a reservation key with TTL
        String reservationKey = String.format("workshop:%d:reservation:%d", workshop.getId(), registration.getId());
        String token = String.valueOf(registration.getId());
        // reservation TTL: 15 minutes
        long ttlMs = 15 * 60 * 1000;

        Long res = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) (connection) -> {
            Object r = connection.scriptingCommands().eval(
                seatReserveScript.getBytes(),
                org.springframework.data.redis.connection.ReturnType.INTEGER,
                2,
                slotKey.getBytes(),
                reservationKey.getBytes(),
                token.getBytes(),
                String.valueOf(ttlMs).getBytes()
            );
            return (Long) r;
        });

        if (res == null || res.longValue() < 0) {
            // rollback created registration
            registrationRepository.delete(registration);
            throw new InsufficientSeatsException("Workshop này đã hết chỗ");
        }

        // reserved successfully; return pending registration info
        return new RegistrationResponse(registration.getId(), null, registration.getStatus());
    }
}
