package com.example.demo.dto;

public class AuthResponse {

    private String token;

    private String userId;

    private String name;

    private String email;

    public AuthResponse(
            String token,
            String userId,
            String name,
            String email) {

        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

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