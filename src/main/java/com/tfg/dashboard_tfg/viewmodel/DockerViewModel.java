package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private ComboBox<String> commandHistory;
    @FXML
    private Label statusLabel;


    // Container Tiles Components
    @FXML
    private FlowPane containerTilesPane;
    @FXML
    private ComboBox<String> containerFilter;
    @FXML
    private ToggleButton autoRefreshToggle;
    @FXML
    private Label containerCountLabel;

    // Data structures
    private final ObservableList<String> historyList = FXCollections.observableArrayList();
    private Map<String, ContainerTile> containerTiles = new HashMap<>();
    private ScheduledExecutorService scheduler;

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

    // Enum to track which metric is currently displayed
    private enum MetricType {
        NAME, CPU, MEMORY, NETWORK, STATUS, UPTIME
    }

    @FXML
    public void initialize() {
        // Initialize Docker terminal
        commandHistory.setItems(historyList);

        // Load initial container data
        refreshContainers();

        cliInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand();
            }
        });
    }

    @FXML
    public void clearTerminal() {
        cliOutput.clear();
    }

    @FXML
    public void executeCommand() {
        String command = cliInput.getText().trim();
        if (command.isEmpty()) return;

        // Add to history
        if (!historyList.contains(command)) {
            historyList.add(0, command);
            commandHistory.setItems(historyList);
        }

        // Show command in output
        cliOutput.appendText("$ " + command + "\n");

        // Execute command
        executeDockerCommand(command);

        // Clear input field
        cliInput.clear();
    }

    private void executeDockerCommand(String command) {
        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();

                if (command.startsWith("docker")) {
                    List<String> commandParts = new ArrayList<>(Arrays.asList(command.split("\\s+")));
                    processBuilder.command(commandParts);
                } else {
                    processBuilder.command("cmd", "/c", command);
                }
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();

                // Read output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    StringBuilder output = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    // Update UI on JavaFX thread
                    String finalOutput = output.toString();
                    Platform.runLater(() -> {
                        cliOutput.appendText(finalOutput);
                        // If this is a container command, refresh the tiles
                        if (command.startsWith("container") || command.startsWith("ps") ||
                                command.contains("start") || command.contains("stop") ||
                                command.contains("kill") || command.contains("rm")) {
                            refreshContainers();
                        }
                        if (command.startsWith("cls")) {
                            clearTerminal();
                        }
                    });

                    int exitCode = process.waitFor();
                    Platform.runLater(() -> statusLabel.setText("Command completed with exit code: " + exitCode));
                }

            } catch (IOException | InterruptedException e) {
                Platform.runLater(() -> {
                    cliOutput.appendText("Error: " + e.getMessage() + "\n");
                    statusLabel.setText("Error executing command");
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
                // Get list of containers with docker command
                Process process = getProcess(finalFilter);

                List<Map<String, String>> containers = getMaps(process);

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

            } catch (IOException e) {
                Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private static List<Map<String, String>> getMaps(Process process) throws IOException {
        List<Map<String, String>> containers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\t");
                if (parts.length >= 6) {
                    Map<String, String> container = new HashMap<>();
                    container.put("id", parts[0]);
                    container.put("name", parts[1]);
                    container.put("image", parts[2]);
                    container.put("status", parts[3]);
                    container.put("runningFor", parts[4]);
                    container.put("size", parts[5]);
                    containers.add(container);
                }
            }
        }
        return containers;
    }

    private static Process getProcess(String finalFilter) throws IOException {
        String cmd = "docker ps -a --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";

        if ("Running Only".equals(finalFilter)) {
            cmd = "docker ps --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";
        } else if ("Stopped Only".equals(finalFilter)) {
            cmd = "docker ps -f status=exited --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(cmd.split("\\s"));
        processBuilder.redirectErrorStream(true);

        return processBuilder.start();
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

        Tile tile = TileBuilder.create()
                .skinType(Tile.SkinType.GAUGE)
                .prefSize(120, 120)
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

        contextMenu.getItems().addAll(metricsMenu, inspectItem, logsItem);

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
                // Use docker stats to get CPU usage
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", containerId);

                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line = reader.readLine();
                    System.out.println(line);
                    if (line != null) {
                        // Parse percentage (e.g., "10.5%")
                        String cpuPerc = line.trim().replace("%", "");
                        try {
                            double cpuUsage = Double.parseDouble(cpuPerc);
                            Platform.runLater(() -> callback.accept(cpuUsage));
                        } catch (NumberFormatException e) {
                            Platform.runLater(() -> {
                                statusLabel.setText("Error parsing CPU stats: " + e.getMessage());
                                callback.accept(0.0);
                            });
                        }
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting CPU stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private void fetchContainerMemoryStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                // Use docker stats to get memory usage
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.MemPerc}}", containerId);

                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line = reader.readLine();
                    System.out.println(line);
                    if (line != null) {
                        // Parse percentage (e.g., "10.5%")
                        String memPerc = line.trim().replace("%", "");
                        try {
                            double memUsage = Double.parseDouble(memPerc);
                            Platform.runLater(() -> callback.accept(memUsage));
                        } catch (NumberFormatException e) {
                            Platform.runLater(() -> {
                                statusLabel.setText("Error parsing memory stats: " + e.getMessage());
                                callback.accept(0.0);
                            });
                        }
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting memory stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private void fetchContainerNetworkStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                // Use docker stats to get network I/O
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.NetIO}}", containerId);

                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line = reader.readLine();
                    System.out.println(line);
                    if (line != null && !line.isEmpty()) {
                        // Parse network I/O (e.g., "10MB / 5MB")
                        try {
                            // Split into received and sent
                            String[] parts = line.split("/");
                            if (parts.length >= 1) {
                                String received = parts[0].trim();

                                // Parse the numeric value
                                double value = parseValueWithUnit(received);

                                Platform.runLater(() -> callback.accept(value));
                            } else {
                                Platform.runLater(() -> callback.accept(0.0));
                            }
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                statusLabel.setText("Error parsing network stats: " + e.getMessage());
                                callback.accept(0.0);
                            });
                        }
                    } else {
                        Platform.runLater(() -> callback.accept(0.0));
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting network stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    // Helper method to parse values with units (MB, GB, kB, etc.)
    private double parseValueWithUnit(String value) {
        // Remove non-numeric parts to get just the value and unit
        String numericPart = value.replaceAll("[^0-9\\.].*$", "").trim();
        double numericValue = Double.parseDouble(numericPart);

        // Determine unit and convert to MB
        if (value.contains("GB")) {
            return numericValue * 1024;  // Convert GB to MB
        } else if (value.contains("kB")) {
            return numericValue / 1024;  // Convert kB to MB
        } else if (value.contains("B") && !value.contains("kB") && !value.contains("MB") && !value.contains("GB")) {
            return numericValue / (1024 * 1024);  // Convert B to MB
        } else {
            // Assume MB or default
            return numericValue;
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

    // New helper method to update basic container information
    private void updateContainerInfo(ContainerTile containerTile) {
        String id = containerTile.id;
        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("docker", "stats", "--no-stream", "--format", "{{json .}}", id);

                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line = reader.readLine();
                    System.out.println(line);
                    if (line != null) {
                        String[] parts = line.split("\\t");
                        if (parts.length >= 4) {
                            Map<String, String> container = getStringStringMap(containerTile, line);

                            Platform.runLater(() -> updateTileWithMetric(containerTile, container));
                        }
                    }
                }
            } catch (Exception e) {
                // Log error but don't disrupt auto-refresh
                System.err.println("Error updating container info: " + e.getMessage());
            }
        }).start();
    }

    private static Map<String, String> getStringStringMap(ContainerTile containerTile, String line) {
        JSONObject json = new JSONObject(line);

        Map<String, String> container = new HashMap<>();
        container.put("id", containerTile.id);
        container.put("name", json.optString("Name"));
        container.put("status", json.optString("CPUPerc"));
        container.put("running", json.optString("MemUsage"));
        container.put("image", json.optString("MemPerc"));
        container.put("startedAt", "");

        container.put("runningFor", "");
        return container;
    }


    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
        statusLabel.setText("Auto-refresh disabled");
    }
}