package com.example.myapplication.models;

public class AuthResponse {

    private String token;
    private String userId;
    private String name;
    private String email;

    public String getToken() {
        return token;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}