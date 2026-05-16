package com.unihub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeResponse {
    private String qrCode;
    private Long registrationId;
    private String studentName;
    private String studentCode;

    @com.fasterxml.jackson.annotation.JsonProperty("isCheckedIn")
    private boolean isCheckedIn;
    
    private String scannedAt;
}
