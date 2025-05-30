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
import java.net.HttpURLConnection;
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
    private long latency;

    private void loadProperties() {
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
                if (!appProperties.getProperty("monitoringApi").startsWith("http://")) {
                    serverMonitoringEndpoint.set("http://" + appProperties.getProperty("monitoringApi"));
                } else {
                    serverMonitoringEndpoint.set(appProperties.getProperty("monitoringApi"));
                }
            }
            if (appProperties.containsKey("update-interval")) {
                autoUpdateInterval.set(appProperties.getProperty("update-interval"));
            }
            if (appProperties.containsKey("jellyfin-apiKey")) {
                apiKey.set(appProperties.getProperty("jellyfin-apiKey"));
            }
            if (appProperties.containsKey("dockerApi")) {
                if (!appProperties.getProperty("dockerApi").startsWith("http://")) {
                    dockerApiEndpoint.set("http://" + appProperties.getProperty("dockerApi") + ":2375");
                } else {
                    dockerApiEndpoint.set(appProperties.getProperty("dockerApi") + ":2375");
                }
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

    public void updateProperty(String key, String value) {
        loadProperties();
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
        loadProperties();
        String autoUpdateInterval = appProperties.getProperty("update-interval");
        if (autoUpdateInterval == null || autoUpdateInterval.isEmpty()) {
            autoUpdateInterval = "5";
            updateProperty("update-interval", "5");
        }
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(Double.parseDouble(autoUpdateInterval)), e -> {
            refreshServerStatus();
        }));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        addLogEntry("Info", "Scheduler", "Scheduled task completed");
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
        addLogEntry("Info", "System", "Startup complete");
    }

    private void setupTextFieldBindings() {
        serverUrlField.textProperty().bindBidirectional(serverUrl);
        apiKeyField.textProperty().bindBidirectional(apiKey);
        usernameField.textProperty().bindBidirectional(username);
        passwordField.textProperty().bindBidirectional(password);

        serverUrlField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                String url = serverUrl.get().trim();
                if (!url.isEmpty() && !url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                    url = "http://" + url;
                    serverUrl.set(url);
                }
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
        loadProperties();
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
                    serverStatusLabel.setStyle("-fx-text-fill: green;");

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
     * Manually refresh server status
     */
    @FXML
    private void updateActiveSession() {
        if (!connected.get()) {
            return;
        }

        CompletableFuture<Void> activeSessions = fetchActiveSessions();
        CompletableFuture<Void> logs = fetchRecentLogs();

        CompletableFuture.allOf(
                activeSessions,
                logs
        ).thenRun(() -> {
            addLogEntry("Info", "Session", "Session status refreshed");
        });
    }

    private CompletableFuture<Void> fetchSystemInfo() {
        return CompletableFuture.runAsync(() -> {
            try {
                long requestSentTime = System.currentTimeMillis();
                String url = "";
                if (!serverMonitoringEndpoint.get().endsWith("/api/v1/system")) {
                    url = serverMonitoringEndpoint.get() + "/api/v1/system" ;
                }
                else {
                    url = serverMonitoringEndpoint.get();
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
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
                long requestTime = System.currentTimeMillis();
                latency = requestTime - requestSentTime;
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

    private CompletableFuture<Void> fetchLibraryStats() {
        return CompletableFuture.runAsync(() -> {
            try {
                String jellyfinApiUrl = serverUrl.get();
                if (!jellyfinApiUrl.endsWith("/")) {
                    jellyfinApiUrl += "/";
                }

                String moviesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Movie";
                String seriesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Series";
                String episodesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Episode";
                String virtualFolder = jellyfinApiUrl + "Library/VirtualFolders";
                String sizeUrl = jellyfinApiUrl + "Items?Recursive=true&Fields=MediaSources&ParentId=";
                int moviesCount = fetchCount(moviesUrl);
                int tvShowsCount = fetchCount(seriesUrl);
                int episodesCount = fetchCount(episodesUrl);
                Map<String, String> folderIds = fetchLibraryFolderIds(virtualFolder);
                long movieSize = fetchTotalSize(sizeUrl, folderIds.get("Movies"));
                long showSize = fetchTotalSize(sizeUrl, folderIds.get("Shows"));
                long animeSize = fetchTotalSize(sizeUrl, folderIds.get("Anime"));

                Platform.runLater(() -> {
                    moviesCountLabel.setText(String.valueOf(moviesCount));
                    tvShowsCountLabel.setText(String.valueOf(tvShowsCount));
                    episodesCountLabel.setText(String.valueOf(episodesCount));
                    episodesCountLabel.setText(String.valueOf(episodesCount));
                    totalSizeLabel.setText(formatSize(movieSize + showSize + animeSize));
                });

                addLogEntry("Info", "Library", String.format(
                        "Stats updated - Movies: %d, TV Shows: %d, Episodes: %d, Size: %S",
                        moviesCount, tvShowsCount, episodesCount, formatSize(movieSize + showSize + animeSize)));

            } catch (Exception e) {
                addLogEntry("Error", "Library", "Error fetching library stats: " + e.getMessage());
                Platform.runLater(() -> {
                    moviesCountLabel.setText("Error");
                    tvShowsCountLabel.setText("Error");
                    episodesCountLabel.setText("Error");
                    totalSizeLabel.setText("Error");
                });
            }
        }, executorService);
    }

    private void filterLogs() {
        String selectedLevel = logLevelFilter.getSelectionModel().getSelectedItem();
        filteredLogEntries.clear();

        for (LogEntry entry : logEntries) {
            if ("All".equals(selectedLevel) || selectedLevel.equals(entry.getLevel())) {
                filteredLogEntries.add(entry);
            }
        }
    }

    @FXML
    private void clearLogs() {
        logEntries.clear();
        filteredLogEntries.clear();
        addLogEntry("Info", "System", "Logs cleared");
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
                        "Network latency detected"
                };
                String[] errorMessages = {
                        "IO error when reading media file",
                        "Authentication failure",
                        "Transcoding error",
                        "Out of memory"
                };

                if (systeminfo == null) {
                    return;
                }

                JSONObject cpuData = systeminfo.optJSONObject("cpu");
                JSONObject memoryData = systeminfo.optJSONObject("memory");
                JSONArray disksData = systeminfo.optJSONArray("disks");
                JSONObject networkData = systeminfo.optJSONObject("network");

                double systemCpuLoad = cpuData != null ? cpuData.optDouble("systemCpuLoad", 0.0) : 0.0;
                double usedMemoryPercent = memoryData != null
                        ? (memoryData.optDouble("used", 0.0) / memoryData.optDouble("total", 1.0)) * 100
                        : 0.0;

                String time = LocalDateTime.now().format(logTimeFormatter);

                if (systemCpuLoad >= 80) {
                    newLogs.add(new LogEntry(time, "Warning", "System", "High CPU usage"));
                }

                if (usedMemoryPercent >= 90) {
                    newLogs.add(new LogEntry(time, "Error", "System", "Out of memory"));
                }

                if (disksData != null) {
                    for (int i = 0; i < disksData.length(); i++) {
                        JSONObject disk = disksData.optJSONObject(i);
                        if (disk != null) {
                            double used = disk.optDouble("used", 0.0);
                            double total = disk.optDouble("total", 1.0);
                            double usage = (used / total) * 100;
                            if (usage >= 90) {
                                newLogs.add(new LogEntry(time, "Warning", "IO", "Disk usage exceeds 90%"));
                                break;
                            }
                        }
                    }
                }
                if (networkData != null) {
//                    System.out.println("latency: " + latency + "ms");
                    if (latency > 100) {
                        newLogs.add(new LogEntry(time, "Warning", "Network", "Network latency detected"));
                    }
                }

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
                });
            }
        }, executorService);
    }

    private int fetchCount(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MediaBrowser-Token", apiKey.get())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            addLogEntry("Error", "Library", "Failed to fetch from " + url + ": HTTP " + response.statusCode() + " - " + response.body());
            return 0;
        }

        JSONObject jsonResponse = new JSONObject(response.body());
        return jsonResponse.optInt("TotalRecordCount", 0);
    }

    private Map<String, String> fetchLibraryFolderIds(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MediaBrowser-Token", apiKey.get())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, String> nameToId = new HashMap<>();

        if (response.statusCode() == 200) {
            JSONArray folders = new JSONArray(response.body());

            for (int i = 0; i < folders.length(); i++) {
                JSONObject folder = folders.getJSONObject(i);
                String name = folder.optString("Name");
                String id = folder.optString("ItemId");
                nameToId.put(name, id);
            }
        } else {
            addLogEntry("Error", "Library", "Failed to fetch virtual folders: HTTP " + response.statusCode());
        }

        return nameToId;
    }

    private long fetchTotalSize(String sizeUrl, String libIds) throws Exception {
        sizeUrl = sizeUrl + libIds;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sizeUrl))
                .header("X-MediaBrowser-Token", apiKey.get())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            addLogEntry("Warning", "Library", "Failed to fetch size info: HTTP " + response.statusCode() + " or is empty");
            return 0;
        }

        JSONObject jsonResponse = new JSONObject(response.body());
        JSONArray items = jsonResponse.optJSONArray("Items");
        long totalBytes = 0;

        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    JSONArray mediaSources = item.optJSONArray("MediaSources");
                    if (mediaSources != null && !mediaSources.isEmpty()) {
                        JSONObject mediaSource = mediaSources.optJSONObject(0);
                        if (mediaSource != null) {
                            totalBytes += mediaSource.optLong("Size", 0);
                        }
                    }
                }
            }
        }
        return totalBytes;
    }

    private VBox createStreamTile(StreamSession session) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(10));
        tile.setStyle(
                "-fx-background-color: -terminal-background; " +
                "-fx-border-color: transparent; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 5px; " +
                "-fx-background-radius: 10px;");
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
        playPauseButton.setVisible(!"Idle".equals(session.getMediaType()));
        playPauseButton.getStyleClass().add("table-button");
        playPauseButton.setOnAction(event -> {
            String sessionId = session.getId();
            String action = session.isPlaying() ? "Pause" : "Unpause";
            String url = serverUrl.getValue() + "/Sessions/" + sessionId + "/Playing/" + action;
            new Thread(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpClient client = HttpClient.newHttpClient();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 204) {
                        Platform.runLater(this::updateActiveSession);
                    } else {
                        Platform.runLater(() -> {
                            Platform.runLater(() -> System.out.println("Play/Pause error"));
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        System.out.println("error");
                    });
                }
            }).start();
        });
        Button stopButton = new Button("Stop");
        stopButton.setVisible(!"Idle".equals(session.getMediaType()));
        stopButton.getStyleClass().add("table-button");
        stopButton.setOnAction(event -> {
            String sessionId = session.getId();
            String url = serverUrl.getValue() + "/Sessions/" + sessionId + "/Playing/Stop";
            new Thread(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpClient client = HttpClient.newHttpClient();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 204) {
                        Platform.runLater(this::updateActiveSession);
                    } else {
                        Platform.runLater(() -> System.out.println("STOP failed"));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> System.out.println("STOP error"));
                }
            }).start();
        });

        Button infoButton = new Button("Details");
        infoButton.setVisible(!"Idle".equals(session.getMediaType()));
        infoButton.getStyleClass().add("table-button");
        infoButton.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Session Details");
            alert.setHeaderText(session.getTitle());
            alert.setContentText(
                    "User: " + session.getUsername() + "\n" +
                            "Device: " + session.getDevice() + "\n" +
                            "Client: " + session.getClient() + "\n" +
                            "Media: " + session.getMediaType() + "\n" +
                            "Resolution: " + session.getResolution() + "\n" +
                            "Codec: " + session.getCodec() + "\n" +
                            "Bitrate: " + session.getBitrate() + " Mbps\n" +
                            "Path: " + session.getFilePath() + "\n" +
                            "Overview: " + session.getOverview()
            );
            alert.showAndWait();
        });

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

    private String formatSize(double bytes) {
        if (bytes >= 1024.0 * 1024.0 * 1024.0) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024.0 * 1024.0) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024.0) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.0f B", bytes);
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
                    if (sessionJson != null) {
                        if (!Objects.equals(sessionJson.optString("Client"), "Jellyseerr")) {
                            StreamSession session = new StreamSession();
                            session.setId(sessionJson.getString("Id"));
                            session.setUsername(sessionJson.optString("UserName", "Unknown User"));
                            session.setClient(sessionJson.optString("Client", "Unknown Client"));
                            session.setDevice(sessionJson.optString("DeviceName", "Unknown Device"));

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
                    }
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
