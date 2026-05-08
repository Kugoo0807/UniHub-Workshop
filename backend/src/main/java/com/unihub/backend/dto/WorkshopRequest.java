package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Schema(description = "Request payload for creating or updating a workshop.")
public record WorkshopRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Workshop title", example = "Workshop: Clean Code với Java")
    String title,

    @Schema(description = "Workshop description (optional, can be filled by AI)", example = "Mô tả nội dung workshop...")
    String description,

    @NotNull(message = "Room ID is required")
    @Schema(description = "ID of the room where the workshop will be held", example = "1")
    Long roomId,

    @NotBlank(message = "Speaker is required")
    @Size(max = 255, message = "Speaker name must not exceed 255 characters")
    @Schema(description = "Speaker / presenter name", example = "Nguyen Van A")
    String speaker,

    @NotNull(message = "Total slots is required")
    @Positive(message = "Total slots must be greater than 0")
    @Schema(description = "Total number of seats", example = "60")
    Integer totalSlots,

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be >= 0")
    @Schema(description = "Ticket price (0 for free)", example = "0")
    Long price,

    @NotNull(message = "Start time is required")
    @Schema(description = "Workshop start time", example = "2026-05-10T08:00:00")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    @Schema(description = "Workshop end time", example = "2026-05-10T12:00:00")
    LocalDateTime endTime,

    @NotNull(message = "Registration start time is required")
    @Schema(description = "Registration start time", example = "2026-05-05T08:00:00")
    LocalDateTime registrationStartTime,

    @NotNull(message = "Registration end time is required")
    @Schema(description = "Registration end time", example = "2026-05-10T07:30:00")
    LocalDateTime registrationEndTime
) {}
