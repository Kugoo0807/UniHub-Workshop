package com.example.mobile.data.remote;

import android.content.Context;
import android.content.Intent;

import com.example.mobile.LoginActivity;
import com.example.mobile.data.remote.dto.AuthResponse;
import com.example.mobile.data.remote.dto.RefreshTokenRequest;
import com.example.mobile.util.TokenManager;
import com.example.mobile.BuildConfig;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // For emulator testing, use 10.0.2.2. For physical device, use IP.
    private static final String BASE_URL = BuildConfig.BASE_URL;
    
    private static Retrofit retrofit = null;

    public static ApiService getApiService(Context context) {
        if (retrofit == null) {
            TokenManager tokenManager = new TokenManager(context);

            // Create Interceptor to add Authorization header with the access token
            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();

                String token = tokenManager.getAccessToken();
                if (token != null && !token.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                Request request = requestBuilder.build();
                return chain.proceed(request);
            };

            // Authenticator to handle 401 responses and refresh tokens
            Authenticator tokenAuthenticator = new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    if (response.request().url().encodedPath().contains("api/v1/auth/refresh")) {
                        return null;
                    }

                    String refreshToken = tokenManager.getRefreshToken();
                    if (refreshToken == null || refreshToken.isEmpty()) {
                        forceLogout(context, tokenManager);
                        return null;
                    }

                    ApiService syncApiService = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService.class);

                    retrofit2.Response<AuthResponse> refreshResponse = syncApiService
                            .refreshToken(new RefreshTokenRequest(refreshToken))
                            .execute();

                    if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                        String newAccessToken = refreshResponse.body().getAccessToken();
                        String newRefreshToken = refreshResponse.body().getRefreshToken();
                        tokenManager.saveTokens(newAccessToken, newRefreshToken);

                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + newAccessToken)
                                .build();
                    } else {
                        forceLogout(context, tokenManager);
                        return null;
                    }
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .authenticator(tokenAuthenticator)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    // -- Force Logout Method --
    private static void forceLogout(Context context, TokenManager tokenManager) {
        tokenManager.clearTokens();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
