package com.unihub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegistrationResponse {
    private Long registrationId;
    private String qrCode;
    private String status;
}
