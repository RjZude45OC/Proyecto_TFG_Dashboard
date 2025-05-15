package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.LogEntry;
import com.tfg.dashboard_tfg.model.NetworkData;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SonarrViewModel implements Initializable {
    @FXML
    public Label serverStatusLabel;
    @FXML
    public ToggleButton autoRefreshToggle;
    @FXML
    public Label activeDownloadsLabel;
    @FXML
    public GridPane connectionPane;
    @FXML
    public TextField serverUrlField;
    @FXML
    public PasswordField apiKeyField;
    @FXML
    public Button connectButton;
    @FXML
    public ProgressBar cpuUsageBar;
    @FXML
    public ProgressBar memoryUsageBar;
    @FXML
    public ProgressBar storageUsageBar;
    @FXML
    public Label networkUsageLabel;
    @FXML
    public Label versionLabel;
    @FXML
    public Label uptimeLabel;
    @FXML
    public Label cpuUsageLabel;
    @FXML
    public Label memoryUsageLabel;
    @FXML
    public Label storageUsageLabel;
    @FXML
    public Label seriesCountLabel;
    @FXML
    public Label episodesCountLabel;
    @FXML
    public Label totalSizeLabel;
    @FXML
    public TableView downloadQueueTable;
    @FXML
    public TableColumn titleColumn;
    @FXML
    public Label lastUpdateLabel;
    @FXML
    public TableColumn messageColumn;
    @FXML
    public TableColumn levelColumn;
    @FXML
    public TableView logTable;
    @FXML
    public TableColumn timeColumn;
    @FXML
    public TableColumn componentColumn;
    @FXML
    public ComboBox logLevelFilter;
    public TableColumn sizeColumn;
    @FXML
    public TableColumn statusColumn;
    @FXML
    public TableColumn progressColumn;
    @FXML
    public TableColumn speedColumn;
    @FXML
    public TableView historyTable;
    @FXML
    public TableColumn historyDateColumn;
    @FXML
    public TableColumn historySeriesColumn;
    @FXML
    public TableColumn historyEpisodeColumn;
    @FXML
    public TableColumn historyQualityColumn;
    @FXML
    public TableColumn historyStatusColumn;
    @FXML
    public TableColumn historySourceColumn;
    @FXML
    public ComboBox historyFilterCombo;
    @FXML
    public TableColumn actionsColumn;
    @FXML
    public TableColumn etaColumn;

    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty serverUrl = new SimpleStringProperty("");
    private final StringProperty apiKey = new SimpleStringProperty("");
    private final StringProperty serverMonitoringEndpoint = new SimpleStringProperty("");
    private final StringProperty dockerApiEndpoint = new SimpleStringProperty("");
    private final StringProperty autoUpdateInterval = new SimpleStringProperty("");
    private long lastUpdateTimestamp = System.currentTimeMillis();
    private Map<String, Long> previousBytesReceived = new HashMap<>();
    private Map<String, Long> previousBytesSent = new HashMap<>();
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private static final String PROPERTIES_FILE = "connection.properties";
    private final Properties appProperties = new Properties();
    private boolean propertiesLoaded = false;
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> filteredLogEntries = FXCollections.observableArrayList();
    private HttpClient httpClient;
    private ExecutorService executorService;
    private Timeline autoRefreshTimeline;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private JSONObject systeminfo;

    private void loadPropertiesIfNeeded() {
        if (propertiesLoaded) {
            return;
        }
        File propertiesFile = new File(PROPERTIES_FILE);

        try {
            if (!propertiesFile.exists()) {
                propertiesFile.createNewFile();
                addLogEntry("Info", "Properties", "Created new properties file");
            }
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                appProperties.load(in);
                addLogEntry("Info", "Properties", "Loaded properties from file");
            }
            if (appProperties.containsKey("sonarr-apiUrl")) {
                serverUrl.set(appProperties.getProperty("sonarr-apiUrl"));
            }
            if (appProperties.containsKey("monitoringApi")) {
                serverMonitoringEndpoint.set(appProperties.getProperty("monitoringApi"));
            }
            if (appProperties.containsKey("update-interval")) {
                autoUpdateInterval.set(appProperties.getProperty("update-interval"));
            }
            if (appProperties.containsKey("sonarr-apiKey")) {
                apiKey.set(appProperties.getProperty("sonarr-apiKey"));
            }
            if (appProperties.containsKey("dockerApi")) {
                dockerApiEndpoint.set("http://" + appProperties.getProperty("dockerApi") + ":2375");
            }
            if (appProperties.containsKey("username")) {
                username.set(appProperties.getProperty("username"));
            }
            if (appProperties.containsKey("password")) {
                password.set(appProperties.getProperty("password"));
            }
            propertiesLoaded = true;
        } catch (IOException e) {
            addLogEntry("Error", "Properties", "Failed to load properties: " + e.getMessage());
        }
    }

    /**
     * Saves the current connection properties to the properties file
     */
    private void saveConnectionProperties() {
        appProperties.setProperty("sonarr-apiUrl", serverUrl.get() != null ? serverUrl.get() : "");
        appProperties.setProperty("sonarr-apiKey", apiKey.get() != null ? apiKey.get() : "");

        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(out, "Updated by user");
            addLogEntry("Info", "Properties", "Saved connection properties to file");
        } catch (IOException e) {
            addLogEntry("Error", "Properties", "Failed to save properties: " + e.getMessage());
        }
    }

    /**
     * Updates a specific property and saves the changes
     */
    public void updateProperty(String key, String value) {
        loadPropertiesIfNeeded();
        appProperties.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(out, "Updated by user");
            addLogEntry("Info", "Properties", "Updated property: " + key);
        } catch (IOException e) {
            addLogEntry("Error", "Properties", "Failed to save property: " + e.getMessage());
        }
    }

    private void connectToServer() {
        connected.set(false);
        loadPropertiesIfNeeded();

        String url = serverUrl.get() != null ? serverUrl.get().trim() : "";
        String key = apiKey.get() != null ? apiKey.get().trim() : "";

        if (url.isEmpty()) {
            addLogEntry("Error", "Connection", "Server URL cannot be empty");
            return;
        }

        if (key.isEmpty()) {
            addLogEntry("Error", "Connection", "API key is required.");
            serverStatusLabel.setText("Failed");
            serverStatusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        serverStatusLabel.textProperty().unbind();
        serverStatusLabel.styleProperty().unbind();
        serverStatusLabel.setText("Connecting...");
        serverStatusLabel.setStyle("-fx-text-fill: orange;");
        System.out.println("DEBUG: Starting async request...");
        CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("testtest");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/v3/system/status"))
                        .timeout(Duration.ofSeconds(5))
                        .header("X-Api-Key", key)
                        .GET()
                        .build();
                System.out.println("testtest2");
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("testtest3");
                System.out.println(response.statusCode());
                if (response.statusCode() != 200) {
                    return "Failed to connect: HTTP " + response.statusCode() + " - " + response.body();
                }
                System.out.println("testtest4");
                JSONObject serverInfo = new JSONObject(response.body());
                String version = serverInfo.optString("version", "Unknown");
                saveConnectionProperties();

                return "Connected to Sonarr v" + version;
            } catch (Exception e) {
                return "Connection failed: " + e.getMessage();
            }
        }, executorService).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                serverStatusLabel.textProperty().unbind();
                serverStatusLabel.styleProperty().unbind();

                if (result.startsWith("Connected")) {
                    connected.set(true);
                    serverStatusLabel.textProperty().bind(
                            Bindings.when(connected)
                                    .then("Connected")
                                    .otherwise("Not Connected")
                    );
                    serverStatusLabel.setStyle("");
                    addLogEntry("Info", "Connection", result);
                    refreshServerStatus();
                } else {
                    serverStatusLabel.setText("Failed");
                    serverStatusLabel.setStyle("-fx-text-fill: red;");
                    addLogEntry("Error", "Connection", result);
                }
            });
        }, Platform::runLater);
    }


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        CompletableFuture<Void> systemInfo = fetchSystemInfo();
        executorService = Executors.newFixedThreadPool(3);
        CompletableFuture.allOf(
                systemInfo
//                libraryStats,
//                activeSessions,
//                logs
        ).thenRun(() -> {
            addLogEntry("Info", "System", "Server status refreshed");
        });
        loadPropertiesIfNeeded();
        if (serverUrl.get() != null && !serverUrl.get().isEmpty()) {
            addLogEntry("Info", "Initialization", "Attempting connection with saved properties");
            connectToServer();
        }
    }

    public void refreshHistory(ActionEvent actionEvent) {
    }

    public void refreshQueue(ActionEvent actionEvent) {
    }

    public void clearCompletedDownloads(ActionEvent actionEvent) {
    }

    public void clearLogs(ActionEvent actionEvent) {
    }

    @FXML
    private void refreshServerStatus() {
        if (!connected.get()) {
            return;
        }

        lastUpdateLabel.setText("Last update: " + timeFormat.format(new Date()));

        CompletableFuture<Void> systemInfo = fetchSystemInfo();
//        CompletableFuture<Void> libraryStats = fetchLibraryStats();
//        CompletableFuture<Void> activeSessions = fetchActiveSessions();
//        CompletableFuture<Void> logs = fetchRecentLogs();

        CompletableFuture.allOf(
                systemInfo
//                libraryStats,
//                activeSessions,
//                logs
        ).thenRun(() -> {
            addLogEntry("Info", "System", "Server status refreshed");
        });
    }

    public void toggleAutoRefresh(ActionEvent actionEvent) {
    }

    private CompletableFuture<Void> fetchSystemInfo() {
        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverMonitoringEndpoint.get()))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    addLogEntry("Error", "System", "Failed to fetch system info: HTTP " + response.statusCode() + " - " + response.body());
                    Platform.runLater(() -> {
                        versionLabel.setText("N/A");
                        uptimeLabel.setText("N/A");
                        cpuUsageBar.setProgress(0);
                        memoryUsageBar.setProgress(0);
                        storageUsageBar.setProgress(0);
                    });
                    return;
                }

                JSONObject systemInfo = new JSONObject(response.body());
                JSONObject cpuData = systemInfo.optJSONObject("cpu");
                JSONObject memoryData = systemInfo.optJSONObject("memory");
                JSONArray disksData = systemInfo.optJSONArray("disks");
                JSONObject networkData = systemInfo.optJSONObject("network");
                systeminfo = systemInfo;
                double cpuUsage = cpuData != null ? processCpuUsage(cpuData) : 0;
