package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import eu.hansolo.tilesfx.chart.ChartData;
import javafx.animation.PauseTransition;
import javafx.application.Platform;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONObject;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DockerViewModel {

    @FXML
    private TextArea cliOutput;
    @FXML
    private TextField cliInput;
    @FXML
    private Label statusLabel;
    @FXML
    public GridPane connectionPane;

    @FXML
    private FlowPane containerTilesPane;
    @FXML
    private ComboBox<String> containerFilter;
    @FXML
    private ToggleButton autoRefreshToggle;
    @FXML
    private Label containerCountLabel;

    @FXML
    private TextField serverHostField;
    @FXML
    private TextField serverPortField;
    @FXML
    private Button connectButton;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private ListView commandHistoryList;

    private final Properties appProperties = new Properties();
    private String url;
    private List<String> commandList = new ArrayList<>();
    private static final String PROPERTIES_FILE = "connection.properties";
    private final StringProperty serverUrl = new SimpleStringProperty("");
    private final StringProperty serverPort = new SimpleStringProperty("");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Map<String, StatCacheEntry<Double>> cpuStatCache = new ConcurrentHashMap<>();
    private final Map<String, StatCacheEntry<Double>> memStatCache = new ConcurrentHashMap<>();
    private final Map<String, StatCacheEntry<Double>> netStatCache = new ConcurrentHashMap<>();
    private static final long STAT_CACHE_DURATION_MS = 5000;

    public void loadProperties() {
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            appProperties.load(fis);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }

    private final Map<String, ContainerTile> containerTiles = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private String dockerApiUrl;

    @FXML
    public void clearTerminal() {
        cliOutput.clear();
    }

    private enum MetricType {
        NAME, CPU, MEMORY, NETWORK, STATUS, UPTIME
    }

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
        loadProperties();
        serverUrl.setValue(appProperties.getProperty("dockerApi", ""));
        serverPort.setValue(appProperties.getProperty("dockerPort", ""));
        if (serverPortField.getText() == null || serverPortField.getText().isEmpty()) {
            serverPortField.setText("2375");
        }

        if (connectButton != null) {
            connectButton.setOnAction(event -> connectToDockerAPI());
        }

        cliInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand();
            }
        });
        setupTextFieldBindings();
        if (containerTilesPane.getParent() instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) containerTilesPane.getParent();
            scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
                if (newBounds.getWidth() > 0) {
                    refreshContainers();
                }
            });
        }

        url = appProperties.getProperty("dockerApi");
        if (url != null || !url.isEmpty()) {
            if (!url.startsWith("http://")){
                url = "http://" + url;
            }
        }
        connectToDockerAPI();
        commandHistoryList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                System.out.println("Selected command: " + newVal);
                cliInput.setText((String) newVal);
            }
        });
    }

    private void connectToDockerAPI() {
        String host = serverUrl.getValue().trim();
        String port = serverPort.getValue().trim();

        if (host.isEmpty()) {
            if (url == null || url.isEmpty()) {
                connectionStatusLabel.setText("Please provide host");
                connectionStatusLabel.setTextFill(Color.ORANGE);
                return;
            } else {
                host = url;
            }
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
        dockerApiUrl = host + ":" + portNum;
        connectionStatusLabel.setText("Testing connection to Docker API...");
        connectionStatusLabel.setStyle("-fx-text-fill: orange;");

        new Thread(() -> {
            try {
                URL url = new URL(dockerApiUrl + "/containers/json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("Connected");
                        connectionStatusLabel.setTextFill(Color.web("#28a745"));
                        refreshContainers();
                    });
                } else {
                    Platform.runLater(() -> {
                        connectionStatusLabel.setText("API Error: HTTP " + responseCode);
                        connectionStatusLabel.setTextFill(Color.web("#dc3545"));
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connection error: " + e.getMessage());
                    connectionStatusLabel.setTextFill(Color.web("#dc3545"));
                });
            }
        }).start();
    }

    @FXML
    public void executeCommand() {
        String command = cliInput.getText().trim();
        if (command.isEmpty()) return;

        cliOutput.appendText("$ " + command + "\n");

        if (command.startsWith("docker ")) {
            executeDockerAPICommand(command);
        } else {
            executeLocalCommand(command);
        }

        cliInput.clear();
    }

    private void setupTextFieldBindings() {
        serverHostField.textProperty().bindBidirectional(serverUrl);
        serverPortField.textProperty().bindBidirectional(serverPort);

        serverHostField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                String url = serverUrl.get().trim();
                if (!url.isEmpty() && !url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                    url = "http://" + url;
                    serverUrl.set(url);
                }
                updateProperty("dockerApi", serverUrl.get());
            }
        });

        serverPortField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                updateProperty("dockerPort", serverPort.get());
            }
        });
    }

    public void updateProperty(String key, String value) {
        loadProperties();
        appProperties.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.store(out, "Updated by user");
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> {
                connectionStatusLabel.setText("Updated property");
                connectionStatusLabel.setTextFill(Color.web("#28a745"));
            });
            pause.play();
        } catch (IOException e) {
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> {
                connectionStatusLabel.setText("Error updating property " + e.getMessage());
                connectionStatusLabel.setTextFill(Color.web("#dc3545"));
            });
            pause.play();
        }
    }

    private void executeDockerAPICommand(String command) {
        new Thread(() -> {
            try {
                String[] parts = command.split(" ");

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

                switch (action) {
                    case "ps":
                        apiPath = "/containers/json?all=true";
                        if (parts.length > 2 && !parts[2].equals("-a")) {
                            apiPath = "/containers/json";
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
                            apiPath = "/containers/json?all=false";
                        } else {
                            apiPath = "/containers/" + parts[2] + "/stats?stream=false";
                        }
                        break;
                    case "-help":
                        cliOutput.appendText(
                                "Docker CLI (API Mode) Commands:\n\n" +
                                        "ps [-a]                 List running containers. Use -a to list all containers.\n" +
                                        "inspect <containerId>   Show detailed information about a container.\n" +
                                        "logs <containerId>      Show stdout/stderr logs for a container.\n" +
                                        "start <containerId>     Start a stopped container.\n" +
                                        "stop <containerId>      Stop a running container.\n" +
                                        "restart <containerId>   Restart a container.\n" +
                                        "stats                   Show CPU and memory usage for all running containers.\n" +
                                        "stats <containerId>     Show stats for a specific container.\n\n"
                        );
                        break;

                    default:
                        Platform.runLater(() -> {
                            cliOutput.appendText("Error: Unsupported command in API mode: " + action + "\n");
                            statusLabel.setText("Command error");
                        });
                        return;
                }
                if (action.equals("-help")) {
                    commandList.add(command);
                    Platform.runLater(() -> {
                        commandHistoryList.getItems().setAll(commandList);
                    });
                    return;
                }

                URL url = new URL(dockerApiUrl + apiPath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method);

                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }

                    final String output = response.toString();
                    boolean finalNeedsRefresh = needsRefresh;
                    Platform.runLater(() -> {
                        commandList.add(command);
                        commandHistoryList.getItems().setAll(commandList);
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
                if (command.equals("cls") || command.equals("clear")) {
                    clearTerminal();
                    return;
                }
                builder.command("cmd.exe", "/c", command);
                builder.redirectErrorStream(true);

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
                    commandList.add(command);
                    commandHistoryList.getItems().setAll(commandList);
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

        String filter = containerFilter.getValue();
        if (filter == null) filter = "All Containers";

        String finalFilter = filter;
        new Thread(() -> {
            try {
                Map<String, MetricType> previousTileMetrics = new HashMap<>();
                for (Map.Entry<String, ContainerTile> entry : containerTiles.entrySet()) {
                    previousTileMetrics.put(entry.getKey(), entry.getValue().currentMetric);
                }
                List<Map<String, String>> containers = getContainersViaAPI(finalFilter);
                Map<String, Map<String, String>> idToContainerMap = new HashMap<>();
                for (Map<String, String> container : containers) {
                    idToContainerMap.put(container.get("id"), container);
                }

                Map<String, ContainerTile> newTiles = new HashMap<>();
                for (String id : idToContainerMap.keySet()) {
                    Map<String, String> info = idToContainerMap.get(id);
                    ContainerTile tile = containerTiles.get(id);
                    if (tile == null) {
                        Tile newTile = createContainerTile(info);
                        tile = new ContainerTile(id, info.get("name"), newTile);
                    } else {
                        tile.name = info.get("name");
                    }
                    MetricType metric = previousTileMetrics.get(id);
                    if (metric != null) tile.currentMetric = metric;
                    Map<String, String> containerInfoFinal = info;
                    ContainerTile finalTile = tile;
                    Platform.runLater(() -> updateTileWithMetric(finalTile, containerInfoFinal));
                    newTiles.put(id, tile);
                }

                Platform.runLater(() -> {
                    containerTilesPane.getChildren().clear();
                    for (String id : idToContainerMap.keySet()) {
                        containerTilesPane.getChildren().add(newTiles.get(id).tile);
                    }
                    containerTiles.clear();
                    containerTiles.putAll(newTiles);

                    containerCountLabel.setText(String.valueOf(containerTiles.size()));
                    statusLabel.setText("Ready");
                    lastUpdateLabel.setText("Last update: " + LocalDateTime.now().format(timeFormatter));
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
                containers.add(container);
            }
        } catch (Exception e) {
            throw new Exception("Error parsing API response: " + e.getMessage());
        }

        return containers;
    }

    private Tile createContainerTile(Map<String, String> container) {
        String name = container.get("name");
        String id = container.get("id");
        String image = container.get("image");
        String status = container.get("status");
        boolean isRunning = status.toLowerCase().contains("up");

        Color tileColor = isRunning ? Color.valueOf("#2ecc71") : Color.valueOf("#e74c3c");

        double flowPaneWidth = containerTilesPane.getWidth();
        if (flowPaneWidth <= 0) {
            ScrollPane scrollPane = (ScrollPane) containerTilesPane.getParent();
            flowPaneWidth = scrollPane.getWidth();

            if (flowPaneWidth <= 0) {
                flowPaneWidth = scrollPane.getPrefViewportWidth();
                if (flowPaneWidth <= 0) {
                    flowPaneWidth = 800;
                }
            }
        }

        double paddingWidth = containerTilesPane.getPadding().getLeft() + containerTilesPane.getPadding().getRight();
        double gapWidth = containerTilesPane.getHgap() * 4;
        double tileWidth = (flowPaneWidth - paddingWidth - gapWidth) / 4.0;

        tileWidth = Math.max(tileWidth, 120);

        Tile tile = TileBuilder.create()
                .skinType(Tile.SkinType.TEXT)
                .prefSize(tileWidth, tileWidth)
                .maxWidth(Double.MAX_VALUE)
                .maxHeight(Double.MAX_VALUE)
                .title(name)
                .description(image)
                .text(status)
                .textSize(Tile.TextSize.NORMAL)
                .valueColor(tileColor)
                .animated(true)
                .build();


        final ContextMenu contextMenu = new ContextMenu();


        Menu metricsMenu = new Menu("Switch Metric");
        for (MetricType metricType : MetricType.values()) {
            final MetricType selectedMetric = metricType;

            MenuItem metricMenuItem = new MenuItem(selectedMetric.name());
            metricMenuItem.setOnAction(event -> {
                ContainerTile containerTile = containerTiles.get(id);
                if (containerTile != null) {
                    containerTile.currentMetric = selectedMetric;
                    updateTileWithMetric(containerTile, container);
                }
            });
            metricsMenu.getItems().add(metricMenuItem);
        }

        MenuItem inspectItem = new MenuItem("Inspect Container");
        inspectItem.setOnAction(event -> {
            cliInput.setText("docker inspect " + id);
            executeCommand();
        });


        MenuItem logsItem = new MenuItem("View Container Logs");
        logsItem.setOnAction(event -> {
            cliInput.setText("docker logs " + id);
            executeCommand();
        });

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

        switch (containerTile.currentMetric) {
            case NAME:
                tile.setSkinType(Tile.SkinType.TEXT);
                tile.setTitle(name);
                tile.setDescription(image);
                tile.setText(status);
                break;

            case CPU:
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
                    tile.setSkinType(Tile.SkinType.SMOOTH_AREA_CHART);
                    tile.setTitle("Network");
                    tile.setDescription(name);
                    if (netIO > 0) {
                        double value = netIO;
                        String unit;

                        ChartData chartData = new ChartData("Network", value);
                        tile.addChartData(chartData);

                        if (tile.getChartData().size() > 15) {
                            tile.getChartData().remove(0);
                        }

                        double lastValue = value;
                        if (!tile.getChartData().isEmpty()) {
                            lastValue = tile.getChartData().get(tile.getChartData().size() - 1).getValue();
                        }

                        if (lastValue >= 1000) {
                            unit = "Mbps";
                            tile.setValue(lastValue / 1000);
                        } else {
                            unit = "Kbps";
                            tile.setValue(lastValue);
                        }

                        tile.setUnit(unit);
                        tile.setDecimals(2);
                    }
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
        StatCacheEntry<Double> cache = cpuStatCache.get(containerId);
        long now = System.currentTimeMillis();
        if (cache != null && (now - cache.timestamp) < STAT_CACHE_DURATION_MS) {
            Platform.runLater(() -> callback.accept(cache.value));
            return;
        }
        new Thread(() -> {
            try {
                double cpuUsage = fetchCpuStatsViaAPI(containerId);
                cpuStatCache.put(containerId, new StatCacheEntry<>(cpuUsage, System.currentTimeMillis()));
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

    private void fetchContainerMemoryStats(String containerId, java.util.function.Consumer<Double> callback) {
        StatCacheEntry<Double> cache = memStatCache.get(containerId);
        long now = System.currentTimeMillis();
        if (cache != null && (now - cache.timestamp) < STAT_CACHE_DURATION_MS) {
            Platform.runLater(() -> callback.accept(cache.value));
            return;
        }
        new Thread(() -> {
            try {
                double memUsage = fetchMemoryStatsViaAPI(containerId);
                memStatCache.put(containerId, new StatCacheEntry<>(memUsage, System.currentTimeMillis()));
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

            JSONObject memStats = json.getJSONObject("memory_stats");

            if (!memStats.has("usage") || !memStats.has("limit")) {
                return 0.0;
            }

            long usage = memStats.getLong("usage");
            long limit = memStats.getLong("limit");
            return (double) usage / limit * 100.0;
        }
    }

    private void fetchContainerNetworkStats(String containerId, java.util.function.Consumer<Double> callback) {
        StatCacheEntry<Double> cache = netStatCache.get(containerId);
        long now = System.currentTimeMillis();
        if (cache != null && (now - cache.timestamp) < STAT_CACHE_DURATION_MS) {
            Platform.runLater(() -> callback.accept(cache.value));
            return;
        }
        new Thread(() -> {
            try {
                double netIO = fetchNetworkStatsViaAPI(containerId);
                netStatCache.put(containerId, new StatCacheEntry<>(netIO, System.currentTimeMillis()));
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

            return (rxBytes + txBytes) / (1024.0 * 1024.0);
        }
    }

    private static class StatCacheEntry<T> {
        T value;
        long timestamp;

        StatCacheEntry(T value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
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
                refreshContainers();
                for (ContainerTile containerTile : containerTiles.values()) {
                    String containerId = containerTile.id;
                    Map<String, String> containerInfo = new HashMap<>();
                    containerInfo.put("id", containerTile.id);
                    containerInfo.put("name", containerTile.name);

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
                            updateContainerInfo(containerTile);
                            break;
                    }
                }
            });
        }, 0, 5, TimeUnit.SECONDS);

        statusLabel.setText("Auto-refresh enabled");
    }

    private void updateContainerInfo(ContainerTile containerTile) {
        String id = containerTile.id;

        new Thread(() -> {
            try {
                Map<String, String> containerInfo = new HashMap<>();
                containerInfo.put("id", id);
                containerInfo.put("name", containerTile.name);

                updateContainerInfoViaAPI(containerTile, containerInfo);
            } catch (Exception e) {
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

                containerInfo.put("runningFor", calculateRunningFor(startedAt));

                Platform.runLater(() -> updateTileWithMetric(containerTile, containerInfo));
            }
        } catch (Exception e) {
            System.err.println("Error updating container info via API: " + e.getMessage());
        }
    }

    private String calculateRunningFor(String startedAt) {
        try {
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

    public void cleanup() {
        stopAutoRefresh();
    }
}