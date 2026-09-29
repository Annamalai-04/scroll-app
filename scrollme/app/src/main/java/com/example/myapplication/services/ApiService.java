package com.example.myapplication.services;

import com.example.myapplication.models.AuthResponse;
import com.example.myapplication.models.History;
import com.example.myapplication.models.HistoryRequest;
import com.example.myapplication.models.SigninRequest;
import com.example.myapplication.models.SignupRequest;
import com.example.myapplication.models.Video;
import com.example.myapplication.models.AppVersion;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/auth/signup")
    Call<AuthResponse> signup(
            @Body SignupRequest request
    );

    @POST("api/auth/signin")
    Call<AuthResponse> signin(
            @Body SigninRequest request
    );

    @POST("api/history")
    Call<String> saveHistory(
            @Body HistoryRequest request
    );

    @GET("api/movies")
    Call<List<Video>> getVideos();

    @GET("api/movies")
    Call<List<Video>> getVideosByType(
            @Query("type") String type
    );

    @DELETE("api/history/{videoId}")
    Call<String> deleteHistory(
            @Path("videoId") String videoId
    );

    @GET("api/history")
    Call<List<History>> getHistory();

    @GET("api/app/version")
    Call<AppVersion> getAppVersion();
}
