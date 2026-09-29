package com.example.demo.model;

import java.time.LocalDateTime;

public class History {

    private String videoId;
    private LocalDateTime date;
    private double percent;

    // Total video length, for example: "0:30"
    private String length;

    // Last watched position, for example: "0:17"
    private String watched;

    public History() {
    }

    public History(
            String videoId,
            LocalDateTime date,
            double percent,
            String length,
            String watched) {

        this.videoId = videoId;
        this.date = date;
        this.percent = percent;
        this.length = length;
        this.watched = watched;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getWatched() {
        return watched;
    }

    public void setWatched(String watched) {
        this.watched = watched;
    }
}
