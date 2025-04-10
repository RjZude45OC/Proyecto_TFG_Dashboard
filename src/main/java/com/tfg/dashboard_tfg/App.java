package com.tfg.dashboard_tfg;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class App extends Application {
    private final double MIN_WIDTH = 800;
    private final double MIN_HEIGHT = 600;
    @Override
    public void start(Stage stage) throws IOException {
        String iconPath = "/com/tfg/dashboard_tfg/assets/logo.png";
        String stylesheetPath = "/com/tfg/dashboard_tfg/styles/styles.css";
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("MainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(String.valueOf(Objects.requireNonNull(App.class.getResource(stylesheetPath)).toExternalForm()));
        stage.setTitle("Dashboard");
        stage.setScene(scene);
        Image icon = new Image(Objects.requireNonNull(App.class.getResourceAsStream(iconPath)));
        stage.getIcons().add(icon);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.show();
    }
    public static void main(String[] args) {
        launch();
    }
}