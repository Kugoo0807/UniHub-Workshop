package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for creating or updating a room.")
public record RoomRequest(

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(description = "Unique room name", example = "Hall A")
    String name,

    @NotNull(message = "Capacity is required")
    @Positive(message = "Capacity must be greater than 0")
    @Schema(description = "Maximum number of people the room can hold", example = "120")
    Integer capacity
) {}
