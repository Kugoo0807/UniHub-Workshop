package com.unihub.backend.repository;

import com.unihub.backend.entity.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByWorkshopId(Long workshopId);

    boolean existsByWorkshopIdAndStatus(Long workshopId, String status);

    long countByWorkshopIdAndStatus(Long workshopId, String status);
}
