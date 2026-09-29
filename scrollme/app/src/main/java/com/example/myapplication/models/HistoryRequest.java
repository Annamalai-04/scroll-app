package com.example.myapplication.models;

public class HistoryRequest {

    private String videoId;
    private double percent;
    private String watched;
    private String length;

    public HistoryRequest(
            String videoId,
            double percent,
            String watched,
            String length) {

        this.videoId = videoId;
        this.percent = percent;
        this.watched = watched;
        this.length = length;
    }

    public String getVideoId() {
        return videoId;
    }

    public double getPercent() {
        return percent;
    }

    public String getWatched() {
        return watched;
    }

    public String getLength() {
        return length;
    }
}
