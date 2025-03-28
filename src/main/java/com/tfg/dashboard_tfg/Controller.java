package com.tfg.dashboard_tfg;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

public class Controller {

    @FXML
    private StackPane mainStackPane;

    @FXML
    private AnchorPane dashboardView;

    @FXML
    private AnchorPane sonarrView;

    @FXML
    private AnchorPane jellyfinView;

    @FXML
    private AnchorPane dockerView;

    @FXML
    private AnchorPane loginView;

    @FXML
    private Button dashboardBtn;

    @FXML
    private Button sonarrBtn;

    @FXML
    private Button jellyfinBtn;

    @FXML
    private Button dockerBtn;

    @FXML
    private Button loginMenuBtn;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginErrorLabel;

    // Method to reset button styles
    private void resetButtonStyles() {
        dashboardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        sonarrBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        jellyfinBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        dockerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
    }

    @FXML
    private void showDashboardView() {
        resetButtonStyles();
        dashboardBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        dashboardView.setVisible(true);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(false);
        dockerView.setVisible(false);
    }

    @FXML
    private void showSonarrView() {
        resetButtonStyles();
        sonarrBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        dashboardView.setVisible(false);
        sonarrView.setVisible(true);
        jellyfinView.setVisible(false);
        dockerView.setVisible(false);
    }

    @FXML
    private void showJellyfinView() {
        resetButtonStyles();
        jellyfinBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        dashboardView.setVisible(false);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(true);
        dockerView.setVisible(false);
    }

    @FXML
    private void showDockerView() {
        resetButtonStyles();
        dockerBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        dashboardView.setVisible(false);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(false);
        dockerView.setVisible(true);
    }

    @FXML
    private void showLoginForm() {
        resetButtonStyles();
        loginMenuBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        dashboardView.setVisible(false);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(false);
        dockerView.setVisible(false);
        loginView.setVisible(true);

        // Clear previous login attempt
        usernameField.clear();
        passwordField.clear();
        loginErrorLabel.setVisible(false);
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Simple login validation (replace with your actual authentication logic)
        if ("admin".equals(username) && "password".equals(password)) {
            // Successful login
            loginErrorLabel.setVisible(false);
            showDashboardView(); // Redirect to dashboard after successful login
        } else {
            // Failed login
            loginErrorLabel.setText("Invalid username or password");
            loginErrorLabel.setVisible(true);
        }
    }

    @FXML
    private void cancelLogin() {
        // Return to dashboard or previous view
        showDashboardView();
    }
}