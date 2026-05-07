package com.unihub.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserRegistrationResponse {
    private Long registrationId;
    private Long workshopId;
    private String title;
    private String status;
    private String qrCode;
    private LocalDateTime createdAt;
    private Long price;
}