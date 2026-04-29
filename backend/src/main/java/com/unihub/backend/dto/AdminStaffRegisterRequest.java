package com.unihub.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin or staff registration request payload.")
public class AdminStaffRegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    @Schema(description = "Full name of the user", example = "Taylor Morgan")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    @Schema(description = "Email address", example = "taylor.morgan@unihub.edu")
    private String email;

    @Size(max = 15)
    @Schema(description = "Phone number", example = "+15551234567")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 255, message = "Password must be at least 6 characters")
    @Schema(description = "Account password", example = "password123")
    private String password;

    @NotBlank(message = "Role is required")
    @Schema(description = "User role", example = "ADMIN")
    private String role;  // ADMIN or STAFF
}
