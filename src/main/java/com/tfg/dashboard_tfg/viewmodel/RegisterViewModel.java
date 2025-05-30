package com.tfg.dashboard_tfg.viewmodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

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
    private Hyperlink loginLink;

    @FXML
    private ProgressIndicator registerProgress;

    @FXML
    private VBox registerFormContainer;

    private Controller mainController;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static String API_BASE_URL = "";
    private final Properties appProperties = new Properties();
    private final File PROPERTIES_FILE = new File("connection.properties");

    public RegisterViewModel() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    public void loadProperties() {
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            appProperties.load(fis);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
    }
    @FXML
    public void initialize() {
        loadProperties();
        registerErrorLabel.setVisible(false);
        registerProgress.setVisible(false);

        fullNameField.setOnAction(event -> emailField.requestFocus());
        emailField.setOnAction(event -> usernameField.requestFocus());
        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> confirmPasswordField.requestFocus());
        confirmPasswordField.setOnAction(event -> handleRegister());
        if (appProperties.containsKey("Login-Url")) {
            API_BASE_URL = appProperties.getProperty("Login-Url");
        }
        else{
            API_BASE_URL = "https://spaniel-positive-snail.ngrok-free.app/psp/api/user";
        }
    }

    public void setMainController(Controller mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void handleRegister() {
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

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

        setRegisterInProgress(true);

        Task<Integer> registerTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return registerUser(fullName, email, username, password);
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    int statusCode = getValue();
                    if (statusCode == 200 || statusCode == 201) {
                        registerErrorLabel.setVisible(false);
                        showRegistrationSuccess();

                        new Thread(() -> {
                            try {
                                Thread.sleep(3000);
                                Platform.runLater(() -> backToLogin());
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    } else if (statusCode == 409) {
                        registerErrorLabel.setText("Username already exists");
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

        new Thread(registerTask).start();
    }

    private int registerUser(String fullName, String email, String username, String password) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("username", username);
            requestBody.put("email", email);
            requestBody.put("name", username);
            requestBody.put("password", password);
            requestBody.put("isActive", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return 500;
        }
    }

    private void showRegistrationSuccess() {
        clearForm();

        registerErrorLabel.setText("Account created successfully! Redirecting to login...");
        registerErrorLabel.setTextFill(javafx.scene.paint.Color.GREEN);
        registerErrorLabel.setVisible(true);

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
}