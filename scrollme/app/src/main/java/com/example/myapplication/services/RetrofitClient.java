package com.example.myapplication.services;

import android.content.Context;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL =
            "http://192.168.1.6:8080/";

    private static Retrofit retrofit;

    public static Retrofit getRetrofit(Context context) {

        if (retrofit == null) {

            OkHttpClient client =
                    new OkHttpClient.Builder()
                            .addInterceptor(
                                    new AuthInterceptor(context)
                            )
                            .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();
        }

        return retrofit;
    }

    public static ApiService getApiService(
            Context context) {

        return getRetrofit(context)
                .create(ApiService.class);
    }
}