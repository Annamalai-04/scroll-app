package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.SigninRequest;
import com.example.demo.dto.SignupRequest;
import com.example.demo.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request) {

        return ResponseEntity.ok(
                authService.signup(request)
        );
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(
            @Valid @RequestBody SigninRequest request) {

        return ResponseEntity.ok(
                authService.signin(request)
        );
    }
}