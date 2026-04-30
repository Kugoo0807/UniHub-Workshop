package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Statistics for a specific workshop.")
public class WorkshopStatsResponse {
    @Schema(description = "Workshop ID", example = "1")
    private Long workshopId;

    @Schema(description = "Workshop title", example = "Workshop: Clean Code với Java")
    private String title;

    @Schema(description = "Total number of seats", example = "60")
    private Integer totalSlots;

    @Schema(description = "Remaining seats (realtime from Redis)", example = "42")
    private Integer remainingSlots;

    @Schema(description = "Number of successful registrations", example = "18")
    private Long registeredCount;

    @Schema(description = "Fill rate percentage", example = "30.0")
    private Double fillRate;
}
