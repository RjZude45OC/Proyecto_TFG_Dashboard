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
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.json.JSONObject;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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

    // Remote Connection Components
    @FXML
    private GridPane connectionPane;
    @FXML
    private TextField serverHostField;
    @FXML
    private TextField serverPortField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ToggleGroup connectionTypeGroup;
    @FXML
    private RadioButton sshRadio;
    @FXML
    private RadioButton apiRadio;
    @FXML
    private Button connectButton;
    @FXML
    private Label connectionStatusLabel;

    // Data structures
    private final ObservableList<String> historyList = FXCollections.observableArrayList();
    private final Map<String, ContainerTile> containerTiles = new HashMap<>();
    private ScheduledExecutorService scheduler;

    // Connection variables
    private Session sshSession;
    private String dockerApiUrl;
    private ConnectionType connectionType = ConnectionType.LOCAL;

    private enum ConnectionType {
        LOCAL, SSH, API
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

    // Enum to track which metric is currently displayed
    private enum MetricType {
        NAME, CPU, MEMORY, NETWORK, STATUS, UPTIME
    }

    @FXML
    public void initialize() {
        // Initialize Docker terminal
        commandHistory.setItems(historyList);

        // Initialize connection type
        if (connectionTypeGroup != null) {
            sshRadio.setUserData(ConnectionType.SSH);
            apiRadio.setUserData(ConnectionType.API);

            connectionTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    connectionType = (ConnectionType) newVal.getUserData();
                    updateConnectionFields();
                }
            });
        }

        // Initialize connect button
        if (connectButton != null) {
            connectButton.setOnAction(event -> connectToServer());
        }

        // Set default values
        if (serverPortField != null) {
            serverPortField.setText("2375");  // Default Docker API port
        }

        // Set up CLI input handler
        cliInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeCommand();
            }
        });

        // Load initial container data
        if (connectionType == ConnectionType.LOCAL) {
            refreshContainers();
        }
    }

    private void updateConnectionFields() {
        if (connectionType == ConnectionType.SSH) {
            serverPortField.setText("22");
            serverPortField.setDisable(false);
            usernameField.setDisable(false);
            passwordField.setDisable(false);
        } else if (connectionType == ConnectionType.API) {
            serverPortField.setText("2375");
            serverPortField.setDisable(false);
            usernameField.setDisable(true);
            passwordField.setDisable(true);
        } else {
            serverPortField.setDisable(true);
            usernameField.setDisable(true);
            passwordField.setDisable(true);
        }
    }

    @FXML
    private void connectToServer() {
        if (connectionType == ConnectionType.SSH) {
            connectViaSSH();
        } else if (connectionType == ConnectionType.API) {
            connectViaDockerAPI();
        } else {
            useLocalDocker();
        }
    }

    private void connectViaSSH() {
        String host = serverHostField.getText().trim();
        String port = serverPortField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (host.isEmpty() || username.isEmpty()) {
            connectionStatusLabel.setText("Please provide host and username");
            return;
        }

        int portNum = 22;
        try {
            if (!port.isEmpty()) {
                portNum = Integer.parseInt(port);
            }
        } catch (NumberFormatException e) {
            connectionStatusLabel.setText("Invalid port number");
            return;
        }

        connectionStatusLabel.setText("Connecting...");

        int finalPortNum = portNum;
        new Thread(() -> {
            try {
                // Close previous session if exists
                if (sshSession != null && sshSession.isConnected()) {
                    sshSession.disconnect();
                }

                JSch jsch = new JSch();
                sshSession = jsch.getSession(username, host, finalPortNum);
                sshSession.setPassword(password);

                // Skip host key checking (for development only)
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                sshSession.setConfig(config);

                sshSession.connect(5000); // 5 second timeout

                Platform.runLater(() -> {
                    connectionStatusLabel.setText("Connected to " + host + " via SSH");
                    connectionType = ConnectionType.SSH;
                    refreshContainers();
                });

            } catch (JSchException e) {
                Platform.runLater(() -> {
                    connectionStatusLabel.setText("SSH Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void connectViaDockerAPI() {
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
                        connectionType = ConnectionType.API;
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

    private void useLocalDocker() {
        connectionType = ConnectionType.LOCAL;
        connectionStatusLabel.setText("Using local Docker");
        refreshContainers();
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

        // Execute command based on connection type
        switch (connectionType) {
            case SSH:
                executeSSHCommand(command);
                break;
            case API:
                executeDockerAPICommand(command);
                break;
            case LOCAL:
            default:
                executeLocalCommand(command);
                break;
        }

        // Clear input field
        cliInput.clear();
    }

    private void executeSSHCommand(String command) {
        new Thread(() -> {
            try {
                if (sshSession == null || !sshSession.isConnected()) {
                    Platform.runLater(() -> {
                        cliOutput.appendText("Error: Not connected to remote server\n");
                        statusLabel.setText("Not connected");
                    });
                    return;
                }

                Channel channel = sshSession.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);

                // Get command output
                InputStream in = channel.getInputStream();
                channel.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder output = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                // Update UI on JavaFX thread
                String finalOutput = output.toString();
                Platform.runLater(() -> {
                    cliOutput.appendText(finalOutput);
                    statusLabel.setText("Command completed");
                    // Check if we need to refresh containers
                    if (command.contains("docker") || command.contains("container") || command.contains("ps") ||
                            command.contains("start") || command.contains("stop") || command.contains("rm")) {
                        refreshContainers();
                    }
                });

                channel.disconnect();

            } catch (Exception e) {
                Platform.runLater(() -> {
                    cliOutput.appendText("Error: " + e.getMessage() + "\n");
                    statusLabel.setText("Error executing command");
                });
            }
        }).start();
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
                        if (command.contains("docker") || command.startsWith("container") || command.startsWith("ps") ||
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
                List<Map<String, String>> containers = new ArrayList<>();

                // Get list of containers based on connection type
                switch (connectionType) {
                    case SSH:
                        containers = getContainersViaSSH(finalFilter);
                        break;
                    case API:
                        containers = getContainersViaAPI(finalFilter);
                        break;
                    case LOCAL:
                    default:
                        containers = getContainersLocally(finalFilter);
                        break;
                }

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

    private List<Map<String, String>> getContainersLocally(String filter) throws IOException {
        Process process = getLocalProcess(filter);
        return parseContainerOutput(process.getInputStream());
    }

    private List<Map<String, String>> getContainersViaSSH(String filter) throws Exception {
        if (sshSession == null || !sshSession.isConnected()) {
            throw new Exception("Not connected to remote server");
        }

        String command = getDockerCommand(filter);

        Channel channel = sshSession.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        InputStream in = channel.getInputStream();
        channel.connect();

        List<Map<String, String>> containers = parseContainerOutput(in);

        channel.disconnect();
        return containers;
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

    private Process getLocalProcess(String filter) throws IOException {
        String cmd = getDockerCommand(filter);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(cmd.split("\\s"));
        processBuilder.redirectErrorStream(true);

        return processBuilder.start();
    }

    private String getDockerCommand(String filter) {
        String cmd = "docker ps -a --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";

        if ("Running Only".equals(filter)) {
            cmd = "docker ps --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";
        } else if ("Stopped Only".equals(filter)) {
            cmd = "docker ps -f status=exited --format \"{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.RunningFor}}\\t{{.Size}}\"";
        }

        return cmd;
    }

    private List<Map<String, String>> parseContainerOutput(InputStream inputStream) throws IOException {
        List<Map<String, String>> containers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
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
                double cpuUsage = 0.0;

                switch (connectionType) {
                    case SSH:
                        cpuUsage = fetchCpuStatsViaSSH(containerId);
                        break;
                    case API:
                        cpuUsage = fetchCpuStatsViaAPI(containerId);
                        break;
                    case LOCAL:
                    default:
                        cpuUsage = fetchCpuStatsLocally(containerId);
                        break;
                }

                final double finalCpuUsage = cpuUsage;
                Platform.runLater(() -> callback.accept(finalCpuUsage));

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting CPU stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private double fetchCpuStatsLocally(String containerId) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}", containerId);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                return parseCpuPercentage(line);
            }
        }
        return 0.0;
    }

    private double fetchCpuStatsViaSSH(String containerId) throws Exception {
        if (sshSession == null || !sshSession.isConnected()) {
            throw new Exception("Not connected to remote server");
        }

        Channel channel = sshSession.openChannel("exec");
        ((ChannelExec) channel).setCommand("docker stats --no-stream --format {{.CPUPerc}} " + containerId);

        InputStream in = channel.getInputStream();
        channel.connect();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line = reader.readLine();
            if (line != null) {
                return parseCpuPercentage(line);
            }
        } finally {
            channel.disconnect();
        }

        return 0.0;
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

    private double parseCpuPercentage(String cpuPerc) {
        try {
            return Double.parseDouble(cpuPerc.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void fetchContainerMemoryStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                double memUsage = 0.0;

                switch (connectionType) {
                    case SSH:
                        memUsage = fetchMemoryStatsViaSSH(containerId);
                        break;
                    case API:
                        memUsage = fetchMemoryStatsViaAPI(containerId);
                        break;
                    case LOCAL:
                    default:
                        memUsage = fetchMemoryStatsLocally(containerId);
                        break;
                }

                final double finalMemUsage = memUsage;
                Platform.runLater(() -> callback.accept(finalMemUsage));

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting memory stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private double fetchMemoryStatsLocally(String containerId) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.MemPerc}}", containerId);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                return parseMemoryPercentage(line);
            }
        }
        return 0.0;
    }

    private double fetchMemoryStatsViaSSH(String containerId) throws Exception {
        if (sshSession == null || !sshSession.isConnected()) {
            throw new Exception("Not connected to remote server");
        }

        Channel channel = sshSession.openChannel("exec");
        ((ChannelExec) channel).setCommand("docker stats --no-stream --format {{.MemPerc}} " + containerId);

        InputStream in = channel.getInputStream();
        channel.connect();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line = reader.readLine();
            if (line != null) {
                return parseMemoryPercentage(line);
            }
        } finally {
            channel.disconnect();
        }

        return 0.0;
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

    private double parseMemoryPercentage(String memPerc) {
        try {
            return Double.parseDouble(memPerc.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void fetchContainerNetworkStats(String containerId, java.util.function.Consumer<Double> callback) {
        new Thread(() -> {
            try {
                double netIO = 0.0;

                switch (connectionType) {
                    case SSH:
                        netIO = fetchNetworkStatsViaSSH(containerId);
                        break;
                    case API:
                        netIO = fetchNetworkStatsViaAPI(containerId);
                        break;
                    case LOCAL:
                    default:
                        netIO = fetchNetworkStatsLocally(containerId);
                        break;
                }

                final double finalNetIO = netIO;
                Platform.runLater(() -> callback.accept(finalNetIO));

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error getting network stats: " + e.getMessage());
                    callback.accept(0.0);
                });
            }
        }).start();
    }

    private double fetchNetworkStatsLocally(String containerId) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("docker", "stats", "--no-stream", "--format", "{{.NetIO}}", containerId);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                return parseNetworkIO(line);
            }
        }
        return 0.0;
    }

    private double fetchNetworkStatsViaSSH(String containerId) throws Exception {
        if (sshSession == null || !sshSession.isConnected()) {
            throw new Exception("Not connected to remote server");
        }

        Channel channel = sshSession.openChannel("exec");
        ((ChannelExec) channel).setCommand("docker stats --no-stream --format {{.NetIO}} " + containerId);

        InputStream in = channel.getInputStream();
        channel.connect();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                return parseNetworkIO(line);
            }
        } finally {
            channel.disconnect();
        }

        return 0.0;
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

    private double parseNetworkIO(String netIO) {
        try {
            // Split into received and sent
            String[] parts = netIO.split("/");
            if (parts.length >= 1) {
                String received = parts[0].trim();

                // Parse the numeric value with unit
                return parseValueWithUnit(received);
            }
        } catch (Exception e) {
            System.err.println("Error parsing network IO: " + e.getMessage());
        }
        return 0.0;
    }

    // Helper method to parse values with units (MB, GB, kB, etc.)
    private double parseValueWithUnit(String value) {
        // Remove non-numeric parts to get just the value and unit
        String numericPart = value.replaceAll("[^0-9\\.].*$", "").trim();
        double numericValue;
        try {
            numericValue = Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            return 0.0;
        }

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

    // Helper method to update basic container information
    private void updateContainerInfo(ContainerTile containerTile) {
        String id = containerTile.id;

        new Thread(() -> {
            try {
                Map<String, String> containerInfo = new HashMap<>();
                containerInfo.put("id", id);
                containerInfo.put("name", containerTile.name);

                switch (connectionType) {
                    case SSH:
                        updateContainerInfoViaSSH(containerTile, containerInfo);
                        break;
                    case API:
                        updateContainerInfoViaAPI(containerTile, containerInfo);
                        break;
                    case LOCAL:
                    default:
                        updateContainerInfoLocally(containerTile, containerInfo);
                        break;
                }

            } catch (Exception e) {
                // Log error but don't disrupt auto-refresh
                System.err.println("Error updating container info: " + e.getMessage());
            }
        }).start();
    }

    private void updateContainerInfoLocally(ContainerTile containerTile, Map<String, String> containerInfo) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("docker", "inspect", "--format",
                    "{{.State.Status}}|{{.State.Running}}|{{.Config.Image}}|{{.State.StartedAt}}",
                    containerTile.id);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        containerInfo.put("status", parts[0]);
                        containerInfo.put("running", parts[1]);
                        containerInfo.put("image", parts[2]);
                        containerInfo.put("startedAt", parts[3]);

                        // Calculate running for
                        containerInfo.put("runningFor", calculateRunningFor(parts[3]));

                        Platform.runLater(() -> updateTileWithMetric(containerTile, containerInfo));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating container info locally: " + e.getMessage());
        }
    }

    private void updateContainerInfoViaSSH(ContainerTile containerTile, Map<String, String> containerInfo) {
        try {
            if (sshSession == null || !sshSession.isConnected()) {
                return;
            }

            Channel channel = sshSession.openChannel("exec");
            ((ChannelExec) channel).setCommand("docker inspect --format " +
                    "\"{{.State.Status}}|{{.State.Running}}|{{.Config.Image}}|{{.State.StartedAt}}\" " +
                    containerTile.id);

            InputStream in = channel.getInputStream();
            channel.connect();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        containerInfo.put("status", parts[0]);
                        containerInfo.put("running", parts[1]);
                        containerInfo.put("image", parts[2]);
                        containerInfo.put("startedAt", parts[3]);

                        // Calculate running for
                        containerInfo.put("runningFor", calculateRunningFor(parts[3]));

                        Platform.runLater(() -> updateTileWithMetric(containerTile, containerInfo));
                    }
                }
            } finally {
                channel.disconnect();
            }
        } catch (Exception e) {
            System.err.println("Error updating container info via SSH: " + e.getMessage());
        }
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

        // Disconnect SSH session if connected
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
    }
}