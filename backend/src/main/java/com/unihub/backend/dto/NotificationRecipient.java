package com.unihub.backend.dto;

public record NotificationRecipient(
        Long userId,
        String fullName,
        String email,
        String phoneNumber
) {
}
