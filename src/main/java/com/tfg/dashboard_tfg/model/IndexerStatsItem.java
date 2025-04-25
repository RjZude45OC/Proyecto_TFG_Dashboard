package com.tfg.dashboard_tfg.model;

import javafx.beans.property.*;


public class IndexerStatsItem {
    private final IntegerProperty indexerId = new SimpleIntegerProperty();
    private final StringProperty indexerName = new SimpleStringProperty();
    private final IntegerProperty queryCount = new SimpleIntegerProperty();
    private final IntegerProperty successCount = new SimpleIntegerProperty();
    private final IntegerProperty failureCount = new SimpleIntegerProperty();
    private final DoubleProperty avgResponseTime = new SimpleDoubleProperty();

    public IndexerStatsItem(int indexerId, String indexerName, int queryCount, int successCount, int failureCount, double avgResponseTime) {
        this.indexerId.set(indexerId);
        this.indexerName.set(indexerName);
        this.queryCount.set(queryCount);
        this.successCount.set(successCount);
        this.failureCount.set(failureCount);
        this.avgResponseTime.set(avgResponseTime);
    }

    // Getters and Setters
    public int getIndexerId() {
        return indexerId.get();
    }

    public void setIndexerId(int indexerId) {
        this.indexerId.set(indexerId);
    }

    public IntegerProperty indexerIdProperty() {
        return indexerId;
    }

    public String getIndexerName() {
        return indexerName.get();
    }

    public void setIndexerName(String indexerName) {
        this.indexerName.set(indexerName);
    }

    public StringProperty indexerNameProperty() {
        return indexerName;
    }

    public int getQueryCount() {
        return queryCount.get();
    }

    public void setQueryCount(int queryCount) {
        this.queryCount.set(queryCount);
    }

    public IntegerProperty queryCountProperty() {
        return queryCount;
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public void setSuccessCount(int successCount) {
        this.successCount.set(successCount);
    }

    public IntegerProperty successCountProperty() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public void setFailureCount(int failureCount) {
        this.failureCount.set(failureCount);
    }

    public IntegerProperty failureCountProperty() {
        return failureCount;
    }

    public double getAvgResponseTime() {
        return avgResponseTime.get();
    }

    public void setAvgResponseTime(double avgResponseTime) {
        this.avgResponseTime.set(avgResponseTime);
    }

    public DoubleProperty avgResponseTimeProperty() {
        return avgResponseTime;
    }
}