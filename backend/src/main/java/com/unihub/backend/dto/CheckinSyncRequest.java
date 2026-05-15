package com.unihub.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinSyncRequest {

    @NotNull(message = "Workshop ID is required")
    private Long workshopId;

    @NotEmpty(message = "Records list cannot be empty")
    @Valid
    private List<CheckinEntry> records;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckinEntry {

        @NotNull(message = "QR code is required")
        private String qrCode;

        @NotNull(message = "Scanned time is required")
        private LocalDateTime scannedAt;
    }
}
