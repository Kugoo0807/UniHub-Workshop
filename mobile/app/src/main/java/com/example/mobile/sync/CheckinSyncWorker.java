package com.example.mobile.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mobile.data.local.AppDatabase;
import com.example.mobile.data.local.AttendeeDao;
import com.example.mobile.data.local.AttendeeEntity;
import com.example.mobile.data.remote.ApiService;
import com.example.mobile.data.remote.RetrofitClient;
import com.example.mobile.data.remote.dto.CheckinSyncRequest;
import com.example.mobile.data.remote.dto.CheckinSyncResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

public class CheckinSyncWorker extends Worker {

    private static final String TAG = "CheckinSyncWorker";

    public CheckinSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting background sync...");
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        AttendeeDao dao = db.attendeeDao();
        ApiService apiService = RetrofitClient.getApiService(getApplicationContext());

        List<AttendeeEntity> unsynced = dao.getAllUnsyncedCheckins();
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No records to sync.");
            return Result.success();
        }

        // Group by workshopId
        Map<Long, List<AttendeeEntity>> grouped = new HashMap<>();
        for (AttendeeEntity entity : unsynced) {
            long wId = entity.getWorkshopId();
            if (!grouped.containsKey(wId)) {
                grouped.put(wId, new ArrayList<>());
            }
            grouped.get(wId).add(entity);
        }

        boolean allSuccess = true;

        for (Map.Entry<Long, List<AttendeeEntity>> entry : grouped.entrySet()) {
            Long workshopId = entry.getKey();
            List<AttendeeEntity> entities = entry.getValue();

            List<CheckinSyncRequest.CheckinEntry> records = new ArrayList<>();
            for (AttendeeEntity e : entities) {
                records.add(new CheckinSyncRequest.CheckinEntry(e.getQrCode(), e.getScannedAt()));
            }

            CheckinSyncRequest request = new CheckinSyncRequest(workshopId, records);
            try {
                Response<CheckinSyncResponse> response = apiService.syncCheckins(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    // Update local DB
                    for (AttendeeEntity e : entities) {
                        e.setSynced(true);
                        dao.update(e);
                    }
                    Log.d(TAG, "Sync success for workshop " + workshopId + ": " + response.body().getSuccessCount() + " synced");
                } else {
                    Log.e(TAG, "Sync failed for workshop " + workshopId + ": " + response.code());
                    allSuccess = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during sync", e);
                allSuccess = false;
            }
        }

        return allSuccess ? Result.success() : Result.retry();
    }
}
