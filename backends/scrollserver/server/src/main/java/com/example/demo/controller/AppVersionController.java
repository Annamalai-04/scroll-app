package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersion() {

        Map<String, Object> response =
                new HashMap<>();

        response.put("latestVersionCode", 1);
        response.put("latestVersionName", "1.1");
        response.put("minimumVersionCode", 1);

        // We will replace this with the real S3 APK URL
        // in the later deployment step.
        response.put(
                "apkUrl",
                "https://my-scroll-videos-2026.s3.us-east-1.amazonaws.com/apk/app-release.apk"
        );

        return ResponseEntity.ok(response);
    }
}