package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.NetworkData;
import com.tfg.dashboard_tfg.model.ProcessedData;
import eu.hansolo.tilesfx.Section;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import javafx.animation.ScaleTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static javafx.scene.control.PopupControl.USE_COMPUTED_SIZE;

public class DashboardController {
    @FXML
    private Tile systemStatusTile;
    @FXML
    private Tile storageTile;
    @FXML
    private Tile networkTile;
    @FXML
    private Tile cpuTile;
    @FXML
    private Tile memoryTile;
    @FXML
    private Tile temperatureTile;
    @FXML
    private Tile jellyfinStatusTile;
    @FXML
    private Tile dockerStatusTile;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField apiUrlField;
    @FXML
    private Button applyUrlButton;

    private List<Tile> tileList;
    private ScheduledExecutorService scheduler;
    private Map<String, Long> previousNetworkBytes = new HashMap<>();

    // API URL property
    private StringProperty apiBaseUrl = new SimpleStringProperty("");
    private final Properties appProperties = new Properties();
    private final File configFile = new File("connection.properties");

    public void loadProperties() {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            appProperties.load(fis);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    public void applySettings(String newApiUrl) {
        if (!newApiUrl.startsWith("http://") && !newApiUrl.startsWith("https://")) {
            newApiUrl = "http://" + newApiUrl;
        }

        appProperties.setProperty("monitoringApi", newApiUrl);

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            appProperties.store(fos, "Updated by user");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    // Add these fields to your DashboardController class
    private Tile expandedTile = null;
    private GridPane originalParent = null;
    private int originalColIndex = 0;
    private int originalRowIndex = 0;
    private Node[] hiddenTiles = null;

    // Method to update tile colors based on theme
    private void updateTileColors(boolean isDarkMode) {
        for (Tile tile : tileList) {
            if (isDarkMode) {
                // Dark theme colors
                tile.setBackgroundColor(Color.web("#282832")); // Dark theme background
                tile.setBarColor(Color.web("#bcc0cc"));
                tile.setTitleColor(Color.web("#bcc0cc"));
                tile.setNeedleColor(Color.web("#5c5f77"));
                tile.setValueColor(Color.web("#bcc0cc"));
                tile.setDescriptionColor(Color.web("#bcc0cc"));
                tile.setUnitColor(Color.web("#bcc0cc"));
            } else {
                // Light theme colors
                tile.setBackgroundColor(Color.web("#DADADC")); // Light theme background
                tile.setBarColor(Color.web("#5c5f77"));
                tile.setTitleColor(Color.web("#5c5f77"));
                tile.setNeedleColor(Color.web("#5c5f77"));
                tile.setValueColor(Color.web("#5c5f77"));
                tile.setDescriptionColor(Color.web("#5c5f77"));
                tile.setUnitColor(Color.web("#5c5f77"));
            }
        }
    }

    @FXML
    public void initialize() {
        temperatureTile.getSections().clear();
        temperatureTile.getSections().add(new Section(0, 65, "Normal", Color.web("#28b745")));
        temperatureTile.getSections().add(new Section(65, 80, "Warning", Color.web("#ffc107")));
        temperatureTile.getSections().add(new Section(80, 100, "Critical", Color.web("dc3545")));
        if (jellyfinStatusTile.isActive()) {
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        } else {
            statusLabel.setText("there is problem with server");
            statusLabel.setTextFill(Color.web("#dc3545"));
        }
        tileList = Arrays.asList(systemStatusTile, storageTile, networkTile, cpuTile,
                memoryTile, temperatureTile, jellyfinStatusTile, dockerStatusTile);
        //inicializa el color inicio de tiles
        updateTileColors(Controller.darkMode.getValue());
        //
        Controller.darkMode.addListener(this::changed);

        // Initialize the API URL field with the current value
        loadProperties();
        apiUrlField.setText(appProperties.getProperty("monitoringApi", ""));

        // Schedule data updates
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::fetchAndUpdateData, 0, 5, TimeUnit.SECONDS);
        ChartData chartData = new ChartData("Network", 0);
        networkTile.addChartData(chartData);

    }

    @FXML
    public void onApplyUrlClicked() {
        String inputUrl = apiUrlField.getText().trim();
        applySettings(inputUrl);
        if (!inputUrl.isEmpty()) {
            apiBaseUrl.set(inputUrl);
            // Force immediate data refresh
            fetchAndUpdateData();
        }
    }

    // Helper method to get current API URLs
    private String getSystemUrl() {
        return apiBaseUrl.get();
    }

    private String getCpuUrl() {
        return apiBaseUrl.get() + "/cpu";
    }

    private String getMemoryUrl() {
        return apiBaseUrl.get() + "/memory";
    }

    private String getDisksUrl() {
        return apiBaseUrl.get() + "/disks";
    }

    private String getNetworkUrl() {
        return apiBaseUrl.get() + "/network";
    }

    private void fetchAndUpdateData() {
        try {
            // Step 1: Fetch data in background thread
            JSONObject systemData = fetchJsonData();

            // Step 2: Process all data in background thread
            final ProcessedData processedData = processAllData(systemData);

            // Step 3: Update UI on the JavaFX thread with all processed data
            javafx.application.Platform.runLater(() -> updateAllTiles(processedData));
        } catch (Exception e) {
            System.err.println("Error in background processing: " + e.getMessage());
            e.printStackTrace();

            // Show error in UI
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText("Error connecting to API: " + e.getMessage());
                statusLabel.setTextFill(Color.web("#dc3545"));
            });
        }
    }

    // Process all data off the UI thread
    private ProcessedData processAllData(JSONObject systemData) {
        ProcessedData data = new ProcessedData();

        // Process CPU data
        JSONObject cpuData = systemData.getJSONObject("cpu");
        data.cpuUsage = processCpuUsage(cpuData);
        data.cpuDescription = String.format("System CPU Load: %.2f%%", data.cpuUsage);

        // Process Memory data
        JSONObject memoryData = systemData.getJSONObject("memory");
        data.memoryUsage = memoryData.getDouble("memoryUsagePercentage");
        double totalMemoryGB = memoryData.getDouble("totalMemory") / (1024 * 1024 * 1024);
        double usedMemoryGB = memoryData.getDouble("usedMemory") / (1024 * 1024 * 1024);
        data.memoryDescription = String.format("%.2f GB / %.2f GB (%.1f%%)",
                usedMemoryGB, totalMemoryGB, data.memoryUsage);

        // Process Storage data
        JSONArray disksData = systemData.getJSONArray("disks");
        double totalSpace = 0;
        double usedSpace = 0;
        for (int i = 0; i < disksData.length(); i++) {
            JSONObject disk = disksData.getJSONObject(i);
            totalSpace += disk.getDouble("totalSpace");
            usedSpace += disk.getDouble("usedSpace");
        }
        data.storagePercentage = (usedSpace / totalSpace) * 100;
        double totalSpaceGB = totalSpace / (1024 * 1024 * 1024);
        double usedSpaceGB = usedSpace / (1024 * 1024 * 1024);
        data.storageDescription = String.format("%.2f GB / %.2f GB (%.1f%%)",
                usedSpaceGB, totalSpaceGB, data.storagePercentage);

        // Process Network data
        data.networkData = processNetworkData(systemData.getJSONObject("network"));

        // Process System Health
        data.systemHealth = 100 - ((data.cpuUsage + data.memoryUsage) / 2);
        if (data.systemHealth > 75) {
            data.systemHealthDescription = "System Health: Excellent";
        } else if (data.systemHealth > 50) {
            data.systemHealthDescription = "System Health: Good";
        } else if (data.systemHealth > 25) {
            data.systemHealthDescription = "System Health: Fair";
        } else {
            data.systemHealthDescription = "System Health: Poor";
        }

        // Process Docker data (based on CPU)
        data.dockerUsage = data.cpuUsage * 0.8;
        data.dockerDescription = "Docker CPU Usage: " + String.format("%.2f", data.dockerUsage) + "%";

        // Process Temperature data (simulated)
        data.temperature = 40 + (data.cpuUsage * 0.6);
        data.temperatureDescription = "CPU Temperature: " + String.format("%.1f", data.temperature) + "°C";

        // Toggle Jellyfin status for demo (in real app, you would check actual status)
        data.jellyfinActive = !jellyfinStatusTile.isActive();

        return data;
    }

    // Helper method for processing CPU data
    private double processCpuUsage(JSONObject cpuData) {
        JSONArray perProcessorLoad = cpuData.getJSONArray("perProcessorLoad");
        double systemCpuLoad = cpuData.getDouble("systemCpuLoad");
        double totalLoad = 0;
        for (int i = 0; i < perProcessorLoad.length(); i++) {
            totalLoad += perProcessorLoad.getDouble(i);
        }
        return systemCpuLoad;
    }

    // Process network data
    private NetworkData processNetworkData(JSONObject networkData) {
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

        // Calculate network traffic rate
        String networkKey = "total";
        if (previousNetworkBytes.containsKey(networkKey)) {
            long previousBytes = previousNetworkBytes.get(networkKey);
            long bytesDifference = data.currentBytes - previousBytes;
            // Convert to KB/s (divided by time between updates)
            data.kbPerSecond = bytesDifference / 1024.0 / 5; // 5 seconds is the update interval
        }
        previousNetworkBytes.put(networkKey, data.currentBytes);

        // Format network usage description
        double receivedMB = totalBytesReceived / (1024.0 * 1024.0);
        double sentMB = totalBytesSent / (1024.0 * 1024.0);
        data.description = String.format("Recv: %.2f MB | Sent: %.2f MB", receivedMB, sentMB);

        return data;
    }

    // Update all UI components with the processed data
    private void updateAllTiles(ProcessedData data) {
        cpuTile.setValue(data.cpuUsage);
        cpuTile.setDescription(data.cpuDescription);

        memoryTile.setValue(data.memoryUsage);
        memoryTile.setDescription(data.memoryDescription);

        storageTile.setValue(data.storagePercentage);
        storageTile.setDescription(data.storageDescription);

        if (data.networkData.kbPerSecond > 0) {
            double value = data.networkData.kbPerSecond;
            String unit;

//            if (value >= 1000) {
//                System.out.println("value with unit: " + value + "kb (" + (value / 1000) + "mb)");
//            } else {
//                System.out.println("value with unit: " + value + "kb");
//            }
            double lastValue = networkTile.getChartData().get(networkTile.getChartData().size() - 1).getValue();
            ChartData chartData = new ChartData("Network", value);
            networkTile.addChartData(chartData);

            if (networkTile.getChartData().size() > 15) {
                networkTile.getChartData().remove(0);
            }
            if (lastValue >= 1000) {
                unit = "Mbps";
                networkTile.setValue(lastValue / 1000);
            } else {
                unit = "Kbps";
                networkTile.setValue(lastValue);
            }
//            System.out.println("lastvalue " + lastValue);
//            System.out.println("is last value mb " + (lastValue >= 1000));
            networkTile.setUnit(unit);
            networkTile.setDecimals(2);
        }
        networkTile.setDescription(data.networkData.description);

        // Update System Status tile
        systemStatusTile.setValue(data.systemHealth);
        systemStatusTile.setDescription(data.systemHealthDescription);

        // Update Docker Status tile
        dockerStatusTile.setValue(data.dockerUsage);
        dockerStatusTile.setDescription(data.dockerDescription);

        // Update Temperature tile
        temperatureTile.setValue(data.temperature);
        temperatureTile.setDescription(data.temperatureDescription);

        // Update Jellyfin Status tile
        jellyfinStatusTile.setActive(data.jellyfinActive);
        if (data.jellyfinActive) {
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        } else {
            statusLabel.setText("there is problem with server");
            statusLabel.setTextFill(Color.web("#dc3545"));
        }
    }

    public JSONObject fetchJsonData() {
        String apiUrl = appProperties.getProperty("monitoringApi");
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new RuntimeException("apiUrl not configured");
        }

        // Ensure protocol
        if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
            apiUrl = "http://" + apiUrl;
        }

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return new JSONObject(response.toString());
            } else {
                throw new RuntimeException("HTTP GET request failed with response code: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("Error fetching data from " + apiUrl + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public void ondrag(MouseEvent mouseEvent) {
        System.out.println(mouseEvent.getClass());
        System.out.println("ondrag");
    }

    public void ondragdrop(DragEvent dragEvent) {
        System.out.println("ondragdrop");
    }

    public void onscroll(ScrollEvent scrollEvent) {
        System.out.println("onscroll");
    }

    @FXML
    public void onclicktile(MouseEvent event) {
        // Get the clicked tile
        Tile clickedTile = (Tile) event.getSource();

        // If there's already an expanded tile
        if (expandedTile != null) {
            // If clicking the already expanded tile, restore the original layout
            if (expandedTile == clickedTile) {
                restoreOriginalLayout();
            }
            // If clicking a different tile while one is expanded, restore and then expand the new one
            else {
                restoreOriginalLayout();
                expandTile(clickedTile);
            }
        }
        // If no tile is expanded yet, expand the clicked one
        else {
            expandTile(clickedTile);
        }
    }

    private void expandTile(Tile tile) {
        // Save the expanded tile reference
        expandedTile = tile;

        // Get parent GridPane
        originalParent = (GridPane) tile.getParent();

        // Store original dimensions
        double originalGridWidth = originalParent.getWidth();
        double originalGridHeight = originalParent.getHeight();
        double originalTileWidth = tile.getWidth();
        double originalTileHeight = tile.getHeight();

        // Set minimum size for the GridPane to prevent resizing
        originalParent.setMinWidth(originalGridWidth);
        originalParent.setMinHeight(originalGridHeight);
        originalParent.setPrefWidth(originalGridWidth);
        originalParent.setPrefHeight(originalGridHeight);

        // Save original position
        originalColIndex = GridPane.getColumnIndex(tile) != null ? GridPane.getColumnIndex(tile) : 0;
        originalRowIndex = GridPane.getRowIndex(tile) != null ? GridPane.getRowIndex(tile) : 0;

        // Store all other tiles to hide them
        hiddenTiles = new Node[originalParent.getChildren().size() - 1];
        int index = 0;

        // Remove all other tiles from the GridPane but save them
        for (Node node : new ArrayList<>(originalParent.getChildren())) {
            if (node != tile) {
                originalParent.getChildren().remove(node);
                hiddenTiles[index++] = node;
            }
        }

        // Make the clicked tile span the entire GridPane
        GridPane.setColumnSpan(tile, originalParent.getColumnConstraints().size());
        GridPane.setRowSpan(tile, originalParent.getRowConstraints().size());
        GridPane.setColumnIndex(tile, 0);
        GridPane.setRowIndex(tile, 0);

        // Force the tile to fill the available space
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);

        // Optional: Add animation effect for smooth transition
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(300), tile);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.02);
        scaleTransition.setToY(1.02);
        scaleTransition.setCycleCount(2);
        scaleTransition.setAutoReverse(true);
        scaleTransition.play();

        // Optional: Change the title to indicate the expanded state
        tile.setDescription(tile.getDescription() + " (Click to minimize)");
    }

    private void restoreOriginalLayout() {
        if (expandedTile == null) return;

        // Reset the expanded tile's position and span
        GridPane.setColumnSpan(expandedTile, 1);
        GridPane.setRowSpan(expandedTile, 1);
        GridPane.setColumnIndex(expandedTile, originalColIndex);
        GridPane.setRowIndex(expandedTile, originalRowIndex);

        // Reset the tile's size constraints
        expandedTile.setMaxWidth(USE_COMPUTED_SIZE);
        expandedTile.setMaxHeight(USE_COMPUTED_SIZE);

        // Restore the original description
        String currentDesc = expandedTile.getDescription();
        if (currentDesc.endsWith(" (Click to minimize)")) {
            expandedTile.setDescription(currentDesc.replace(" (Click to minimize)", ""));
        }

        // Add back all the hidden tiles
        for (Node node : hiddenTiles) {
            if (node != null) {
                originalParent.getChildren().add(node);
            }
        }

        // Clear the expanded tile reference
        expandedTile = null;
        hiddenTiles = null;
    }

    private void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        updateTileColors(newValue);
    }

    public StringProperty apiBaseUrlProperty() {
        return apiBaseUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl.get();
    }

    public void setApiBaseUrl(String url) {
        apiBaseUrl.set(url);
    }
}