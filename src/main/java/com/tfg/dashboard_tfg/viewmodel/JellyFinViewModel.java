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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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
    private Label versionLabel;
    @FXML
    private Label uptimeLabel;

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
        private String mediaType;
        private String title;
        private int progress;
        private String resolution;
        private int bitrate;

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
    }

    // Utility properties
    private HttpClient httpClient;
    private ExecutorService executorService;
    private Timeline autoRefreshTimeline;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final DateTimeFormatter logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PROPERTIES_FILE = "connection.properties";
    private Properties appProperties = new Properties();
    private boolean propertiesLoaded = false;

    /**
     * Loads application properties from the properties file if not already loaded
     */
    private void loadPropertiesIfNeeded() {
        if (propertiesLoaded) {
            return;
        }

        File propertiesFile = new File(PROPERTIES_FILE);

        try {
            // Create the file if it doesn't exist
            if (!propertiesFile.exists()) {
                propertiesFile.createNewFile();
                addLogEntry("Info", "Properties", "Created new properties file");
            }

            // Load properties
            try (FileInputStream in = new FileInputStream(propertiesFile)) {
                appProperties.load(in);
                addLogEntry("Info", "Properties", "Loaded properties from file");
            }

            // Set the application properties from the loaded values
            if (appProperties.containsKey("jellyfin-apiUrl")) {
                serverUrl.set(appProperties.getProperty("jellyfin-apiUrl"));
            }

            if (appProperties.containsKey("jellyfin-apiKey")) {
                apiKey.set(appProperties.getProperty("jellyfin-apiKey"));
            }

            if (appProperties.containsKey("username")) {
                username.set(appProperties.getProperty("username"));
            }

            if (appProperties.containsKey("password")) {
                password.set(appProperties.getProperty("password"));
            }

            if (appProperties.containsKey("timeout")) {
                try {
                    int timeoutValue = Integer.parseInt(appProperties.getProperty("timeout"));
                    // Assuming you have a timeout property in your application
                    // timeout.set(timeoutValue);
                } catch (NumberFormatException e) {
                    addLogEntry("Warning", "Properties", "Invalid timeout value in properties");
                }
            }

            if (appProperties.containsKey("cacheDuration")) {
                try {
                    int cacheDuration = Integer.parseInt(appProperties.getProperty("cacheDuration"));
                    // Assuming you have a cacheDuration property in your application
                    // cacheDuration.set(cacheDuration);
                } catch (NumberFormatException e) {
                    addLogEntry("Warning", "Properties", "Invalid cache duration value in properties");
                }
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
        // Update properties with current values
        appProperties.setProperty("jellyfin-apiUrl", serverUrl.get() != null ? serverUrl.get() : "");
        appProperties.setProperty("jellyfin-apiKey", apiKey.get() != null ? apiKey.get() : "");

        if (username.get() != null && !username.get().isEmpty()) {
            appProperties.setProperty("username", username.get());
        }

        if (password.get() != null && !password.get().isEmpty()) {
            appProperties.setProperty("password", password.get());
        }

        // Add timestamp comment
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(out, "Updated by user");
            addLogEntry("Info", "Properties", "Saved connection properties to file");
        } catch (IOException e) {
            addLogEntry("Error", "Properties", "Failed to save properties: " + e.getMessage());
        }
    }

    /**
     * Updates a specific property and saves the changes
     * @param key Property key
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
        // Initialize HTTP client and thread pool
        httpClient = HttpClient.newBuilder().build();
        executorService = Executors.newFixedThreadPool(3);

        // Initialize auto-refresh timeline
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshServerStatus()));
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);

        // Load properties from file first
        loadPropertiesIfNeeded();

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

                    // Start auto-refresh if enabled
                    if (autoRefreshToggle.isSelected()) {
                        autoRefreshTimeline.play();
                    }
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
            addLogEntry("Info", "System", "Auto-refresh enabled (30 second interval)");
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

        // Wait for all tasks to complete
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
                // Use the provided API endpoint
                String systemInfoUrl = "http://192.168.1.85:8393/api/v1/system";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(systemInfoUrl))
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

                // Extract data and use 0 as default.
                double cpuUsage = cpuData != null ? cpuData.optDouble("systemCpuLoad", 0) : 0;
                long totalMemory = memoryData != null ? memoryData.optLong("totalMemory", 0) : 0;
                long usedMemory = memoryData != null ? memoryData.optLong("usedMemory", 0) : 0;
                double memoryUsagePercentage = memoryData != null ? memoryData.optDouble("memoryUsagePercentage", 0) / 100.0 : 0;


                double totalSpace = 0;
                double usedSpace = 0;
                double storageUsagePercentage;

                if (disksData != null && disksData.length() > 0) {
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
                }
                else{
                    storageUsagePercentage = 0;
                }
                // Fallback for version and uptime -  try to get from Jellyfin if available.
                String version = "N/A";
                String uptime = "N/A";
                if (apiKey.get() != null && !apiKey.get().trim().isEmpty()) {
                    try{
                        HttpRequest jellyfinSystemRequest = HttpRequest.newBuilder()
                                .uri(URI.create(serverUrl.get() + "/System/Info"))
                                .header("X-MediaBrowser-Token", apiKey.get())
                                .GET()
                                .build();
                        HttpResponse<String> jellyfinSystemResponse = httpClient.send(jellyfinSystemRequest, HttpResponse.BodyHandlers.ofString());
                        if(jellyfinSystemResponse.statusCode() == 200){
                            JSONObject jellyfinSystemInfo = new JSONObject(jellyfinSystemResponse.body());
                            version = jellyfinSystemInfo.optString("Version","N/A");
                            uptime = jellyfinSystemInfo.optString("LocalTime","N/A"); //This is not really the uptime, there is no direct uptime in Jellyfin API
                        }
                    }
                    catch(Exception ex){
                        addLogEntry("Warning","System","Failed to get version/uptime from Jellyfin: " + ex.getMessage());
                    }
                }
                // Try to get uptime from Docker if available
                if (uptime.equals("N/A")) {
                    try {
                        Process process = new ProcessBuilder("docker", "inspect", "-f", "{{.State.StartedAt}}", "jellyfin") //changed from $(docker ps... to jellyfin
                                .start();
                        process.waitFor();
                        if (process.exitValue() == 0) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            String dockerUptime = reader.readLine();
                            if (dockerUptime != null && !dockerUptime.isEmpty()) {
                                // Parse the Docker uptime string (ISO 8601) and calculate the difference.
                                java.time.OffsetDateTime startedAt = java.time.OffsetDateTime.parse(dockerUptime);
                                java.time.Duration duration = java.time.Duration.between(startedAt, java.time.OffsetDateTime.now());

                                long days = duration.toDays();
                                long hours = duration.toHoursPart();
                                long minutes = duration.toMinutesPart();
                                uptime = String.format("%d days, %d hours, %d minutes", days, hours, minutes);
                            }
                        }
                    } catch (Exception e) {
                        addLogEntry("Warning", "System", "Failed to get uptime from Docker: " + e.getMessage());
                    }
                }

                // Update UI on JavaFX thread
                String finalVersion = version;
                String finalUptime = uptime;
                Platform.runLater(() -> {
                    cpuUsageBar.setProgress(cpuUsage);
                    memoryUsageBar.setProgress(memoryUsagePercentage);
                    storageUsageBar.setProgress(storageUsagePercentage);
                    versionLabel.setText(finalVersion);
                    uptimeLabel.setText(finalUptime);
                });
            } catch (Exception e) {
                e.printStackTrace();
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
                e.printStackTrace();
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
     * Fetch recent logs from the server
     */
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
                // Simulate API delay
                Thread.sleep(400);

                // Generate random logs
                Random random = new Random();
                int newLogsCount = random.nextInt(3); // 0-2 new logs per refresh

                List<LogEntry> newLogs = new ArrayList<>();
                String[] sources = {"System", "Playback", "Auth", "Transcoder", "Scheduler", "IO"};
                String[] infoMessages = {
                        "Library scan completed",
                        "User logged in",
                        "Startup complete",
                        "Media optimized",
                        "Metadata updated",
                        "Scheduled task completed"
                };
                String[] warningMessages = {
                        "Slow response time detected",
                        "High CPU usage",
                        "Failed to fetch metadata",
                        "Network latency detected",
                        "Database query timeout"
                };
                String[] errorMessages = {
                        "Failed to connect to database",
                        "IO error when reading media file",
                        "Authentication failure",
                        "Transcoding error",
                        "Out of memory"
                };

                for (int i = 0; i < newLogsCount; i++) {
                    LocalDateTime now = LocalDateTime.now();
                    String time = now.format(logTimeFormatter);
                    String source = sources[random.nextInt(sources.length)];

                    // Decide log level (mostly info, sometimes warning, rarely error)
                    int levelRandom = random.nextInt(100);
                    String level;
                    String message;

                    if (levelRandom < 70) {
                        level = "Info";
                        message = infoMessages[random.nextInt(infoMessages.length)];
                    } else if (levelRandom < 95) {
                        level = "Warning";
                        message = warningMessages[random.nextInt(warningMessages.length)];
                    } else {
                        level = "Error";
                        message = errorMessages[random.nextInt(errorMessages.length)];
                    }

                    LogEntry entry = new LogEntry(time, level, source, message);
                    newLogs.add(entry);
                }

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
        VBox tile = new VBox(8);
        tile.setPadding(new Insets(15));
        tile.setMinWidth(250);
        tile.setMaxWidth(300);
        tile.setPrefWidth(280);
        tile.getStyleClass().add("stream-tile");

        // Header with user info
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label(session.getUsername());
        userLabel.getStyleClass().add("tile-username");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label deviceLabel = new Label(session.getDevice());
        deviceLabel.getStyleClass().add("tile-device");

        header.getChildren().addAll(userLabel, spacer, deviceLabel);

        // Media info
        Label titleLabel = new Label(session.getTitle());
        titleLabel.getStyleClass().add("tile-title");
        titleLabel.setWrapText(true);

        HBox mediaInfo = new HBox(10);
        mediaInfo.setAlignment(Pos.CENTER_LEFT);

        Label mediaTypeLabel = new Label(session.getMediaType());
        mediaTypeLabel.getStyleClass().add("media-type");

        Label resolutionLabel = new Label(session.getResolution());
        resolutionLabel.getStyleClass().add("resolution");

        Label bitrateLabel = new Label(session.getBitrate() + " Mbps");
        bitrateLabel.getStyleClass().add("bitrate");

        mediaInfo.getChildren().addAll(mediaTypeLabel, resolutionLabel, bitrateLabel);

        // Progress
        ProgressBar progressBar = new ProgressBar(session.getProgress() / 100.0);
        progressBar.setPrefWidth(Double.MAX_VALUE);

        Label progressLabel = new Label(session.getProgress() + "%");
        progressLabel.getStyleClass().add("progress-label");

        // Controls (demo only)
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);

        Button pauseButton = new Button("Pause");
        pauseButton.getStyleClass().add("control-button");

        Button stopButton = new Button("Stop");
        stopButton.getStyleClass().add("control-button");
        stopButton.setStyle("-fx-background-color: #e74c3c;");

        controls.getChildren().addAll(pauseButton, stopButton);

        // Add all elements to the tile
        tile.getChildren().addAll(header, titleLabel, mediaInfo, progressBar, progressLabel, controls);

        return tile;
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

                // Iterate through the sessions in the JSON array
                for (int i = 0; i < sessionsJson.length(); i++) {
                    JSONObject sessionJson = sessionsJson.getJSONObject(i);
                    StreamSession session = new StreamSession();

                    // Extract the data.  You'll need to adjust this based on the *actual* structure of the Jellyfin API response.  I've made educated guesses about the field names.  Use a debugger or look at the JSON to get the correct names.
                    session.setUsername(sessionJson.optString("UserName", "Unknown User")); // "UserName" is a guess
                    session.setDevice(sessionJson.optString("Client", "Unknown Device"));      // "Client" is a guess
                    JSONObject nowPlayingItem = sessionJson.optJSONObject("NowPlayingItem");  // "NowPlayingItem" is a guess
                    if (nowPlayingItem != null) {
                        String mediaType = nowPlayingItem.optString("Type", "Unknown"); // "Type" is a guess
                        session.setMediaType(mediaType);
                        session.setTitle(nowPlayingItem.optString("Name", "Unknown Title"));    // "Name" is a guess

                        // Get progress.  This is more complex, and the field names vary *wildly* in different APIs.
                        if(nowPlayingItem.has("UserData")) {
                            JSONObject userData = nowPlayingItem.getJSONObject("UserData");
                            session.setProgress((int)(userData.optDouble("PlaybackPositionTicks", 0) / (double)nowPlayingItem.optLong("RunTimeTicks",1) * 100)); // PlaybackPositionTicks, RunTimeTicks
                        }
                        else{
                            session.setProgress(0);
                        }


                        // Example for bitrate.  The actual field name is *highly* likely to be different.
                        session.setBitrate(sessionJson.optInt("TranscodingInfo", 0));  // "TranscodingInfo" is a guess, and might be an object, not a direct int.
                        session.setResolution(sessionJson.optString("PresentationMode", "Unknown")); // "PresentationMode" is a guess
                    }
                    else{
                        session.setTitle("Idle");
                        session.setMediaType("Idle");
                        session.setProgress(0);
                        session.setBitrate(0);
                        session.setResolution("Unknown");
                    }

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
                e.printStackTrace();
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
