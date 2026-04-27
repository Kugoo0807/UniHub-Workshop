package com.unihub.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id")
    private Workshop workshop;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    @Column(nullable = false, length = 20)
    private String status;  // PENDING, SUCCESS, CANCELLED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
