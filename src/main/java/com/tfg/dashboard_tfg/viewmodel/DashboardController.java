package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DashboardController {
    @FXML
    private Tile systemStatusTile;
    @FXML
    private Tile storageTile;
    @FXML
    private Tile networkTile;
    @FXML
    private Tile cpuTile;
    @FXML
    private Tile memoryTile;
    @FXML
    private Tile temperatureTile;
    @FXML
    private Tile jellyfinStatusTile;
    @FXML
    private Tile dockerStatusTile;
    @FXML
    private Label statusLabel;

    private List<Tile> tileList;

    // Method to update tile colors based on theme

    private void updateTileColors(boolean isDarkMode) {
        for (Tile tile : tileList) {
            if (isDarkMode) {
                // Dark theme colors
                tile.setBackgroundColor(Color.web("#282832")); // Dark theme background
                tile.setBarColor(Color.web("#bcc0cc"));
                tile.setTitleColor(Color.web("#bcc0cc"));
                tile.setNeedleColor(Color.web("#5c5f77"));
                tile.setValueColor(Color.web("#bcc0cc"));
                tile.setDescriptionColor(Color.web("#bcc0cc"));
                tile.setUnitColor(Color.web("#bcc0cc"));
            } else {
                // Light theme colors
                tile.setBackgroundColor(Color.web("#DADADC")); // Light theme background
                tile.setBarColor(Color.web("#5c5f77"));
                tile.setTitleColor(Color.web("#5c5f77"));
                tile.setNeedleColor(Color.web("#5c5f77"));
                tile.setValueColor(Color.web("#5c5f77"));
                tile.setDescriptionColor(Color.web("#5c5f77"));
                tile.setUnitColor(Color.web("#5c5f77"));
            }
        }
    }

    @FXML
    private ToggleButton themeToggle;

    @FXML
    public void initialize() {
        if (jellyfinStatusTile.isActive()){
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        }
        else {
            statusLabel.setText("there is problem with server");
            statusLabel.setTextFill(Color.web("#dc3545"));
        }
        tileList = Arrays.asList(systemStatusTile, storageTile, networkTile, cpuTile,
                memoryTile, temperatureTile, jellyfinStatusTile, dockerStatusTile);
        //inicializa el color inicio de tiles
        updateTileColors(Controller.darkMode.getValue());
        //
        Controller.darkMode.addListener((observable, oldValue, newValue) -> {
            updateTileColors(newValue);
        });

//        Timeline cleardata = new Timeline(new KeyFrame(Duration.seconds(30), event -> removeolddata(null)));
//        cleardata.setCycleCount(Timeline.INDEFINITE);
//        cleardata.play();

        //update every x time
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> update(null)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
    public void removeolddata(MouseEvent mouseEvent){
        networkTile.clearData();
    }
    public void update(MouseEvent mouseEvent) {
        int MAX_VALUE = 15;
        if (networkTile.getChartData().size() > MAX_VALUE){
            for (int i = 0; i < 5; i++) {
                networkTile.getChartData().remove(i);
            }
        }
        Random RND = new Random();
        ChartData smoothChartData1 = new ChartData("Item 1", RND.nextDouble() * 25, Tile.BLUE);
        ChartData smoothChartData2 = new ChartData("Item 2", RND.nextDouble() * 25, Tile.BLUE);
        ChartData smoothChartData3 = new ChartData("Item 3", RND.nextDouble() * 25, Tile.BLUE);
        ChartData smoothChartData4 = new ChartData("Item 4", RND.nextDouble() * 25, Tile.BLUE);
        networkTile.addChartData(smoothChartData1, smoothChartData2, smoothChartData3, smoothChartData4);
        cpuTile.setValue(RND.nextDouble() * 25);
        dockerStatusTile.setValue(RND.nextDouble() * 25);
        systemStatusTile.setValue(RND.nextDouble() * 25);
        storageTile.setValue(RND.nextDouble() * 25);
        memoryTile.setValue(RND.nextDouble() * 25);
        temperatureTile.setValue(RND.nextDouble() * 100);
        jellyfinStatusTile.setActive(!jellyfinStatusTile.isActive());
        if (jellyfinStatusTile.isActive()){
            statusLabel.setText("server is running healthy");
            statusLabel.setTextFill(Color.web("#28a745"));
        }
        else {
            statusLabel.setText("there is problem with server");
            statusLabel.setTextFill(Color.web("#dc3545"));
        }
    }

    public void onclicktile(MouseEvent mouseEvent) {
        System.out.println("onclick");
    }

    public void ondrag(MouseEvent mouseEvent) {
        System.out.println("ondrag");
    }

    public void ondragdrop(DragEvent dragEvent) {
        System.out.println("ondragdrop");
    }
}
