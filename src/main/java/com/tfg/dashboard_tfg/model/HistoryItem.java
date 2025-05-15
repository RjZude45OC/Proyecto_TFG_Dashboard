package com.tfg.dashboard_tfg.model;

public class HistoryItem {
    private final String date;
    private final String series;
    private final String episode;
    private final String quality;
    private final String status;
    private final String source;

    public HistoryItem(String date, String series, String episode, String quality, String status, String source) {
        this.date = date;
        this.series = series;
        this.episode = episode;
        this.quality = quality;
        this.status = status;
        this.source = source;
    }

    public String getDate() {
        return date;
    }

    public String getSeries() {
        return series;
    }

    public String getEpisode() {
        return episode;
    }

    public String getQuality() {
        return quality;
    }

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }
}
