package com.example.myapplication.models;

public class SigninRequest {

    private String email;
    private String password;

    public SigninRequest(
            String email,
            String password) {

        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}