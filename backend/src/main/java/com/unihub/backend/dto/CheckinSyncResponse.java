package com.unihub.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinSyncResponse {

    private int totalReceived;
    private int successCount;
    private int duplicateCount;
    private int failedCount;

    @Builder.Default
    private List<FailedEntry> failures = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedEntry {
        private String qrCode;
        private String reason;
    }
}
