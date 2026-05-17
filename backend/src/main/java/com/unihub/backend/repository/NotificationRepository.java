package com.unihub.backend.repository;

import com.unihub.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.channel = :channel AND n.status = 'SUCCESS' AND (n.user.id = :userId OR n.user IS NULL)")
    Page<Notification> findInAppNotificationsForUser(
            @Param("userId") Long userId,
            @Param("channel") String channel,
            Pageable pageable
    );

    List<Notification> findByStatusAndRetryCountLessThan(String status, int maxRetries);
}
