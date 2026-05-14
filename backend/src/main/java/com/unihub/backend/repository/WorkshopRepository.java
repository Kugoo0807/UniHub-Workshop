package com.unihub.backend.repository;

import com.unihub.backend.entity.Workshop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, Long> {

    /**
     * Fetch all workshops with their Room eagerly loaded in a single query.
     * Prevents N+1 queries when mapping to WorkshopResponse (which needs room name/capacity).
     */
    @Query("SELECT w FROM Workshop w JOIN FETCH w.room ORDER BY w.id ASC")
    List<Workshop> findAllWithRoom();

    /**
     * Paginated version — JOIN FETCH requires a separate countQuery to avoid
     * HibernateQueryException with pagination + fetch joins.
     */
    @Query(
        value      = "SELECT w FROM Workshop w JOIN FETCH w.room ORDER BY w.id ASC",
        countQuery = "SELECT COUNT(w) FROM Workshop w"
    )
    Page<Workshop> findAllWithRoom(Pageable pageable);

    @Query("SELECT w FROM Workshop w JOIN FETCH w.room WHERE w.status = 'PUBLISHED' ORDER BY w.id ASC")
    List<Workshop> findAllPublishedWithRoom();

    /**
     * Fetch a single workshop with its Room eagerly loaded.
     * Prevents an extra query when accessing room fields in toResponse().
     */
    @Query("SELECT w FROM Workshop w JOIN FETCH w.room WHERE w.id = :id")
    Optional<Workshop> findByIdWithRoom(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Workshop w SET w.description = :description WHERE w.id = :id")
    void updateDescription(@Param("id") Long id, @Param("description") String description);

    List<Workshop> findByStatusAndEndTimeBefore(String status, LocalDateTime time);
}
