package com.example.myapplication.models;

public class SignupRequest {

    private String name;
    private String email;
    private String password;

    public SignupRequest(
            String name,
            String email,
            String password) {

        this.name = name;
        this.email = email;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}