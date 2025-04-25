package com.tfg.dashboard_tfg.model;

import javafx.beans.property.*;

public class TagItem {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty label = new SimpleStringProperty();

    public TagItem(int id, String label) {
        this.id.set(id);
        this.label.set(label);
    }

    // Getters and Setters
    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getLabel() {
        return label.get();
    }

    public void setLabel(String label) {
        this.label.set(label);
    }

    public StringProperty labelProperty() {
        return label;
    }
}