package com.tfg.dashboard_tfg.model;

public class LogEntry {
    private final String time;
    private final String level;
    private final String source;
    private final String message;

    public LogEntry(String time, String level, String source, String message) {
        this.time = time;
        this.level = level;
        this.source = source;
        this.message = message;
    }

    public String getTime() {
        return time;
    }

    public String getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }
}
