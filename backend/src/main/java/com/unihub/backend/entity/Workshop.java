package com.unihub.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
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

    @Column(name = "total_slots", nullable = false)
    private Integer totalSlots;

    @Column(name = "remaining_slots", nullable = false)
    private Integer remainingSlots;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
}
