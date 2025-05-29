package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.*;
import com.tfg.dashboard_tfg.services.QbittorrentApiClient;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DownloaderClientView implements Initializable {

    @FXML
    private GridPane connectionPane;
    @FXML
    private TextField hostField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button connectButton;
    @FXML
    private Label clientStatusLabel;
    @FXML
    private ToggleButton autoRefreshToggle;

    @FXML
    private Label activeTorrentsLabel;
    @FXML
    private Label totalTorrentsLabel;
    @FXML
    private Label downloadSpeedLabel;
    @FXML
    private Label uploadSpeedLabel;
    @FXML
    private Label sessionDownloadedLabel;
    @FXML
    private Label sessionUploadedLabel;
    @FXML
    private Label allTimeDownloadLabel;
    @FXML
    private Label allTimeUploadLabel;
    @FXML
    private Label ratioLabel;
    @FXML
    private Label freeSpaceLabel;
    @FXML
    private Label usedSpaceLabel;
    @FXML
    private Label qbittorrentVersionLabel;
    @FXML
    private Label lastUpdateLabel;

    @FXML
    private LineChart<String, Number> speedHistoryChart;
    @FXML
    private PieChart storageChart;

    @FXML
    private TableView<TorrentData> torrentTable;
    @FXML
    private TableColumn<TorrentData, String> nameColumn;
    @FXML
    private TableColumn<TorrentData, String> sizeColumn;
    @FXML
    private TableColumn<TorrentData, Double> progressColumn;
    @FXML
    private TableColumn<TorrentData, String> statusColumn;
    @FXML
    private TableColumn<TorrentData, String> downloadSpeedColumn;
    @FXML
    private TableColumn<TorrentData, String> uploadSpeedColumn;
    @FXML
    private TableColumn<TorrentData, String> etaColumn;
    @FXML
    private TableColumn<TorrentData, Double> ratioColumn;
    @FXML
    private TableColumn<TorrentData, String> actionsColumn;

    @FXML
    private TabPane detailsTabPane;
    @FXML
    private Label hashLabel;
    @FXML
    private Label savePathLabel;
    @FXML
    private Label creationDateLabel;
    @FXML
    private Label addedOnLabel;
    @FXML
    private Label lastActivityLabel;
    @FXML
    private Label timeActiveLabel;
    @FXML
    private Label downloadLimitLabel;
    @FXML
    private Label uploadLimitLabel;
    @FXML
    private Label connectionsLabel;
    @FXML
    private Button setDownloadLimitButton;
    @FXML
    private Button setUploadLimitButton;

    @FXML
    private TableView<TrackerData> trackersTable;
    @FXML
    private TableColumn<TrackerData, String> trackerUrlColumn;
    @FXML
    private TableColumn<TrackerData, String> trackerStatusColumn;
    @FXML
    private TableColumn<TrackerData, Integer> trackerTierColumn;
    @FXML
    private TableColumn<TrackerData, Integer> trackerPeersColumn;
    @FXML
    private TableColumn<TrackerData, Integer> trackerSeedsColumn;
    @FXML
    private TableColumn<TrackerData, Integer> trackerLeechesColumn;

    @FXML
    private TableView<FileData> filesTable;
    @FXML
    private TableColumn<FileData, String> fileNameColumn;
    @FXML
    private TableColumn<FileData, String> fileSizeColumn;
    @FXML
    private TableColumn<FileData, Double> fileProgressColumn;
    @FXML
    private TableColumn<FileData, String> filePriorityColumn;

    @FXML
    private TableView<PeerData> peersTable;
    @FXML
    private TableColumn<PeerData, String> peerAddressColumn;
    @FXML
    private TableColumn<PeerData, String> peerClientColumn;
    @FXML
    private TableColumn<PeerData, Double> peerProgressColumn;
    @FXML
    private TableColumn<PeerData, String> peerDownSpeedColumn;
    @FXML
    private TableColumn<PeerData, String> peerUpSpeedColumn;
    @FXML
    private TableColumn<PeerData, Double> peerRelevanceColumn;

    @FXML
    private ComboBox<String> statusFilter;

    private final ObservableList<TorrentData> torrents = FXCollections.observableArrayList();
    private final ObservableList<TrackerData> trackers = FXCollections.observableArrayList();
    private final ObservableList<FileData> files = FXCollections.observableArrayList();
    private final ObservableList<PeerData> peers = FXCollections.observableArrayList();

    private XYChart.Series<String, Number> downloadSeries;
    private XYChart.Series<String, Number> uploadSeries;
    private static final int MAX_DATA_POINTS = 20;
    private final Queue<String> timeLabels = new LinkedList<>();

    private ScheduledExecutorService scheduler;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private QbittorrentApiClient apiClient;

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty selectedTorrentHash = new SimpleStringProperty();
    private final Properties appProperties = new Properties();
    private static final String PROPERTIES_FILE = "connection.properties";

    public void loadProperties() {
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            appProperties.load(fis);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    private void updateProperty() {
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.setProperty("qbittorrent-url", hostField.getText());
            appProperties.setProperty("qbittorrent-Username", usernameField.getText());
            appProperties.setProperty("qbittorrent-Password", passwordField.getText());
            appProperties.store(out, "Prowlarr Dashboard Settings");
        } catch (Exception e) {
            clientStatusLabel.setText("Failed to save settings: " + e.getMessage());
            clientStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        apiClient = new QbittorrentApiClient();
        loadProperties();

        hostField.setText(appProperties.getProperty("qbittorrent-url"));
        usernameField.setText(appProperties.getProperty("qbittorrent-Username"));
        passwordField.setText(appProperties.getProperty("qbittorrent-Password"));
        connectButton.setOnAction(event -> connectToClient());

        initializeCharts();

        initializeTorrentTable();
        initializeTrackerTable();
        initializeFileTable();
        initializePeerTable();

        statusFilter.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> filterTorrents(newValue));

        torrentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedTorrentHash.set(newSelection.getHash());
                loadTorrentDetails(newSelection);
            }
        });

        setDownloadLimitButton.setOnAction(e -> showSpeedLimitDialog("download"));
        setUploadLimitButton.setOnAction(e -> showSpeedLimitDialog("upload"));
        connectToClient();
    }

    private void initializeCharts() {

        downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("Download");

        uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("Upload");

        speedHistoryChart.getData().add(downloadSeries);
        speedHistoryChart.getData().add(uploadSeries);

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(new PieChart.Data("Free", 100), new PieChart.Data("Used", 0));
        storageChart.setData(pieChartData);
    }

    private void initializeTorrentTable() {

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        progressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        downloadSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("downloadSpeed"));
        uploadSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("uploadSpeed"));
        etaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        ratioColumn.setCellValueFactory(new PropertyValueFactory<>("ratio"));

        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button pauseButton = new Button("Pause");
            private final Button resumeButton = new Button("Resume");
            private final Button deleteButton = new Button("Delete");

            {
                pauseButton.getStyleClass().add("table-button");
                resumeButton.getStyleClass().add("table-button");
                deleteButton.getStyleClass().add("table-button");

                pauseButton.setOnAction(event -> {
                    TorrentData torrent = getTableView().getItems().get(getIndex());
                    pauseTorrent(torrent.getHash());
                });

                resumeButton.setOnAction(event -> {
                    TorrentData torrent = getTableView().getItems().get(getIndex());
                    resumeTorrent(torrent.getHash());
                });

                deleteButton.setOnAction(event -> {
                    TorrentData torrent = getTableView().getItems().get(getIndex());
                    showDeleteConfirmation(torrent);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    TorrentData torrent = getTableView().getItems().get(getIndex());
                    HBox buttons = new HBox(5);
                    buttons.setAlignment(Pos.CENTER);

                    if ("Paused".equals(torrent.getStatus())) {
                        buttons.getChildren().add(resumeButton);
                    } else {
                        buttons.getChildren().add(pauseButton);
                    }

                    buttons.getChildren().add(deleteButton);
                    setGraphic(buttons);
                }
            }
        });

        torrentTable.setItems(torrents);
    }

    private void initializeTrackerTable() {
        trackerUrlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));
        trackerStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        trackerTierColumn.setCellValueFactory(new PropertyValueFactory<>("tier"));
        trackerPeersColumn.setCellValueFactory(new PropertyValueFactory<>("peers"));
        trackerSeedsColumn.setCellValueFactory(new PropertyValueFactory<>("seeds"));
        trackerLeechesColumn.setCellValueFactory(new PropertyValueFactory<>("leeches"));

        trackersTable.setItems(trackers);
    }

    private void initializeFileTable() {
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        fileProgressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        fileProgressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        filePriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));

        filesTable.setItems(files);
    }

    private void initializePeerTable() {
        peerAddressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        peerClientColumn.setCellValueFactory(new PropertyValueFactory<>("client"));
        peerProgressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        peerProgressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        peerDownSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("downloadSpeed"));
        peerUpSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("uploadSpeed"));
        peerRelevanceColumn.setCellValueFactory(new PropertyValueFactory<>("relevance"));

        peersTable.setItems(peers);
    }

    @FXML
    private void connectToClient() {
        String host = hostField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (hostField.getText().isEmpty()) {
            clientStatusLabel.setText("Please provide URL");
            clientStatusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        if (usernameField.getText().isEmpty()) {
            clientStatusLabel.setText("Please provide username and password");
            clientStatusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        if (passwordField.getText().isEmpty()) {
            clientStatusLabel.setText("Please provide URL");
            clientStatusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        if (!host.startsWith("http://")) {
            hostField.setText("http://" + hostField.getText());
            host = hostField.getText();
        }
        updateProperty();

        clientStatusLabel.setText("Connecting...");
        clientStatusLabel.setStyle("-fx-text-fill: orange;");

        boolean success = apiClient.login(host, username, password);

        if (success) {
            connected.set(true);
            updateUI();
            clientStatusLabel.setText("Connected");
            clientStatusLabel.setStyle("-fx-text-fill: green;");
            if (autoRefreshToggle.isSelected()) {
                startAutoRefresh();
            }
        } else {
            clientStatusLabel.setText("Connection Error: Failed to connect to qBittorrent");
            clientStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    private void refreshClientStatus() {
        if (connected.get()) {
            updateUI();
        } else {
            clientStatusLabel.setText("Not Connected: Please connect to qBittorrent first.");
            clientStatusLabel.setStyle("-fx-text-fill: orange;");
        }
    }

    @FXML
    private void toggleAutoRefresh() {
        if (autoRefreshToggle.isSelected()) {
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    }

    @FXML
    private void showAddTorrentDialog() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Client is not connected", "Please connect to qBittorrent first.");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add Torrent");
        dialog.setHeaderText("Enter magnet link or torrent URL");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField urlField = new TextField();
        urlField.setPromptText("Magnet link or URL");
        urlField.setPrefWidth(400);

        dialog.getDialogPane().setContent(urlField);

        Platform.runLater(urlField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return urlField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(url -> {
            if (!url.isEmpty()) {
                boolean success = apiClient.addTorrent(url);
                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Torrent added successfully", "The torrent has been added to qBittorrent.");
                    updateUI();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to add torrent", "Please check the URL and try again.");
                }
            }
        });
    }

    private void showDeleteConfirmation(TorrentData torrent) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Torrent");
        alert.setContentText("Are you sure you want to delete '" + torrent.getName() + "'?\n\nCheck to also delete files from disk:");

        CheckBox deleteFiles = new CheckBox("Delete Files");
        alert.getDialogPane().setContent(deleteFiles);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = apiClient.deleteTorrent(torrent.getHash(), deleteFiles.isSelected());
            if (success) {
                updateUI();
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete torrent", "An error occurred while trying to delete the torrent.");
            }
        }
    }

    private void showSpeedLimitDialog(String type) {
        if (selectedTorrentHash.get() == null) {
            return;
        }

        String title = type.equals("download") ? "Set Download Limit" : "Set Upload Limit";

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Enter speed limit in KiB/s (0 for unlimited)");

        ButtonType setButtonType = new ButtonType("Set", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(setButtonType, ButtonType.CANCEL);

        TextField limitField = new TextField();
        limitField.setPromptText("KiB/s");

        dialog.getDialogPane().setContent(limitField);

        Platform.runLater(limitField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == setButtonType) {
                return limitField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(limit -> {
            try {
                int limitValue = Integer.parseInt(limit);
                boolean success;

                if (type.equals("download")) {
                    success = apiClient.setTorrentDownloadLimit(selectedTorrentHash.get(), limitValue);
                } else {
                    success = apiClient.setTorrentUploadLimit(selectedTorrentHash.get(), limitValue);
                }

                if (success) {
                    loadTorrentDetails(torrentTable.getSelectionModel().getSelectedItem());
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to set limit", "An error occurred while trying to set the speed limit.");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid number", "The speed limit must be a whole number.");
            }
        });
    }

    private void pauseTorrent(String hash) {
        if (apiClient.pauseTorrent(hash)) {
            updateUI();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to pause torrent", "An error occurred while trying to pause the torrent.");
        }
    }

    private void resumeTorrent(String hash) {
        if (apiClient.resumeTorrent(hash)) {
            updateUI();
        } else {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to resume torrent", "An error occurred while trying to resume the torrent.");
        }
    }

    private void updateUI() {
        if (!connected.get()) {
            return;
        }

        TransferStats stats = apiClient.getTransferInfo();
        downloadSpeedLabel.setText(formatSpeed(stats.getDownloadSpeed()));
        uploadSpeedLabel.setText(formatSpeed(stats.getUploadSpeed()));
        sessionDownloadedLabel.setText(formatSize(stats.getSessionDownloaded()));
        sessionUploadedLabel.setText(formatSize(stats.getSessionUploaded()));
        allTimeDownloadLabel.setText(formatSize(stats.getAllTimeDownloaded()));
        allTimeUploadLabel.setText(formatSize(stats.getAllTimeUploaded()));
        ratioLabel.setText(String.format("%.2f", stats.getRatio()));

        ProcessedData data = DashboardController.processedData;
        freeSpaceLabel.setText("Free: " + String.format("%.2f", data.storageFreeSpace) + " GB");
        usedSpaceLabel.setText("Used: " + String.format("%.2f", data.storageUsedSpace) + " GB");

        updateStorageChart(data);

        String version = apiClient.getApiVersion();
        qbittorrentVersionLabel.setText("Version: " + version);

        List<TorrentData> torrentList = apiClient.getTorrents();
        int activeCount = 0;
        for (TorrentData torrent : torrentList) {
            if ("Downloading".equals(torrent.getStatus()) || "Uploading".equals(torrent.getStatus())) {
                activeCount++;
            }
        }

        activeTorrentsLabel.setText(String.valueOf(activeCount));
        totalTorrentsLabel.setText(String.valueOf(torrentList.size()));

        torrents.setAll(torrentList);

        String filter = statusFilter.getValue();
        if (filter != null && !filter.equals("All")) {
            filterTorrents(filter);
        }

        updateSpeedChart(stats.getDownloadSpeed(), stats.getUploadSpeed());

        lastUpdateLabel.setText("Last update: " + LocalDateTime.now().format(timeFormatter));
    }

    private void updateSpeedChart(long downloadSpeed, long uploadSpeed) {
        String timeLabel = LocalDateTime.now().format(timeFormatter);
        timeLabels.add(timeLabel);

        downloadSeries.getData().add(new XYChart.Data<>(timeLabel, downloadSpeed / 1024.0));
        uploadSeries.getData().add(new XYChart.Data<>(timeLabel, uploadSpeed / 1024.0));

        if (timeLabels.size() > MAX_DATA_POINTS) {
            timeLabels.remove();
            downloadSeries.getData().remove(0);
            uploadSeries.getData().remove(0);
        }
    }

    private void updateStorageChart(ProcessedData data) {
        storageChart.getData().get(0).setPieValue(100 - data.storagePercentage);
        storageChart.getData().get(1).setPieValue(data.storagePercentage);
    }

    private void loadTorrentDetails(TorrentData torrent) {
        if (torrent == null) {
            return;
        }

        TorrentDetails details = apiClient.getTorrentDetails(torrent.getHash());
        hashLabel.setText(details.getHash());
        savePathLabel.setText(details.getSavePath());
        creationDateLabel.setText(details.getCreationDate());
        addedOnLabel.setText(details.getAddedOn());
        lastActivityLabel.setText(details.getLastActivity());
        timeActiveLabel.setText(details.getTimeActive());
        downloadLimitLabel.setText(formatSpeed(details.getDownloadLimit()) + "/s");
        uploadLimitLabel.setText(formatSpeed(details.getUploadLimit()) + "/s");
        connectionsLabel.setText(String.valueOf(details.getConnections()));

        List<TrackerData> trackerList = apiClient.getTorrentTrackers(torrent.getHash());
        trackers.setAll(trackerList);

        List<FileData> fileList = apiClient.getTorrentFiles(torrent.getHash());
        files.setAll(fileList);

        List<PeerData> peerList = apiClient.getTorrentPeers(torrent.getHash());
        peers.setAll(peerList);
    }

    private void filterTorrents(String status) {
        if (status == null || status.equals("All")) {
            torrentTable.setItems(torrents);
            return;
        }

        ObservableList<TorrentData> filteredList = FXCollections.observableArrayList();
        for (TorrentData torrent : torrents) {
            if (torrent.getStatus().equals(status)) {
                filteredList.add(torrent);
            }
        }

        torrentTable.setItems(filteredList);
    }

    private void startAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            stopAutoRefresh();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                Platform.runLater(this::updateUI);
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopAutoRefresh() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KiB/s", bytesPerSecond / 1024.0);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.2f MiB/s", bytesPerSecond / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GiB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KiB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MiB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
