package com.example.mobile.data.remote;

import android.content.Context;

import com.example.mobile.util.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // For emulator testing, use 10.0.2.2. For physical device, use IP.
    private static final String BASE_URL = "http://10.0.2.2:8080/";
    
    private static Retrofit retrofit = null;

    public static ApiService getApiService(Context context) {
        if (retrofit == null) {
            TokenManager tokenManager = new TokenManager(context);
            
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder();
                    
                    String token = tokenManager.getAccessToken();
                    if (token != null && !token.isEmpty()) {
                        requestBuilder.header("Authorization", "Bearer " + token);
                    }
                    
                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }
            }).build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
