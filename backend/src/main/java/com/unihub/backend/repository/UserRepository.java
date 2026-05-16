package com.unihub.backend.repository;

import com.unihub.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.unihub.backend.dto.NotificationRecipient;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByStudentCodeAndFullNameAndEmail(String studentCode, String fullName, String email);

    @org.springframework.data.jpa.repository.Query("""
            select new com.unihub.backend.dto.NotificationRecipient(
                u.id, u.fullName, u.email, u.phoneNumber
            )
            from User u
            where u.role = :role
            """)
    java.util.List<NotificationRecipient> findRecipientsByRole(@org.springframework.data.repository.query.Param("role") String role);
}
