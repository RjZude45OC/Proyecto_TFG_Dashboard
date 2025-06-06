package com.tfg.dashboard_tfg.model;

import javafx.beans.property.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class IndexerItem {
    private final int id;
    private String name;
    private final String type;
    private boolean enabled;
    private int priority;
    private final List<Integer> tagIds;
    private final String jsonConfig;
    private String language;
    private String privacy;
    private List<Integer> categories;

    public IndexerItem(int id, String name, String type, boolean enabled, int priority,
                       List<Integer> tagIds, String jsonConfig) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.priority = priority;
        this.tagIds = tagIds;
        this.jsonConfig = jsonConfig;

        try {
            JSONObject config = new JSONObject(jsonConfig);
            this.language = config.optString("language", "English");
            this.privacy = config.optString("privacy", "Public");

            this.categories = new ArrayList<>();
            if (config.has("categories")) {
                JSONArray categoriesArray = config.getJSONArray("categories");
                for (int i = 0; i < categoriesArray.length(); i++) {
                    this.categories.add(categoriesArray.getInt(i));
                }
            }
        } catch (Exception e) {
            this.language = "English";
            this.privacy = "Public";
            this.categories = new ArrayList<>();
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public List<Integer> getTagIds() {
        return tagIds;
    }

    public String getJsonConfig() {
        return jsonConfig;
    }

    public String getLanguage() {
        return language;
    }

    public String getPrivacy() {
        return privacy;
    }

    public List<Integer> getCategories() {
        return categories;
    }

    public void setName(String name) { this.name = name; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setPriority(int priority) {  this.priority = priority; }
}