package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.NetworkData;
import com.tfg.dashboard_tfg.model.ProcessedData;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import eu.hansolo.tilesfx.skins.BarChartItem;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.Chart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
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
    private Tile uptimeTile;
    @FXML
    private Tile jellyfinStatusTile;
    @FXML
    private Tile dockerStatusTile;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField apiUrlField;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private Button applyUrlButton;

    private List<Tile> tileList;
    private ScheduledExecutorService scheduler;
    private Map<String, Long> previousNetworkBytes = new HashMap<>();

    private final Properties appProperties = new Properties();
    private final File PROPERTIES_FILE = new File("connection.properties");
    private String dockerApiUrl;
    public static ProcessedData processedData = new ProcessedData();

    private Tile expandedTile = null;
    private GridPane originalParent = null;
    private int originalColIndex = 0;
    private int originalRowIndex = 0;
    private Node[] hiddenTiles = null;
    private String apiUrl;

    //load property
    public void loadProperties() {
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            appProperties.load(fis);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    //apply property
    public void applySettings(String newApiUrl) {
        if (!newApiUrl.startsWith("http://") && !newApiUrl.startsWith("https://")) {
            newApiUrl = "http://" + newApiUrl;
        }
        updateProperty("monitoringApi", newApiUrl);
        apiUrl = newApiUrl;
        String serverUrlPort = apiUrl.split("/")[2];
        String serverIp = serverUrlPort.split(":")[0];
        updateProperty("dockerApi", serverIp);
        try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(fos, "Updated by user");
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    private void updateTileColors(boolean isDarkMode) {
        for (Tile tile : tileList) {
            if (isDarkMode) {
                tile.setBackgroundColor(Color.web("#282832"));
                tile.setBarColor(Color.web("#bcc0cc"));
                tile.setTitleColor(Color.web("#bcc0cc"));
                tile.setNeedleColor(Color.web("#5c5f77"));
                tile.setValueColor(Color.web("#bcc0cc"));
                tile.setDescriptionColor(Color.web("#bcc0cc"));
                tile.setUnitColor(Color.web("#bcc0cc"));
            } else {
                tile.setBackgroundColor(Color.web("#DADADC"));
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
        showLoading(true);
        if (jellyfinStatusTile.isActive()) {
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        }
        tileList = Arrays.asList(systemStatusTile, storageTile, networkTile, cpuTile,
                memoryTile, uptimeTile, jellyfinStatusTile, dockerStatusTile);
        updateTileColors(Controller.darkMode.getValue());

        Controller.darkMode.addListener(this::changed);

        loadProperties();
        apiUrlField.setText(appProperties.getProperty("monitoringApi", ""));

        scheduler = Executors.newSingleThreadScheduledExecutor();
        ChartData chartData = new ChartData("Network", 0);
        networkTile.addChartData(chartData);
        scheduler.scheduleAtFixedRate(() -> {
            fetchAndUpdateData();

            Platform.runLater(() -> {
                        showLoading(false);
                    });
        }, 0, 3, TimeUnit.SECONDS);
    }


    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        loadingOverlay.setManaged(show);
    }

    @FXML
    public void onApplyUrlClicked() {
        String inputUrl = apiUrlField.getText().trim();
        applySettings(inputUrl);
        if (!inputUrl.isEmpty()) {
            updateProperty("monitoringApi", inputUrl);
        }
    }

    public void updateProperty(String key, String value) {
        loadProperties();
        appProperties.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(out, "Updated by user");
            statusLabel.setText("Updated property: " + key);
            statusLabel.setTextFill(Color.web("#28a745"));
        } catch (IOException e) {
            statusLabel.setText("Failed to save property: " + e.getMessage());
        }
    }

    //<editor-fold desc="Data handling and tiles update">
    private void fetchAndUpdateData() {
        try {
            JSONObject systemData = fetchJsonData();
            if (systemData == null) {
                return;
            }
            processedData = processAllData(systemData);

            javafx.application.Platform.runLater(() -> updateAllTiles(processedData));
        } catch (Exception e) {
            System.err.println("Error in background processing: " + e.getMessage());
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText("Error connecting to API: " + e.getMessage());
                statusLabel.setTextFill(Color.web("#dc3545"));
            });
        }
    }

    private ProcessedData processAllData(JSONObject systemData) {
        ProcessedData data = new ProcessedData();

        JSONObject cpuData = systemData.getJSONObject("cpu");
        data.cpuUsage = processCpuUsage(cpuData);
        data.cpuDescription = String.format("System CPU Load: %.2f%%", data.cpuUsage);

        JSONObject memoryData = systemData.getJSONObject("memory");
        data.memoryUsage = memoryData.getDouble("memoryUsagePercentage");
        double totalMemoryGB = memoryData.getDouble("totalMemory") / (1024 * 1024 * 1024);
        double usedMemoryGB = memoryData.getDouble("usedMemory") / (1024 * 1024 * 1024);
        data.memoryDescription = String.format("%.2f GB / %.2f GB (%.1f%%)",
                usedMemoryGB, totalMemoryGB, data.memoryUsage);

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
        data.storageUsedSpace = usedSpaceGB;
        data.storageFreeSpace = totalSpaceGB - usedSpaceGB;
        data.storageDescription = String.format("%.2f GB / %.2f GB (%.1f%%)",
                usedSpaceGB, totalSpaceGB, data.storagePercentage);

        data.networkData = processNetworkData(systemData.getJSONObject("network"));

        data.systemHealth = 100 - ((data.cpuUsage + data.memoryUsage) / 2);
        if (data.systemHealth > 75) {
            data.systemHealthDescription = "System Health: Excellent";
            Platform.runLater(() -> {
                statusLabel.setText("System is running optimally.");
                statusLabel.setTextFill(Color.web("#28a745"));
            });
        } else if (data.systemHealth > 50) {
            data.systemHealthDescription = "System Health: Good";
            Platform.runLater(() -> {
                statusLabel.setText("System is healthy.");
                statusLabel.setTextFill(Color.web("#218838"));
            });
        } else if (data.systemHealth > 25) {
            data.systemHealthDescription = "System Health: Fair";
            Platform.runLater(() -> {
                statusLabel.setText("System performance is degrading.");
                statusLabel.setTextFill(Color.web("#ffc107"));
            });
        } else {
            data.systemHealthDescription = "System Health: Poor";
            Platform.runLater(() -> {
                statusLabel.setText("Warning: System health is critical!");
                statusLabel.setTextFill(Color.web("#dc3545"));
            });
        }


        data.uptime = getFirstContainerUptime(data);

        data.uptimeDescription = "Container Runtime";

        data.jellyfinActive = data.uptime.startsWith("Up ");

        return data;
    }

    private String getFirstContainerUptime(ProcessedData data) {
        String apiPath = "/containers/json?all=true";
        loadProperties();
        String url = appProperties.getProperty("dockerApi");
        if (url == null || url.trim().isEmpty()) {
            statusLabel.setText("please provide Docker monitoring URL");
            statusLabel.setTextFill(Color.web("#dc3545"));
        }
        int portNum = 2375;
        dockerApiUrl = "http://" + url + ":" + portNum;
        String uptime = "";
        try {
            URL dockerUrl = new URL(dockerApiUrl + apiPath);
            HttpURLConnection connection = (HttpURLConnection) dockerUrl.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                statusLabel.setText("error fetching server info: API Error");
                statusLabel.setTextFill(Color.web("#dc3545"));
                return "API Error";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONArray jsonArray = new JSONArray(response.toString());

            if (!jsonArray.isEmpty()) {
                JSONObject containerJson = jsonArray.getJSONObject(0);

                data.jellyfinUsage = 0;
                data.jellyseerUsage = 0;
                data.qbittorrentUsage = 0;
                data.prowlarrUsage = 0;
                data.sonarrUsage = 0;

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonContainer = jsonArray.getJSONObject(i);
                    String containerId = jsonContainer.getString("Id");
                    String containerName = jsonContainer.getJSONArray("Names").getString(0).replaceAll("^/", "").toLowerCase();

                    double cpuUsage = fetchCpuStatsViaAPI(containerId);

                    if (containerName.contains("jellyfin")) {
                        data.jellyfinUsage = cpuUsage;
                    } else if (containerName.contains("jellyseer")) {
                        data.jellyseerUsage = cpuUsage;
                    } else if (containerName.contains("qbittorrent")) {
                        data.qbittorrentUsage = cpuUsage;
                    } else if (containerName.contains("prowlarr")) {
                        data.prowlarrUsage = cpuUsage;
                    } else if (containerName.contains("sonarr")) {
                        data.sonarrUsage = cpuUsage;
                    }
                }
                String status = containerJson.getString("Status");
                if (status.startsWith("Up ")) {
                    uptime = "Up for " + status.replaceAll("^Up ", "").split("\\(")[0].trim();
                } else {
                    uptime = "Down";
                }
            }
        } catch (Exception e) {
            uptime = "Error: " + e.getMessage();
        }

        return uptime;
    }

    private double fetchCpuStatsViaAPI(String containerId) throws Exception {
        URL url = new URL(dockerApiUrl + "/containers/" + containerId + "/stats?stream=false");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("API Error: HTTP " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            JSONObject json = new JSONObject(response.toString());
            JSONObject cpuStats = json.getJSONObject("cpu_stats");
            JSONObject preCpuStats = json.getJSONObject("precpu_stats");

            long cpuDelta = cpuStats.getJSONObject("cpu_usage").getLong("total_usage") -
                    preCpuStats.getJSONObject("cpu_usage").getLong("total_usage");


            long systemDelta = cpuStats.getLong("system_cpu_usage") -
                    preCpuStats.getLong("system_cpu_usage");

            int numCPUs = cpuStats.getInt("online_cpus");
            double cpuUsage = 0.0;
            if (systemDelta > 0 && cpuDelta > 0) {
                cpuUsage = ((double) cpuDelta / systemDelta) * numCPUs * 100.0;
            }
            return cpuUsage;
        }
    }

    private double processCpuUsage(JSONObject cpuData) {
        JSONArray perProcessorLoad = cpuData.getJSONArray("perProcessorLoad");
        double systemCpuLoad = cpuData.getDouble("systemCpuLoad");
        double totalLoad = 0;
        for (int i = 0; i < perProcessorLoad.length(); i++) {
            totalLoad += perProcessorLoad.getDouble(i);
        }
        return systemCpuLoad;
    }

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

        String networkKey = "total";
        if (previousNetworkBytes.containsKey(networkKey)) {
            long previousBytes = previousNetworkBytes.get(networkKey);
            long bytesDifference = data.currentBytes - previousBytes;
            data.kbPerSecond = bytesDifference / 1024.0 / 5;
        }
        previousNetworkBytes.put(networkKey, data.currentBytes);

        double receivedMB = totalBytesReceived / (1024.0 * 1024.0);
        double sentMB = totalBytesSent / (1024.0 * 1024.0);
        data.description = String.format("Recv: %.2f MB | Sent: %.2f MB", receivedMB, sentMB);

        return data;
    }

    private void updateAllTiles(ProcessedData data) {
        if (data.cpuUsage == 0) {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> {
                statusLabel.setText("there is problem server monitoring");
                statusLabel.setTextFill(Color.web("#dc3545"));
            });
            pause.play();
        }
        if (data.uptime.startsWith("Error: ")) {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> {
                statusLabel.setText("there is problem with docker: Unable to fetch data");
                statusLabel.setTextFill(Color.web("#dc3545"));
            });
            pause.play();
        }
        cpuTile.setValue(data.cpuUsage);
        cpuTile.setDescription(data.cpuDescription);

        memoryTile.setValue(data.memoryUsage);
        memoryTile.setDescription(data.memoryDescription);

        storageTile.setValue(data.storagePercentage);
        storageTile.setDescription(data.storageDescription);

        if (data.networkData.kbPerSecond > 0) {
            double value = data.networkData.kbPerSecond;
            String unit;

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

            networkTile.setUnit(unit);
            networkTile.setDecimals(2);
        }
        networkTile.setDescription(data.networkData.description);

        systemStatusTile.setText(data.systemHealthDescription);
        systemStatusTile.setDescription("System Health");

        dockerStatusTile.getBarChartItems().clear();
        List<BarChartItem> barChartItems = new ArrayList<>();
        barChartItems.add(new BarChartItem("Jellyfin", data.jellyfinUsage * 100 > 100 ? 100 : data.jellyfinUsage * 100, Tile.RED));
        barChartItems.add(new BarChartItem("Jellyseer", data.jellyseerUsage * 100 > 100 ? 100 : data.jellyseerUsage * 100, Tile.BLUE));
        barChartItems.add(new BarChartItem("QBit", data.qbittorrentUsage * 100 > 100 ? 100 : data.qbittorrentUsage * 100, Tile.GREEN));
        barChartItems.add(new BarChartItem("Prowlarr", data.prowlarrUsage * 100 > 100 ? 100 : data.prowlarrUsage * 100, Tile.ORANGE));
        barChartItems.add(new BarChartItem("Sonarr", data.sonarrUsage * 100 > 100 ? 100 : data.sonarrUsage * 100, Tile.MAGENTA));
        dockerStatusTile.setBarChartItems(barChartItems);
        dockerStatusTile.setDescription("Docker Usage: " + String.format("%.2f", data.dockerUsage) + "%");

        uptimeTile.setText(String.valueOf(data.uptime));
        uptimeTile.setDescription("Server Is");

        jellyfinStatusTile.setActive(data.jellyfinActive);
        if (data.jellyfinActive) {
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        }
    }

    public JSONObject fetchJsonData() {
        apiUrl = appProperties.getProperty("monitoringApi");
        if (apiUrl == null || apiUrl.isEmpty()) {
            statusLabel.setText("Monitoring URL empty: Please provide Correct URL");
            statusLabel.setTextFill(Color.web("#dc3545"));
            return null;
        }
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
    //</editor-fold>

    @FXML
    public void onclicktile(MouseEvent event) {
        Tile clickedTile = (Tile) event.getSource();
        if (expandedTile != null) {
            if (expandedTile == clickedTile) {
                restoreOriginalLayout();
            } else {
                restoreOriginalLayout();
                expandTile(clickedTile);
            }
        } else {
            expandTile(clickedTile);
        }
    }

    private void expandTile(Tile tile) {
        expandedTile = tile;

        originalParent = (GridPane) tile.getParent();

        double originalGridWidth = originalParent.getWidth();
        double originalGridHeight = originalParent.getHeight();
        double originalTileWidth = tile.getWidth();
        double originalTileHeight = tile.getHeight();

        originalParent.setMinWidth(originalGridWidth);
        originalParent.setMinHeight(originalGridHeight);
        originalParent.setPrefWidth(originalGridWidth);
        originalParent.setPrefHeight(originalGridHeight);

        originalColIndex = GridPane.getColumnIndex(tile) != null ? GridPane.getColumnIndex(tile) : 0;
        originalRowIndex = GridPane.getRowIndex(tile) != null ? GridPane.getRowIndex(tile) : 0;

        hiddenTiles = new Node[originalParent.getChildren().size() - 1];
        int index = 0;

        for (Node node : new ArrayList<>(originalParent.getChildren())) {
            if (node != tile) {
                originalParent.getChildren().remove(node);
                hiddenTiles[index++] = node;
            }
        }

        GridPane.setColumnSpan(tile, originalParent.getColumnConstraints().size());
        GridPane.setRowSpan(tile, originalParent.getRowConstraints().size());
        GridPane.setColumnIndex(tile, 0);
        GridPane.setRowIndex(tile, 0);

        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);

        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(300), tile);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.02);
        scaleTransition.setToY(1.02);
        scaleTransition.setCycleCount(2);
        scaleTransition.setAutoReverse(true);
        scaleTransition.play();

        tile.setDescription(tile.getDescription() + " (Click to minimize)");
    }

    private void restoreOriginalLayout() {
        if (expandedTile == null) return;

        GridPane.setColumnSpan(expandedTile, 1);
        GridPane.setRowSpan(expandedTile, 1);
        GridPane.setColumnIndex(expandedTile, originalColIndex);
        GridPane.setRowIndex(expandedTile, originalRowIndex);

        expandedTile.setMaxWidth(USE_COMPUTED_SIZE);
        expandedTile.setMaxHeight(USE_COMPUTED_SIZE);

        String currentDesc = expandedTile.getDescription();
        if (currentDesc.endsWith(" (Click to minimize)")) {
            expandedTile.setDescription(currentDesc.replace(" (Click to minimize)", ""));
        }

        for (Node node : hiddenTiles) {
            if (node != null) {
                originalParent.getChildren().add(node);
            }
        }

        expandedTile = null;
        hiddenTiles = null;
    }

    //handle theme change
    private void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        updateTileColors(newValue);
    }
}