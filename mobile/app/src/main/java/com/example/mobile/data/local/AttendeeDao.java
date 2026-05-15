package com.example.mobile.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AttendeeDao {

    @Query("SELECT * FROM attendees WHERE workshop_id = :workshopId")
    List<AttendeeEntity> getAttendeesByWorkshop(long workshopId);

    @Query("SELECT * FROM attendees WHERE qr_code = :qrCode AND workshop_id = :workshopId LIMIT 1")
    AttendeeEntity getAttendeeByQrCode(String qrCode, long workshopId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AttendeeEntity> attendees);

    @Update
    void update(AttendeeEntity attendee);

    @Query("SELECT * FROM attendees WHERE is_checked_in = 1 AND is_synced = 0 AND workshop_id = :workshopId")
    List<AttendeeEntity> getUnsyncedCheckins(long workshopId);

    @Query("SELECT * FROM attendees WHERE is_checked_in = 1 AND is_synced = 0")
    List<AttendeeEntity> getAllUnsyncedCheckins();

    @Query("DELETE FROM attendees WHERE workshop_id = :workshopId")
    void deleteByWorkshopId(long workshopId);
}
