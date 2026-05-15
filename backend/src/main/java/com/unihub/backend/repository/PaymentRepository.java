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

    /** Total revenue from all COMPLETED payments. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = 'COMPLETED'")
    Long sumRevenue();

    /** Count all COMPLETED payments globally. */
    @Query("select count(p) from Payment p where p.status = 'COMPLETED'")
    long countCompleted();

    /** Count all FAILED payments globally. */
    @Query("select count(p) from Payment p where p.status = 'FAILED'")
    long countFailed();

    /** Count payments for paid workshops (price > 0) that are COMPLETED. */
    @Query("""
            select count(p) from Payment p
            where p.registration.workshop.price > 0
              and p.status = 'COMPLETED'
            """)
    long countCompletedForPaidWorkshops();

    /** Count payments for paid workshops (price > 0) that are FAILED. */
    @Query("""
            select count(p) from Payment p
            where p.registration.workshop.price > 0
              and p.status = 'FAILED'
            """)
    long countFailedForPaidWorkshops();

    /**
     * Total payment attempts for paid workshops (COMPLETED + FAILED).
     * Used to compute success/failure rate.
     */
    @Query("""
            select count(p) from Payment p
            where p.registration.workshop.price > 0
              and p.status in ('COMPLETED', 'FAILED')
            """)
    long countTotalAttemptsForPaidWorkshops();
}
