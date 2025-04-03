package com.tfg.dashboard_tfg.viewmodel;

import eu.hansolo.tilesfx.Tile;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private List<Tile> tileList;

    // Method to update tile colors based on theme

    private void updateTileColors(boolean isDarkMode) {
        for(Tile tile :tileList) {
        if (isDarkMode) {
            // Dark theme colors
            tile.setBackgroundColor(Color.web("#282832")); // Dark theme background
        } else {
            // Light theme colors
            tile.setBackgroundColor(Color.web("#DADADC")); // Light theme background
        }
        }
    }
    @FXML
    private ToggleButton themeToggle;

    @FXML
    public void initialize() {
        tileList = Arrays.asList(systemStatusTile, storageTile, networkTile, cpuTile,
                memoryTile, temperatureTile, jellyfinStatusTile, dockerStatusTile);
        //inicializa el color inicio de tiles
        updateTileColors(Controller.darkMode.getValue());
        //
        Controller.darkMode.addListener((observable, oldValue, newValue) -> {
            updateTileColors(newValue);
        });
    }
}
