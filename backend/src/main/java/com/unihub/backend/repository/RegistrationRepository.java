package com.unihub.backend.repository;

import com.unihub.backend.entity.Registration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByWorkshopId(Long workshopId);
    boolean existsByUserIdAndWorkshopId(Long userId, Long workshopId);

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

    // Find PENDING registrations created before the cutoff time (used by cancellation job)
    List<Registration> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
}
