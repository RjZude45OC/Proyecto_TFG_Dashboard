package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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

    @FXML
    public void initialize() {
        try {
            loadViews();
            showDashboardView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Navigation Methods
    @FXML
    public void showDashboardView() {
        resetViewStyles();
        dashboardBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(dashboardView);
    }

    @FXML
    public void showSonarrView() {
        resetViewStyles();
        sonarrBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(sonarrView);
    }

    @FXML
    public void showJellyfinView() {
        resetViewStyles();
        jellyfinBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(jellyfinView);
    }

    @FXML
    public void showDockerView() {
        resetViewStyles();
        dockerBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(dockerView);
    }

    @FXML
    public void showRssView() {
        resetViewStyles();
        rssBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        setViewVisibility(rssView);
    }

    @FXML
    public void showLoginForm() {
        resetViewStyles();
        loginMenuBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: white;");

        // Reset login form
//        usernameField.clear();
//        passwordField.clear();
//        loginErrorLabel.setVisible(false);

        setViewVisibility(loginView);
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

    private void loadViews() throws IOException {
        String path = "";
        System.out.println("check null");
        //load dashboard view
        path = "/com/tfg/dashboard_tfg/view/DashboardView.fxml";
        FXMLLoader dashboardLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        dashboardView = dashboardLoader.load();

        //load docker view
        path = "/com/tfg/dashboard_tfg/view/dockerView.fxml";
        FXMLLoader dockerLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        dockerView = dockerLoader.load();

        //load jellyFin view
        path = "/com/tfg/dashboard_tfg/view/jellyFinView.fxml";
        FXMLLoader jellyFinLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        jellyfinView = jellyFinLoader.load();

        //load login view
        path = "/com/tfg/dashboard_tfg/view/loginView.fxml";
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        loginView = loginLoader.load();

        //load rss view
        path = "/com/tfg/dashboard_tfg/view/rssView.fxml";
        FXMLLoader rssLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        rssView = rssLoader.load();

        //load sonarr view
        path = "/com/tfg/dashboard_tfg/view/sonarrView.fxml";
        FXMLLoader sonarrLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        sonarrView = sonarrLoader.load();

        //add all child to main panel
        mainStackPane.getChildren().addAll(dashboardView, dockerView,jellyfinView,loginView,rssView,sonarrView);
        loginViewModel loginController = loginLoader.getController();
        loginController.setMainController(this);
    }

    private void PathCheck(String filePath) throws FileNotFoundException {
        if (getClass().getResource(filePath) != null) {
            System.out.println("false");
        } else {
            throw new FileNotFoundException("File '" + filePath + "' not found");
        }
    }
}