package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

public class Controller {

    // FXML Injected Views
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
    private AnchorPane rssView;

    // FXML Injected Buttons
    @FXML
    private Button dashboardBtn;

    @FXML
    private Button sonarrBtn;

    @FXML
    private Button jellyfinBtn;

    @FXML
    private Button dockerBtn;

    @FXML
    private Button rssBtn;

    @FXML
    private Button loginMenuBtn;

    // FXML Injected Login Components
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginErrorLabel;

    @FXML private Tile systemStatusTile;
    @FXML private Tile storageTile;
    @FXML private Tile networkTile;
    @FXML private Tile cpuTile;
    @FXML private Tile memoryTile;
    @FXML private Tile temperatureTile;
    @FXML private Tile jellyfinStatusTile;
    @FXML private Tile dockerStatusTile;

    // Navigation Methods
    @FXML
    private void showDashboardView() {
        resetViewStyles();
        dashboardBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(dashboardView);
    }

    @FXML
    private void showSonarrView() {
        resetViewStyles();
        sonarrBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(sonarrView);
    }

    @FXML
    private void showJellyfinView() {
        resetViewStyles();
        jellyfinBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(jellyfinView);
    }

    @FXML
    private void showDockerView() {
        resetViewStyles();
        dockerBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(dockerView);
    }

    @FXML
    private void showRssView() {
        resetViewStyles();
        rssBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(rssView);
    }

    @FXML
    private void showLoginForm() {
        resetViewStyles();
        loginMenuBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        // Reset login form
        usernameField.clear();
        passwordField.clear();
        loginErrorLabel.setVisible(false);

        setViewVisibility(loginView);
    }

    // Login Handling Methods
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Basic login validation (replace with actual authentication)
        if ("admin".equals(username) && "password".equals(password)) {
            loginErrorLabel.setVisible(false);
            showDashboardView(); // Redirect to dashboard after successful login
        } else {
            loginErrorLabel.setText("Invalid username or password");
            loginErrorLabel.setVisible(true);
        }
    }

    @FXML
    private void cancelLogin() {
        showDashboardView(); // Return to dashboard
    }

    // Utility Methods
    private void resetViewStyles() {
        // Reset all button styles
        dashboardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        sonarrBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        jellyfinBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        dockerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        loginMenuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        rssBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
    }

    private void setViewVisibility(AnchorPane activeView) {
        // Hide all views
        dashboardView.setVisible(false);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(false);
        dockerView.setVisible(false);
        loginView.setVisible(false);
        rssView.setVisible(false);
        // Show the active view
        activeView.setVisible(true);
    }


}