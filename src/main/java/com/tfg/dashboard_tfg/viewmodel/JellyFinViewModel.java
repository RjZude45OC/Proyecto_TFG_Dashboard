package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.LogEntry;
import com.tfg.dashboard_tfg.model.NetworkData;
import com.tfg.dashboard_tfg.model.StreamSession;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.io.*;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class JellyFinViewModel implements Initializable {
    @FXML
    private TextField serverUrlField;
    @FXML
    private PasswordField apiKeyField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button connectButton;
    @FXML
    private Label serverStatusLabel;
    @FXML
    private ToggleButton autoRefreshToggle;
    @FXML
    private ProgressBar cpuUsageBar;
    @FXML
    private ProgressBar memoryUsageBar;
    @FXML
    private ProgressBar storageUsageBar;
    @FXML
    private Label versionLabel;
    @FXML
    private Label uptimeLabel;
    @FXML
    private Label cpuUsageLabel;
    @FXML
    public Label memoryUsageLabel;
    @FXML
    public Label storageUsageLabel;
    @FXML
    public Label networkUsageLabel;
    @FXML
    private Label moviesCountLabel;
    @FXML
    private Label tvShowsCountLabel;
    @FXML
    private Label episodesCountLabel;
    @FXML
    private Label albumsCountLabel;
    @FXML
    private Label songsCountLabel;
    @FXML
    private Label totalSizeLabel;
    @FXML
    private Label activeStreamsLabel;
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private FlowPane activeStreamsTilesPane;
    @FXML
    private TableView<LogEntry> logTable;
    @FXML
    private TableColumn<LogEntry, String> timeColumn;
    @FXML
    private TableColumn<LogEntry, String> levelColumn;
    @FXML
    private TableColumn<LogEntry, String> sourceColumn;
    @FXML
    private TableColumn<LogEntry, String> messageColumn;
    @FXML
    private ComboBox<String> logLevelFilter;

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty serverUrl = new SimpleStringProperty("");
    private final StringProperty apiKey = new SimpleStringProperty("");
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty serverMonitoringEndpoint = new SimpleStringProperty("");
    private final StringProperty dockerApiEndpoint = new SimpleStringProperty("");
    private final StringProperty autoUpdateInterval = new SimpleStringProperty("");

    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> filteredLogEntries = FXCollections.observableArrayList();
    private Map<String, Long> previousBytesReceived = new HashMap<>();
    private Map<String, Long> previousBytesSent = new HashMap<>();
    private long lastUpdateTimestamp = System.currentTimeMillis();

    private HttpClient httpClient;
    private ExecutorService executorService;
    private Timeline autoRefreshTimeline;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PROPERTIES_FILE = "connection.properties";
    private final Properties appProperties = new Properties();
    private boolean propertiesLoaded = false;
    private JSONObject systeminfo;

    /**
     * Loads application properties from the properties file if not already loaded
     */
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
            if (appProperties.containsKey("jellyfin-apiUrl")) {
                serverUrl.set(appProperties.getProperty("jellyfin-apiUrl"));
            }
            if (appProperties.containsKey("monitoringApi")) {
                serverMonitoringEndpoint.set(appProperties.getProperty("monitoringApi"));
            }
            if (appProperties.containsKey("update-interval")) {
                autoUpdateInterval.set(appProperties.getProperty("update-interval"));
            }
            if (appProperties.containsKey("jellyfin-apiKey")) {
                apiKey.set(appProperties.getProperty("jellyfin-apiKey"));
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

    private void saveConnectionProperties() {
        appProperties.setProperty("jellyfin-apiUrl", serverUrl.get() != null ? serverUrl.get() : "");
        appProperties.setProperty("jellyfin-apiKey", apiKey.get() != null ? apiKey.get() : "");

        if (username.get() != null && !username.get().isEmpty()) {
            appProperties.setProperty("username", username.get());
        }

        if (password.get() != null && !password.get().isEmpty()) {
            appProperties.setProperty("password", password.get());
        }

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        httpClient = HttpClient.newBuilder().build();
        executorService = Executors.newFixedThreadPool(3);
        loadPropertiesIfNeeded();
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(Double.parseDouble(autoUpdateInterval.getValue())), e -> {
            refreshServerStatus();
        }));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        serverStatusLabel.textProperty().bind(
                Bindings.when(connected)
                        .then("Connected")
                        .otherwise("Not Connected")
        );

        serverStatusLabel.styleProperty().bind(
                Bindings.when(connected)
                        .then("-fx-text-fill: green;")
                        .otherwise("-fx-text-fill: red;")
        );

        setupLogTable();
        connectButton.setOnAction(event -> connectToServer());
        setupTextFieldBindings();

        logLevelFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            filterLogs();
        });
        if (serverUrl.get() != null && !serverUrl.get().isEmpty()) {
            addLogEntry("Info", "Initialization", "Attempting connection with saved properties");
            connectToServer();
        }
        refreshServerStatus();
    }

    /**
     * Sets up bindings text fields and properties
     */
    private void setupTextFieldBindings() {
        serverUrlField.textProperty().bindBidirectional(serverUrl);
        apiKeyField.textProperty().bindBidirectional(apiKey);
        usernameField.textProperty().bindBidirectional(username);
        passwordField.textProperty().bindBidirectional(password);

        serverUrlField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                updateProperty("jellyfin-apiUrl", serverUrl.get());
            }
        });

        apiKeyField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                updateProperty("jellyfin-apiKey", apiKey.get());
            }
        });

        usernameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                updateProperty("username", username.get());
            }
        });

        passwordField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                updateProperty("password", password.get());
            }
        });
    }

    private void setupLogTable() {
        timeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTime()));
        levelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLevel()));
        sourceColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSource()));
        messageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMessage()));

        levelColumn.setCellFactory(column -> new TableCell<LogEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "Error":
                            setStyle("-fx-text-fill: red;");
                            break;
                        case "Warning":
                            setStyle("-fx-text-fill: orange;");
                            break;
                        case "Info":
                            setStyle("-fx-text-fill: green;");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });

        logTable.setItems(filteredLogEntries);
    }

    private void connectToServer() {
        connected.set(false);
        loadPropertiesIfNeeded();
        if (serverUrl.get() == null || serverUrl.get().trim().isEmpty()) {
            addLogEntry("Error", "Connection", "Server URL cannot be empty");
            return;
        }

        serverStatusLabel.textProperty().unbind();
        serverStatusLabel.styleProperty().unbind();
        serverStatusLabel.setText("Connecting...");
        serverStatusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request;

                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(serverUrl.get() + "/System/Info"))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .GET()
                            .build();
                } else if (username.get() != null && !username.get().trim().isEmpty() &&
                        password.get() != null && !password.get().trim().isEmpty()) {
                    return "Authentication requires API key in this implementation";
                } else {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(serverUrl.get() + "/System/Info/Public"))
                            .GET()
                            .build();
                }

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return "Failed to connect: HTTP " + response.statusCode();
                }

                JSONObject serverInfo = new JSONObject(response.body());
                String version = serverInfo.optString("Version", "Unknown");
                saveConnectionProperties();

                return "Connected to Jellyfin v" + version;
            } catch (Exception e) {
                return "Connection failed: " + e.getMessage();
            }
        }, executorService).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                if (result.startsWith("Connected")) {
                    connected.set(true);
                    serverStatusLabel.textProperty().bind(
                            Bindings.when(connected)
                                    .then("Connected")
                                    .otherwise("Not Connected")
                    );
                    serverStatusLabel.styleProperty().unbind();
                    serverStatusLabel.setStyle("");

                    addLogEntry("Info", "Connection", result);

                    refreshServerStatus();
                } else {
                    serverStatusLabel.textProperty().unbind();
                    serverStatusLabel.styleProperty().unbind();
                    serverStatusLabel.setText("Failed");
                    serverStatusLabel.setStyle("-fx-text-fill: red;");
                    addLogEntry("Error", "Connection", result);
                }
            });
        }, Platform::runLater);
    }

    @FXML
    private void toggleAutoRefresh() {
        if (autoRefreshToggle.isSelected()) {
            autoRefreshTimeline.play();
            addLogEntry("Info", "System", "Auto-refresh enabled (" + autoUpdateInterval.get() + " second interval)");
        } else {
            autoRefreshTimeline.stop();
            addLogEntry("Info", "System", "Auto-refresh disabled");
        }
    }

    /**
     * Manually refresh server status
     */
    @FXML
    private void refreshServerStatus() {
        if (!connected.get()) {
            return;
        }

        lastUpdateLabel.setText("Last update: " + timeFormat.format(new Date()));

        CompletableFuture<Void> systemInfo = fetchSystemInfo();
        CompletableFuture<Void> libraryStats = fetchLibraryStats();
        CompletableFuture<Void> activeSessions = fetchActiveSessions();
        CompletableFuture<Void> logs = fetchRecentLogs();

        CompletableFuture.allOf(
                systemInfo,
                libraryStats,
                activeSessions,
                logs
        ).thenRun(() -> {
            addLogEntry("Info", "System", "Server status refreshed");
        });
    }

    /**
     * Fetch system information from the server
     */
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
                    String containerName = "jellyfin";
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

    /**
     * Fetch library statistics from the server using Jellyfin API
     */
    private CompletableFuture<Void> fetchLibraryStats() {
        return CompletableFuture.runAsync(() -> {
            try {
                String jellyfinApiUrl = serverUrl.get() + "/";
                String moviesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Movie&Limit=0";
                String seriesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Series&Limit=0";
                String episodesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Episode&Limit=0";
                String albumsUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=AudioAlbum&Limit=0";
                String songsUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Audio&Recursive=true&Limit=0";

                final class ApiFetch {
                    private int fetchCount(String url) throws Exception {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("X-MediaBrowser-Token", apiKey.get())
                                .GET()
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() != 200) {
                            addLogEntry("Error", "Library", "Failed to fetch from " + url + ": HTTP " + response.statusCode() + " - " + response.body());
                            return 0;
                        }

                        JSONObject jsonResponse = new JSONObject(response.body());
                        JSONArray items = jsonResponse.optJSONArray("Items");
                        return (items != null) ? items.length() : 0;
                    }
                }
                ApiFetch apiFetch = new ApiFetch();
                int moviesCount = apiFetch.fetchCount(moviesUrl);
                int tvShowsCount = apiFetch.fetchCount(seriesUrl);
                int episodesCount = apiFetch.fetchCount(episodesUrl);
                int albumsCount = apiFetch.fetchCount(albumsUrl);
                int songsCount = apiFetch.fetchCount(songsUrl);

                Random random = new Random();
                double totalSize = 500 + random.nextInt(1500);

                Platform.runLater(() -> {
                    moviesCountLabel.setText(String.valueOf(moviesCount));
                    tvShowsCountLabel.setText(String.valueOf(tvShowsCount));
                    episodesCountLabel.setText(String.valueOf(episodesCount));
                    albumsCountLabel.setText(String.valueOf(albumsCount));
                    songsCountLabel.setText(String.valueOf(songsCount));
                    totalSizeLabel.setText(String.format("%.1f GB", totalSize));
                });
            } catch (Exception e) {
                addLogEntry("Error", "Library", "Error fetching library stats: " + e.getMessage());
                Platform.runLater(() -> {
                    moviesCountLabel.setText("0");
                    tvShowsCountLabel.setText("0");
                    episodesCountLabel.setText("0");
                    albumsCountLabel.setText("0");
                    songsCountLabel.setText("0");
                    totalSizeLabel.setText("0 GB");
                });
            }
        }, executorService);
    }


    /**
     * Filter logs based on selected level
     */
    private void filterLogs() {
        String selectedLevel = logLevelFilter.getSelectionModel().getSelectedItem();
        filteredLogEntries.clear();

        for (LogEntry entry : logEntries) {
            if ("All".equals(selectedLevel) || selectedLevel.equals(entry.getLevel())) {
                filteredLogEntries.add(entry);
            }
        }
    }

    /**
     * Clear all logs from the view
     */
    @FXML
    private void clearLogs() {
        logEntries.clear();
        filteredLogEntries.clear();
        addLogEntry("Info", "System", "Logs cleared");
    }

    /**
     * Add a new log entry programmatically
     */
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

    private CompletableFuture<Void> fetchRecentLogs() {
        return CompletableFuture.runAsync(() -> {
            try {

                List<LogEntry> newLogs = new ArrayList<>();
                String[] sources = {"System", "Playback", "Auth", "Transcoder", "Scheduler", "IO"};
                String[] infoMessages = {
                        "Library scan completed",
                        "User logged in",
                        "Startup complete",
                        "Metadata updated",
                        "Scheduled task completed"
                };
                String[] warningMessages = {
                        "High CPU usage",
                        "Failed to fetch metadata",
                        "Network latency detected",
                };
                String[] errorMessages = {
                        "IO error when reading media file",
                        "Authentication failure",
                        "Transcoding error",
                        "Out of memory"
                };

                LocalDateTime now = LocalDateTime.now();
                String time = now.format(logTimeFormatter);
                String source = "System";

                String level = "";
                String message = "";
                JSONObject cpuData = systeminfo.optJSONObject("cpu");
                JSONObject memoryData = systeminfo.optJSONObject("memory");
                JSONArray disksData = systeminfo.optJSONArray("disks");
                JSONObject networkData = systeminfo.optJSONObject("network");
                double systemCpuLoad = cpuData.getDouble("systemCpuLoad");
                if (systemCpuLoad >= 80) {
                    level = "Warning";
                    message = warningMessages[0];
                }
                if (systemCpuLoad >= 80) {
                    level = "Warning";
                    message = warningMessages[0];
                }
//                    if (levelRandom < 70) {
//                        level = "Info";
//                        message = infoMessages[random.nextInt(infoMessages.length)];
//                    } else if (levelRandom < 95) {
//                        level = "Warning";
//                        message = warningMessages[random.nextInt(warningMessages.length)];
//                    } else {
//                        level = "Error";
//                        message = errorMessages[random.nextInt(errorMessages.length)];
//                    }

                LogEntry entry = new LogEntry(time, level, source, message);
                newLogs.add(entry);

                Platform.runLater(() -> {
                    logEntries.addAll(0, newLogs);

                    while (logEntries.size() > 100) {
                        logEntries.remove(logEntries.size() - 1);
                    }

                    filterLogs();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addLogEntry("Error", "Logs", "Failed to fetch logs: " + e.getMessage());
                    System.out.println(e.getMessage());
                });
            }
        }, executorService);
    }

    private VBox createStreamTile(StreamSession session) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(10));
        tile.setMinWidth(280);
        tile.setMaxWidth(350);
        tile.setPrefWidth(280);
        tile.getStyleClass().add("stream-tile");

        HBox statusBar = new HBox(5);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Circle statusIndicator = new Circle(6);
        statusIndicator.getStyleClass().add(session.isPlaying() ? "status-active" : "status-idle");

        Label statusLabel = new Label(session.isPlaying() ? "Active" : "Idle");
        statusLabel.getStyleClass().add("status-text");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label lastActiveLabel = new Label("Last active: " + formatDateTime(session.getLastActivity()));
        lastActiveLabel.getStyleClass().add("last-active");

        statusBar.getChildren().addAll(statusIndicator, statusLabel, topSpacer, lastActiveLabel);

        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);

        Circle userAvatar = new Circle(15);
        userAvatar.setFill(Color.DARKGRAY);
        Text userInitial = new Text(session.getUsername().substring(0, 1).toUpperCase());
        userInitial.setFill(Color.WHITE);
        userInitial.setFont(Font.font("System", FontWeight.BOLD, 16));
        StackPane avatarPane = new StackPane(userAvatar, userInitial);

        VBox userInfo = new VBox(2);
        Label userLabel = new Label(session.getUsername());
        userLabel.getStyleClass().add("tile-username");

        Label deviceInfoLabel = new Label(session.getClient() + " • " + session.getDevice());
        deviceInfoLabel.getStyleClass().add("tile-device");

        userInfo.getChildren().addAll(userLabel, deviceInfoLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label remoteLabel = null;
        if (!"Local".equals(session.getRemoteAddress())) {
            remoteLabel = new Label("REMOTE");
            remoteLabel.getStyleClass().add("remote-tag");
        }

        header.getChildren().addAll(avatarPane, userInfo, headerSpacer);
        if (remoteLabel != null) {
            header.getChildren().add(remoteLabel);
        }

        VBox mediaInfoBox = new VBox(6);

        HBox titleBox = new HBox(8);
        Label titleLabel = new Label(session.getTitle());
        titleLabel.getStyleClass().add("tile-title");
        titleLabel.setWrapText(true);

        Label yearLabel = null;
        if (session.getYear() > 0) {
            yearLabel = new Label("(" + session.getYear() + ")");
            yearLabel.getStyleClass().add("year-label");
        }

        titleBox.getChildren().add(titleLabel);
        if (yearLabel != null) {
            titleBox.getChildren().add(yearLabel);
        }

        FlowPane techSpecs = new FlowPane(8, 8);
        techSpecs.setPrefWrapLength(280);

        if (!"Idle".equals(session.getMediaType())) {
            Label mediaTypeTag = createTag(session.getMediaType());
            Label resolutionTag = createTag(session.getResolution());
            Label codecTag = createTag(session.getCodec());
            Label bitrateTag = createTag(session.getBitrate() + " Mbps");

            techSpecs.getChildren().addAll(mediaTypeTag, resolutionTag, codecTag, bitrateTag);

            if ("HDR".equals(session.getVideoRange()) || "HDR10".equals(session.getVideoRange()) ||
                    "DolbyVision".equals(session.getVideoRange())) {
                Label hdrTag = createTag(session.getVideoRange());
                hdrTag.getStyleClass().add("hdr-tag");
                techSpecs.getChildren().add(hdrTag);
            }

            if (session.getFrameRate() > 0) {
                Label fpsTag = createTag(Math.round(session.getFrameRate()) + " fps");
                techSpecs.getChildren().add(fpsTag);
            }
        } else {
            Label idleTag = createTag("No media playing");
            techSpecs.getChildren().add(idleTag);
        }

        mediaInfoBox.getChildren().addAll(titleBox, techSpecs);

        VBox progressBox = new VBox(4);
        progressBox.setVisible(!"Idle".equals(session.getMediaType()));
        progressBox.setManaged(!"Idle".equals(session.getMediaType()));

        HBox progressLabels = new HBox();
        progressLabels.setAlignment(Pos.CENTER_LEFT);

        Label elapsedLabel = new Label(formatTime(session.getProgress()));
        elapsedLabel.getStyleClass().add("time-label");

        Region progressSpacer = new Region();
        HBox.setHgrow(progressSpacer, Priority.ALWAYS);

        Label durationLabel = new Label(formatTime(session.getRuntime()));
        durationLabel.getStyleClass().add("time-label");
        progressLabels.getChildren().addAll(elapsedLabel, progressSpacer, durationLabel);

        double progressPercentage = session.getRuntime() > 0 ?
                (double) session.getProgress() / session.getRuntime() : 0;
        ProgressBar progressBar = new ProgressBar(progressPercentage);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("media-progress");
        progressBox.getChildren().addAll(progressBar, progressLabels);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);

        Button playPauseButton = new Button(session.isPlaying() ? "Pause" : "Play");
        playPauseButton.getStyleClass().add("table-button");

        Button stopButton = new Button("Stop");
        stopButton.getStyleClass().add("table-button");

        Button infoButton = new Button("Details");
        infoButton.getStyleClass().add("table-button");

        controls.getChildren().addAll(playPauseButton, stopButton, infoButton);

        Label pathLabel = new Label(session.getFilePath());
        pathLabel.getStyleClass().add("path-label");
        pathLabel.setWrapText(true);
        pathLabel.setVisible(!"Idle".equals(session.getMediaType()));
        pathLabel.setManaged(!"Idle".equals(session.getMediaType()));

        tile.getChildren().addAll(statusBar, header, mediaInfoBox, progressBox, controls);

        if (!"Idle".equals(session.getMediaType())) {
            tile.getChildren().add(pathLabel);
        }

        return tile;
    }

    private Label createTag(String text) {
        Label tag = new Label(text);
        tag.getStyleClass().add("tech-tag");
        return tag;
    }

    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    private String formatDateTime(String isoDateTime) {
        if (isoDateTime == null || "Unknown".equals(isoDateTime)) {
            return "Unknown";
        }

        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ISO_DATE_TIME;
            LocalDateTime dateTime = LocalDateTime.parse(isoDateTime, inputFormatter);

            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm");
            return dateTime.format(outputFormatter);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private CompletableFuture<Void> fetchActiveSessions() {
        return CompletableFuture.runAsync(() -> {
            try {
                String activeSessionsUrl = serverUrl.get() + "/Sessions";
                HttpRequest request;

                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {

                    request = HttpRequest.newBuilder()
                            .uri(URI.create(activeSessionsUrl))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .GET()
                            .build();
                } else {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(activeSessionsUrl))
                            .GET()
                            .build();
                }
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    addLogEntry("Error", "Active Sessions", "Failed to fetch active sessions: HTTP " + response.statusCode() + " - " + response.body());
                    Platform.runLater(() -> {
                        activeStreamsLabel.setText("0");
                        activeStreamsTilesPane.getChildren().clear();
                        Label errorLabel = new Label("Failed to load active sessions.");
                        errorLabel.getStyleClass().add("error-text");
                        activeStreamsTilesPane.getChildren().add(errorLabel);

                    });
                    return;
                }
                JSONArray sessionsJson = new JSONArray(response.body());
                List<StreamSession> sessions = new ArrayList<>();

                for (int i = 0; i < sessionsJson.length(); i++) {
                    JSONObject sessionJson = sessionsJson.getJSONObject(i);
                    StreamSession session = new StreamSession();
                    session.setUsername(sessionJson.optString("UserName", "Unknown User"));
                    session.setDevice(sessionJson.optString("DeviceName", "Unknown Device"));
                    session.setClient(sessionJson.optString("Client", "Unknown Client"));

                    session.setRemoteAddress(sessionJson.optString("RemoteEndPoint", "Local"));

                    JSONObject playState = sessionJson.optJSONObject("PlayState");
                    if (playState != null) {
                        session.setPlaying(!playState.optBoolean("IsPaused", false));
                        session.setMuted(playState.optBoolean("IsMuted", false));
                        session.setRepeatMode(playState.optString("RepeatMode", "None"));
                        session.setCanSeek(playState.optBoolean("CanSeek", false));
                    }

                    JSONArray nowPlayingQueueItems = sessionJson.optJSONArray("NowPlayingQueueFullItems");
                    if (nowPlayingQueueItems != null && !nowPlayingQueueItems.isEmpty()) {
                        JSONObject mediaItem = nowPlayingQueueItems.getJSONObject(0);

                        session.setTitle(mediaItem.optString("Name", "Unknown Title"));
                        session.setMediaType(mediaItem.optString("MediaType", "Unknown"));
                        session.setYear(mediaItem.optInt("ProductionYear", 0));
                        session.setOverview(mediaItem.optString("Overview", ""));

                        long runtimeTicks = mediaItem.optLong("RunTimeTicks", 0);
                        session.setRuntime(runtimeTicks > 0 ? (int) (runtimeTicks / 10000000) : 0);


                        JSONObject userData = mediaItem.optJSONObject("UserData");
                        if (userData != null && userData.has("PlaybackPositionTicks")) {
                            long positionTicks = userData.optLong("PlaybackPositionTicks", 0);
                            session.setProgress((int) (positionTicks / 10000000));
                        } else {
                            playState = sessionJson.optJSONObject("PlayState");
                            if (playState != null) {
                                if (playState.has("PositionTicks")) {
                                    long positionTicks = playState.optLong("PositionTicks", 0);
                                    session.setProgress((int) (positionTicks / 10000000));
                                } else {
                                    session.setTitle("Idle");
                                    session.setMediaType("Idle");
                                    session.setProgress(0);
                                    session.setBitrate(0);
                                    session.setResolution("None");
                                    session.setPlaying(false);
                                    session.setYear(0);
                                    session.setProgress(0);
                                }
                            } else {
                                session.setProgress(0);
                            }
                        }

                        JSONArray mediaStreams = mediaItem.optJSONArray("MediaStreams");
                        if (mediaStreams != null) {
                            for (int j = 0; j < mediaStreams.length(); j++) {
                                JSONObject stream = mediaStreams.getJSONObject(j);
                                String streamType = stream.optString("Type", "");

                                if ("Video".equals(streamType)) {
                                    int width = stream.optInt("Width", 0);
                                    int height = stream.optInt("Height", 0);
                                    session.setResolution(width > 0 && height > 0 ? width + "x" + height : "Unknown");

                                    session.setCodec(stream.optString("Codec", "Unknown"));
                                    session.setBitrate(Math.round(stream.optInt("BitRate", 0) / 1000000.0f));
                                    session.setFrameRate(stream.optDouble("RealFrameRate", 0));
                                    session.setVideoRange(stream.optString("VideoRange", "Unknown"));
                                    break;
                                }
                            }
                        }

                        session.setFilePath(mediaItem.optString("Path", ""));
                        session.setSourceType(mediaItem.optString("LocationType", "Unknown"));
                    } else {
                        session.setTitle("Idle");
                        session.setMediaType("Idle");
                        session.setProgress(0);
                        session.setBitrate(0);
                        session.setResolution("None");
                        session.setPlaying(false);
                    }
                    session.setLastActivity(sessionJson.optString("LastActivityDate", "Unknown"));
                    session.setDeviceId(sessionJson.optString("DeviceId", "Unknown"));

                    sessions.add(session);
                }

                Platform.runLater(() -> {
                    activeStreamsLabel.setText(String.valueOf(sessions.size()));

                    activeStreamsTilesPane.getChildren().clear();

                    for (StreamSession session : sessions) {
                        activeStreamsTilesPane.getChildren().add(createStreamTile(session));
                    }

                    if (sessions.isEmpty()) {
                        Label placeholder = new Label("No active streams");
                        placeholder.getStyleClass().add("placeholder-text");
                        activeStreamsTilesPane.getChildren().add(placeholder);
                    }
                });
            } catch (Exception e) {
                addLogEntry("Error", "Active Sessions", "Error fetching active sessions: " + e.getMessage());
                Platform.runLater(() -> {
                    activeStreamsLabel.setText("0");
                    activeStreamsTilesPane.getChildren().clear();
                    Label errorLabel = new Label("Error loading active sessions.");
                    errorLabel.getStyleClass().add("error-text");
                    activeStreamsTilesPane.getChildren().add(errorLabel);

                });
            }
        });
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
}
