package com.tfg.dashboard_tfg.viewmodel;

import javafx.event.ActionEvent;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class dockerViewModel {

    @FXML private TextArea cliOutput;
    @FXML private TextField cliInput;
    @FXML private ComboBox<String> commandHistory;
    @FXML private Label statusLabel;

    private final StringProperty outputText = new SimpleStringProperty("");
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final ObservableList<String> commandHistoryList = FXCollections.observableArrayList();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Initialize method to be called after FXML loading
    public void initialize() {
        // Bind properties to UI components
        cliOutput.textProperty().bind(outputText);
        statusLabel.textProperty().bind(statusText);
        commandHistory.setItems(commandHistoryList);

        // Set up command history selection handler
        commandHistory.setOnAction(event -> {
            String selectedCommand = commandHistory.getSelectionModel().getSelectedItem();
            if (selectedCommand != null && !selectedCommand.isEmpty()) {
                cliInput.setText(selectedCommand);
            }
        });
    }

    @FXML
    public void executeCommand(ActionEvent actionEvent) {
        String command = cliInput.getText().trim();
        if (command.isEmpty()) {
            return;
        }

        // Add command to history if not already present
        if (!commandHistoryList.contains(command)) {
            commandHistoryList.add(0, command);
            // Limit history size
            if (commandHistoryList.size() > 20) {
                commandHistoryList.remove(20);
            }
        }

        // Update UI
        appendToOutput("\n$ " + command + "\n");
        statusText.set("Running...");
        cliInput.clear();

        // Run command in background thread
        executorService.submit(() -> runDockerCommand(command));
    }

    @FXML
    public void clearTerminal(ActionEvent actionEvent) {
        outputText.set("");
        statusText.set("Ready");
    }

    private void runDockerCommand(String command) {
        try {
            // Split the command into components
            List<String> commandList = new ArrayList<>();

            // Check OS and prepare command
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                commandList.add("cmd.exe");
                commandList.add("/c");
            } else {
                commandList.add("/bin/bash");
                commandList.add("-c");
            }

            // Add the docker command
            commandList.add(command);

            // Create process builder
            ProcessBuilder processBuilder = new ProcessBuilder(commandList);
            processBuilder.redirectErrorStream(true); // Merge error stream with standard output

            // Start the process
            Process process = processBuilder.start();

            // Read output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String output = line;
                    // Update UI on JavaFX thread
                    javafx.application.Platform.runLater(() -> appendToOutput(output + "\n"));
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();

            // Update status based on exit code
            javafx.application.Platform.runLater(() -> {
                if (exitCode == 0) {
                    statusText.set("Command completed successfully");
                } else {
                    statusText.set("Command failed with exit code: " + exitCode);
                }
            });

        } catch (IOException | InterruptedException e) {
            final String errorMessage = e.getMessage();
            javafx.application.Platform.runLater(() -> {
                appendToOutput("Error: " + errorMessage + "\n");
                statusText.set("Command failed");
            });
        }
    }

    private void appendToOutput(String text) {
        outputText.set(outputText.get() + text);
        // Auto-scroll to bottom
        cliOutput.setScrollTop(Double.MAX_VALUE);
    }

    // Method to check if Docker is installed
    public void checkDockerInstallation() {
        executorService.submit(() -> {
            try {
                ProcessBuilder processBuilder;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    processBuilder = new ProcessBuilder("cmd.exe", "/c", "docker --version");
                } else {
                    processBuilder = new ProcessBuilder("/bin/bash", "-c", "docker --version");
                }
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    int exitCode = process.waitFor();
                    final String dockerVersion = output.toString().trim();

                    javafx.application.Platform.runLater(() -> {
                        if (exitCode == 0) {
                            appendToOutput("Docker is installed: " + dockerVersion + "\n");
                            statusText.set("Docker is ready");
                        } else {
                            appendToOutput("Docker might not be installed or running\n");
                            statusText.set("Docker unavailable");
                        }
                    });
                }
            } catch (IOException | InterruptedException e) {
                javafx.application.Platform.runLater(() -> {
                    appendToOutput("Error checking Docker installation: " + e.getMessage() + "\n");
                    statusText.set("Error checking Docker");
                });
            }
        });
    }

    // Clean up resources when no longer needed
    public void shutdown() {
        executorService.shutdown();
    }
}
