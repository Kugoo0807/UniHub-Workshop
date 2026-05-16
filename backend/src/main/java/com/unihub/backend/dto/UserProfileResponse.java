package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Basic user profile information.")
public class UserProfileResponse {

    @Schema(description = "User identifier", example = "42")
    private String userId;

    @Schema(description = "Full name", example = "Alex Johnson")
    private String fullName;

    @Schema(description = "Email address", example = "alex.johnson@unihub.edu")
    private String email;

    @Schema(description = "Student code", example = "SE123456")
    private String studentCode;

    @Schema(description = "Phone number", example = "+15559876543")
    private String phoneNumber;

    @Schema(description = "Telegram chat identifier", example = "583920123")
    private String chatId;

    @Schema(description = "User role", example = "STUDENT")
    private String role;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
}

