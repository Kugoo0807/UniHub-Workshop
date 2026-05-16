package com.unihub.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_code", unique = true, length = 20)
    private String studentCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @JsonIgnore
    @Column(length = 255)
    private String password;

    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "chat_id", length = 255)
    private String chatId;

    @Column(nullable = false, length = 20)
    private String role;    // STUDENT, ADMIN, STAFF

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "INACTIVE";  // ACTIVE, INACTIVE
}
