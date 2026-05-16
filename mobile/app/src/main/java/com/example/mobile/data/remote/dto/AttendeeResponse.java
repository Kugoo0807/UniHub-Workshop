package com.example.mobile.data.remote.dto;

public class AttendeeResponse {
    private String qrCode;
    private Long registrationId;
    private String studentName;
    private String studentCode;

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public Long getRegistrationId() { return registrationId; }
    public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    @com.google.gson.annotations.SerializedName("isCheckedIn")
    private boolean isCheckedIn;
    
    @com.google.gson.annotations.SerializedName("scannedAt")
    private String scannedAt;

    public boolean isCheckedIn() { return isCheckedIn; }
    public void setCheckedIn(boolean checkedIn) { isCheckedIn = checkedIn; }

    public String getScannedAt() { return scannedAt; }
    public void setScannedAt(String scannedAt) { this.scannedAt = scannedAt; }
}
