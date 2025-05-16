package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.DownloadQueueItem;
import com.tfg.dashboard_tfg.model.HistoryItem;
import com.tfg.dashboard_tfg.model.LogEntry;
import com.tfg.dashboard_tfg.model.NetworkData;
import javafx.animation.KeyFrame;
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
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
    public Label lastUpdateLabel;
    @FXML
    private TableView<DownloadQueueItem> downloadQueueTable;
    @FXML
    private TableColumn<DownloadQueueItem, String> titleColumn;
    @FXML
    private TableColumn<DownloadQueueItem, String> sizeColumn;
    @FXML
    private TableColumn<DownloadQueueItem, String> statusColumn;
    @FXML
    private TableColumn<DownloadQueueItem, Double> progressColumn;
    @FXML
    private TableColumn<DownloadQueueItem, String> speedColumn;
    @FXML
    private TableColumn<DownloadQueueItem, String> etaColumn;
    @FXML
    private TableColumn<DownloadQueueItem, Void> actionsColumn;
    @FXML
    private TableView<HistoryItem> historyTable;
    @FXML
    private TableColumn<HistoryItem, String> historyDateColumn;
    @FXML
    private TableColumn<HistoryItem, String> historySeriesColumn;
    @FXML
    private TableColumn<HistoryItem, String> historyEpisodeColumn;
    @FXML
    private TableColumn<HistoryItem, String> historyQualityColumn;
    @FXML
    private TableColumn<HistoryItem, String> historyStatusColumn;
    @FXML
    private TableColumn<HistoryItem, String> historySourceColumn;
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
    private ComboBox<String> historyFilterCombo;
    @FXML
    private ComboBox<String> logLevelFilter;

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
    private HttpClient httpClient;
    private ExecutorService executorService;
    private Timeline autoRefreshTimeline;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private JSONObject systeminfo;
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> filteredLogEntries = FXCollections.observableArrayList();
    private final ObservableList<DownloadQueueItem> queueItems = FXCollections.observableArrayList();
    private final ObservableList<HistoryItem> historyItems = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> logItems = FXCollections.observableArrayList();


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

    private void setupTextFieldBindings() {
        serverUrlField.textProperty().bindBidirectional(serverUrl);
        apiKeyField.textProperty().bindBidirectional(apiKey);

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
    }

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

        HttpClient timeoutClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("Building request");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/v3/system/status"))
                        .timeout(Duration.ofSeconds(5))
                        .header("X-Api-Key", key)
                        .GET()
                        .build();

                System.out.println("Sending request to: " + url + "/api/v3/system/status");

                CompletableFuture<HttpResponse<String>> responseFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return timeoutClient.send(request, HttpResponse.BodyHandlers.ofString());
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        });

                HttpResponse<String> response = responseFuture.get(8, TimeUnit.SECONDS);

                System.out.println("Response received with status code: " + response.statusCode());

                if (response.statusCode() != 200) {
                    return "Failed to connect: HTTP " + response.statusCode() + " - " + response.body();
                }

                JSONObject serverInfo = new JSONObject(response.body());
                String version = serverInfo.optString("version", "Unknown");
                saveConnectionProperties();

                return "Connected to Sonarr v" + version;
            } catch (TimeoutException e) {
                System.out.println("Connection timed out");
                return "Connection timed out. Server may be unreachable or blocked.";
            } catch (InterruptedException | ExecutionException e) {
                System.out.println("Connection error: " + e.getMessage());
                Throwable cause = e.getCause();
                if (cause != null) {
                    System.out.println("Caused by: " + cause.getMessage());
                    if (cause instanceof ConnectException) {
                        return "Connection refused. Please check server URL and that Sonarr is running.";
                    } else if (cause instanceof java.net.UnknownHostException) {
                        return "Unknown host. Please check server URL.";
                    }
                }
                return "Connection failed: " + e.getMessage();
            } catch (Exception e) {
                System.out.println("General error: " + e.getMessage());
                e.printStackTrace();
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
                    serverStatusLabel.setStyle("-fx-text-fill: green;");
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
        CompletableFuture<Void> logs = fetchRecentLogs();
        executorService = Executors.newFixedThreadPool(3);
        loadPropertiesIfNeeded();
        CompletableFuture.allOf(
                systemInfo,
//                libraryStats,
                logs
        ).thenRun(() -> {
            addLogEntry("Info", "System", "Server status refreshed");
        });
        autoRefreshTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(Double.parseDouble(autoUpdateInterval.getValue())), e -> {
            refreshServerStatus();
        }));
        setupLogTable();
        setupDownloadQueueTable();
        initializeHistoryTable();
        if (serverUrl.get() != null && !serverUrl.get().isEmpty()) {
            addLogEntry("Info", "Initialization", "Attempting connection with saved properties");
            connectToServer();
        }
        connectButton.setOnAction(event -> connectToServer());
        setupTextFieldBindings();
        refreshServerStatus();
    }

    private void setupDownloadQueueTable() {
        // Set up table columns
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        progressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        speedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        etaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));

        // Set up progress bar cell
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());

        // Custom status cell coloring
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("Completed")) {
                        setStyle("-fx-text-fill: #23d160;");
                    } else if (item.equals("Failed")) {
                        setStyle("-fx-text-fill: #ff3860;");
                    } else if (item.equals("Downloading")) {
                        setStyle("-fx-text-fill: #209cee;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        setupActionsColumn();

        downloadQueueTable.setItems(queueItems);
    }

    private void setupActionsColumn() {
        Callback<TableColumn<DownloadQueueItem, Void>, TableCell<DownloadQueueItem, Void>> cellFactory =
                new Callback<>() {
                    @Override
                    public TableCell<DownloadQueueItem, Void> call(final TableColumn<DownloadQueueItem, Void> param) {
                        return new TableCell<>() {
                            private final Button pauseButton = new Button("Pause");
                            private final Button removeButton = new Button("Remove");
                            private final HBox pane = new HBox(5, pauseButton, removeButton);

                            {
                                pauseButton.getStyleClass().add("action-button");
                                removeButton.getStyleClass().add("action-button");
                                pane.setAlignment(Pos.CENTER);

                                pauseButton.setOnAction(event -> {
                                    DownloadQueueItem item = getTableView().getItems().get(getIndex());
                                    handlePauseDownload(item);
                                });

                                removeButton.setOnAction(event -> {
                                    DownloadQueueItem item = getTableView().getItems().get(getIndex());
                                    handleRemoveDownload(item);
                                });
                            }

                            @Override
                            public void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setGraphic(null);
                                } else {
                                    // Update button text based on status
                                    DownloadQueueItem downloadItem = getTableView().getItems().get(getIndex());
                                    if (downloadItem.getStatus().equals("Paused")) {
                                        pauseButton.setText("Resume");
                                    } else {
                                        pauseButton.setText("Pause");
                                    }

                                    setGraphic(pane);
                                }
                            }
                        };
                    }
                };

        actionsColumn.setCellFactory(cellFactory);
    }
    private void initializeHistoryTable() {
        historyDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        historySeriesColumn.setCellValueFactory(new PropertyValueFactory<>("series"));
        historyEpisodeColumn.setCellValueFactory(new PropertyValueFactory<>("episode"));
        historyQualityColumn.setCellValueFactory(new PropertyValueFactory<>("quality"));
        historyStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        historySourceColumn.setCellValueFactory(new PropertyValueFactory<>("source"));
        historyStatusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("Completed")) {
                        setStyle("-fx-text-fill: #23d160;");
                    } else if (item.equals("Failed")) {
                        setStyle("-fx-text-fill: #ff3860;");
                    } else if (item.equals("Grabbed")) {
                        setStyle("-fx-text-fill: #209cee;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        historyTable.setItems(historyItems);
        historyFilterCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            filterHistoryItems(newValue);
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


    private void filterHistoryItems(String filter) {
        if (filter == null || filter.equals("All")) {
            historyTable.setItems(historyItems);
        } else {
            ObservableList<HistoryItem> filteredItems = historyItems.filtered(
                    item -> item.getStatus().equals(filter)
            );
            historyTable.setItems(filteredItems);
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
        CompletableFuture<Void> logs = fetchRecentLogs();

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
    private CompletableFuture<Void> fetchRecentLogs() {
        // Guard against null executorService - this prevents NullPointerException
        if (executorService == null) {
            // Create the executor service if it doesn't exist yet
            executorService = Executors.newFixedThreadPool(3);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                List<LogEntry> newLogs = new ArrayList<>();
                LocalDateTime now = LocalDateTime.now();
                String time = now.format(logTimeFormatter);

                // Process CPU information
                if (systeminfo != null) {
                    JSONObject cpuData = systeminfo.optJSONObject("cpu");
                    if (cpuData != null) {
                        double systemCpuLoad = cpuData.getDouble("systemCpuLoad");
                        String source = "System";

                        if (systemCpuLoad >= 90) {
                            newLogs.add(new LogEntry(time, "Error", source,
                                    "Critical CPU load: " + String.format("%.1f%%", systemCpuLoad)));
                        } else if (systemCpuLoad >= 75) {
                            newLogs.add(new LogEntry(time, "Warning", source,
                                    "High CPU usage: " + String.format("%.1f%%", systemCpuLoad)));
                        } else if (systemCpuLoad >= 50) {
                            newLogs.add(new LogEntry(time, "Info", source,
                                    "Moderate CPU load: " + String.format("%.1f%%", systemCpuLoad)));
                        }
                    }

                    // Process memory information
                    JSONObject memoryData = systeminfo.optJSONObject("memory");
                    if (memoryData != null) {
                        long total = memoryData.optLong("total", 0);
                        long used = memoryData.optLong("used", 0);
                        double usedMemoryPercentage =
                                total > 0 ? (used * 100.0 / total) : 0;
                        String source = "Memory";

                        if (usedMemoryPercentage >= 90) {
                            newLogs.add(new LogEntry(time, "Error", source,
                                    "Critical memory usage: " + String.format("%.1f%%", usedMemoryPercentage)));
                        } else if (usedMemoryPercentage >= 80) {
                            newLogs.add(new LogEntry(time, "Warning", source,
                                    "High memory usage: " + String.format("%.1f%%", usedMemoryPercentage)));
                        }
                    }

                    // Process disks information
                    JSONArray disksData = systeminfo.optJSONArray("disks");
                    if (disksData != null) {
                        for (int i = 0; i < disksData.length(); i++) {
                            JSONObject disk = disksData.optJSONObject(i);
                            if (disk != null) {
                                String diskName = disk.optString("name", "Unknown Disk");
                                long totalSpace = disk.optLong("totalSpace", 0);
                                long usedSpace = disk.optLong("usedSpace", 0);
                                double usedSpacePercentage =
                                        totalSpace > 0 ? (usedSpace * 100.0 / totalSpace) : 0;
                                String source = "Storage";

                                if (usedSpacePercentage >= 95) {
                                    newLogs.add(new LogEntry(time, "Error", source,
                                            "Critical disk space on " + diskName + ": " + String.format("%.1f%%", usedSpacePercentage)));
                                } else if (usedSpacePercentage >= 85) {
                                    newLogs.add(new LogEntry(time, "Warning", source,
                                            "Low disk space on " + diskName + ": " + String.format("%.1f%%", usedSpacePercentage)));
                                }
                            }
                        }
                    }

                    // Process network information
                    JSONObject networkData = systeminfo.optJSONObject("network");
                    if (networkData != null) {
                        try {
                            JSONObject interfaces = networkData.optJSONObject("interfaces");
                            if (interfaces != null) {
                                long receivedBytes = 0;
                                long sentBytes = 0;

                                for (String interfaceName : interfaces.keySet()) {
                                    JSONObject networkInterface = interfaces.optJSONObject(interfaceName);
                                    if (networkInterface != null) {
                                        receivedBytes += networkInterface.optLong("bytesReceived", 0);
                                        sentBytes += networkInterface.optLong("bytesSent", 0);
                                    }
                                }

                                long totalTraffic = receivedBytes + sentBytes;
                                String source = "Network";

                                // Calculate network speed if we have previous measurements
                                String networkKey = "total";
                                long currentTime = System.currentTimeMillis();

                                if (previousBytesReceived != null && previousBytesReceived.containsKey(networkKey) &&
                                        previousBytesSent != null && previousBytesSent.containsKey(networkKey)) {

                                    double timeDiffSeconds = (currentTime - lastUpdateTimestamp) / 1000.0;
                                    if (timeDiffSeconds > 0) {
                                        long receivedDiff = receivedBytes - previousBytesReceived.get(networkKey);
                                        long sentDiff = sentBytes - previousBytesSent.get(networkKey);

                                        double downloadSpeed = receivedDiff / timeDiffSeconds;
                                        double uploadSpeed = sentDiff / timeDiffSeconds;

                                        // Log high network activity
                                        if (downloadSpeed > 5_000_000) { // 5MB/s
                                            newLogs.add(new LogEntry(time, "Info", source,
                                                    "High download speed: " + formatBytes((long)downloadSpeed) + "/s"));
                                        }

                                        if (uploadSpeed > 2_000_000) { // 2MB/s
                                            newLogs.add(new LogEntry(time, "Info", source,
                                                    "High upload speed: " + formatBytes((long)uploadSpeed) + "/s"));
                                        }
                                    }
                                }

                                // Store current values for next calculation
                                if (previousBytesReceived != null && previousBytesSent != null) {
                                    previousBytesReceived.put(networkKey, receivedBytes);
                                    previousBytesSent.put(networkKey, sentBytes);
                                    lastUpdateTimestamp = currentTime;
                                }
                            }
                        } catch (Exception e) {
                            // In case of any JSON parsing exceptions, add an error log
                            newLogs.add(new LogEntry(time, "Error", "Network",
                                    "Error processing network data: " + e.getMessage()));
                        }
                    }
                }

                // Add a generic system status entry if no specific issues were detected
                if (newLogs.isEmpty()) {
                    newLogs.add(new LogEntry(time, "Info", "System", "System running normally"));
                }

                Platform.runLater(() -> {
                    logEntries.addAll(0, newLogs);

                    // Keep log size manageable
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
    private CompletableFuture<Void> fetchSystemInfo() {
        System.out.println("fetch system info");
        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverMonitoringEndpoint.get()))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response);
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
        String selectedLevel = logLevelFilter.getSelectionModel().getSelectedItem();
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
    @FXML
    public void clearCompletedDownloads() {
        List<DownloadQueueItem> completedItems = queueItems.stream()
                .filter(item -> item.getStatus().equals("Completed"))
                .collect(Collectors.toList());

        if (completedItems.isEmpty()) {
            addLogEntry("Info", "Queue", "No completed downloads to clear");
            return;
        }

        queueItems.removeAll(completedItems);
        addLogEntry("Info", "Queue", "Cleared " + completedItems.size() + " completed downloads");
    }
    private void handlePauseDownload(DownloadQueueItem item) {
        int index = queueItems.indexOf(item);
        if (index >= 0) {
            DownloadQueueItem current = queueItems.get(index);
            if (current.getStatus().equals("Downloading")) {
                current.setStatus("Paused");
                addLogEntry("Info", "Queue", "Paused download: " + current.getTitle());
            } else if (current.getStatus().equals("Paused")) {
                current.setStatus("Downloading");
                addLogEntry("Info", "Queue", "Resumed download: " + current.getTitle());
            }
            queueItems.set(index, current);
        }
    }
    private void handleRemoveDownload(DownloadQueueItem item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remove Download");
        alert.setHeaderText("Remove Download");
        alert.setContentText("Are you sure you want to remove this download?\n" + item.getTitle());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            queueItems.remove(item);
            addLogEntry("Info", "Queue", "Removed download: " + item.getTitle());
        }
    }
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
