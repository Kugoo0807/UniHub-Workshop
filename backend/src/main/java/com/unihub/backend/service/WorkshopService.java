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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RoomRepository roomRepository;
    private final RegistrationRepository registrationRepository;
    private final SeatLockingService seatLockingService;

    // ────────────── Admin ──────────────

    public List<WorkshopResponse> getAllWorkshops() {
        return workshopRepository.findAllWithRoom()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<WorkshopResponse> getPublishedWorkshops() {
        return workshopRepository.findAllPublishedWithRoom()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkshopResponse getWorkshopById(Long id) {
        Workshop workshop = findWorkshopWithRoomOrThrow(id);
        return toResponse(workshop);
    }

    public WorkshopResponse getPublishedWorkshopById(Long id) {
        Workshop workshop = findWorkshopWithRoomOrThrow(id);
        if (!"PUBLISHED".equals(workshop.getStatus())) {
            throw new ResourceNotFoundException("Workshop not found or is not currently available");
        }
        return toResponse(workshop);
    }

    @Transactional
    public WorkshopResponse createWorkshop(WorkshopRequest request) {
        // 1. Lookup Room
        Room room = findRoomOrThrow(request.roomId());

        // 2. Validate all business rules
        validateBusinessRules(request, room);

        // 3. Build entity — status defaults to DRAFT via @Builder.Default
        Workshop workshop = Workshop.builder()
                .title(request.title())
                .description(request.description())
                .room(room)
                .speaker(request.speaker())
                // status defaults to "DRAFT" via @Builder.Default
                .totalSlots(request.totalSlots())
                .remainingSlots(request.totalSlots())
                .price(request.price() != null ? request.price() : 0L)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .registrationStartTime(request.registrationStartTime())
                .registrationEndTime(request.registrationEndTime())
                .build();

        Workshop saved = workshopRepository.save(workshop);

        // 4. Initialize Redis slot counter with TTL
        long ttlSeconds = computeSlotTtl(saved);
        seatLockingService.initSlots(String.valueOf(saved.getId()), saved.getTotalSlots(), ttlSeconds);
        log.info("Initialized Redis slots for workshop {}: total={}, TTL={}s", saved.getId(), saved.getTotalSlots(), ttlSeconds);

        return toResponse(saved);
    }

    @Transactional
    public WorkshopResponse updateWorkshop(Long id, WorkshopRequest request) {
        Workshop workshop = findWorkshopWithRoomOrThrow(id);

        // 1. Lookup Room (may be the same or different)
        Room room = findRoomOrThrow(request.roomId());

        // 2. Validate all business rules
        validateBusinessRules(request, room);

        int oldTotalSlots = workshop.getTotalSlots();

        // 3. Check total_slots change restriction
        if (!workshop.getTotalSlots().equals(request.totalSlots())) {
            if (registrationRepository.existsByWorkshopId(id)) {
                throw new ConflictException(
                        "Cannot change total_slots because registrations already exist for this workshop");
            }
            workshop.setRemainingSlots(request.totalSlots());
        }

        // 4. Update all fields
        workshop.setTitle(request.title());
        workshop.setDescription(request.description());
        workshop.setRoom(room);
        workshop.setSpeaker(request.speaker());
        workshop.setTotalSlots(request.totalSlots());
        workshop.setPrice(request.price() != null ? request.price() : 0L);
        workshop.setStartTime(request.startTime());
        workshop.setEndTime(request.endTime());
        workshop.setRegistrationStartTime(request.registrationStartTime());
        workshop.setRegistrationEndTime(request.registrationEndTime());
        // Note: status is NOT updated via this endpoint — managed by lifecycle

        Workshop saved = workshopRepository.save(workshop);

        // 5. Sync slot changes to Redis
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

        // G10: Only allow deletion of DRAFT workshops
        if (!"DRAFT".equals(workshop.getStatus())) {
            throw new ConflictException("Only DRAFT workshops can be deleted");
        }

        workshopRepository.delete(workshop);

        // Clean up Redis slot keys
        seatLockingService.removeSlots(String.valueOf(id));
        log.info("Removed Redis slots for deleted workshop {}", id);
    }

    /**
     * Publish a workshop (DRAFT → PUBLISHED).
     * Only DRAFT workshops can be published.
     */
    @Transactional
    public void publishWorkshop(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        if (!"DRAFT".equals(workshop.getStatus())) {
            throw new IllegalArgumentException(
                    "Only DRAFT workshops can be published. Current status: " + workshop.getStatus());
        }

        workshop.setStatus("PUBLISHED");
        workshopRepository.save(workshop);
        log.info("Workshop {} has been published.", id);
    }

    /**
     * Cancel a workshop (G8/G9).
     * Sets status to CANCELLED and removes Redis slots to block in-flight registrations.
     */
    @Transactional
    public void cancelWorkshop(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        String currentStatus = workshop.getStatus();
        if ("COMPLETED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new IllegalArgumentException(
                    "Cannot cancel a workshop that is already " + currentStatus);
        }

        // Prevent cancelling if there are successful registrations
        if (registrationRepository.existsByWorkshopIdAndStatus(id, "SUCCESS")) {
            throw new ConflictException("Cannot cancel workshop with successful registrations");
        }

        workshop.setStatus("CANCELLED");
        workshopRepository.save(workshop);

        // Remove Redis key to block any in-flight registrations
        seatLockingService.removeSlots(String.valueOf(id));
        log.info("Workshop {} has been cancelled. Redis slots removed.", id);
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

    /**
     * Find workshop with Room eagerly loaded (JOIN FETCH) to avoid N+1 queries.
     */
    private Workshop findWorkshopWithRoomOrThrow(Long id) {
        return workshopRepository.findByIdWithRoom(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found with id: " + id));
    }

    private Room findRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
    }

    /**
     * Validate all business rules for create/update (G4/G5/G6/G24).
     */
    private void validateBusinessRules(WorkshopRequest request, Room room) {
        // 1. end_time > start_time
        validateTimeRange(request.startTime(), request.endTime());

        // 2. start_time and end_time must be on the same day (G24 — mandatory per design.md)
        if (request.startTime() != null && request.endTime() != null
                && !request.startTime().toLocalDate().equals(request.endTime().toLocalDate())) {
            throw new IllegalArgumentException("start_time and end_time must be on the same day");
        }

        // 3. registration_start_time < registration_end_time
        if (request.registrationEndTime() != null && request.registrationStartTime() != null
                && !request.registrationEndTime().isAfter(request.registrationStartTime())) {
            throw new IllegalArgumentException(
                    "registration_end_time must be after registration_start_time");
        }

        // 4. registration_start_time < start_time
        if (request.registrationStartTime() != null && request.startTime() != null
                && !request.startTime().isAfter(request.registrationStartTime())) {
            throw new IllegalArgumentException(
                    "registration_start_time must be before start_time");
        }

        // 5. total_slots <= room.capacity (G6)
        if (request.totalSlots() > room.getCapacity()) {
            throw new ConflictException(
                    "total_slots (" + request.totalSlots() + ") exceeds room capacity ("
                            + room.getCapacity() + ")");
        }
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
     * Convert Workshop entity to WorkshopResponse DTO (G11/G23).
     * Includes room info to avoid N+1 — Room is already fetched via JOIN FETCH.
     */
    private WorkshopResponse toResponse(Workshop w) {
        int remainingSlots = getAccurateRemainingSlots(w);

        return WorkshopResponse.builder()
                .id(w.getId())
                .title(w.getTitle())
                .description(w.getDescription())
                .roomId(w.getRoom().getId())
                .roomName(w.getRoom().getName())
                .roomCapacity(w.getRoom().getCapacity())
                .speaker(w.getSpeaker())
                .status(w.getStatus())
                .totalSlots(w.getTotalSlots())
                .remainingSlots(remainingSlots)
                .price(w.getPrice())
                .startTime(w.getStartTime())
                .endTime(w.getEndTime())
                .registrationStartTime(w.getRegistrationStartTime())
                .registrationEndTime(w.getRegistrationEndTime())
                .build();
    }

    /**
     * Get the most accurate remaining slots for a workshop, preferring Redis value if available.
     * G19: getRemainingSlots() now returns -1 when key doesn't exist → proper fallback to DB.
     */
    private int getAccurateRemainingSlots(Workshop workshop) {
        int redisSlots = seatLockingService.getRemainingSlots(String.valueOf(workshop.getId()));
        if (redisSlots < 0) {
            // Redis key doesn't exist — fallback to DB
            return workshop.getRemainingSlots();
        }
        return redisSlots;
    }
}
