package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Response payload representing a workshop.")
public class WorkshopResponse {
    @Schema(description = "Workshop ID", example = "1")
    private Long id;

    @Schema(description = "Workshop title", example = "Workshop: Clean Code với Java")
    private String title;

    @Schema(description = "Workshop description")
    private String description;

    @Schema(description = "Total number of seats", example = "60")
    private Integer totalSlots;

    @Schema(description = "Remaining seats (realtime from Redis)", example = "42")
    private Integer remainingSlots;

    @Schema(description = "Ticket price", example = "0")
    private BigDecimal price;

    @Schema(description = "Workshop start time")
    private LocalDateTime startTime;

    @Schema(description = "Workshop end time")
    private LocalDateTime endTime;
}
