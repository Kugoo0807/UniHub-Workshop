package com.unihub.backend.service;

import com.unihub.backend.dto.WorkshopRequest;
import com.unihub.backend.dto.WorkshopResponse;
import com.unihub.backend.dto.WorkshopStatsResponse;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.ConflictException;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final SeatLockingService seatLockingService;

    // ────────────── Admin ──────────────

    public List<WorkshopResponse> getAllWorkshops() {
        return workshopRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkshopResponse getWorkshopById(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);
        return toResponse(workshop);
    }

    @Transactional
    public WorkshopResponse createWorkshop(WorkshopRequest request) {
        validateTimeRange(request.startTime(), request.endTime());

        Workshop workshop = Workshop.builder()
                .title(request.title())
                .description(request.description())
                .totalSlots(request.totalSlots())
                .remainingSlots(request.totalSlots())
                .price(request.price() != null ? request.price() : 0L)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        Workshop saved = workshopRepository.save(workshop);

        // Initialize Redis slot counter with TTL
        long ttlSeconds = computeSlotTtl(saved);
        seatLockingService.initSlots(String.valueOf(saved.getId()), saved.getTotalSlots(), ttlSeconds);
        log.info("Initialized Redis slots for workshop {}: total={}, TTL={}s", saved.getId(), saved.getTotalSlots(), ttlSeconds);

        return toResponse(saved);
    }

    @Transactional
    public WorkshopResponse updateWorkshop(Long id, WorkshopRequest request) {
        Workshop workshop = findWorkshopOrThrow(id);

        validateTimeRange(request.startTime(), request.endTime());

        int oldTotalSlots = workshop.getTotalSlots();

        if (!workshop.getTotalSlots().equals(request.totalSlots())) {
            if (registrationRepository.existsByWorkshopId(id)) {
                throw new ConflictException(
                        "Cannot change total_slots because registrations already exist for this workshop");
            }
            workshop.setRemainingSlots(request.totalSlots());
        }

        workshop.setTitle(request.title());
        workshop.setDescription(request.description());
        workshop.setTotalSlots(request.totalSlots());
        workshop.setPrice(request.price() != null ? request.price() : 0L);
        workshop.setStartTime(request.startTime());
        workshop.setEndTime(request.endTime());

        Workshop saved = workshopRepository.save(workshop);

        // Sync slot changes to Redis
        if (!Integer.valueOf(oldTotalSlots).equals(request.totalSlots())) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    seatLockingService.updateSlots(String.valueOf(id), request.totalSlots(), oldTotalSlots);
                    log.info("Successfully updated Redis slots for workshop {} after DB commit: {} -> {}",
                            id, oldTotalSlots, request.totalSlots());
                }
            });
        }

        return toResponse(saved);
    }

    @Transactional
    public void deleteWorkshop(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        if (registrationRepository.existsByWorkshopIdAndStatus(id, "SUCCESS")) {
            throw new ConflictException(
                    "Cannot delete workshop because it has successful registrations");
        }

        workshopRepository.delete(workshop);

        // Clean up Redis slot keys
        seatLockingService.removeSlots(String.valueOf(id));
        log.info("Removed Redis slots for deleted workshop {}", id);
    }

    public WorkshopStatsResponse getWorkshopStats(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        // Read remaining_slots from Redis (authoritative source)
        int remainingSlots = getAccurateRemainingSlots(workshop);

        long registeredCount = registrationRepository.countByWorkshopIdAndStatus(id, "SUCCESS");

        double fillRate = workshop.getTotalSlots() > 0
                ? (double) (workshop.getTotalSlots() - remainingSlots) / workshop.getTotalSlots() * 100
                : 0.0;

        fillRate = BigDecimal.valueOf(fillRate)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        return WorkshopStatsResponse.builder()
                .workshopId(workshop.getId())
                .title(workshop.getTitle())
                .totalSlots(workshop.getTotalSlots())
                .remainingSlots(remainingSlots)
                .registeredCount(registeredCount)
                .fillRate(fillRate)
                .build();
    }

    // ────────────── Private helpers ──────────────

    private Workshop findWorkshopOrThrow(Long id) {
        return workshopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found with id: " + id));
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("end_time must be after start_time");
        }
    }

    /**
     * Compute slot TTL in seconds: (registration_end_time - now) + 24h buffer.
     */
    private long computeSlotTtl(Workshop w) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = w.getRegistrationEndTime() != null
                ? w.getRegistrationEndTime()
                : w.getEndTime();
        long seconds = Duration.between(now, end).getSeconds();
        // Add 24h buffer
        seconds += Duration.ofHours(24).getSeconds();
        return Math.max(seconds, 3600); // minimum 1 hour
    }

    /**
     * Convert Workshop entity to WorkshopResponse DTO.
     * For remaining_slots, prefer Redis value if available, otherwise fall back to DB.
     */
    private WorkshopResponse toResponse(Workshop w) {
        int remainingSlots = getAccurateRemainingSlots(w);

        return WorkshopResponse.builder()
                .id(w.getId())
                .title(w.getTitle())
                .description(w.getDescription())
                .totalSlots(w.getTotalSlots())
                .remainingSlots(remainingSlots)
                .price(w.getPrice())
                .startTime(w.getStartTime())
                .endTime(w.getEndTime())
                .build();
    }

    /**
     * Get the most accurate remaining slots for a workshop, preferring Redis value if available.
     */
    private int getAccurateRemainingSlots(Workshop workshop) {
        int redisSlots = seatLockingService.getRemainingSlots(String.valueOf(workshop.getId()));
        return redisSlots >= 0 ? redisSlots : workshop.getRemainingSlots();
    }
}
