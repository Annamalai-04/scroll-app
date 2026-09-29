package com.example.myapplication.services;

import android.content.Context;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class AuthInterceptor implements Interceptor {

    private final Context context;

    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain)
            throws IOException {

        Request original = chain.request();

        String path = original.url().encodedPath();

        // =========================================
        // PUBLIC APIs
        // =========================================

        boolean publicApi =
                path.equals("/api/auth/register")
                        || path.equals("/api/auth/login")
                        || path.equals("/api/videos")
                        || path.startsWith("/api/videos/");

        // Do NOT attach JWT to public APIs
        if (publicApi) {
            return chain.proceed(original);
        }

        // =========================================
        // PROTECTED APIs
        // =========================================

        String token =
                SessionManager.getToken(context);

        if (token == null || token.trim().isEmpty()) {
            return chain.proceed(original);
        }

        Request authenticatedRequest =
                original.newBuilder()
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .build();

        return chain.proceed(authenticatedRequest);
    }
}