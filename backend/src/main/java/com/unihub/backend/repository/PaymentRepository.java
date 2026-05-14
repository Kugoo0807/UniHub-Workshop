package com.unihub.backend.repository;

import com.unihub.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByRegistrationId(Long registrationId);

    /**
     * Count COMPLETED payments linked to registrations of a specific workshop.
     * Used during workshop cancellation to identify paid registrants requiring refund.
     */
    @Query("""
            select count(p) from Payment p
            where p.registration.workshop.id = :workshopId
              and p.status = 'COMPLETED'
            """)
    long countCompletedByWorkshopId(@Param("workshopId") Long workshopId);
}
