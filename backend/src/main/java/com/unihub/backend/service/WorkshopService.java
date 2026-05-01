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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String SLOT_KEY_FMT = "workshop:%d:slots";

    // ────────────── Admin ──────────────

    public List<WorkshopResponse> getAllWorkshops() {
        return workshopRepository.findAll()
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
                .remainingSlots(request.totalSlots()) // remaining = total on creation
                .price(request.price() != null ? request.price() : BigDecimal.ZERO)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        Workshop saved = workshopRepository.save(workshop);

        String key = String.format(SLOT_KEY_FMT, saved.getId());
        redisTemplate.opsForValue().set(key, String.valueOf(saved.getTotalSlots()));

        return toResponse(saved);
    }

    @Transactional
    public WorkshopResponse updateWorkshop(Long id, WorkshopRequest request) {
        Workshop workshop = findWorkshopOrThrow(id);

        validateTimeRange(request.startTime(), request.endTime());

        // Business rule: cannot change total_slots if registrations exist
        if (!workshop.getTotalSlots().equals(request.totalSlots())) {
            if (registrationRepository.existsByWorkshopId(id)) {
                throw new ConflictException(
                        "Cannot change total_slots because registrations already exist for this workshop");
            }
            // Update remaining_slots to match new total_slots since there are no registrations
            workshop.setRemainingSlots(request.totalSlots());
        }

        workshop.setTitle(request.title());
        workshop.setDescription(request.description());
        workshop.setTotalSlots(request.totalSlots());
        workshop.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);
        workshop.setStartTime(request.startTime());
        workshop.setEndTime(request.endTime());

        Workshop saved = workshopRepository.save(workshop);
        return toResponse(saved);
    }

    @Transactional
    public void deleteWorkshop(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        // Business rule: cannot delete if there are SUCCESS registrations
        if (registrationRepository.existsByWorkshopIdAndStatus(id, "SUCCESS")) {
            throw new ConflictException(
                    "Cannot delete workshop because it has successful registrations");
        }

        workshopRepository.delete(workshop);

        String key = String.format(SLOT_KEY_FMT, id);
        redisTemplate.delete(key);
    }

    public WorkshopStatsResponse getWorkshopStats(Long id) {
        Workshop workshop = findWorkshopOrThrow(id);

        String key = String.format(SLOT_KEY_FMT, id);
        String redisVal = redisTemplate.opsForValue().get(key);
        int remainingSlots = redisVal != null ? Integer.parseInt(redisVal) : workshop.getRemainingSlots();

        long registeredCount = registrationRepository.countByWorkshopIdAndStatus(id, "SUCCESS");

        double fillRate = workshop.getTotalSlots() > 0
                ? (double) (workshop.getTotalSlots() - remainingSlots) / workshop.getTotalSlots() * 100
                : 0.0;

        // Round to 1 decimal
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

    private void validateTimeRange(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("end_time must be after start_time");
        }
    }

    private WorkshopResponse toResponse(Workshop w) {
        String key = String.format(SLOT_KEY_FMT, w.getId());
        String redisVal = redisTemplate.opsForValue().get(key);
        int remaining = redisVal != null ? Integer.parseInt(redisVal) : w.getRemainingSlots();

        return WorkshopResponse.builder()
                .id(w.getId())
                .title(w.getTitle())
                .description(w.getDescription())
                .totalSlots(w.getTotalSlots())
                .remainingSlots(remaining)
                .price(w.getPrice())
                .startTime(w.getStartTime())
                .endTime(w.getEndTime())
                .build();
    }
}
