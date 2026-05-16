package com.unihub.backend.repository;

import com.unihub.backend.entity.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

    boolean existsByRegistrationId(Long registrationId);

    @Modifying
    @Query(value = """
            INSERT INTO checkin_records (registration_id, scanned_at, synced_at)
            VALUES (:registrationId, :scannedAt, :syncedAt)
            ON CONFLICT (registration_id) DO NOTHING
            """, nativeQuery = true)
    int insertOnConflictDoNothing(
            @Param("registrationId") Long registrationId,
            @Param("scannedAt") LocalDateTime scannedAt,
            @Param("syncedAt") LocalDateTime syncedAt);

    long countByRegistrationWorkshopId(Long workshopId);

    List<CheckinRecord> findByRegistrationWorkshopId(Long workshopId);
}
