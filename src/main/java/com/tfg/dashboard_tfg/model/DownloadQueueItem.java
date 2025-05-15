package com.tfg.dashboard_tfg.model;

public class DownloadQueueItem {
    private final int id;
    private final String title;
    private final String size;
    private String status;
    private final double progress;
    private final String speed;
    private final String eta;

    public DownloadQueueItem(int id, String title, String size, String status, double progress, String speed, String eta) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.status = status;
        this.progress = progress;
        this.speed = speed;
        this.eta = eta;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSize() {
        return size;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getProgress() {
        return progress;
    }

    public String getSpeed() {
        return speed;
    }

    public String getEta() {
        return eta;
    }
}
