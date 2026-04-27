package com.unihub.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkin_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckinRecord {

    @Id
    @Column(name = "registration_id")
    private Long registrationId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "registration_id")
    private Registration registration;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onCreate() {
        if (this.syncedAt == null) {
            this.syncedAt = LocalDateTime.now();
        }
    }
}
