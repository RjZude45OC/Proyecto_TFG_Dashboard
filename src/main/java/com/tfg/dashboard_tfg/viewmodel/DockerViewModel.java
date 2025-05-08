package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DockerViewModel {


    // Docker Terminal Components
    @FXML
    private TextArea cliOutput;
    @FXML
    private TextField cliInput;
    @FXML
    private Label statusLabel;
    @FXML
    public GridPane connectionPane;
    // Container Tiles Components
    @FXML
    private FlowPane containerTilesPane;
    @FXML
    private ComboBox<String> containerFilter;
    @FXML
    private ToggleButton autoRefreshToggle;
    @FXML
    private Label containerCountLabel;

    // Remote Connection Components
    @FXML
    private TextField serverHostField;
    @FXML
    private TextField serverPortField;
    @FXML
    private Button connectButton;
    @FXML
    private Label connectionStatusLabel;

    @FXML
    private ListView commandHistoryList;
    // Data structures
    private final Map<String, ContainerTile> containerTiles = new HashMap<>();
    private ScheduledExecutorService scheduler;

    private String dockerApiUrl;

    public void clearTerminal(ActionEvent actionEvent) {
    }

    // Enum to track which metric is currently displayed
    private enum MetricType {
        NAME, CPU, MEMORY, NETWORK, STATUS, UPTIME
    }

    // Container tile class to store container data and current display metric
    private static class ContainerTile {
        String id;
        String name;
        Tile tile;
        MetricType currentMetric = MetricType.NAME;

        public ContainerTile(String id, String name, Tile tile) {
            this.id = id;
            this.name = name;
            this.tile = tile;
        }
    }

    @FXML
    public void initialize() {
        // Set default values
        if (serverPortField != null) {
            serverPortField.setText("2375");  // Default Docker API port
        }

        // Initialize connect button
        if (connectButton != null) {
            connectButton.setOnAction(event -> connectToDockerAPI());
        }

        // Set up CLI input handler
        cliInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand();
            }
        });
        // Add this in your initialization method
        if (containerTilesPane.getParent() instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) containerTilesPane.getParent();
            scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
                if (newBounds.getWidth() > 0) {
                    refreshContainers(); // Your method to refresh container display
                }
            });
        }
    }

    private void connectToDockerAPI() {
        String host = serverHostField.getText().trim();
        String port = serverPortField.getText().trim();

        if (host.isEmpty()) {
            connectionStatusLabel.setText("Please provide host");
            return;
        }

        int portNum = 2375;
        try {
            if (!port.isEmpty()) {
                portNum = Integer.parseInt(port);
            }
        } catch (NumberFormatException e) {
            connectionStatusLabel.setText("Invalid port number");
            return;
        }

        dockerApiUrl = "http://" + host + ":" + portNum;
        connectionStatusLabel.setText("Testing connection to Docker API...");

        new Thread(() -> {
            try {
                URL url = new URL(dockerApiUrl + "/containers/json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000); // 5 second timeout

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("Connected to Docker API at " + dockerApiUrl);
                        refreshContainers();
                    });
                } else {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("API Error: HTTP " + responseCode);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connection error: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    public void executeCommand() {
        String command = cliInput.getText().trim();
        if (command.isEmpty()) return;

        // Show command in output
        cliOutput.appendText("$ " + command + "\n");

        // Execute Docker API command if command start with docker
        if (command.startsWith("docker ")) {
            executeDockerAPICommand(command);
        } else {
            executeLocalCommand(command);
        }

        // Clear input field
        cliInput.clear();
    }

    private void executeDockerAPICommand(String command) {
        // For simplicity, we'll parse basic commands and translate them to API calls
        new Thread(() -> {
            try {
                String[] parts = command.split(" ");

                // Check if it's a Docker command
                if (parts.length < 2 || !parts[0].equals("docker")) {
                    Platform.runLater(() -> {
                        cliOutput.appendText("Error: Only docker commands are supported in API mode\n");
                        statusLabel.setText("Command error");
                    });
                    return;
                }

                String action = parts[1];
                String apiPath = "";
                String method = "GET";
                boolean needsRefresh = false;

                // Handle different command types
                switch (action) {
                    case "ps":
                        apiPath = "/containers/json?all=true";
                        if (parts.length > 2 && !parts[2].equals("-a")) {
                            apiPath = "/containers/json";  // Just running containers
                        }
                        break;
                    case "inspect":
                        if (parts.length < 3) {
                            Platform.runLater(() -> cliOutput.appendText("Error: Missing container ID\n"));
                            return;
                        }
                        apiPath = "/containers/" + parts[2] + "/json";
                        break;
                    case "logs":
                        if (parts.length < 3) {
                            Platform.runLater(() -> cliOutput.appendText("Error: Missing container ID\n"));
                            return;
                        }
                        apiPath = "/containers/" + parts[2] + "/logs?stdout=true&stderr=true";
                        break;
                    case "start":
                        if (parts.length < 3) {
                            Platform.runLater(() -> cliOutput.appendText("Error: Missing container ID\n"));
                            return;
                        }
                        apiPath = "/containers/" + parts[2] + "/start";
                        method = "POST";
                        needsRefresh = true;
                        break;
                    case "stop":
                        if (parts.length < 3) {
                            Platform.runLater(() -> cliOutput.appendText("Error: Missing container ID\n"));
                            return;
                        }
                        apiPath = "/containers/" + parts[2] + "/stop";
                        method = "POST";
                        needsRefresh = true;
                        break;
                    case "restart":
                        if (parts.length < 3) {
                            Platform.runLater(() -> cliOutput.appendText("Error: Missing container ID\n"));
                            return;
                        }
                        apiPath = "/containers/" + parts[2] + "/restart";
                        method = "POST";
                        needsRefresh = true;
                        break;
                    case "stats":
                        if (parts.length < 3) {
                            apiPath = "/containers/json?all=false";  // Just get running containers
                        } else {
                            apiPath = "/containers/" + parts[2] + "/stats?stream=false";
                        }
                        break;
                    default:
                        Platform.runLater(() -> {
                            cliOutput.appendText("Error: Unsupported command in API mode: " + action + "\n");
                            statusLabel.setText("Command error");
                        });
                        return;
                }

                // Execute the API call
                URL url = new URL(dockerApiUrl + apiPath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);

                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {
                    // Success response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }

                    final String output = response.toString();
                    boolean finalNeedsRefresh = needsRefresh;
                    Platform.runLater(() -> {
                        // Format JSON response if possible
                        try {
                            if (output.trim().startsWith("{") || output.trim().startsWith("[")) {
                                JSONObject json = new JSONObject(output);
                                cliOutput.appendText(json.toString(2) + "\n");
                            } else {
                                cliOutput.appendText(output);
                            }
                        } catch (Exception e) {
                            cliOutput.appendText(output);
                        }

                        statusLabel.setText("Command completed");
                        if (finalNeedsRefresh) {
                            refreshContainers();
                        }
                    });
                } else {
                    // Error response
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;

                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine).append("\n");
                    }

                    final String errorOutput = errorResponse.toString();
                    Platform.runLater(() -> {
                        cliOutput.appendText("Error (HTTP " + responseCode + "): " + errorOutput + "\n");
                        statusLabel.setText("Command error");
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    cliOutput.appendText("Error: " + e.getMessage() + "\n");
                    statusLabel.setText("Command error");
                });
            }
        }).start();
    }

    private void executeLocalCommand(String command) {
        new Thread(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder();
                // Use cmd /c to execute the full command on Windows
                builder.command("cmd.exe", "/c", command);
                builder.redirectErrorStream(true);  // Merge stderr with stdout

                Process process = builder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = process.waitFor();
                final String result = output.toString();

                Platform.runLater(() -> {
                    cliOutput.appendText(result);
                    statusLabel.setText(exitCode == 0 ? "Command completed" : "Command failed with code " + exitCode);
                });

            } catch (IOException | InterruptedException e) {
                Platform.runLater(() -> {
                    cliOutput.appendText("Error executing command: " + e.getMessage() + "\n");
                    statusLabel.setText("Command error");
                });
            }
        }).start();
    }

    @FXML
    public void refreshContainers() {
        statusLabel.setText("Refreshing containers...");
        containerTilesPane.getChildren().clear();

        // Get filter setting
        String filter = containerFilter.getValue();
        if (filter == null) filter = "All Containers";

        // Run in background thread
        String finalFilter = filter;
        new Thread(() -> {
            try {
                List<Map<String, String>> containers = getContainersViaAPI(finalFilter);

                // Clear existing tiles map
                containerTiles.clear();

                // Create tiles for each container
                for (Map<String, String> container : containers) {
                    Tile tile = createContainerTile(container);
                    ContainerTile containerTile = new ContainerTile(
                            container.get("id"),
                            container.get("name"),
                            tile);
                    containerTiles.put(container.get("id"), containerTile);
                }

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    // Add tiles to the pane
                    for (ContainerTile containerTile : containerTiles.values()) {
                        containerTilesPane.getChildren().add(containerTile.tile);
                    }

                    // Update container count
                    containerCountLabel.setText(String.valueOf(containerTiles.size()));
                    statusLabel.setText("Ready");
                });

            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private List<Map<String, String>> getContainersViaAPI(String filter) throws Exception {
        String apiPath = "/containers/json?all=true";

        if ("Running Only".equals(filter)) {
            apiPath = "/containers/json";
        } else if ("Stopped Only".equals(filter)) {
            apiPath = "/containers/json?filters={\"status\":[\"exited\"]}";
        }

        URL url = new URL(dockerApiUrl + apiPath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("API Error: HTTP " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        // Parse JSON response
        List<Map<String, String>> containers = new ArrayList<>();

        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(response.toString());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject containerJson = jsonArray.getJSONObject(i);
                Map<String, String> container = new HashMap<>();

                container.put("id", containerJson.getString("Id").substring(0, 12));
                container.put("name", containerJson.getJSONArray("Names").getString(0).replaceAll("^/", ""));
                container.put("image", containerJson.getString("Image"));
                container.put("status", containerJson.getString("Status"));
                container.put("runningFor", containerJson.getString("Status").replaceAll("^(Up|Exited) ", ""));
                container.put("size", "");  // Not readily available in API response

                containers.add(container);
            }
        } catch (Exception e) {
            throw new Exception("Error parsing API response: " + e.getMessage());
        }

        return containers;
    }

    private Tile createContainerTile(Map<String, String> container) {
        // Create a basic tile with container name
        String name = container.get("name");
        String id = container.get("id");
        String image = container.get("image");
        String status = container.get("status");
        boolean isRunning = status.toLowerCase().contains("up");

        // Set tile color based on container status
        Color tileColor = isRunning ? Color.valueOf("#2ecc71") : Color.valueOf("#e74c3c");

        // Calculate tile width to have 5 tiles per row
        double flowPaneWidth = containerTilesPane.getWidth();
        if (flowPaneWidth <= 0) {
            // If FlowPane has no width yet, try to get parent width
            ScrollPane scrollPane = (ScrollPane) containerTilesPane.getParent();
            flowPaneWidth = scrollPane.getWidth();

            // If still no width, use default viewport width
            if (flowPaneWidth <= 0) {
                flowPaneWidth = scrollPane.getPrefViewportWidth();
                if (flowPaneWidth <= 0) {
                    flowPaneWidth = 800; // Default fallback width
                }
            }
        }

        // Calculate tile width: (container width - padding - gaps) / 5
        double paddingWidth = containerTilesPane.getPadding().getLeft() + containerTilesPane.getPadding().getRight();
        double gapWidth = containerTilesPane.getHgap() * 4; // 4 gaps for 5 tiles
        double tileWidth = (flowPaneWidth - paddingWidth - gapWidth) / 4.0;

        tileWidth = Math.max(tileWidth, 120);

        Tile tile = TileBuilder.create()
                .skinType(Tile.SkinType.GAUGE)
                .prefSize(tileWidth, tileWidth)
                .maxWidth(Double.MAX_VALUE)
                .maxHeight(Double.MAX_VALUE)
                .title(name)
                .description(image)
                .text(status)
                .textSize(Tile.TextSize.SMALLER)
                .valueColor(tileColor)
                .animated(true)
                .build();

        // Create context menu with view options
        final ContextMenu contextMenu = new ContextMenu();

        // Add metric display options
        Menu metricsMenu = new Menu("Switch Metric");
        for (MetricType metricType : MetricType.values()) {
            MenuItem metricMenuItem = new MenuItem(metricType.name());
            metricMenuItem.setOnAction(event -> {
                ContainerTile containerTile = containerTiles.get(id);
                if (containerTile != null) {
                    containerTile.currentMetric = metricType;
                    updateTileWithMetric(containerTile, container);
                }
            });
            metricsMenu.getItems().add(metricMenuItem);
        }

        // Add inspect container option
        MenuItem inspectItem = new MenuItem("Inspect Container");
        inspectItem.setOnAction(event -> {
            cliInput.setText("docker inspect " + id);
            executeCommand();
        });

        // Add logs container option
        MenuItem logsItem = new MenuItem("View Container Logs");
        logsItem.setOnAction(event -> {
            cliInput.setText("docker logs " + id);
            executeCommand();
        });

        // Add container actions
        MenuItem startItem = new MenuItem("Start Container");
        startItem.setOnAction(event -> {
            cliInput.setText("docker start " + id);
            executeCommand();
        });

        MenuItem stopItem = new MenuItem("Stop Container");
        stopItem.setOnAction(event -> {
            cliInput.setText("docker stop " + id);
            executeCommand();
        });

        MenuItem restartItem = new MenuItem("Restart Container");
        restartItem.setOnAction(event -> {
            cliInput.setText("docker restart " + id);
            executeCommand();
        });

        contextMenu.getItems().addAll(metricsMenu, new SeparatorMenuItem(),
                inspectItem, logsItem, new SeparatorMenuItem(),
                startItem, stopItem, restartItem);

        // Attach context menu to tile
        tile.setOnContextMenuRequested(e ->
                contextMenu.show(tile, e.getScreenX(), e.getScreenY()));

        return tile;
    }

    private void updateTileWithMetric(ContainerTile containerTile, Map<String, String> container) {
        String id = container.get("id");
        String name = container.get("name");
        String image = container.get("image");
        String status = container.get("status");
        String runningFor = container.get("runningFor");
        boolean isRunning = status.toLowerCase().contains("up");

        Tile tile = containerTile.tile;

        // Update the tile based on the current metric type
        switch (containerTile.currentMetric) {
            case NAME:
                tile.setSkinType(Tile.SkinType.CHARACTER);
                tile.setTitle(name);
                tile.setDescription(image);
                tile.setText(status);
                break;

            case CPU:
                // Get real-time CPU stats
                fetchContainerCpuStats(id, (cpuUsage) -> {
                    tile.setSkinType(Tile.SkinType.GAUGE);
                    tile.setTitle("CPU");
                    tile.setDescription(name);
                    tile.setValue(cpuUsage);
                    tile.setUnit("%");
                    tile.setMaxValue(100);
                    tile.setThreshold(80);
                    tile.setThresholdVisible(true);
                });
                break;

            case MEMORY:
                // Get real-time memory stats
                fetchContainerMemoryStats(id, (memUsage) -> {
                    tile.setSkinType(Tile.SkinType.CIRCULAR_PROGRESS);
                    tile.setTitle("Memory");
                    tile.setDescription(name);
                    tile.setValue(memUsage);
                    tile.setUnit("%");
                    tile.setMaxValue(100);
                    System.out.println(memUsage);
                });
                break;

            case NETWORK:
                fetchContainerNetworkStats(id, (netIO) -> {
                    tile.setSkinType(Tile.SkinType.HIGH_LOW);
                    tile.setTitle("Network");
                    tile.setDescription(name);

                    // Format with proper units
                    String displayValue;
                    if (netIO >= 1024) {
                        displayValue = String.format("%.2f GB/s", netIO / 1024);
                    } else if (netIO >= 1) {
                        displayValue = String.format("%.2f MB/s", netIO);
                    } else {
                        displayValue = String.format("%.2f kB/s", netIO * 1024);
                    }

                    tile.setText(displayValue);
                });
                break;

            case STATUS:
                tile.setSkinType(Tile.SkinType.SWITCH);
                tile.setTitle("Status");
                tile.setDescription(name);
                tile.setActive(isRunning);
                tile.setText(status);
                break;

            case UPTIME:
                tile.setSkinType(Tile.SkinType.TEXT);
                tile.setTitle("Uptime");
                tile.setDescription(name);
                tile.setText(runningFor);
                break;
        }
    }

    private void fetchContainerCpuStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                double cpuUsage = fetchCpuStatsViaAPI(containerId);
                Platform.runLater(() -> callback.accept(cpuUsage));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting CPU stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
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

            // CPU calculation logic based on Docker stats API
            JSONObject cpuStats = json.getJSONObject("cpu_stats");
            JSONObject preCpuStats = json.getJSONObject("precpu_stats");

            long cpuDelta = cpuStats.getJSONObject("cpu_usage").getLong("total_usage") -
                    preCpuStats.getJSONObject("cpu_usage").getLong("total_usage");

            long systemDelta = cpuStats.getLong("system_cpu_usage") -
                    preCpuStats.getLong("system_cpu_usage");

            int numCPUs = cpuStats.getJSONObject("cpu_usage").getJSONArray("percpu_usage").length();
            double cpuUsage = 0.0;

            if (systemDelta > 0 && cpuDelta > 0) {
                cpuUsage = ((double) cpuDelta / systemDelta) * numCPUs * 100.0;
            }

            return cpuUsage;
        }
    }

    private void fetchContainerMemoryStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                double memUsage = fetchMemoryStatsViaAPI(containerId);
                Platform.runLater(() -> callback.accept(memUsage));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting memory stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private double fetchMemoryStatsViaAPI(String containerId) throws Exception {
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

            // Memory calculation logic based on Docker stats API
            JSONObject memStats = json.getJSONObject("memory_stats");

            // Some containers might not have memory metrics
            if (!memStats.has("usage") || !memStats.has("limit")) {
                return 0.0;
            }

            long usage = memStats.getLong("usage");
            long limit = memStats.getLong("limit");
            return (double) usage / limit * 100.0;
        }
    }

    private void fetchContainerNetworkStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                double netIO = fetchNetworkStatsViaAPI(containerId);
                Platform.runLater(() -> callback.accept(netIO));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting network stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private double fetchNetworkStatsViaAPI(String containerId) throws Exception {
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

            // Network calculation logic based on Docker stats API
            JSONObject networks = json.optJSONObject("networks");
            if (networks == null) {
                return 0.0;
            }

            long rxBytes = 0;
            long txBytes = 0;

            for (String interfaceName : networks.keySet()) {
                JSONObject netInterface = networks.getJSONObject(interfaceName);
                rxBytes += netInterface.getLong("rx_bytes");
                txBytes += netInterface.getLong("tx_bytes");
            }

            // Since we can't calculate the rate directly (need two samples),
            // we'll just return the total amount in MB
            return (rxBytes + txBytes) / (1024.0 * 1024.0);
        }
    }

    @FXML
    public void applyContainerFilter() {
        refreshContainers();
    }

    @FXML
    public void toggleAutoRefresh() {
        if (autoRefreshToggle.isSelected()) {
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    }

    private void startAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                // First, update the container list to catch any new/removed containers
                refreshContainers();

                // Then update metrics for each container based on its current display type
                for (ContainerTile containerTile : containerTiles.values()) {
                    String containerId = containerTile.id;
                    Map<String, String> containerInfo = new HashMap<>();
                    containerInfo.put("id", containerTile.id);
                    containerInfo.put("name", containerTile.name);

                    // Update metrics based on what's currently being displayed
                    switch (containerTile.currentMetric) {
                        case CPU:
                            fetchContainerCpuStats(containerId, (cpuUsage) -> {
                                containerTile.tile.setValue(cpuUsage);
                            });
                            break;
                        case MEMORY:
                            fetchContainerMemoryStats(containerId, (memUsage) -> {
                                containerTile.tile.setValue(memUsage);
                            });
                            break;
                        case NETWORK:
                            fetchContainerNetworkStats(containerId, (netIO) -> {
                                // Format with proper units
                                String displayValue;
                                if (netIO >= 1024) {
                                    displayValue = String.format("%.2f GB/s", netIO / 1024);
                                } else if (netIO >= 1) {
                                    displayValue = String.format("%.2f MB/s", netIO);
                                } else {
                                    displayValue = String.format("%.2f kB/s", netIO * 1024);
                                }
                                containerTile.tile.setText(displayValue);
                            });
                            break;
                        case STATUS:
                        case NAME:
                        case UPTIME:
                            // For these metrics, we need to get fresh container data
                            updateContainerInfo(containerTile);
                            break;
                    }
                }
            });
        }, 0, 5, TimeUnit.SECONDS);

        statusLabel.setText("Auto-refresh enabled");
    }

    // Helper method to update basic container information
    private void updateContainerInfo(ContainerTile containerTile) {
        String id = containerTile.id;

        new Thread(() -> {
            try {
                Map<String, String> containerInfo = new HashMap<>();
                containerInfo.put("id", id);
                containerInfo.put("name", containerTile.name);

                updateContainerInfoViaAPI(containerTile, containerInfo);
            } catch (Exception e) {
                // Log error but don't disrupt auto-refresh
                System.err.println("Error updating container info: " + e.getMessage());
            }
        }).start();
    }

    private void updateContainerInfoViaAPI(ContainerTile containerTile, Map<String, String> containerInfo) {
        try {
            URL url = new URL(dockerApiUrl + "/containers/" + containerTile.id + "/json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());

                JSONObject state = json.getJSONObject("State");
                String status = state.getString("Status");
                boolean running = state.getBoolean("Running");
                String image = json.getJSONObject("Config").getString("Image");
                String startedAt = state.getString("StartedAt");

                containerInfo.put("status", status);
                containerInfo.put("running", String.valueOf(running));
                containerInfo.put("image", image);
                containerInfo.put("startedAt", startedAt);

                // Calculate running for
                containerInfo.put("runningFor", calculateRunningFor(startedAt));

                Platform.runLater(() -> updateTileWithMetric(containerTile, containerInfo));
            }
        } catch (Exception e) {
            System.err.println("Error updating container info via API: " + e.getMessage());
        }
    }

    private String calculateRunningFor(String startedAt) {
        try {
            // Parse ISO timestamp
            java.time.OffsetDateTime startTime = java.time.OffsetDateTime.parse(startedAt);
            java.time.OffsetDateTime now = java.time.OffsetDateTime.now();

            java.time.Duration duration = java.time.Duration.between(startTime, now);

            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;

            if (days > 0) {
                return String.format("%d days, %d hours", days, hours);
            } else if (hours > 0) {
                return String.format("%d hours, %d minutes", hours, minutes);
            } else {
                return String.format("%d minutes", minutes);
            }

        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
        statusLabel.setText("Auto-refresh disabled");
    }

    // Cleanup method to be called when the application is closing
    public void cleanup() {
        stopAutoRefresh();
    }
}