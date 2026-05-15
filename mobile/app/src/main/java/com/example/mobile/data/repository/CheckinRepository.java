package com.example.mobile.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.mobile.data.local.AppDatabase;
import com.example.mobile.data.local.AttendeeDao;
import com.example.mobile.data.local.AttendeeEntity;
import com.example.mobile.data.remote.ApiService;
import com.example.mobile.data.remote.RetrofitClient;
import com.example.mobile.data.remote.dto.AttendeeResponse;
import com.example.mobile.sync.CheckinSyncWorker;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckinRepository {

    private final AttendeeDao attendeeDao;
    private final ApiService apiService;
    private final Context context;

    public CheckinRepository(Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getDatabase(context);
        this.attendeeDao = db.attendeeDao();
        this.apiService = RetrofitClient.getApiService(context);
    }

    public void downloadAttendees(long workshopId, final DownloadCallback callback) {
        apiService.getAttendees(workshopId).enqueue(new Callback<List<AttendeeResponse>>() {
            @Override
            public void onResponse(Call<List<AttendeeResponse>> call, Response<List<AttendeeResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new Thread(() -> {
                        attendeeDao.deleteByWorkshopId(workshopId);
                        List<AttendeeEntity> entities = new ArrayList<>();
                        for (AttendeeResponse dto : response.body()) {
                            AttendeeEntity entity = new AttendeeEntity(
                                    dto.getQrCode(),
                                    dto.getRegistrationId(),
                                    dto.getStudentName(),
                                    dto.getStudentCode(),
                                    workshopId
                            );
                            entity.setCheckedIn(dto.isCheckedIn());
                            entity.setScannedAt(dto.getScannedAt());
                            // It's downloaded from the server, so if it's checked in, it's already synced
                            entity.setSynced(dto.isCheckedIn()); 
                            entities.add(entity);
                        }
                        attendeeDao.insertAll(entities);
                        callback.onSuccess();
                    }).start();
                } else {
                    callback.onError("Failed to download: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<AttendeeResponse>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void triggerBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(CheckinSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(syncRequest);
    }

    public interface DownloadCallback {
        void onSuccess();
        void onError(String error);
    }
}
