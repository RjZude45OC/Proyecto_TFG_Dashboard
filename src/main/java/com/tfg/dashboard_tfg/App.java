package com.tfg.dashboard_tfg;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class App extends Application {
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
        stage.show();
//        tilesfx test
//        double TILE_WIDTH  = 150;
//        double TILE_HEIGHT = 150;
//        Tile clockTile = TileBuilder.create()
//                .skinType(Tile.SkinType.CLOCK)
//                .prefSize(TILE_WIDTH, TILE_HEIGHT)
//                .title("Clock Tile")
//                .text("Whatever text")
//                .dateVisible(true)
//                .locale(Locale.US)
//                .running(true)
//                .build();
//        FlowGridPane pane = new FlowGridPane(1,1,clockTile);
//        pane.setHgap(5);
//        pane.setVgap(5);
//        pane.setAlignment(Pos.CENTER);
//        pane.setCenterShape(true);
//        pane.setPadding(new Insets(5));
//        //pane.setPrefSize(800, 600);
//        pane.setBackground(new Background(new BackgroundFill(Color.web("#101214"), CornerRadii.EMPTY, Insets.EMPTY)));
//        PerspectiveCamera camera = new PerspectiveCamera();
//        camera.setFieldOfView(10);
//
//        Scene scene = new Scene(pane);
//        scene.setCamera(camera);
//
//        stage.setTitle("TilesFX");
//        stage.setScene(scene);
//        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}