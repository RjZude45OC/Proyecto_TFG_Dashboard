package com.tfg.dashboard_tfg.model;

public class TrackerData {
    private String url;
    private String status;
    private int tier;
    private int peers;
    private int seeds;
    private int leeches;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getPeers() {
        return peers;
    }

    public void setPeers(int peers) {
        this.peers = peers;
    }

    public int getSeeds() {
        return seeds;
    }

    public void setSeeds(int seeds) {
        this.seeds = seeds;
    }

    public int getLeeches() {
        return leeches;
    }

    public void setLeeches(int leeches) {
        this.leeches = leeches;
    }
}
