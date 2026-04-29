package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Standard error response payload.")
public class ErrorResponse {
    @Schema(description = "Timestamp of the error", example = "2024-01-01T00:00:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error title", example = "Bad Request")
    private String error;

    @Schema(description = "Error detail message", example = "Validation failed for request payload")
    private String message;
}
