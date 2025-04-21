package com.tfg.dashboard_tfg.viewmodel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class RegisterViewModel {

    // FXML Injected Registration Components
    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label registerErrorLabel;

    @FXML
    private Button registerButton;

    @FXML
    private Button backToLoginButton;

    @FXML
    private Hyperlink loginLink;

    @FXML
    private ProgressIndicator registerProgress;

    @FXML
    private VBox registerFormContainer;

    private Controller mainController;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String API_BASE_URL = "http://localhost:8080/api/user"; // Adjust the URL to match your API

    public RegisterViewModel() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @FXML
    public void initialize() {
        registerErrorLabel.setVisible(false);
        registerProgress.setVisible(false);

        // Add listeners for enter key on fields
        fullNameField.setOnAction(event -> emailField.requestFocus());
        emailField.setOnAction(event -> usernameField.requestFocus());
        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> confirmPasswordField.requestFocus());
        confirmPasswordField.setOnAction(event -> handleRegister());
    }

    public void setMainController(Controller mainController) {
        this.mainController = mainController;
    }

    // Registration Handling Methods
    @FXML
    private void handleRegister() {
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Basic validation
        if (fullName.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            registerErrorLabel.setText("All fields are required");
            registerErrorLabel.setVisible(true);
            return;
        }

        if (!password.equals(confirmPassword)) {
            registerErrorLabel.setText("Passwords do not match");
            registerErrorLabel.setVisible(true);
            return;
        }

        if (!isValidEmail(email)) {
            registerErrorLabel.setText("Please enter a valid email address");
            registerErrorLabel.setVisible(true);
            return;
        }

        if (password.length() < 8) {
            registerErrorLabel.setText("Password must be at least 8 characters");
            registerErrorLabel.setVisible(true);
            return;
        }

        // Show progress indicator and disable form
        setRegisterInProgress(true);

        // Create Task for background processing
        Task<Integer> registerTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return registerUser(fullName, email, username, password);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    int statusCode = getValue();
                    if (statusCode == 201) {
                        registerErrorLabel.setVisible(false);
                        // Show success message or directly navigate to login
                        showRegistrationSuccess();
                    } else if (statusCode == 409) {
                        registerErrorLabel.setText("Username or email already exists");
                        registerErrorLabel.setVisible(true);
                        setRegisterInProgress(false);
                    } else {
                        registerErrorLabel.setText("Registration failed. Please try again.");
                        registerErrorLabel.setVisible(true);
                        setRegisterInProgress(false);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    registerErrorLabel.setText("Connection error: " + getException().getMessage());
                    registerErrorLabel.setVisible(true);
                    setRegisterInProgress(false);
                });
            }
        };

        // Start the background task
        new Thread(registerTask).start();
    }

    private int registerUser(String fullName, String email, String username, String password) {
        try {
            // Create JSON request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("fullName", fullName);
            requestBody.put("email", email);
            requestBody.put("username", username);
            requestBody.put("password", password);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            // Send request and get response
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Return status code for handling in the UI thread
            return response.statusCode();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return 500; // Internal error
        }
    }

    private void showRegistrationSuccess() {
        // Clear the form
        clearForm();

        // Display success message
        registerErrorLabel.setText("Registration successful! Please check your email to activate your account.");
        registerErrorLabel.setTextFill(javafx.scene.paint.Color.GREEN);
        registerErrorLabel.setVisible(true);

        // Disable progress indicator
        setRegisterInProgress(false);
    }

    private void clearForm() {
        fullNameField.clear();
        emailField.clear();
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private void setRegisterInProgress(boolean inProgress) {
        registerProgress.setVisible(inProgress);
        registerButton.setDisable(inProgress);
        backToLoginButton.setDisable(inProgress);
        fullNameField.setDisable(inProgress);
        emailField.setDisable(inProgress);
        usernameField.setDisable(inProgress);
        passwordField.setDisable(inProgress);
        confirmPasswordField.setDisable(inProgress);
        loginLink.setDisable(inProgress);
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email.matches(emailRegex);
    }

    @FXML
    public void backToLogin() {
        mainController.showLoginForm();
    }

    private void showError(String message) {
        registerProgress.setVisible(false);
        registerErrorLabel.setText(message);
        registerErrorLabel.setVisible(true);
    }
}
