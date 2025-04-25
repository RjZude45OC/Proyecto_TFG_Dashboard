package com.tfg.dashboard_tfg.model;

import javafx.beans.property.*;

import java.util.List;

public class IndexerItem {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty();
    private final IntegerProperty priority = new SimpleIntegerProperty();
    private final List<Integer> tagIds;
    private final String settings;

    public IndexerItem(int id, String name, String type, boolean enabled, int priority, List<Integer> tagIds, String settings) {
        this.id.set(id);
        this.name.set(name);
        this.type.set(type);
        this.enabled.set(enabled);
        this.priority.set(priority);
        this.tagIds = tagIds;
        this.settings = settings;
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

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public int getPriority() {
        return priority.get();
    }

    public void setPriority(int priority) {
        this.priority.set(priority);
    }

    public IntegerProperty priorityProperty() {
        return priority;
    }

    public List<Integer> getTagIds() {
        return tagIds;
    }

    public String getSettings() {
        return settings;
    }
}