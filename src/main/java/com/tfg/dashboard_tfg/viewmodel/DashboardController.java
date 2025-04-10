package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Section;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static javafx.scene.control.PopupControl.USE_COMPUTED_SIZE;

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

//    private int test;

    // Add these fields to your DashboardController class
    private Tile expandedTile = null;
    private GridPane originalParent = null;
    private int originalColIndex = 0;
    private int originalRowIndex = 0;
    private Node[] hiddenTiles = null;


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

//    @FXML
//    private ToggleButton themeToggle;

    @FXML
    public void initialize() {
        temperatureTile.getSections().clear();
        temperatureTile.getSections().add(new Section(0, 65, "Normal", Color.web("#28b745")));
        temperatureTile.getSections().add(new Section(65, 80, "Warning", Color.web("#ffc107")));
        temperatureTile.getSections().add(new Section(80, 100, "Critical", Color.web("dc3545")));
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
        Controller.darkMode.addListener(this::changed);

//        Timeline cleardata = new Timeline(new KeyFrame(Duration.seconds(30), event -> removeolddata(null)));
//        cleardata.setCycleCount(Timeline.INDEFINITE);
//        cleardata.play();

        //update every x time
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> update()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
//        test = 0;
    }
//    public void removeolddata(MouseEvent mouseEvent){
//        networkTile.clearData();
//    }
    public void update() {
        int MAX_VALUE = 15;
        if (networkTile.getChartData().size() > MAX_VALUE){
            networkTile.getChartData().subList(0, 5).clear();
        }
        Random RND = new Random();
        ChartData smoothChartData1 = new ChartData("Item 1", RND.nextDouble() * 25, Tile.BLUE);
        networkTile.addChartData(smoothChartData1);
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

    public void ondrag(MouseEvent mouseEvent) {
        System.out.println(mouseEvent.getClass());
        System.out.println("ondrag");
    }

    public void ondragdrop(DragEvent dragEvent) {
        System.out.println("ondragdrop");
    }

    public void onscroll(ScrollEvent scrollEvent) {
        System.out.println("onscroll");
//        switch (test){
//            case 0:
//                cpuTile.setSkinType(TIMELINE);
//                break;
//            case 1:
//                cpuTile.setSkinType(GAUGE_SPARK_LINE);
//                break;
//            default:
//                cpuTile.setSkinType(SPARK_LINE);
//                break;
//        }
//        if (test == 2)
//        {
//            test = 0;
//        }
//        else{
//            test++;
//        }
    }
    @FXML
    public void onclicktile(MouseEvent event) {
        // Get the clicked tile
        Tile clickedTile = (Tile) event.getSource();

        // If there's already an expanded tile
        if (expandedTile != null) {
            // If clicking the already expanded tile, restore the original layout
            if (expandedTile == clickedTile) {
                restoreOriginalLayout();
            }
            // If clicking a different tile while one is expanded, restore and then expand the new one
            else {
                restoreOriginalLayout();
                expandTile(clickedTile);
            }
        }
        // If no tile is expanded yet, expand the clicked one
        else {
            expandTile(clickedTile);
        }
    }

    private void expandTile(Tile tile) {
        // Save the expanded tile reference
        expandedTile = tile;

        // Get parent GridPane
        originalParent = (GridPane) tile.getParent();

        // Store original dimensions
        double originalGridWidth = originalParent.getWidth();
        double originalGridHeight = originalParent.getHeight();
        double originalTileWidth = tile.getWidth();
        double originalTileHeight = tile.getHeight();

        // Set minimum size for the GridPane to prevent resizing
        originalParent.setMinWidth(originalGridWidth);
        originalParent.setMinHeight(originalGridHeight);
        originalParent.setPrefWidth(originalGridWidth);
        originalParent.setPrefHeight(originalGridHeight);

        // Save original position
        originalColIndex = GridPane.getColumnIndex(tile) != null ? GridPane.getColumnIndex(tile) : 0;
        originalRowIndex = GridPane.getRowIndex(tile) != null ? GridPane.getRowIndex(tile) : 0;

        // Store all other tiles to hide them
        hiddenTiles = new Node[originalParent.getChildren().size() - 1];
        int index = 0;

        // Remove all other tiles from the GridPane but save them
        for (Node node : new ArrayList<>(originalParent.getChildren())) {
            if (node != tile) {
                originalParent.getChildren().remove(node);
                hiddenTiles[index++] = node;
            }
        }

        // Make the clicked tile span the entire GridPane
        GridPane.setColumnSpan(tile, originalParent.getColumnConstraints().size());
        GridPane.setRowSpan(tile, originalParent.getRowConstraints().size());
        GridPane.setColumnIndex(tile, 0);
        GridPane.setRowIndex(tile, 0);

        // Force the tile to fill the available space
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMaxHeight(Double.MAX_VALUE);

        // Optional: Add animation effect for smooth transition
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(300), tile);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.02);
        scaleTransition.setToY(1.02);
        scaleTransition.setCycleCount(2);
        scaleTransition.setAutoReverse(true);
        scaleTransition.play();

        // Optional: Change the title to indicate the expanded state
        tile.setDescription(tile.getDescription() + " (Click to minimize)");
    }

    private void restoreOriginalLayout() {
        if (expandedTile == null) return;

        // Reset the expanded tile's position and span
        GridPane.setColumnSpan(expandedTile, 1);
        GridPane.setRowSpan(expandedTile, 1);
        GridPane.setColumnIndex(expandedTile, originalColIndex);
        GridPane.setRowIndex(expandedTile, originalRowIndex);

        // Reset the tile's size constraints
        expandedTile.setMaxWidth(USE_COMPUTED_SIZE);
        expandedTile.setMaxHeight(USE_COMPUTED_SIZE);

        // Restore the original description
        String currentDesc = expandedTile.getDescription();
        if (currentDesc.endsWith(" (Click to minimize)")) {
            expandedTile.setDescription(currentDesc.replace(" (Click to minimize)", ""));
        }

        // Add back all the hidden tiles
        for (Node node : hiddenTiles) {
            if (node != null) {
                originalParent.getChildren().add(node);
            }
        }

        // Clear the expanded tile reference
        expandedTile = null;
        hiddenTiles = null;
    }

    private void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        updateTileColors(newValue);
    }
}