//                long totalMemory = memoryData != null ? memoryData.optLong("totalMemory", 0) : 0;
//                long usedMemory = memoryData != null ? memoryData.optLong("usedMemory", 0) : 0;
                double memoryUsagePercentage = memoryData != null ? memoryData.optDouble("memoryUsagePercentage", 0) / 100.0 : 0;
                double totalSpace = 0;
                double usedSpace = 0;
                double storageUsagePercentage;

                if (disksData != null && !disksData.isEmpty()) {
                    for (int i = 0; i < disksData.length(); i++) {
                        JSONObject disk = disksData.getJSONObject(i);
                        totalSpace += disk.optDouble("totalSpace", 0);
                        usedSpace += disk.optDouble("usedSpace", 0);
                    }
                    if (totalSpace > 0)
                        storageUsagePercentage = usedSpace / totalSpace;
                    else {
                        storageUsagePercentage = 0;
                    }
                } else {
                    storageUsagePercentage = 0;
                }

                String version = "N/A";
                String uptime = "N/A";
                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {
                    try {
                        HttpRequest jellyfinSystemRequest = HttpRequest.newBuilder()
                                .uri(URI.create(serverUrl.get() + "/System/Info"))
                                .header("X-MediaBrowser-Token", apiKey.get())
                                .GET()
                                .build();
                        HttpResponse<String> jellyfinSystemResponse = httpClient.send(jellyfinSystemRequest, HttpResponse.BodyHandlers.ofString());
                        if (jellyfinSystemResponse.statusCode() == 200) {
                            JSONObject jellyfinSystemInfo = new JSONObject(jellyfinSystemResponse.body());
                            version = jellyfinSystemInfo.optString("Version", "N/A");
                        }
                    } catch (Exception ex) {
                        addLogEntry("Warning", "System", "Failed to get version from Jellyfin: " + ex.getMessage());
                    }
                }

                try {
                    String containerName = "sonarr";
                    String dockerHost = dockerApiEndpoint.get();
                    String urlStr = dockerHost + "/containers/" + containerName + "/json";

                    HttpRequest dockerRequest = HttpRequest.newBuilder()
                            .uri(URI.create(urlStr))
                            .GET()
                            .build();
                    HttpResponse<String> dockerResponse = httpClient.send(dockerRequest, HttpResponse.BodyHandlers.ofString());

                    if (dockerResponse.statusCode() != 200) {
                        addLogEntry("Error", "Info", "Failed to fetch from " + urlStr + ": HTTP " + dockerResponse.statusCode() + " - " + response.body());
                    }

                    JSONObject jsonResponse = new JSONObject(dockerResponse.body());
                    String dockerUptime = jsonResponse.getJSONObject("State").getString("StartedAt");

                    if (dockerUptime != null && !dockerUptime.isEmpty()) {
                        java.time.OffsetDateTime startedAt = java.time.OffsetDateTime.parse(dockerUptime);
                        java.time.Duration duration = java.time.Duration.between(startedAt, java.time.OffsetDateTime.now());

                        long days = duration.toDays();
                        long hours = duration.toHoursPart();
                        long minutes = duration.toMinutesPart();
                        uptime = String.format("%d days, %d hours, %d minutes", days, hours, minutes);
                    }

                } catch (Exception e) {
                    addLogEntry("Warning", "System", "Failed to get uptime from Docker API: " + e.getMessage());
                }

                NetworkData data = new NetworkData();
                JSONObject interfaces = networkData.getJSONObject("interfaces");

                long totalBytesReceived = 0;
                long totalBytesSent = 0;

                for (String interfaceName : interfaces.keySet()) {
                    JSONObject networkInterface = interfaces.getJSONObject(interfaceName);
                    totalBytesReceived += networkInterface.getLong("bytesReceived");
                    totalBytesSent += networkInterface.getLong("bytesSent");
                }

                data.currentBytes = totalBytesReceived + totalBytesSent;

                long currentTime = System.currentTimeMillis();
                double timeDiffSeconds = (currentTime - lastUpdateTimestamp) / 1000.0;

                String networkKey = "total";
                double downloadKBps = 0;
                double uploadKBps = 0;

                if (previousBytesReceived.containsKey(networkKey) && previousBytesSent.containsKey(networkKey)) {
                    long receivedDiff = totalBytesReceived - previousBytesReceived.get(networkKey);
                    downloadKBps = receivedDiff / timeDiffSeconds / 1024.0;

                    long sentDiff = totalBytesSent - previousBytesSent.get(networkKey);
                    uploadKBps = sentDiff / timeDiffSeconds / 1024.0;

                    data.kbPerSecond = (receivedDiff + sentDiff) / timeDiffSeconds / 1024.0;
                } else {
                    data.kbPerSecond = 0;
                }

                previousBytesReceived.put(networkKey, totalBytesReceived);
                previousBytesSent.put(networkKey, totalBytesSent);
                lastUpdateTimestamp = currentTime;

                data.description = formatNetworkUsage(downloadKBps, uploadKBps);
                String finalVersion = version;
                String finalUptime = uptime;
                Platform.runLater(() -> {
                    cpuUsageBar.setProgress(cpuUsage);
                    cpuUsageLabel.setText(String.format("%.1f%%", cpuUsage * 100));
                    memoryUsageBar.setProgress(memoryUsagePercentage);
                    memoryUsageLabel.setText(String.format("%.1f%%", memoryUsagePercentage * 100));

                    storageUsageBar.setProgress(storageUsagePercentage);
                    storageUsageLabel.setText(String.format("%.1f%%", storageUsagePercentage * 100));

                    networkUsageLabel.setText(data.description);

                    versionLabel.setText(finalVersion);
                    uptimeLabel.setText(finalUptime);
                });
            } catch (Exception e) {
                addLogEntry("Error", "System", "Error fetching system info: " + e.getMessage());
                Platform.runLater(() -> {
                    versionLabel.setText("N/A");
                    uptimeLabel.setText("N/A");
                    cpuUsageBar.setProgress(0);
                    memoryUsageBar.setProgress(0);
                    storageUsageBar.setProgress(0);
                });
            }
        });
    }

    private double processCpuUsage(JSONObject cpuData) {
        double systemCpuLoad = cpuData.getDouble("systemCpuLoad");
        return systemCpuLoad / 100;
    }

    private String formatNetworkUsage(double downloadKBps, double uploadKBps) {
        String downloadText;
        if (downloadKBps < 1000) {
            downloadText = String.format("%.1f KB/s", downloadKBps);
        } else {
            downloadText = String.format("%.2f MB/s", downloadKBps / 1024.0);
        }
        String uploadText;
        if (uploadKBps < 1000) {
            uploadText = String.format("%.1f KB/s", uploadKBps);
        } else {
            uploadText = String.format("%.2f MB/s", uploadKBps / 1024.0);
        }
        return String.format("Down: %s / Up: %s", downloadText, uploadText);
    }

    private void filterLogs() {
        String selectedLevel = (String) logLevelFilter.getSelectionModel().getSelectedItem();
        filteredLogEntries.clear();

        for (LogEntry entry : logEntries) {
            if ("All".equals(selectedLevel) || selectedLevel.equals(entry.getLevel())) {
                filteredLogEntries.add(entry);
            }
        }
    }

    private void addLogEntry(String level, String source, String message) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(logTimeFormatter);
        LogEntry entry = new LogEntry(time, level, source, message);

        logEntries.add(0, entry);

        while (logEntries.size() > 100) {
            logEntries.remove(logEntries.size() - 1);
        }

        filterLogs();
    }


}
