package com.tfg.dashboard_tfg.viewmodel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.tfg.dashboard_tfg.services.LoginStatus;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginViewModel {

    // FXML Injected Login Components
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginErrorLabel;

    @FXML
    private Button loginButton;

    @FXML
    private Button cancelButton;

    @FXML
    private ProgressIndicator loginProgress;

    @FXML
    private VBox loginFormContainer;

    private Controller mainController;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    public static final String API_BASE_URL = "http://localhost:8080/api/user"; // Adjust the URL to match your API

    public LoginViewModel() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        // Enable Jackson to bypass module restrictions
        objectMapper.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @FXML
    public void initialize() {
        loginErrorLabel.setVisible(false);
        loginProgress.setVisible(false);

        // Add listeners for enter key on fields
        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> handleLogin());
    }

    public void setMainController(Controller mainController) {
        this.mainController = mainController;
    }

    // Login Handling Methods
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Basic validation
        if (username.isEmpty() || password.isEmpty()) {
            loginErrorLabel.setText("Username and password cannot be empty");
            loginErrorLabel.setVisible(true);
            return;
        }

        // Show progress indicator and disable form
        setLoginInProgress(true);

        // Create Task for background processing
        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return authenticateUser(username, password);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    boolean success = getValue();
                    if (success) {
                        loginErrorLabel.setVisible(false);
                        createUserSession(username, password);
                        mainController.showDashboardView();
                    } else {
                        loginErrorLabel.setText("Invalid username or password");
                        loginErrorLabel.setVisible(true);
                        setLoginInProgress(false);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    loginErrorLabel.setText("Connection error: " + getException().getMessage());
                    loginErrorLabel.setVisible(true);
                    setLoginInProgress(false);
                });
            }
        };

        // Start the background task
        new Thread(loginTask).start();
    }

    private boolean authenticateUser(String username, String password) {
        try {
            // Create JSON request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("username", username);
            requestBody.put("password", password);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            // Send request and get response
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Check response status
            int statusCode = response.statusCode();

            // 200 OK means successful login
            if (statusCode == 200) {
                System.out.println("200");
                return true;
            } else if (statusCode == 403) {
                // Account not activated
                System.out.println("403");
                Platform.runLater(() -> loginErrorLabel.setText("Account not activated. Please check your email."));
                return false;
            } else {
                // Other errors (401 for invalid credentials, etc.)
                System.out.println("invalid");
                return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createUserSession(String username, String password) {
        try {
            // Create a simple Map or ObjectNode instead of LoginStatus
            ObjectNode loginData = objectMapper.createObjectNode();
            loginData.put("username", username);
            loginData.put("password", password);

            // Convert to JSON
            String jsonBody = loginData.toString();

            // Rest of your code remains the same...
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/session/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // Continue with your request...
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLoginInProgress(boolean inProgress) {
        loginProgress.setVisible(inProgress);
        loginButton.setDisable(inProgress);
        cancelButton.setDisable(inProgress);
        usernameField.setDisable(inProgress);
        passwordField.setDisable(inProgress);
    }

    @FXML
    private void cancelLogin() {
        usernameField.clear();
        passwordField.clear();
        loginErrorLabel.setVisible(false);
        mainController.showDashboardView(); // Return to dashboard
    }
}
