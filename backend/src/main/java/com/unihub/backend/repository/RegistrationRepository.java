package com.unihub.backend.repository;

import com.unihub.backend.entity.Registration;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByWorkshopId(Long workshopId);
    boolean existsByUserIdAndWorkshopId(Long userId, Long workshopId);
    java.util.Optional<Registration> findByUserIdAndWorkshopId(Long userId, Long workshopId);
    boolean existsByUserIdAndWorkshopIdAndStatusIn(Long userId, Long workshopId, List<String> statuses);
    java.util.Optional<Registration> findByUserIdAndWorkshopIdAndStatus(Long userId, Long workshopId, String status);

    boolean existsByWorkshopIdAndStatus(Long workshopId, String status);

    long countByWorkshopIdAndStatus(Long workshopId, String status);

    @Query("""
            select r
            from Registration r
            join fetch r.workshop w
            where r.user.id = :userId
            order by r.createdAt desc
            """)
    List<Registration> findAllByUserIdWithWorkshop(@Param("userId") Long userId);

    /**
     * Paginated version — separate countQuery to avoid
     * HibernateQueryException with pagination + fetch joins.
     */
    @Query(
        value      = "SELECT r FROM Registration r JOIN FETCH r.workshop w WHERE r.user.id = :userId ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM Registration r WHERE r.user.id = :userId"
    )
    Page<Registration> findAllByUserIdWithWorkshop(@Param("userId") Long userId, Pageable pageable);

    // Find PENDING registrations created before the cutoff time (used by cancellation job)
    List<Registration> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);

    /**
     * Bulk-update registration status to CANCELLED for all active registrations of a workshop.
     * Only affects rows whose status is in the provided list (SUCCESS, PENDING).
     * Returns the number of rows updated.
     */
    @Modifying
    @Query("""
            update Registration r
            set r.status = 'CANCELLED'
            where r.workshop.id = :workshopId
              and r.status in :statuses
            """)
    int bulkCancelByWorkshopId(
            @Param("workshopId") Long workshopId,
            @Param("statuses") List<String> statuses);

    // ─── Global stats queries ───────────────────────────────────────────────

    /** Count all registrations by status. */
    long countByStatus(String status);

    /**
     * Count registrations with a specific status that belong to COMPLETED workshops only.
     * Used for participation/cancellation rate calculations scoped to past events.
     */
    @Query("""
            select count(r) from Registration r
            where r.status = :status
              and r.workshop.status = 'COMPLETED'
            """)
    long countByStatusAndWorkshopCompleted(@Param("status") String status);

    /**
     * Count all registrations that belong to COMPLETED workshops only.
     */
    @Query("""
            select count(r) from Registration r
            where r.workshop.status = 'COMPLETED'
            """)
    long countByWorkshopCompleted();

    /**
     * Count registrations grouped by hour-of-day (0–23) using created_at.
     * Returns Object[] { hour (Integer), count (Long) }.
     */
    @Query(value = """
            SELECT EXTRACT(HOUR FROM created_at) AS hour, COUNT(id)
            FROM registrations
            GROUP BY hour
            ORDER BY hour
            """, nativeQuery = true)
    List<Object[]> countByHourOfDay();


    /**
     * Speaker stats: number of workshops per speaker (non-null speakers only).
     * Returns Object[] { speaker (String), count (Long) }.
     */
    @Query("""
            select w.speaker, count(w.id)
            from Workshop w
            where w.speaker is not null and w.speaker <> ''
            group by w.speaker
            order by count(w.id) desc
            """)
    List<Object[]> countWorkshopsBySpeaker();

    /**
     * Room utilization: avg fill rate per room.
     * Returns Object[] { roomName, capacity, workshopCount, avgFillRate }
     */
    @Query("""
            select w.room.name, w.room.capacity, count(w.id),
                   avg(case when w.totalSlots > 0
                            then cast((w.totalSlots - w.remainingSlots) as double) / w.totalSlots * 100
                            else 0.0 end)
            from Workshop w
            where w.status in ('PUBLISHED', 'COMPLETED', 'CANCELLED')
            group by w.room.id, w.room.name, w.room.capacity
            order by avg(case when w.totalSlots > 0
                              then cast((w.totalSlots - w.remainingSlots) as double) / w.totalSlots * 100
                              else 0.0 end) desc
            """)
    List<Object[]> roomUtilizationStats();

    /**
     * Fill rate per workshop (for bar chart).
     * Returns Object[] { workshopId, title, totalSlots, successCount }
     */
    @Query("""
            select w.id, w.title, w.totalSlots,
                   (select count(r2) from Registration r2
                    where r2.workshop.id = w.id and r2.status = 'SUCCESS')
            from Workshop w
            order by w.id asc
            """)
    List<Object[]> workshopFillRates();

    // ─── Attendance queries ─────────────────────────────────────────────────

    /**
     * Fetch SUCCESS registrations for a workshop (paginated).
     * Uses LEFT JOIN FETCH so students who have not checked in are still included.
     * Only returns registrations with status = 'SUCCESS'.
     */
    @Query("""
            select r
            from Registration r
            join fetch r.user u
            left join fetch r.checkinRecord cr
            where r.workshop.id = :workshopId
              and r.status = 'SUCCESS'
            order by (case when cr is not null then 1 else 0 end) desc,
                     r.createdAt asc
            """)
    Page<Registration> findAttendancesByWorkshopId(
            @Param("workshopId") Long workshopId,
            Pageable pageable);
}
