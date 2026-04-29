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
@Schema(description = "Student registration request payload.")
public class StudentRegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    @Schema(description = "Full name of the student", example = "Nguyen Van A")
    private String fullName;

    @NotBlank(message = "Student code is required")
    @Size(max = 20)
    @Schema(description = "Student code", example = "21127001")
    private String studentCode;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    @Schema(description = "Email address", example = "21127001@student.hcmus.edu.vn")
    private String email;

    @Size(max = 15)
    @Schema(description = "Phone number", example = "+84778008234")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 255, message = "Password must be at least 6 characters")
    @Schema(description = "Account password", example = "password123")
    private String password;
}
