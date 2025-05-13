package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.prefs.Preferences;

import static javafx.scene.Cursor.*;

public class Controller {

    @FXML
    public AnchorPane rootPane;

    @FXML
    public HBox customBar;

    @FXML
    public Button closeButton;

    @FXML
    public Button minimizeButton;

    @FXML
    public Button fullscreenButton;

    @FXML
    public Button DownloadBtn;

    @FXML
    public Button JellyseerBtn;
    // Theme Properties
    @FXML
    private ToggleButton themeToggle;

    @FXML
    private FontIcon lightThemeIcon;

    @FXML
    private FontIcon darkThemeIcon;

    public static BooleanProperty darkMode = new SimpleBooleanProperty(true);
    private final Preferences prefs = Preferences.userNodeForPackage(Controller.class);

    // Authentication Property
    private BooleanProperty isLoggedIn = new SimpleBooleanProperty(false);

    // FXML Injected Views
    @FXML
    private StackPane mainStackPane;

    @FXML
    private AnchorPane dashboardView;

    @FXML
    private AnchorPane jellyseerView;

    @FXML
    private AnchorPane sonarrView;

    @FXML
    private AnchorPane jellyfinView;

    @FXML
    private AnchorPane dockerView;

    @FXML
    private AnchorPane loginView;

    @FXML
    private AnchorPane registerView; // Changed to lowercase for consistency

    @FXML
    private AnchorPane rssView;
    @FXML
    private AnchorPane DownloaderClientView;
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

    private double xOffset = 0;
    private double yOffset = 0;

    private final int RESIZE_MARGIN = 6;
    private double mouseX, mouseY;
    private Stage stage;

    public BooleanProperty darkModeProperty() {
        return darkMode;
    }

