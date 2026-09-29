package com.example.myapplication.models;

public class AppVersion {

    private int latestVersionCode;
    private String latestVersionName;
    private int minimumVersionCode;
    private String apkUrl;

    public int getLatestVersionCode() {
        return latestVersionCode;
    }

    public String getLatestVersionName() {
        return latestVersionName;
    }

    public int getMinimumVersionCode() {
        return minimumVersionCode;
    }

    public String getApkUrl() {
        return apkUrl;
    }
}