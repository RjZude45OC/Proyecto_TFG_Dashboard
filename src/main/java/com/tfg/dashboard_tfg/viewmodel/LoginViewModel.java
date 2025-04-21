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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginViewModel {

    public Hyperlink registerLink;
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
                // First authenticate the user
                boolean isAuthenticated = authenticateUser(username, password);

                if (isAuthenticated) {
                    // Then create the session if authentication succeeded
                    return createUserSession(username, password);
                }

                return false;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    boolean success = getValue();
                    if (success) {
                        // Update login status in controller
                        mainController.setIsLoggedIn(true);
                        mainController.setCurrentUsername(username); // Add this line
                        mainController.updateLoginButtonText(); // Add this line

                        // Clear form and navigate to dashboard
                        loginErrorLabel.setVisible(false);
                        usernameField.clear();
                        passwordField.clear();
                        setLoginInProgress(false);
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
                    Throwable exception = getException();
                    loginErrorLabel.setText("Connection error: " + exception.getMessage());
                    loginErrorLabel.setVisible(true);
                    setLoginInProgress(false);
                    exception.printStackTrace();
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
                System.out.println("Authentication successful (200 OK)");
                return true;
            } else if (statusCode == 403) {
                // Account not activated
                System.out.println("Account not activated (403 Forbidden)");
                Platform.runLater(() -> loginErrorLabel.setText("Account not activated. Please check your email."));
                return false;
            } else {
                // Other errors (401 for invalid credentials, etc.)
                System.out.println("Authentication failed with status code: " + statusCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Authentication error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean createUserSession(String username, String password) {
        try {
            ObjectNode sessionData = objectMapper.createObjectNode();
            sessionData.put("usuario", username);
            sessionData.put("password", password);

            HttpRequest sessionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/session/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sessionData.toString()))
                    .build();

            // Use synchronous call for the task
            HttpResponse<String> response = httpClient.send(sessionRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Session created successfully (200 OK)");
                // Here you could parse the session token if the server returns one
                // and store it for future authenticated requests
                return true;
            } else {
                System.out.println("Session creation failed with status code: " + response.statusCode());
                System.out.println("Response body: " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("Session creation error: " + e.getMessage());
            e.printStackTrace();
            return false;
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
    @FXML
    public void switchToRegisterView() {
        mainController.showRegisterForm();
    }
}