    private Cursor currentCursor;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            stage = (Stage) rootPane.getScene().getWindow();
            setupResizeListeners();
        });
        customBar.setOnMousePressed((MouseEvent event) -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        customBar.setOnMouseDragged((MouseEvent event) -> {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
        try {
            // Initialize theme toggle
            initializeThemeToggle();

            loadViews();
            showDashboardView();
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateLoginButtonText();
        isLoggedIn.addListener((obs, oldValue, newValue) -> {
            updateLoginButtonText();
        });
    }

    // Authentication Methods

    public void setIsLoggedIn(boolean value) {
        isLoggedIn.set(value);
    }

    // Add this field to the Controller class
    private String currentUsername = "";

    // Add this method to update the login button text
    public void updateLoginButtonText() {
        if (isLoggedIn.get() && !currentUsername.isEmpty()) {
            loginMenuBtn.setText(currentUsername);
        } else {
            loginMenuBtn.setText("Login");
        }
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    // Theme Methods
    private void initializeThemeToggle() {
        // Load saved theme preference or use dark theme as default
        boolean savedDarkMode = prefs.getBoolean("darkMode", true);
        darkMode.set(savedDarkMode);

        // Bind toggle button to dark mode property
        themeToggle.setSelected(darkMode.get());

        // Set initial icon visibility
        updateThemeIcons(darkMode.get());

        // Listen for theme changes
        themeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            darkMode.set(newVal);
            updateThemeIcons(newVal);
            applyTheme(newVal);
            prefs.putBoolean("darkMode", newVal);
        });
    }

    @FXML
    public void toggleTheme() {
        boolean newValue = !darkMode.get();
        darkMode.set(newValue);
        themeToggle.setSelected(newValue);
    }

    private void updateThemeIcons(boolean isDarkMode) {
        darkThemeIcon.setVisible(isDarkMode);
        lightThemeIcon.setVisible(!isDarkMode);

        // Get the toggle button handle
        Node toggleHandle = themeToggle.getGraphic();

        // Animate the handle
        TranslateTransition transition = new TranslateTransition(Duration.millis(150), toggleHandle);

        if (isDarkMode) {
            themeToggle.setStyle("-fx-background-radius: 12.5; -fx-background-color: #3d3d5c;");
            transition.setToX(10);
        } else {
            themeToggle.setStyle("-fx-background-radius: 12.5; -fx-background-color: #d1d1e0;");
            transition.setToX(-10);
        }

        transition.play();
    }

    public void applyTheme(boolean isDarkMode) {
        Scene scene = mainStackPane.getScene();
        if (scene == null) return;

        Parent root = scene.getRoot();

        if (isDarkMode) {
            // Apply dark theme
            root.getStyleClass().remove("light-theme");
            root.getStyleClass().add("dark-theme");
            applyDarkThemeToButtons();
        } else {
            // Apply light theme
            root.getStyleClass().remove("dark-theme");
            root.getStyleClass().add("light-theme");
            applyLightThemeToButtons();
        }

        // Apply theme to all loaded views
        applyThemeToViews(isDarkMode);
    }

    private void applyThemeToViews(boolean isDarkMode) {
        for (Node view : mainStackPane.getChildren()) {
            if (isDarkMode) {
                view.getStyleClass().remove("light-theme");
                view.getStyleClass().add("dark-theme");
            } else {
                view.getStyleClass().remove("dark-theme");
                view.getStyleClass().add("light-theme");
            }
        }
    }

    private void applyDarkThemeToButtons() {
        String activeStyle = "-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #CDD6F4;";

        Button activeButton = getActiveButton();

        dashboardBtn.setStyle(activeButton == dashboardBtn ? activeStyle : inactiveStyle);
        sonarrBtn.setStyle(activeButton == sonarrBtn ? activeStyle : inactiveStyle);
        jellyfinBtn.setStyle(activeButton == jellyfinBtn ? activeStyle : inactiveStyle);
        dockerBtn.setStyle(activeButton == dockerBtn ? activeStyle : inactiveStyle);
        loginMenuBtn.setStyle(activeButton == loginMenuBtn ? activeStyle : inactiveStyle);
        rssBtn.setStyle(activeButton == rssBtn ? activeStyle : inactiveStyle);
        DownloadBtn.setStyle(activeButton == DownloadBtn ? activeStyle : inactiveStyle);
        JellyseerBtn.setStyle(activeButton == JellyseerBtn ? activeStyle : inactiveStyle);
    }

    private void applyLightThemeToButtons() {
        String activeStyle = "-fx-background-color: #e0e0e0; -fx-text-fill: #333333;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #333333;";

        // Get the active button and only apply active style to it
        Button activeButton = getActiveButton();

        dashboardBtn.setStyle(activeButton == dashboardBtn ? activeStyle : inactiveStyle);
        sonarrBtn.setStyle(activeButton == sonarrBtn ? activeStyle : inactiveStyle);
        jellyfinBtn.setStyle(activeButton == jellyfinBtn ? activeStyle : inactiveStyle);
        dockerBtn.setStyle(activeButton == dockerBtn ? activeStyle : inactiveStyle);
        loginMenuBtn.setStyle(activeButton == loginMenuBtn ? activeStyle : inactiveStyle);
        rssBtn.setStyle(activeButton == rssBtn ? activeStyle : inactiveStyle);
        DownloadBtn.setStyle(activeButton == DownloadBtn ? activeStyle : inactiveStyle);
    }

    private Button getActiveButton() {
        if (dashboardView != null && dashboardView.isVisible()) return dashboardBtn;
        if (sonarrView != null && sonarrView.isVisible()) return sonarrBtn;
        if (jellyfinView != null && jellyfinView.isVisible()) return jellyfinBtn;
        if (dockerView != null && dockerView.isVisible()) return dockerBtn;
        if (loginView != null && loginView.isVisible()) return loginMenuBtn;
        if (rssView != null && rssView.isVisible()) return rssBtn;
        if (DownloadBtn != null && DownloadBtn.isVisible()) return DownloadBtn;
        return dashboardBtn; // Default
    }

    // Navigation Methods
    @FXML
    public void showDashboardView() {
        resetViewStyles();
        if (darkMode.get()) {
            dashboardBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            dashboardBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(dashboardView);
    }

    @FXML
    public void showSonarrView() {
//        if (!isLoggedIn.get()) {
//            showLoginForm();
//            return;
//        }

        resetViewStyles();
        if (darkMode.get()) {
            sonarrBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            sonarrBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(sonarrView);
    }

    @FXML
    public void showJellyfinView() {
//        if (!isLoggedIn.get()) {
//            showLoginForm();
//            return;
//        }

        resetViewStyles();
        if (darkMode.get()) {
            jellyfinBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            jellyfinBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(jellyfinView);
    }

    @FXML
    public void showDockerView() {
//        if (!isLoggedIn.get()) {
//            showLoginForm();
//            return;
//        }

        resetViewStyles();
        if (darkMode.get()) {
            dockerBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            dockerBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(dockerView);
    }

    @FXML
    public void showRssView() {
//        if (!isLoggedIn.get()) {
//            showLoginForm();
//            return;
//        }

        resetViewStyles();
        if (darkMode.get()) {
            rssBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            rssBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(rssView);
    }

    @FXML
    public void showDownloadView() {
//        if (!isLoggedIn.get()) {
//            showLoginForm();
//            return;
//        }

        resetViewStyles();
        if (darkMode.get()) {
            DownloadBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            DownloadBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(DownloaderClientView);
    }

    @FXML
    public void showJellyseerView() {

        resetViewStyles();
        if (darkMode.get()) {
            JellyseerBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            JellyseerBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(jellyseerView);
    }

    @FXML
    public void showLoginForm() {
        // If already logged in, this is a logout action
        if (isLoggedIn.get()) {
            // Log out the user
            setIsLoggedIn(false);
            setCurrentUsername("");
            updateLoginButtonText();
            // Now show the dashboard rather than the login form
            showDashboardView();
            return;
        }

        // If not logged in, proceed with normal login form display
        resetViewStyles();
        if (darkMode.get()) {
            loginMenuBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            loginMenuBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(loginView);
    }

    // Add method to show register form
    @FXML
    public void showRegisterForm() {
        resetViewStyles();
        if (darkMode.get()) {
            loginMenuBtn.setStyle("-fx-background-color: #2e2e42; -fx-text-fill: #CDD6F4;");
        } else {
            loginMenuBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333;");
        }
        setViewVisibility(registerView);
    }

    // Utility Methods
    private void resetViewStyles() {
        // Reset all button styles based on current theme
        if (darkMode.get()) {
            dashboardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            sonarrBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            jellyfinBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            dockerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            loginMenuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            rssBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            DownloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
            JellyseerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #CDD6F4;");
        } else {
            dashboardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            sonarrBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            jellyfinBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            dockerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            loginMenuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            rssBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            DownloadBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
            JellyseerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333;");
        }
    }

    private void setViewVisibility(AnchorPane activeView) {
        // Hide all views
        dashboardView.setVisible(false);
        sonarrView.setVisible(false);
        jellyfinView.setVisible(false);
        dockerView.setVisible(false);
        loginView.setVisible(false);
        registerView.setVisible(false);
        rssView.setVisible(false);
        DownloaderClientView.setVisible(false);
        jellyseerView.setVisible(false);
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

        //load register view
        path = "/com/tfg/dashboard_tfg/view/registerView.fxml";
        FXMLLoader registerLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        registerView = registerLoader.load();

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

        //load DownloadClient view
        path = "/com/tfg/dashboard_tfg/view/DownloaderClientView.fxml";
        FXMLLoader downloadClientLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        DownloaderClientView = downloadClientLoader.load();

        //load DownloadClient view
        path = "/com/tfg/dashboard_tfg/view/jellyseerView.fxml";
        FXMLLoader jellyseerLoader = new FXMLLoader(getClass().getResource(path));
        PathCheck(path);
        jellyseerView = jellyseerLoader.load();

        //add all child to main panel
        mainStackPane.getChildren().addAll(dashboardView, dockerView, jellyfinView, loginView, registerView, rssView, sonarrView, DownloaderClientView, jellyseerView);

        // Set up controllers
        LoginViewModel loginController = loginLoader.getController();
        loginController.setMainController(this);

        RegisterViewModel registerController = registerLoader.getController();
        registerController.setMainController(this);

        // Initialize theme once all views are loaded and scene is available
        mainStackPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                applyTheme(darkMode.get());
            }
        });
    }

    private void PathCheck(String filePath) throws FileNotFoundException {
        String RESET = "\u001B[0m";
        String RED = "\u001B[31m";
        String GREEN = "\u001B[32m";
        if (getClass().getResource(filePath) != null) {
            System.out.println(GREEN + "false" + RESET);
        } else {
            System.out.println(RED + "true" + RESET);
            throw new FileNotFoundException("File '" + filePath + "' not found");
        }
    }

    public void handleClose(ActionEvent actionEvent) {
        Button source = (Button) actionEvent.getSource();
        Stage stage = (Stage) source.getScene().getWindow();

        if (source == closeButton) {
            stage.close();
            javafx.application.Platform.exit();
            System.exit(0);
            System.out.println("close app");
        } else if (source == minimizeButton) {
            stage.setIconified(true);
            System.out.println("minimize");
        } else if (source == fullscreenButton) {
            stage.setFullScreen(!stage.isFullScreen());
            System.out.println("full screen");
        }
    }

    private void setupResizeListeners() {
        rootPane.setOnMouseMoved(event -> {
            double x = event.getX();
            double y = event.getY();
            double width = rootPane.getWidth();
            double height = rootPane.getHeight();

            Cursor cursor = Cursor.DEFAULT;
            if (x < RESIZE_MARGIN && y < RESIZE_MARGIN) {
                cursor = Cursor.NW_RESIZE;
            } else if (x > width - RESIZE_MARGIN && y < RESIZE_MARGIN) {
                cursor = Cursor.NE_RESIZE;
            } else if (x < RESIZE_MARGIN && y > height - RESIZE_MARGIN) {
                cursor = Cursor.SW_RESIZE;
            } else if (x > width - RESIZE_MARGIN && y > height - RESIZE_MARGIN) {
                cursor = Cursor.SE_RESIZE;
            } else if (x < RESIZE_MARGIN) {
                cursor = W_RESIZE;
            } else if (x > width - RESIZE_MARGIN) {
                cursor = E_RESIZE;
            } else if (y < RESIZE_MARGIN) {
                cursor = Cursor.N_RESIZE;
            } else if (y > height - RESIZE_MARGIN) {
                cursor = S_RESIZE;
            }

            rootPane.setCursor(cursor);
        });

        rootPane.setOnMousePressed(event -> {
            mouseX = event.getScreenX();
            mouseY = event.getScreenY();

            currentCursor = rootPane.getCursor();
        });

        rootPane.setOnMouseDragged(event -> {
            if (stage == null) return;

            double dx = event.getScreenX() - mouseX;
            double dy = event.getScreenY() - mouseY;

            Cursor cursor = rootPane.getCursor();

            // Store initial values before any changes
            double initialX = stage.getX();
            double initialY = stage.getY();
            double initialWidth = stage.getWidth();
            double initialHeight = stage.getHeight();

            // Calculate new values
            double newX = initialX;
            double newY = initialY;
            double newWidth = initialWidth;
            double newHeight = initialHeight;

            if (cursor.equals(E_RESIZE)) {
                newWidth = initialWidth + dx;
            } else if (cursor.equals(W_RESIZE)) {
                double proposedWidth = initialWidth - dx;
                if (proposedWidth > stage.getMinWidth()) {
                    newX = initialX + dx;
                    newWidth = proposedWidth;
                }
            } else if (cursor.equals(S_RESIZE)) {
                newHeight = initialHeight + dy;
            } else if (cursor.equals(N_RESIZE)) {
                double proposedHeight = initialHeight - dy;
                if (proposedHeight > stage.getMinHeight()) {
                    newY = initialY + dy;
                    newHeight = proposedHeight;
                }
            } else if (cursor.equals(SE_RESIZE)) {
                newWidth = initialWidth + dx;
                newHeight = initialHeight + dy;
            } else if (cursor.equals(SW_RESIZE)) {
                double proposedWidth = initialWidth - dx;
                if (proposedWidth > stage.getMinWidth()) {
                    newX = initialX + dx;
                    newWidth = proposedWidth;
                }
                newHeight = initialHeight + dy;
            } else if (cursor.equals(NE_RESIZE)) {
                newWidth = initialWidth + dx;
                double proposedHeight = initialHeight - dy;
                if (proposedHeight > stage.getMinHeight()) {
                    newY = initialY + dy;
                    newHeight = proposedHeight;
                }
            } else if (cursor.equals(NW_RESIZE)) {
                double proposedWidth = initialWidth - dx;
                double proposedHeight = initialHeight - dy;

                if (proposedWidth > stage.getMinWidth()) {
                    newX = initialX + dx;
                    newWidth = proposedWidth;
                }

                if (proposedHeight > stage.getMinHeight()) {
                    newY = initialY + dy;
                    newHeight = proposedHeight;
                }
            }

            stage.setX(newX);
            stage.setY(newY);
            stage.setWidth(newWidth);
            stage.setHeight(newHeight);

            mouseX = event.getScreenX();
            mouseY = event.getScreenY();
        });
    }
}