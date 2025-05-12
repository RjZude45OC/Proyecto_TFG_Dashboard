package com.tfg.dashboard_tfg.viewmodel;

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


    // FXML Controls - Connection
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

    // FXML Controls - Server Stats
    @FXML
    private ProgressBar cpuUsageBar;
    @FXML
    private ProgressBar memoryUsageBar;
    @FXML
    private ProgressBar storageUsageBar;
    @FXML
    private ProgressBar networkUsageBar;
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

    // FXML Controls - Media Stats
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

    // FXML Controls - Content Area
    @FXML
    private ComboBox<String> mediaFilter;
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

    // Properties for binding
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty serverUrl = new SimpleStringProperty("");
    private final StringProperty apiKey = new SimpleStringProperty("");
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty serverMonitoringEndpoint = new SimpleStringProperty("");
    private final StringProperty dockerApiEndpoint = new SimpleStringProperty("");
    private final StringProperty autoUpdateInterval = new SimpleStringProperty("");


    // Data collections
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> filteredLogEntries = FXCollections.observableArrayList();

    // Model classes for UI data
    public static class LogEntry {
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

    public static class StreamSession {
        private String username;
        private String device;
        private String client;
        private String mediaType;
        private String title;
        private int progress;
        private int runtime;
        private String resolution;
        private int bitrate;  // In Mbps
        private boolean playing;
        private boolean muted;
        private String repeatMode;
        private boolean canSeek;
        private String remoteAddress;
        private String lastActivity;
        private String deviceId;
        private int year;
        private String overview;
        private String codec;
        private double frameRate;
        private String videoRange;
        private String filePath;
        private String sourceType;

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public int getRuntime() {
            return runtime;
        }

        public void setRuntime(int runtime) {
            this.runtime = runtime;
        }

        public String getResolution() {
            return resolution;
        }

        public void setResolution(String resolution) {
            this.resolution = resolution;
        }

        public int getBitrate() {
            return bitrate;
        }

        public void setBitrate(int bitrate) {
            this.bitrate = bitrate;
        }

        public boolean isPlaying() {
            return playing;
        }

        public void setPlaying(boolean playing) {
            this.playing = playing;
        }

        public boolean isMuted() {
            return muted;
        }

        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        public String getRepeatMode() {
            return repeatMode;
        }

        public void setRepeatMode(String repeatMode) {
            this.repeatMode = repeatMode;
        }

        public boolean isCanSeek() {
            return canSeek;
        }

        public void setCanSeek(boolean canSeek) {
            this.canSeek = canSeek;
        }

        public String getRemoteAddress() {
            return remoteAddress;
        }

        public void setRemoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        public String getLastActivity() {
            return lastActivity;
        }

        public void setLastActivity(String lastActivity) {
            this.lastActivity = lastActivity;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public String getOverview() {
            return overview;
        }

        public void setOverview(String overview) {
            this.overview = overview;
        }

        public String getCodec() {
            return codec;
        }

        public void setCodec(String codec) {
            this.codec = codec;
        }

        public double getFrameRate() {
            return frameRate;
        }

        public void setFrameRate(double frameRate) {
            this.frameRate = frameRate;
        }

        public String getVideoRange() {
            return videoRange;
        }

        public void setVideoRange(String videoRange) {
            this.videoRange = videoRange;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }
    }

    // Utility properties
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

    /**
     * Saves the current connection properties to the properties file
     */
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
     *
     * @param key   Property key
     * @param value Property value
     */
    public void updateProperty(String key, String value) {
        // Ensure properties are loaded
        loadPropertiesIfNeeded();

        // Update the property
        appProperties.setProperty(key, value);

        // Save the changes
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
        // Setup data bindings
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

        // Setup table
        setupLogTable();

        // Setup event handlers
        connectButton.setOnAction(event -> connectToServer());

        // Setup property listeners with property change tracking
        setupTextFieldBindings();

        logLevelFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            filterLogs();
        });

        // Initial refresh attempt if we have valid URL
        if (serverUrl.get() != null && !serverUrl.get().isEmpty()) {
            addLogEntry("Info", "Initialization", "Attempting connection with saved properties");
            connectToServer();
        }
        refreshServerStatus();

    }

    /**
     * Sets up bidirectional bindings between text fields and properties,
     * with change listeners to save updates to the properties file
     */
    private void setupTextFieldBindings() {
        // Setup bidirectional bindings
        serverUrlField.textProperty().bindBidirectional(serverUrl);
        apiKeyField.textProperty().bindBidirectional(apiKey);
        usernameField.textProperty().bindBidirectional(username);
        passwordField.textProperty().bindBidirectional(password);

        // Add focus lost listeners to save changes to properties file
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
        // Configure table columns
        timeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTime()));
        levelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLevel()));
        sourceColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSource()));
        messageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMessage()));

        // Set custom cell factories for coloring based on log level
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

        // Link table to observable list
        logTable.setItems(filteredLogEntries);
    }

    private void connectToServer() {
        // Reset connection status
        connected.set(false);

        // Load properties from file if not already loaded
        loadPropertiesIfNeeded();

        // Validate input from properties
        if (serverUrl.get() == null || serverUrl.get().trim().isEmpty()) {
            addLogEntry("Error", "Connection", "Server URL cannot be empty");
            return;
        }

        // Show connecting status
        serverStatusLabel.textProperty().unbind();
        serverStatusLabel.styleProperty().unbind(); // Ensure style is unbound as well
        serverStatusLabel.setText("Connecting...");
        serverStatusLabel.setStyle("-fx-text-fill: orange;");

        // Create connection task
        CompletableFuture.supplyAsync(() -> {
            try {
                // Build the system info request
                HttpRequest request;

                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {
                    // API Key authentication
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(serverUrl.get() + "/System/Info"))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .GET()
                            .build();
                } else if (username.get() != null && !username.get().trim().isEmpty() &&
                        password.get() != null && !password.get().trim().isEmpty()) {
                    // Username/password auth would require a more complex flow in a real app
                    // This is simplified for demonstration purposes
                    return "Authentication requires API key in this implementation";
                } else {
                    // No authentication
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(serverUrl.get() + "/System/Info/Public"))
                            .GET()
                            .build();
                }

                // Execute request
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return "Failed to connect: HTTP " + response.statusCode();
                }

                // Parse response
                JSONObject serverInfo = new JSONObject(response.body());
                String version = serverInfo.optString("Version", "Unknown");
                // Save successful connection details to properties
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
                    // You might want to set a default "connected" style here if needed
                    serverStatusLabel.styleProperty().unbind(); // Ensure style is unbound before potentially setting a default
                    serverStatusLabel.setStyle(""); // Or a specific connected style

                    addLogEntry("Info", "Connection", result);

                    // Fetch initial data
                    refreshServerStatus();
                } else {
                    serverStatusLabel.textProperty().unbind();
                    serverStatusLabel.styleProperty().unbind(); // Unbind the style property
                    serverStatusLabel.setText("Failed");
                    serverStatusLabel.setStyle("-fx-text-fill: red;");
                    addLogEntry("Error", "Connection", result);
                }
            });
        }, Platform::runLater);
    }

    /**
     * Toggle auto-refresh functionality
     */
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

        // Update last refresh time
        lastUpdateLabel.setText("Last update: " + timeFormat.format(new Date()));

        // Fetch data in parallel
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
                    // Handle errors
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

                // Parse the JSON response
                JSONObject systemInfo = new JSONObject(response.body());
                JSONObject cpuData = systemInfo.optJSONObject("cpu");
                JSONObject memoryData = systemInfo.optJSONObject("memory");
                JSONArray disksData = systemInfo.optJSONArray("disks");
                JSONObject networkData = systemInfo.optJSONObject("network"); //might be unused

                systeminfo = systemInfo;
                // Extract data and use 0 as default.
                double cpuUsage = cpuData != null ? processCpuUsage(cpuData) : 0;
                long totalMemory = memoryData != null ? memoryData.optLong("totalMemory", 0) : 0;
                long usedMemory = memoryData != null ? memoryData.optLong("usedMemory", 0) : 0;
                double memoryUsagePercentage = memoryData != null ? memoryData.optDouble("memoryUsagePercentage", 0) / 100.0 : 0;


                double totalSpace = 0;
                double usedSpace = 0;
                double storageUsagePercentage;

                if (disksData != null && !disksData.isEmpty()) {
                    // Sum up the space from all disks
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
                // Fallback for version and uptime -  try to get from Jellyfin if available.
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


                // Update UI on JavaFX thread
                String finalVersion = version;
                String finalUptime = uptime;
                Platform.runLater(() -> {
                    cpuUsageBar.setProgress(cpuUsage);
                    cpuUsageLabel.setText(String.format("%.1f%%", cpuUsage * 100));
                    memoryUsageBar.setProgress(memoryUsagePercentage);
                    memoryUsageLabel.setText(String.format("%.1f%%", memoryUsagePercentage * 100));

                    storageUsageBar.setProgress(storageUsagePercentage);
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
                // Base URL for Jellyfin API.
                String jellyfinApiUrl = serverUrl.get() + "/";

                // Construct URLs for the API calls.
                String moviesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Movie&Limit=0";
                String seriesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Series&Limit=0";
                String episodesUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Episode&Limit=0";
                String albumsUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=AudioAlbum&Limit=0";
                String songsUrl = jellyfinApiUrl + "Items?Recursive=true&IncludeItemTypes=Audio&Recursive=true&Limit=0";

                // Function to fetch data from Jellyfin API and handle errors
                final class ApiFetch {
                    private int fetchCount(String url) throws Exception {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("X-MediaBrowser-Token", apiKey.get()) // Use the API key
                                .GET()
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() != 200) {
                            addLogEntry("Error", "Library", "Failed to fetch from " + url + ": HTTP " + response.statusCode() + " - " + response.body());
                            return 0; // Return 0 on error to avoid crashing the whole method
                        }

                        JSONObject jsonResponse = new JSONObject(response.body());
                        JSONArray items = jsonResponse.optJSONArray("Items");
                        return (items != null) ? items.length() : 0;
                    }
                }
                ApiFetch apiFetch = new ApiFetch();

                // Fetch counts for each media type.
                int moviesCount = apiFetch.fetchCount(moviesUrl);
                int tvShowsCount = apiFetch.fetchCount(seriesUrl);
                int episodesCount = apiFetch.fetchCount(episodesUrl);
                int albumsCount = apiFetch.fetchCount(albumsUrl);
                int songsCount = apiFetch.fetchCount(songsUrl);


                //Simulate total size.  There is no direct API call for this in Jellyfin.
                Random random = new Random();
                double totalSize = 500 + random.nextInt(1500);


                // Update UI on JavaFX thread
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
     * Apply filter to media library view
     */
    @FXML
    private void applyMediaFilter() {
        String filter = mediaFilter.getSelectionModel().getSelectedItem();
        addLogEntry("Info", "UI", "Media filter applied: " + filter);

        // In a real implementation, this would refresh the media display
        // For now, we just log it
    }

    /**
     * Add a new log entry programmatically
     */
    private void addLogEntry(String level, String source, String message) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(logTimeFormatter);
        LogEntry entry = new LogEntry(time, level, source, message);

        // Add to main collection
        logEntries.add(0, entry);

        // Keep collection at reasonable size
        while (logEntries.size() > 100) {
            logEntries.remove(logEntries.size() - 1);
        }

        // Apply filtering
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
//                System.out.println("cpu "+ cpuData);
//                System.out.println("memory "+ memoryData);
//                System.out.println("disk "+ disksData);
//                System.out.println("network " + networkData);
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

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    // Add new logs
                    logEntries.addAll(0, newLogs);

                    // Keep only the most recent 100 logs
                    while (logEntries.size() > 100) {
                        logEntries.remove(logEntries.size() - 1);
                    }

                    // Apply filtering
                    filterLogs();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addLogEntry("Error", "Logs", "Failed to fetch logs: " + e.getMessage());
                });
            }
        }, executorService);
    }

    /**
     * Creates a visual tile for an active stream session
     */
    private VBox createStreamTile(StreamSession session) {
        VBox tile = new VBox(10);
        tile.setPadding(new Insets(10));
        tile.setMinWidth(280);
        tile.setMaxWidth(350);
        tile.setPrefWidth(280);
        tile.getStyleClass().add("stream-tile");

        // Status indicator - active or idle
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

        // Header with user info
        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);

        // User avatar circle
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

        // Remote connection indicator if applicable
        Label remoteLabel = null;
        if (!"Local".equals(session.getRemoteAddress())) {
            remoteLabel = new Label("REMOTE");
            remoteLabel.getStyleClass().add("remote-tag");
        }

        header.getChildren().addAll(avatarPane, userInfo, headerSpacer);
        if (remoteLabel != null) {
            header.getChildren().add(remoteLabel);
        }

        // Media info section
        VBox mediaInfoBox = new VBox(6);

        // Media title with year if available
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

        // Technical specs in tags
        FlowPane techSpecs = new FlowPane(8, 8);
        techSpecs.setPrefWrapLength(280);

        // Only add these if we have active media playing
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

        // Controls section
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);

        Button playPauseButton = new Button(session.isPlaying() ? "Pause" : "Play");
        playPauseButton.getStyleClass().add("control-button");

        Button stopButton = new Button("Stop");
        stopButton.getStyleClass().add("control-button");
        stopButton.getStyleClass().add("stop-button");

        Button infoButton = new Button("Details");
        infoButton.getStyleClass().add("control-button");
        infoButton.getStyleClass().add("info-button");

        controls.getChildren().addAll(playPauseButton, stopButton, infoButton);

        // Source path (truncate and make subtle)
        Label pathLabel = new Label(session.getFilePath());
        pathLabel.getStyleClass().add("path-label");
        pathLabel.setWrapText(true);
        pathLabel.setVisible(!"Idle".equals(session.getMediaType()));
        pathLabel.setManaged(!"Idle".equals(session.getMediaType()));

        // Add all elements to the tile
        tile.getChildren().addAll(statusBar, header, mediaInfoBox, progressBox, controls);

        // Only add path if there's media playing
        if (!"Idle".equals(session.getMediaType())) {
            tile.getChildren().add(pathLabel);
        }

        return tile;
    }

    // Helper method to create tag labels
    private Label createTag(String text) {
        Label tag = new Label(text);
        tag.getStyleClass().add("tech-tag");
        return tag;
    }

    // Helper method to format time in HH:MM:SS
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

    // Helper method to format datetime
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

    /**
     * Fetch active sessions from the server
     */
    private CompletableFuture<Void> fetchActiveSessions() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Construct the API URL to get active sessions.  You'll need the server URL.
                String activeSessionsUrl = serverUrl.get() + "/Sessions";
                HttpRequest request;

                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {
                    // Include the API key if available
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(activeSessionsUrl))
                            .header("X-MediaBrowser-Token", apiKey.get())
                            .GET()
                            .build();
                } else {
                    // If no API key, try without authentication (this might not work on most Jellyfin setups)
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(activeSessionsUrl))
                            .GET()
                            .build();
                }
                // Send the request and get the response
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    // Handle errors.  For now, just log and return.  A proper application would have better error handling.
                    addLogEntry("Error", "Active Sessions", "Failed to fetch active sessions: HTTP " + response.statusCode() + " - " + response.body());
                    Platform.runLater(() -> {
                        activeStreamsLabel.setText("0"); // Or some error indicator
                        activeStreamsTilesPane.getChildren().clear();
                        Label errorLabel = new Label("Failed to load active sessions.");
                        errorLabel.getStyleClass().add("error-text"); // You can define this in your CSS
                        activeStreamsTilesPane.getChildren().add(errorLabel);

                    });
                    return; // IMPORTANT: Exit the CompletableFuture
                }

                // Parse the JSON response
                JSONArray sessionsJson = new JSONArray(response.body());
                List<StreamSession> sessions = new ArrayList<>();

                for (int i = 0; i < sessionsJson.length(); i++) {
                    JSONObject sessionJson = sessionsJson.getJSONObject(i);
                    StreamSession session = new StreamSession();
                    // Extract basic user information
                    session.setUsername(sessionJson.optString("UserName", "Unknown User"));
                    session.setDevice(sessionJson.optString("DeviceName", "Unknown Device"));
                    session.setClient(sessionJson.optString("Client", "Unknown Client"));

                    // Get remote connection information
                    session.setRemoteAddress(sessionJson.optString("RemoteEndPoint", "Local"));

                    // Extract playback state
                    JSONObject playState = sessionJson.optJSONObject("PlayState");
                    if (playState != null) {
                        session.setPlaying(!playState.optBoolean("IsPaused", false));
                        session.setMuted(playState.optBoolean("IsMuted", false));
                        session.setRepeatMode(playState.optString("RepeatMode", "None"));
                        session.setCanSeek(playState.optBoolean("CanSeek", false));
                    }

                    // Check for active playback
                    JSONArray nowPlayingQueueItems = sessionJson.optJSONArray("NowPlayingQueueFullItems");
                    if (nowPlayingQueueItems != null && !nowPlayingQueueItems.isEmpty()) {
                        JSONObject mediaItem = nowPlayingQueueItems.getJSONObject(0);

                        // Basic media information
                        session.setTitle(mediaItem.optString("Name", "Unknown Title"));
                        session.setMediaType(mediaItem.optString("MediaType", "Unknown"));
                        session.setYear(mediaItem.optInt("ProductionYear", 0));
                        session.setOverview(mediaItem.optString("Overview", ""));

                        // In the parsing method where we extract session data:
                        long runtimeTicks = mediaItem.optLong("RunTimeTicks", 0);
                        session.setRuntime(runtimeTicks > 0 ? (int) (runtimeTicks / 10000000) : 0); // Convert ticks to seconds


                        JSONObject userData = mediaItem.optJSONObject("UserData");
                        if (userData != null && userData.has("PlaybackPositionTicks")) {
                            long positionTicks = userData.optLong("PlaybackPositionTicks", 0);
                            session.setProgress((int) (positionTicks / 10000000)); // Convert ticks to seconds
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

                        // Get video stream details if available
                        JSONArray mediaStreams = mediaItem.optJSONArray("MediaStreams");
                        if (mediaStreams != null) {
                            for (int j = 0; j < mediaStreams.length(); j++) {
                                JSONObject stream = mediaStreams.getJSONObject(j);
                                String streamType = stream.optString("Type", "");

                                if ("Video".equals(streamType)) {
                                    // Resolution information
                                    int width = stream.optInt("Width", 0);
                                    int height = stream.optInt("Height", 0);
                                    session.setResolution(width > 0 && height > 0 ? width + "x" + height : "Unknown");

                                    // Video codec and quality information
                                    session.setCodec(stream.optString("Codec", "Unknown"));
                                    session.setBitrate(Math.round(stream.optInt("BitRate", 0) / 1000000.0f)); // Convert to Mbps
                                    session.setFrameRate(stream.optDouble("RealFrameRate", 0));
                                    session.setVideoRange(stream.optString("VideoRange", "Unknown"));
                                    break;
                                }
                            }
                        }

                        // Get path and location information
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

                    // Add other session metadata
                    session.setLastActivity(sessionJson.optString("LastActivityDate", "Unknown"));
                    session.setDeviceId(sessionJson.optString("DeviceId", "Unknown"));

                    sessions.add(session);
                }

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    activeStreamsLabel.setText(String.valueOf(sessions.size()));

                    // Clear existing tiles
                    activeStreamsTilesPane.getChildren().clear();

                    // Create new tiles for each active session
                    for (StreamSession session : sessions) {
                        activeStreamsTilesPane.getChildren().add(createStreamTile(session));
                    }

                    // Add placeholder if no active streams
                    if (sessions.isEmpty()) {
                        Label placeholder = new Label("No active streams");
                        placeholder.getStyleClass().add("placeholder-text");
                        activeStreamsTilesPane.getChildren().add(placeholder);
                    }
                });
            } catch (Exception e) {
                // Catch any exceptions (e.g., network errors, JSON parsing errors)
                addLogEntry("Error", "Active Sessions", "Error fetching active sessions: " + e.getMessage());
                Platform.runLater(() -> {
                    activeStreamsLabel.setText("0"); // Or some error indicator
                    activeStreamsTilesPane.getChildren().clear();
                    Label errorLabel = new Label("Error loading active sessions.");
                    errorLabel.getStyleClass().add("error-text"); // You can define this in your CSS
                    activeStreamsTilesPane.getChildren().add(errorLabel);

                });
            }
        });
    }
}
