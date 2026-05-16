package com.example.mobile.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "attendees")
public class AttendeeEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "qr_code")
    private String qrCode;

    @ColumnInfo(name = "registration_id")
    private long registrationId;

    @ColumnInfo(name = "student_name")
    private String studentName;

    @ColumnInfo(name = "student_code")
    private String studentCode;

    @ColumnInfo(name = "workshop_id")
    private long workshopId;

    @ColumnInfo(name = "is_checked_in")
    private boolean isCheckedIn;

    @ColumnInfo(name = "scanned_at")
    private String scannedAt; // ISO 8601 string

    @ColumnInfo(name = "is_synced")
    private boolean isSynced;

    public AttendeeEntity(@NonNull String qrCode, long registrationId, String studentName, String studentCode, long workshopId) {
        this.qrCode = qrCode;
        this.registrationId = registrationId;
        this.studentName = studentName;
        this.studentCode = studentCode;
        this.workshopId = workshopId;
        this.isCheckedIn = false;
        this.isSynced = false;
    }

    @NonNull
    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(@NonNull String qrCode) {
        this.qrCode = qrCode;
    }

    public long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(long registrationId) {
        this.registrationId = registrationId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode;
    }

    public long getWorkshopId() {
        return workshopId;
    }

    public void setWorkshopId(long workshopId) {
        this.workshopId = workshopId;
    }

    public boolean isCheckedIn() {
        return isCheckedIn;
    }

    public void setCheckedIn(boolean checkedIn) {
        isCheckedIn = checkedIn;
    }

    public String getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(String scannedAt) {
        this.scannedAt = scannedAt;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }
}
