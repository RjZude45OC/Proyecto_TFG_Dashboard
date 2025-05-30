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
    public static String API_BASE_URL = "";
    private final Properties appProperties = new Properties();
    private final File PROPERTIES_FILE = new File("connection.properties");

    public LoginViewModel() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        objectMapper.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
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
        loginErrorLabel.setVisible(false);
        loginProgress.setVisible(false);

        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> handleLogin());
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
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            loginErrorLabel.setText("Username and password cannot be empty");
            loginErrorLabel.setVisible(true);
            return;
        }

        setLoginInProgress(true);

        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {

                boolean isAuthenticated = authenticateUser(username, password);

                if (isAuthenticated) {
                    return createUserSession(username, password);
                }

                return false;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    boolean success = getValue();
                    if (success) {
                        mainController.setIsLoggedIn(true);
                        mainController.setCurrentUsername(username);
                        mainController.updateLoginButtonText();

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

        new Thread(loginTask).start();
    }

    private boolean authenticateUser(String username, String password) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("username", username);
            requestBody.put("password", password);
            System.out.println(username);
            System.out.println(password);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                System.out.println("Authentication successful (200 OK)");
                return true;
            } else if (statusCode == 403) {
                System.out.println("Account not activated (403 Forbidden)");
                Platform.runLater(() -> loginErrorLabel.setText("Account not activated. Please check your email."));
                return false;
            } else {
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

            HttpResponse<String> response = httpClient.send(sessionRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Session created successfully (200 OK)");
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
        mainController.showDashboardView();
    }
    @FXML
    public void switchToRegisterView() {
        mainController.showRegisterForm();
    }
}