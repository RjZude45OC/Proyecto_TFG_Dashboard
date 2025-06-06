package com.tfg.dashboard_tfg.viewmodel;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class jellyseerViewModel implements Initializable {

    @FXML
    private WebView webView;
    @FXML
    private TextField urlField;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button reloadButton;
    @FXML
    private Button homeButton;
    @FXML
    private Button goButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar loadProgress;
    @FXML
    private ProgressIndicator loadingIndicator;

    private WebEngine webEngine;
    private final String ROOT = "http://192.168.30.2";
    private String DEFAULT_HOME_PAGE = "";


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        webEngine = webView.getEngine();
        urlField.setOnAction(event -> handleNavigate());
        String path = "/com/tfg/dashboard_tfg/assets/home.html";
        URL homePageUrl = getClass().getResource(path);
        if (homePageUrl != null) {
            DEFAULT_HOME_PAGE = homePageUrl.toExternalForm();
            navigateToHomePage();
        } else {
            System.err.println("Error: html not found in resources");
            DEFAULT_HOME_PAGE = "about:blank";
            statusLabel.setText("Home screen not found!");
        }
        configureLoadStateListeners();

        configureHistoryStateListeners();
    }

    @FXML
    public void handleNavigate() {
        String url = urlField.getText().trim();
        if (!url.isEmpty() && !url.matches("^[a-zA-Z]+://.*")) {
            url = "http://" + url;
            urlField.setText(url);
        }

        if (!url.isEmpty()) {
            loadUrl(url);
        }
    }

    @FXML
    public void handleBack() {
        if (webEngine.getHistory().getCurrentIndex() > 0) {
            webEngine.getHistory().go(-1);
        }
    }

    @FXML
    public void handleForward() {
        if (webEngine.getHistory().getCurrentIndex() < webEngine.getHistory().getEntries().size() - 1) {
            webEngine.getHistory().go(1);
        }
    }

    @FXML
    public void handleReload() {
        webEngine.reload();
    }

    @FXML
    public void handleHome() {
        navigateToHomePage();
    }

    private void navigateToHomePage() {
        loadUrl(DEFAULT_HOME_PAGE);
    }

    private void loadUrl(String url) {
        statusLabel.setText("Loading...");
        loadingIndicator.setVisible(true);
        webEngine.load(url);
    }

    private void configureLoadStateListeners() {
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case SUCCEEDED:
                    statusLabel.setText("Page loaded: " + webEngine.getLocation());
                    urlField.setText(webEngine.getLocation());
                    loadingIndicator.setVisible(false);
                    break;
                case FAILED:
                    statusLabel.setText("Failed to load page");
                    loadingIndicator.setVisible(false);
                    Throwable exception = webEngine.getLoadWorker().getException();
                    System.err.println("Error loading page:");
                    if (exception != null) {
                        exception.printStackTrace();
                        String errorMsg = "Failed to load: " + exception.getMessage();
                        statusLabel.setText(errorMsg);
                    } else {
                        System.err.println("No detailed exception available.");
                        statusLabel.setText("Failed to load: Unknown error");
                    }
                    System.err.println("URL tried: " + webEngine.getLocation());

                    try {
                        URL url = new URL(webEngine.getLocation());
                        InetAddress address = InetAddress.getByName(url.getHost());
                        System.out.println("IP address: " + address.getHostAddress());

                        try (Socket socket = new Socket()) {
                            socket.connect(new InetSocketAddress(url.getHost(), url.getPort() == -1 ? 80 : url.getPort()), 3000);
                            System.out.println("Port is reachable");
                        } catch (IOException e) {
                            System.out.println("Port is not reachable: " + e.getMessage());
                            statusLabel.setText("Connection failed: Server not responding");
                        }
                    } catch (Exception e) {
                        System.out.println("Diagnostic failed: " + e.getMessage());
                    }
                    break;
                case CANCELLED:
                    statusLabel.setText("Page load cancelled");
                    loadingIndicator.setVisible(false);
                    break;
                case RUNNING:
                    statusLabel.setText("Loading...");
                    loadingIndicator.setVisible(true);
                    break;
                default:
                    break;
            }
        });

        loadProgress.progressProperty().bind(webEngine.getLoadWorker().progressProperty());
    }

    private void configureHistoryStateListeners() {
        webEngine.getHistory().currentIndexProperty().addListener(
                (observable, oldValue, newValue) -> {
                    int currentIndex = newValue.intValue();
                    int maxIndex = webEngine.getHistory().getEntries().size() - 1;

                    backButton.setDisable(currentIndex <= 0);
                    forwardButton.setDisable(currentIndex >= maxIndex);
                }
        );
    }

    @FXML
    public void handleTileClick(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof Button button) {
            String url = (String) button.getUserData();
            if (url != null && !url.isEmpty()) {
                if (url.contains("google")) {
                    loadUrl(url);
                } else {
                    loadUrl(ROOT + ":" + url);
                }
            }
        }
    }

}
