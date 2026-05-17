package com.unihub.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workshops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workshop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    @Builder.Default
    private String speaker = "TBD";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";  // DRAFT, PUBLISHED, COMPLETED, CANCELLED

    @Column(name = "total_slots", nullable = false)
    private Integer totalSlots;

    @Column(name = "remaining_slots", nullable = false)
    private Integer remainingSlots;

    @Column(columnDefinition = "BIGINT DEFAULT 0")
    @Builder.Default
    private Long price = 0L;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "registration_start_time", nullable = false)
    private LocalDateTime registrationStartTime;

    @Column(name = "registration_end_time", nullable = false)
    private LocalDateTime registrationEndTime;
}
