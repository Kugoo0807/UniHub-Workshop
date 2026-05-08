package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

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

    @Schema(description = "Room ID", example = "1")
    private Long roomId;

    @Schema(description = "Room name", example = "Hall A")
    private String roomName;

    @Schema(description = "Room capacity", example = "100")
    private Integer roomCapacity;

    @Schema(description = "Speaker name", example = "Nguyen Van A")
    private String speaker;

    @Schema(description = "Workshop status", example = "DRAFT")
    private String status;

    @Schema(description = "Total number of seats", example = "60")
    private Integer totalSlots;

    @Schema(description = "Remaining seats (realtime from Redis)", example = "42")
    private Integer remainingSlots;

    @Schema(description = "Ticket price", example = "0")
    private Long price;

    @Schema(description = "Workshop start time")
    private LocalDateTime startTime;

    @Schema(description = "Workshop end time")
    private LocalDateTime endTime;

    @Schema(description = "Registration start time")
    private LocalDateTime registrationStartTime;

    @Schema(description = "Registration end time")
    private LocalDateTime registrationEndTime;
}
