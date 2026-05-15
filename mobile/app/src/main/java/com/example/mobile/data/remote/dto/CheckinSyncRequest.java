package com.example.mobile.data.remote.dto;

import java.util.List;

public class CheckinSyncRequest {
    private Long workshopId;
    private List<CheckinEntry> records;

    public CheckinSyncRequest(Long workshopId, List<CheckinEntry> records) {
        this.workshopId = workshopId;
        this.records = records;
    }

    public Long getWorkshopId() { return workshopId; }
    public void setWorkshopId(Long workshopId) { this.workshopId = workshopId; }

    public List<CheckinEntry> getRecords() { return records; }
    public void setRecords(List<CheckinEntry> records) { this.records = records; }

    public static class CheckinEntry {
        private String qrCode;
        private String scannedAt;

        public CheckinEntry(String qrCode, String scannedAt) {
            this.qrCode = qrCode;
            this.scannedAt = scannedAt;
        }

        public String getQrCode() { return qrCode; }
        public void setQrCode(String qrCode) { this.qrCode = qrCode; }

        public String getScannedAt() { return scannedAt; }
        public void setScannedAt(String scannedAt) { this.scannedAt = scannedAt; }
    }
}
