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
                        if (command.startsWith("cls")){
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
        String image = container.get("image");
        String status = container.get("status");
        boolean isRunning = status.toLowerCase().contains("up");


        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem item1 = new MenuItem("open a file");
        final MenuItem item2 = new MenuItem("quit");

        contextMenu.getItems().addAll(item1, item2);

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

        // Add mouse scroll handler to cycle through metrics
        tile.setOnScroll(event -> {
            ContainerTile containerTile = containerTiles.get(container.get("id"));
            if (containerTile != null) {
                cycleMetricDisplay(containerTile, container, event.getDeltaY() > 0);
            }
        });

//        // Add mouse click handler to focus on container
//        tile.setOnMouseClicked(event -> {
//            String id = container.get("id");
//            cliInput.setText("docker inspect " + id);
//            executeCommand();
//        });
        tile.setOnContextMenuRequested(e ->
                contextMenu.show(tile, e.getScreenX(), e.getScreenY()));
        return tile;
    }

    private void cycleMetricDisplay(ContainerTile containerTile, Map<String, String> container, boolean forward) {
        // Get next or previous metric
        MetricType[] metrics = MetricType.values();
        int currentIndex = containerTile.currentMetric.ordinal();
        int nextIndex;

        if (forward) {
            nextIndex = (currentIndex + 1) % metrics.length;
        } else {
            nextIndex = (currentIndex - 1 + metrics.length) % metrics.length;
        }

        containerTile.currentMetric = metrics[nextIndex];

        // Update tile to show new metric
        updateTileWithMetric(containerTile, container);
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
                // Get real-time network stats
                fetchContainerNetworkStats(id, (netIO) -> {
                    tile.setSkinType(Tile.SkinType.HIGH_LOW);
                    tile.setTitle("Network");
                    tile.setDescription(name);
                    tile.setText(netIO + " MB/s");
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
                    if (line != null) {
                        // Parse percentage (e.g., "10.5%")
                        String cpuPerc = line.replace("%", "");
                        double cpuUsage = Double.parseDouble(cpuPerc);

                        Platform.runLater(() -> callback.accept(cpuUsage));
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
                    if (line != null) {
                        // Parse percentage (e.g., "10.5%")
                        String memPerc = line.replace("%", "");
                        double memUsage = Double.parseDouble(memPerc);

                        Platform.runLater(() -> callback.accept(memUsage));
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
                    if (line != null) {
                        // Parse network I/O (e.g., "10MB / 5MB")
                        double netIO = 0.0;
                        if (line.contains("MB")) {
                            netIO = Double.parseDouble(line.split("MB")[0].trim());
                        } else if (line.contains("kB")) {
                            netIO = Double.parseDouble(line.split("kB")[0].trim()) / 1024.0;
                        }

                        double finalNetIO = netIO;
                        Platform.runLater(() -> callback.accept(finalNetIO));
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
            // Update container metrics for each tile
            for (ContainerTile containerTile : containerTiles.values()) {
                // Get container data
                final String id = containerTile.id;
                final String name = containerTile.name;

                // Get container stats
                new Thread(() -> {
                    try {
                        ProcessBuilder processBuilder = new ProcessBuilder();
                        processBuilder.command("docker", "inspect", "--format",
                                "{{.State.Status}}\\t{{.State.Running}}\\t{{.Config.Image}}\\t{{.State.StartedAt}}", id);

                        Process process = processBuilder.start();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {

                            String line = reader.readLine();
                            if (line != null) {
                                String[] parts = line.split("\\t");
                                if (parts.length >= 4) {
                                    Map<String, String> container = getStringStringMap(id, name, parts);

                                    // Update the tile on JavaFX thread
                                    Platform.runLater(() -> updateTileWithMetric(containerTile, container));
                                }
                            }
                        }

                    } catch (Exception e) {
                        // Ignore errors in background update
                    }
                }).start();
            }
        }, 0, 5, TimeUnit.SECONDS);

        statusLabel.setText("Auto-refresh enabled");
    }

    private static Map<String, String> getStringStringMap(String id, String name, String[] parts) {
        Map<String, String> container = new HashMap<>();
        container.put("id", id);
        container.put("name", name);
        container.put("status", parts[0]);
        container.put("running", parts[1]);
        container.put("image", parts[2]);
        container.put("startedAt", parts[3]);
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