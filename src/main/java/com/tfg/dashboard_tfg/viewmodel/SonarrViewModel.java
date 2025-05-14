package com.tfg.dashboard_tfg.viewmodel;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class SonarrViewModel {
    @FXML
    public Label serverStatusLabel;
    @FXML
    public ToggleButton autoRefreshToggle;
    @FXML
    public Label activeDownloadsLabel;
    @FXML
    public GridPane connectionPane;
    @FXML
    public TextField serverUrlField;
    @FXML
    public PasswordField apiKeyField;
    @FXML
    public Button connectButton;
    @FXML
    public ProgressBar cpuUsageBar;
    @FXML
    public ProgressBar memoryUsageBar;
    @FXML
    public ProgressBar storageUsageBar;
    @FXML
    public Label networkUsageLabel;
    @FXML
    public Label versionLabel;
    @FXML
    public Label uptimeLabel;
    @FXML
    public Label cpuUsageLabel;
    @FXML
    public Label memoryUsageLabel;
    @FXML
    public Label storageUsageLabel;
    @FXML
    public Label seriesCountLabel;
    @FXML
    public Label episodesCountLabel;
    @FXML
    public Label totalSizeLabel;
    @FXML
    public TableView downloadQueueTable;
    @FXML
    public TableColumn titleColumn;
    @FXML
    public Label lastUpdateLabel;
    @FXML
    public TableColumn messageColumn;
    @FXML
    public TableColumn levelColumn;
    @FXML
    public TableView logTable;
    @FXML
    public TableColumn timeColumn;
    @FXML
    public TableColumn componentColumn;
    @FXML
    public ComboBox logLevelFilter;
    public TableColumn sizeColumn;
    @FXML
    public TableColumn statusColumn;
    @FXML
    public TableColumn progressColumn;
    @FXML
    public TableColumn speedColumn;
    @FXML
    public TableView historyTable;
    @FXML
    public TableColumn historyDateColumn;
    @FXML
    public TableColumn historySeriesColumn;
    @FXML
    public TableColumn historyEpisodeColumn;
    @FXML
    public TableColumn historyQualityColumn;
    @FXML
    public TableColumn historyStatusColumn;
    @FXML
    public TableColumn historySourceColumn;
    @FXML
    public ComboBox historyFilterCombo;
    @FXML
    public TableColumn actionsColumn;
    @FXML
    public TableColumn etaColumn;

    public void refreshHistory(ActionEvent actionEvent) {
    }

    public void refreshQueue(ActionEvent actionEvent) {
    }

    public void clearCompletedDownloads(ActionEvent actionEvent) {
    }

    public void clearLogs(ActionEvent actionEvent) {
    }

    public void refreshServerStatus(ActionEvent actionEvent) {
    }

    public void toggleAutoRefresh(ActionEvent actionEvent) {
    }
}
