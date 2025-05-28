package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.DownloadQueueItem;
import com.tfg.dashboard_tfg.model.HistoryItem;
import com.tfg.dashboard_tfg.model.LogEntry;
import com.tfg.dashboard_tfg.model.NetworkData;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
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
    private ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private ObservableList<LogEntry> filteredLogEntries = FXCollections.observableArrayList();
    private ObservableList<DownloadQueueItem> queueItems = FXCollections.observableArrayList();
    private ObservableList<HistoryItem> historyItems = FXCollections.observableArrayList();
    private ObservableList<LogEntry> logItems = FXCollections.observableArrayList();


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

        HttpClient timeoutClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/v3/system/status"))
                        .timeout(Duration.ofSeconds(5))
                        .header("X-Api-Key", key)
                        .GET()
                        .build();

                CompletableFuture<HttpResponse<String>> responseFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return timeoutClient.send(request, HttpResponse.BodyHandlers.ofString());
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        });

                HttpResponse<String> response = responseFuture.get(8, TimeUnit.SECONDS);
                if (response.statusCode() != 200) {
                    return "Failed to connect: HTTP " + response.statusCode() + " - " + response.body();
                }

                JSONObject serverInfo = new JSONObject(response.body());
                String version = serverInfo.optString("version", "Unknown");

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
        httpClient = HttpClient.newBuilder().build();
        executorService = Executors.newFixedThreadPool(3);
        loadPropertiesIfNeeded();
        String autoUpdateInterval = appProperties.getProperty("update-interval");
        if (autoUpdateInterval == null || autoUpdateInterval.isEmpty()) {
            autoUpdateInterval = "5";
            updateProperty("update-interval", "5");
        }
        autoRefreshTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(Double.parseDouble(autoUpdateInterval)), e -> {
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
        setupDownloadQueueTable();
        initializeHistoryTable();
        if (serverUrl.get() != null && !serverUrl.get().isEmpty()) {
            addLogEntry("Info", "Initialization", "Attempting connection with saved properties");
            connectToServer();
        }
        serverUrlField.setText(appProperties.getProperty("sonarr-apiUrl", ""));
        apiKeyField.setText(appProperties.getProperty("sonarr-apiKey", ""));
        refreshServerStatus();
    }

    private void setupDownloadQueueTable() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        progressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        speedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        etaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
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
                                pauseButton.getStyleClass().add("table-button");
                                removeButton.getStyleClass().add("table-button");
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

    @FXML
    public void refreshHistory() {
        CompletableFuture<Void> history = fetchHistory();
        CompletableFuture.allOf(
                history
        ).thenRun(() -> {
            addLogEntry("Info", "History", "History updated");
        });
    }

    @FXML
    public void refreshQueue(ActionEvent actionEvent) {
        CompletableFuture<Void> downloadQueue = fetchDownloadQueue();
        CompletableFuture.allOf(
                downloadQueue
        ).thenRun(() -> {
            addLogEntry("Info", "Queue", "History updated");
        });
    }

    @FXML
    public void clearLogs(ActionEvent actionEvent) {
        logTable.getItems().clear();
    }

    @FXML
    public void clearHistory(ActionEvent actionEvent) {
        historyTable.getItems().clear();
    }

    @FXML
    private void refreshServerStatus() {
        if (!connected.get()) {
            return;
        }

        lastUpdateLabel.setText("Last update: " + timeFormat.format(new Date()));

        CompletableFuture<Void> systemInfo = fetchSystemInfo();
        CompletableFuture<Void> downloadQueue = fetchDownloadQueue();
        CompletableFuture<Void> history = fetchHistory();
        CompletableFuture<Void> logs = fetchRecentLogs();

        CompletableFuture.allOf(
                systemInfo,
                downloadQueue,
                history,
                logs
        ).thenRun(() -> {
            addLogEntry("Info", "System", "Server status refreshed");
        });
    }

    private CompletableFuture<Void> fetchHistory() {
        if (!connected.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl.get() + "/api/v3/history?page=1&pageSize=50&sortDirection=descending&sortKey=date"))
                        .header("X-Api-Key", apiKey.get())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    addLogEntry("Error", "History", "Failed to fetch history: HTTP " + response.statusCode());
                    return;
                }

                JSONObject historyData = new JSONObject(response.body());
                JSONArray records = historyData.getJSONArray("records");

                List<HistoryItem> items = new ArrayList<>();
                DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);

                    String date = record.optString("date", "");
                    String formattedDate = "";
                    try {
                        LocalDateTime dateTime = LocalDateTime.parse(date, inputFormat);
                        formattedDate = dateTime.format(outputFormat);
                    } catch (Exception e) {
                        formattedDate = date;
                    }

                    JSONObject episodeInfo = record.optJSONObject("episode");
                    JSONObject seriesInfo = record.optJSONObject("series");
                    JSONObject qualityInfo = record.optJSONObject("quality");

                    String seriesTitle = seriesInfo != null ? seriesInfo.optString("title", "Unknown") : "Unknown";

                    String episodeTitle = "Unknown";
                    if (episodeInfo != null) {
                        int seasonNumber = episodeInfo.optInt("seasonNumber", 0);
                        int episodeNumber = episodeInfo.optInt("episodeNumber", 0);
                        String title = episodeInfo.optString("title", "");
                        episodeTitle = String.format("S%02dE%02d - %s", seasonNumber, episodeNumber, title);
                    }

                    String quality = "Unknown";
                    if (qualityInfo != null) {
                        JSONObject qualityData = qualityInfo.optJSONObject("quality");
                        if (qualityData != null) {
                            quality = qualityData.optString("name", "Unknown");
                        }
                    }

                    String eventType = record.optString("eventType", "Unknown");
                    String status = eventType;

                    switch (eventType.toLowerCase()) {
                        case "grabbed":
                            status = "Grabbed";
                            break;
                        case "downloadfolderimported":
                            status = "Completed";
                            break;
                        case "downloadfailed":
                            status = "Failed";
                            break;
                        case "episodefiledeleted":
                            status = "Deleted";
                            break;
                        default:
                            status = eventType;
                    }

                    String source = "N/A";
                    if (record.has("data")) {
                        try {
                            JSONObject data = record.getJSONObject("data");
                            if (data.has("indexer")) {
                                source = data.getString("indexer");
                            } else if (data.has("downloadClient")) {
                                source = data.getString("downloadClient");
                            }
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }

                    HistoryItem item = new HistoryItem(formattedDate, seriesTitle, episodeTitle, quality, status, source);
                    items.add(item);
                }

                Platform.runLater(() -> {
                    historyItems.clear();
                    historyItems.addAll(items);
                    addLogEntry("Info", "History", "Updated history: " + items.size() + " items");

                    if (historyFilterCombo.getItems().isEmpty()) {
                        Set<String> statusTypes = items.stream().map(HistoryItem::getStatus).collect(Collectors.toSet());
                        historyFilterCombo.getItems().add("All");
                        historyFilterCombo.getItems().addAll(statusTypes);
                        historyFilterCombo.getSelectionModel().select("All");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    addLogEntry("Error", "History", "Failed to fetch history: " + e.getMessage());
                });
            }
        }, executorService);
    }

    private CompletableFuture<Void> fetchDownloadQueue() {
        if (!connected.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl.get() + "/api/v3/queue"))
                        .header("X-Api-Key", apiKey.get())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    addLogEntry("Error", "Queue", "Failed to fetch download queue: HTTP " + response.statusCode());
                    return;
                }

                JSONObject queueData = new JSONObject(response.body());
                JSONArray records = queueData.getJSONArray("records");

                List<DownloadQueueItem> items = new ArrayList<>();

                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);

                    String title = record.optString("title", "Unknown");
                    long size = record.optLong("size", 0);
                    String sizeFormatted = formatBytes(size);

                    String status = record.optString("status", "Unknown");
                    status = status;

                    double progress = record.optDouble("sizeleft", 0) / size;
                    progress = 1.0 - progress;
                    if (Double.isNaN(progress) || Double.isInfinite(progress)) {
                        progress = 0;
                    }

                    String speed = "N/A";
                    if (record.has("downloadSpeed")) {
                        long downloadSpeed = record.optLong("downloadSpeed", 0);
                        speed = formatBytes(downloadSpeed) + "/s";
                    }

                    String estimatedCompletionTime = record.optString("estimatedCompletionTime", "");
                    String eta = "N/A";
                    if (!estimatedCompletionTime.isEmpty()) {
                        try {
                            LocalDateTime completionTime = LocalDateTime.parse(estimatedCompletionTime,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
                            LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
                            Duration duration = Duration.between(now, completionTime);

                            long hours = duration.toHours();
                            long minutes = duration.toMinutesPart();

                            if (hours > 0) {
                                eta = hours + "h " + minutes + "m";
                            } else {
                                eta = minutes + "m";
                            }
                        } catch (Exception e) {
                            eta = "N/A";
                        }
                    }

                    int id = record.optInt("id", 0);

                    DownloadQueueItem item = new DownloadQueueItem(id, title, sizeFormatted, status, progress, speed, eta);
                    items.add(item);
                }

                Platform.runLater(() -> {
                    queueItems.clear();
                    queueItems.addAll(items);
                    activeDownloadsLabel.setText(String.valueOf(items.size()));
                    addLogEntry("Info", "Queue", "Updated download queue: " + items.size() + " items");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    addLogEntry("Error", "Queue", "Failed to fetch download queue: " + e.getMessage());
                });
            }
        }, executorService);
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

    private CompletableFuture<Void> fetchRecentLogs() {
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(3);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                List<LogEntry> newLogs = new ArrayList<>();
                LocalDateTime now = LocalDateTime.now();
                String time = now.format(logTimeFormatter);

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

                                        if (downloadSpeed > 5_000_000) {
                                            newLogs.add(new LogEntry(time, "Info", source,
                                                    "High download speed: " + formatBytes((long) downloadSpeed) + "/s"));
                                        }

                                        if (uploadSpeed > 2_000_000) {
                                            newLogs.add(new LogEntry(time, "Info", source,
                                                    "High upload speed: " + formatBytes((long) uploadSpeed) + "/s"));
                                        }
                                    }
                                }

                                if (previousBytesReceived != null && previousBytesSent != null) {
                                    previousBytesReceived.put(networkKey, receivedBytes);
                                    previousBytesSent.put(networkKey, sentBytes);
                                    lastUpdateTimestamp = currentTime;
                                }
                            }
                        } catch (Exception e) {

                            newLogs.add(new LogEntry(time, "Error", "Network",
                                    "Error processing network data: " + e.getMessage()));
                        }
                    }
                }
                if (newLogs.isEmpty()) {
                    newLogs.add(new LogEntry(time, "Info", "System", "System running normally"));
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
                    System.out.println(e.getMessage());
                });
            }
        }, executorService);
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
                long totalMemory = memoryData != null ? memoryData.optLong("totalMemory", 0) : 0;
                long usedMemory = memoryData != null ? memoryData.optLong("usedMemory", 0) : 0;
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
                        HttpRequest sonarrSystemRequest = HttpRequest.newBuilder()
                                .uri(URI.create(serverUrl.get() + "/api/v3/system/status"))
                                .header("X-Api-Key", apiKey.get())
                                .GET()
                                .build();
                        HttpResponse<String> sonarrSystemResponse = httpClient.send(sonarrSystemRequest, HttpResponse.BodyHandlers.ofString());
                        if (sonarrSystemResponse.statusCode() == 200) {
                            JSONObject sonarrSystemInfo = new JSONObject(sonarrSystemResponse.body());
                            version = sonarrSystemInfo.optString("version", "N/A");
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

        Platform.runLater(() -> {
            filteredLogEntries.clear();

            List<LogEntry> logEntriesCopy;
            synchronized (logEntries) {
                logEntriesCopy = new ArrayList<>(logEntries);
            }

            for (LogEntry entry : logEntriesCopy) {
                if ("All".equals(selectedLevel) || selectedLevel.equals(entry.getLevel())) {
                    filteredLogEntries.add(entry);
                }
            }
        });
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
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @FXML
    public void onConnectClicked() {
        String inputUrl = serverUrlField.getText().trim();
        String apiKey = apiKeyField.getText().trim();

        if (inputUrl.isEmpty() || apiKey.isEmpty()) {

            serverStatusLabel.textProperty().unbind();
            serverStatusLabel.styleProperty().unbind();

            if (inputUrl.isEmpty() && apiKey.isEmpty()) {
                addLogEntry("Error", "Connection", "Missing server URL and API key");
                serverStatusLabel.setText("Failed: Missing server URL and API key");
            } else if (inputUrl.isEmpty()) {
                addLogEntry("Error", "Connection", "Missing server URL");
                serverStatusLabel.setText("Failed: Missing server URL");
            } else {
                addLogEntry("Error", "Connection", "Missing API key");
                serverStatusLabel.setText("Failed: Missing API key");
            }
            serverStatusLabel.setStyle("-fx-text-fill: red;");

            PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(event -> {
                rebindServerStatusLabel();
            });
            pause.play();
            return;
        }

        serverStatusLabel.textProperty().unbind();
        serverStatusLabel.styleProperty().unbind();
        serverStatusLabel.setText("Connecting...");
        serverStatusLabel.setStyle("-fx-text-fill: orange;");

        applySettings(inputUrl, apiKey);

        PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(event -> {
            rebindServerStatusLabel();
        });
        pause.play();
    }

    private void rebindServerStatusLabel() {
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
    }

    public void applySettings(String newApiUrl, String apikey) {
        if (!newApiUrl.startsWith("http://") && !newApiUrl.startsWith("https://")) {
            newApiUrl = "http://" + newApiUrl;
        }
        serverUrlField.setText(newApiUrl);
        apiKeyField.setText(apikey);
        updateProperty("sonarr-apiUrl", newApiUrl);
        serverUrl.setValue(newApiUrl);
        updateProperty("sonarr-apiKey", apikey);
        apiKey.setValue(apikey);
        connectToServer();
        try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(fos, "Updated by user");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }
}
