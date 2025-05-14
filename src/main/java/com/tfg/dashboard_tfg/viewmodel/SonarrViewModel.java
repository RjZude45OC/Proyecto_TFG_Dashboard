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
    public Label lastUpdateLabel;
    public TableColumn messageColumn;
    public TableColumn levelColumn;
    public TableView logTable;
    public TableColumn timeColumn;
    public TableColumn componentColumn;
    public ComboBox logLevelFilter;
    public TableColumn sizeColumn;
    public TableColumn statusColumn;
    public TableColumn progressColumn;
    public TableColumn speedColumn;
    public TableView historyTable;
    public TableColumn historyDateColumn;
    public TableColumn historySeriesColumn;
    public TableColumn historyEpisodeColumn;
    public TableColumn historyQualityColumn;
    public TableColumn historyStatusColumn;
    public TableColumn historySourceColumn;
    public ComboBox historyFilterCombo;
    public TableColumn actionsColumn;
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
