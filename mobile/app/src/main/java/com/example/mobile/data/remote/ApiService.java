package com.example.mobile.data.remote;

import com.example.mobile.data.remote.dto.AttendeeResponse;
import com.example.mobile.data.remote.dto.AuthResponse;
import com.example.mobile.data.remote.dto.CheckinSyncRequest;
import com.example.mobile.data.remote.dto.CheckinSyncResponse;
import com.example.mobile.data.remote.dto.LoginRequest;
import com.example.mobile.data.remote.dto.PageResponse;
import com.example.mobile.data.remote.dto.RefreshTokenRequest;
import com.example.mobile.data.remote.dto.WorkshopResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/v1/auth/login/app")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/v1/auth/refresh")
    Call<AuthResponse> refreshToken(@Body RefreshTokenRequest refreshToken);

    @GET("api/v1/workshops")
    Call<PageResponse<WorkshopResponse>> getWorkshops(@Query("page") int page, @Query("size") int size);

    @GET("api/v1/workshops/{id}/attendees")
    Call<List<AttendeeResponse>> getAttendees(@Path("id") long workshopId);

    @POST("api/v1/checkins/sync")
    Call<CheckinSyncResponse> syncCheckins(@Body CheckinSyncRequest request);
}
