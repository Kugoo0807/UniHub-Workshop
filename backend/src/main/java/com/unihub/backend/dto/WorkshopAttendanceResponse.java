package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Attendance record for a single registered student in a workshop.")
public class WorkshopAttendanceResponse {

    @Schema(description = "Registration ID", example = "42")
    private Long registrationId;

    @Schema(description = "Student user ID", example = "7")
    private Long userId;

    @Schema(description = "Student code", example = "SV001")
    private String studentCode;

    @Schema(description = "Student full name", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "Student email", example = "sva@example.com")
    private String email;

    @Schema(description = "Student phone number", example = "0901234567")
    private String phoneNumber;

    @Schema(description = "Registration status (PENDING / SUCCESS / CANCELLED)", example = "SUCCESS")
    private String registrationStatus;

    @Schema(description = "Timestamp when the student registered", example = "2026-05-14T08:00:00")
    private LocalDateTime registeredAt;

    @Schema(description = "Whether the student has checked in", example = "true")
    private Boolean checkedIn;

    @Schema(description = "Timestamp when the student scanned the QR code; null if not checked in")
    private LocalDateTime checkedInAt;
}
