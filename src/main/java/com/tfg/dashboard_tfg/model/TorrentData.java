package com.tfg.dashboard_tfg.model;

public class TorrentData {
    private String name;
    private String hash;
    private String size;
    private double progress;
    private String status;
    private String downloadSpeed;
    private String uploadSpeed;
    private String eta;
    private double ratio;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(String downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public String getUploadSpeed() {
        return uploadSpeed;
    }

    public void setUploadSpeed(String uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }
}