package com.tfg.dashboard_tfg.model;

public class TransferStats {
    private long downloadSpeed;
    private long uploadSpeed;
    private long sessionDownloaded;
    private long sessionUploaded;
    private long allTimeDownloaded;
    private long allTimeUploaded;
    private double ratio;

    public long getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(long downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public long getUploadSpeed() {
        return uploadSpeed;
    }

    public void setUploadSpeed(long uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public long getSessionDownloaded() {
        return sessionDownloaded;
    }

    public void setSessionDownloaded(long sessionDownloaded) {
        this.sessionDownloaded = sessionDownloaded;
    }

    public long getSessionUploaded() {
        return sessionUploaded;
    }

    public void setSessionUploaded(long sessionUploaded) {
        this.sessionUploaded = sessionUploaded;
    }

    public long getAllTimeDownloaded() {
        return allTimeDownloaded;
    }

    public void setAllTimeDownloaded(long allTimeDownloaded) {
        this.allTimeDownloaded = allTimeDownloaded;
    }

    public long getAllTimeUploaded() {
        return allTimeUploaded;
    }

    public void setAllTimeUploaded(long allTimeUploaded) {
        this.allTimeUploaded = allTimeUploaded;
    }

    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }
}
