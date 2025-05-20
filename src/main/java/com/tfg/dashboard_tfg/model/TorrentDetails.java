package com.tfg.dashboard_tfg.model;

public class TorrentDetails {
    private String hash;
    private String savePath;
    private String creationDate;
    private String addedOn;
    private String lastActivity;
    private long downloadLimit;
    private long uploadLimit;
    private String timeActive;
    private int connections;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(String addedOn) {
        this.addedOn = addedOn;
    }

    public String getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(String lastActivity) {
        this.lastActivity = lastActivity;
    }

    public long getDownloadLimit() {
        return downloadLimit;
    }

    public void setDownloadLimit(long downloadLimit) {
        this.downloadLimit = downloadLimit;
    }

    public long getUploadLimit() {
        return uploadLimit;
    }

    public void setUploadLimit(long uploadLimit) {
        this.uploadLimit = uploadLimit;
    }

    public String getTimeActive() {
        return timeActive;
    }

    public void setTimeActive(String timeActive) {
        this.timeActive = timeActive;
    }

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }
}
