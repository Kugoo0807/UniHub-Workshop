package com.unihub.backend.repository;

import com.unihub.backend.entity.Registration;
import com.unihub.backend.entity.Workshop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import com.unihub.backend.entity.User;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByWorkshopId(Long workshopId);

    boolean existsByWorkshopIdAndStatus(Long workshopId, String status);

    long countByWorkshopIdAndStatus(Long workshopId, String status);
    
    Optional<Registration> findByUserAndWorkshop(User user, Workshop workshop);

    List<Registration> findByUser(User user);
}
