package com.unihub.backend.service;

import com.unihub.backend.dto.AttendeeResponse;
import com.unihub.backend.dto.CheckinSyncRequest;
import com.unihub.backend.dto.CheckinSyncResponse;
import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;
import com.unihub.backend.exception.ResourceNotFoundException;
import com.unihub.backend.repository.CheckinRecordRepository;
import com.unihub.backend.repository.RegistrationRepository;
import com.unihub.backend.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckinRecordRepository checkinRecordRepository;

    @Transactional(readOnly = true)
    public List<AttendeeResponse> getWorkshopAttendees(Long workshopId) {
        if (!workshopRepository.existsById(workshopId)) {
            throw new ResourceNotFoundException("Workshop not found with id " + workshopId);
        }

        // We only care about SUCCESS registrations for check-in
        List<Registration> validRegistrations = registrationRepository.findByStatusAndCreatedAtBefore("SUCCESS", java.time.LocalDateTime.now().plusYears(100))
                .stream().filter(r -> r.getWorkshop().getId().equals(workshopId)).toList();

        // Get all check-in records for this workshop
        List<com.unihub.backend.entity.CheckinRecord> checkinRecords = checkinRecordRepository.findByRegistrationWorkshopId(workshopId);
        Map<Long, com.unihub.backend.entity.CheckinRecord> checkinMap = checkinRecords.stream()
                .collect(Collectors.toMap(com.unihub.backend.entity.CheckinRecord::getRegistrationId, c -> c));
        
        return validRegistrations.stream()
                .map(r -> {
                    com.unihub.backend.entity.CheckinRecord record = checkinMap.get(r.getId());
                    return AttendeeResponse.builder()
                            .qrCode(r.getQrCode())
                            .registrationId(r.getId())
                            .studentName(r.getUser().getFullName())
                            .studentCode(r.getUser().getStudentCode())
                            .isCheckedIn(record != null)
                            .scannedAt(record != null ? record.getScannedAt().toString() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CheckinSyncResponse syncCheckins(CheckinSyncRequest request) {
        Long workshopId = request.getWorkshopId();
        
        if (!workshopRepository.existsById(workshopId)) {
            throw new ResourceNotFoundException("Workshop not found with id " + workshopId);
        }

        CheckinSyncResponse response = new CheckinSyncResponse();
        response.setTotalReceived(request.getRecords().size());

        // We only care about SUCCESS registrations
        List<Registration> validRegistrations = registrationRepository.findByStatusAndCreatedAtBefore("SUCCESS", java.time.LocalDateTime.now().plusYears(100))
                .stream().filter(r -> r.getWorkshop().getId().equals(workshopId)).toList();

        Map<String, Registration> qrToRegistrationMap = validRegistrations.stream()
                .collect(Collectors.toMap(Registration::getQrCode, r -> r));

        for (CheckinSyncRequest.CheckinEntry entry : request.getRecords()) {
            Registration registration = qrToRegistrationMap.get(entry.getQrCode());

            if (registration == null) {
                response.setFailedCount(response.getFailedCount() + 1);
                response.getFailures().add(CheckinSyncResponse.FailedEntry.builder()
                        .qrCode(entry.getQrCode())
                        .reason("QR Code not found, not SUCCESS, or does not belong to this workshop")
                        .build());
                continue;
            }

            try {
                // Use the idempotent insert query
                int inserted = checkinRecordRepository.insertOnConflictDoNothing(
                        registration.getId(),
                        entry.getScannedAt(),
                        java.time.LocalDateTime.now()
                );

                if (inserted > 0) {
                    response.setSuccessCount(response.getSuccessCount() + 1);
                } else {
                    // It was already checked in (duplicate)
                    response.setDuplicateCount(response.getDuplicateCount() + 1);
                }
            } catch (Exception e) {
                log.error("Error syncing check-in for QR " + entry.getQrCode(), e);
                response.setFailedCount(response.getFailedCount() + 1);
                response.getFailures().add(CheckinSyncResponse.FailedEntry.builder()
                        .qrCode(entry.getQrCode())
                        .reason("Database error: " + e.getMessage())
                        .build());
            }
        }

        log.info("Synced check-ins for workshop {}: {} total, {} success, {} duplicates, {} failed",
                workshopId, response.getTotalReceived(), response.getSuccessCount(),
                response.getDuplicateCount(), response.getFailedCount());

        return response;
    }
}
