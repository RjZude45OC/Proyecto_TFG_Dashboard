package com.tfg.dashboard_tfg.viewmodel;

import com.tfg.dashboard_tfg.model.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RssViewModel implements Initializable {

    // API Connection Fields
    @FXML private TextField apiUrlField;
    @FXML private TextField apiKeyField;
    @FXML private Label statusLabel;

    // Indexer Tab Fields
    @FXML private TextField indexerSearchField;
    @FXML private TableView<IndexerItem> indexersTableView;
    @FXML private TableColumn<IndexerItem, Integer> indexerIdColumn;
    @FXML private TableColumn<IndexerItem, String> indexerNameColumn;
    @FXML private TableColumn<IndexerItem, String> indexerTypeColumn;
    @FXML private TableColumn<IndexerItem, Boolean> indexerEnabledColumn;
    @FXML private TableColumn<IndexerItem, Integer> indexerPriorityColumn;
    @FXML private TableColumn<IndexerItem, Void> indexerActionColumn;

    // Tags Tab Fields
    @FXML private TextField tagSearchField;
    @FXML private TableView<TagItem> tagsTableView;
    @FXML private TableColumn<TagItem, Integer> tagIdColumn;
    @FXML private TableColumn<TagItem, String> tagLabelColumn;
    @FXML private TableColumn<TagItem, Void> tagActionColumn;

    // Stats Tab Fields
    @FXML private Label versionLabel;
    @FXML private Label indexersCountLabel;
    @FXML private Label enabledIndexersLabel;
    @FXML private TableView<IndexerStatsItem> indexerStatsTableView;
    @FXML private TableColumn<IndexerStatsItem, String> statsIndexerNameColumn;
    @FXML private TableColumn<IndexerStatsItem, Integer> statsSuccessCountColumn;
    @FXML private TableColumn<IndexerStatsItem, Integer> statsFailureCountColumn;
    @FXML private TableColumn<IndexerStatsItem, Double> statsAvgResponseColumn;

    // Settings Tab Fields
    @FXML private TextField timeoutField;
    @FXML private TextField cacheDurationField;

    // Observable Lists
    private final ObservableList<IndexerItem> indexersList = FXCollections.observableArrayList();
    private final ObservableList<TagItem> tagsList = FXCollections.observableArrayList();
    private final ObservableList<IndexerStatsItem> indexerStatsList = FXCollections.observableArrayList();

    // Filtered Lists
    private FilteredList<IndexerItem> filteredIndexers;
    private FilteredList<TagItem> filteredTags;

    // Properties
    private final StringProperty apiUrl = new SimpleStringProperty();
    private final StringProperty apiKey = new SimpleStringProperty();
    private final BooleanProperty connected = new SimpleBooleanProperty(false);

    // Constants
    private static final int CONNECTION_TIMEOUT = 30000; // 30 seconds

    // API Endpoints
    private static final String INDEXERS_ENDPOINT = "/api/v1/indexer";
    private static final String TAG_ENDPOINT = "/api/v1/tag";
    private static final String STATUS_ENDPOINT = "/api/v1/system/status";
    private static final String STATS_ENDPOINT = "/api/v1/indexerstats";

    // Indexer Types Map
    private final Map<String, String> indexerDefinitions = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Load saved API settings
        loadApiSettings();

        // Initialize filtered lists
        filteredIndexers = new FilteredList<>(indexersList);
        filteredTags = new FilteredList<>(tagsList);

        // Set up listeners
        apiUrl.bind(apiUrlField.textProperty());
        apiKey.bind(apiKeyField.textProperty());

        // Set up indexer search filter
        indexerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredIndexers.setPredicate(indexer -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return indexer.getName().toLowerCase().contains(lowerCaseFilter) ||
                        indexer.getType().toLowerCase().contains(lowerCaseFilter);
            });
        });

        // Set up tag search filter
        tagSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredTags.setPredicate(tag -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return tag.getLabel().toLowerCase().contains(lowerCaseFilter);
            });
        });

        // Set up table views
        setupIndexerTableView();
        setupTagsTableView();
        setupStatsTableView();

        // Bind status label to connection state
        connected.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                statusLabel.setText("Connected to Prowlarr");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("Not connected");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        });

        // Populate indexer definitions
        populateIndexerDefinitions();
    }

    private void populateIndexerDefinitions() {
        // Populate common Prowlarr indexer types
        indexerDefinitions.put("newznab", "Newznab");
        indexerDefinitions.put("torznab", "Torznab");
        indexerDefinitions.put("torrentRss", "Torrent RSS");
        indexerDefinitions.put("cardigann", "Cardigann");
        indexerDefinitions.put("usenetIndexer", "Usenet Indexer");
        indexerDefinitions.put("torrentIndexer", "Torrent Indexer");
    }

    private void setupIndexerTableView() {
        // Configure table columns
        indexerIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        indexerNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        indexerTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        indexerEnabledColumn.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        indexerPriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));

        // Add action buttons to table
        setupIndexerActionColumn();

        // Set the items
        indexersTableView.setItems(filteredIndexers);
    }

    private void setupTagsTableView() {
        // Configure table columns
        tagIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        tagLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));

        // Add action buttons to table
        setupTagActionColumn();

        // Set the items
        tagsTableView.setItems(filteredTags);
    }

    private void setupStatsTableView() {
        // Configure table columns
        statsIndexerNameColumn.setCellValueFactory(new PropertyValueFactory<>("indexerName"));
        statsSuccessCountColumn.setCellValueFactory(new PropertyValueFactory<>("successCount"));
        statsFailureCountColumn.setCellValueFactory(new PropertyValueFactory<>("failureCount"));
        statsAvgResponseColumn.setCellValueFactory(new PropertyValueFactory<>("avgResponseTime"));

        // Set the items
        indexerStatsTableView.setItems(indexerStatsList);
    }

    private void setupIndexerActionColumn() {
        indexerActionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final Button testButton = new Button("Test");
            private final HBox box = new HBox(5, editButton, testButton, deleteButton);

            {
                box.setAlignment(Pos.CENTER);

                editButton.setOnAction(event -> {
                    IndexerItem indexer = getTableView().getItems().get(getIndex());
                    showEditIndexerDialog(indexer);
                });

                deleteButton.setOnAction(event -> {
                    IndexerItem indexer = getTableView().getItems().get(getIndex());
                    showDeleteIndexerConfirmation(indexer);
                });

                testButton.setOnAction(event -> {
                    IndexerItem indexer = getTableView().getItems().get(getIndex());
                    testIndexer(indexer.getId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });
    }

    private void setupTagActionColumn() {
        tagActionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox box = new HBox(5, editButton, deleteButton);

            {
                box.setAlignment(Pos.CENTER);

                editButton.setOnAction(event -> {
                    TagItem tag = getTableView().getItems().get(getIndex());
                    showEditTagDialog(tag);
                });

                deleteButton.setOnAction(event -> {
                    TagItem tag = getTableView().getItems().get(getIndex());
                    showDeleteTagConfirmation(tag);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });
    }

    // Event handlers

    @FXML
    private void connectToProwlarr() {
        // Validate connection inputs
        if (apiUrlField.getText().isEmpty() || apiKeyField.getText().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Connection Error", "Please provide both API URL and API Key");
            return;
        }

        // Save API settings
        saveApiSettings();

        // Test connection
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject statusResponse = makeApiGetRequest(STATUS_ENDPOINT);
                if (statusResponse != null) {
                    Platform.runLater(() -> {
                        connected.set(true);
                        versionLabel.setText(statusResponse.getString("version"));
                        showAlert(Alert.AlertType.INFORMATION, "Connection Success",
                                "Successfully connected to Prowlarr v" + statusResponse.getString("version"));

                        // Load data from API
                        refreshIndexers();
                        refreshTags();
                        refreshStats();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connected.set(false);
                    showAlert(Alert.AlertType.ERROR, "Connection Error",
                            "Failed to connect to Prowlarr: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void refreshIndexers() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                JSONArray indexersArray = makeApiGetRequest(INDEXERS_ENDPOINT).getJSONArray("records");
                List<IndexerItem> indexers = new ArrayList<>();

                for (int i = 0; i < indexersArray.length(); i++) {
                    JSONObject indexer = indexersArray.getJSONObject(i);
                    indexers.add(new IndexerItem(
                            indexer.getInt("id"),
                            indexer.getString("name"),
                            getIndexerTypeDisplayName(indexer.getString("protocol")),
                            indexer.getBoolean("enable"),
                            indexer.getInt("priority"),
                            extractTagIds(indexer.getJSONArray("tags")),
                            indexer.has("settings") ? indexer.getJSONObject("settings").toString() : "{}"
                    ));
                }

                Platform.runLater(() -> {
                    indexersList.clear();
                    indexersList.addAll(indexers);
                    indexersCountLabel.setText(String.valueOf(indexers.size()));
                    enabledIndexersLabel.setText(String.valueOf(
                            indexers.stream().filter(IndexerItem::isEnabled).count()));
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to load indexers: " + e.getMessage())
                );
            }
        });
    }

    @FXML
    private void refreshTags() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                JSONArray tagsArray = makeApiGetRequest(TAG_ENDPOINT).getJSONArray("records");
                List<TagItem> tags = new ArrayList<>();

                for (int i = 0; i < tagsArray.length(); i++) {
                    JSONObject tag = tagsArray.getJSONObject(i);
                    tags.add(new TagItem(
                            tag.getInt("id"),
                            tag.getString("label")
                    ));
                }

                Platform.runLater(() -> {
                    tagsList.clear();
                    tagsList.addAll(tags);
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to load tags: " + e.getMessage())
                );
            }
        });
    }

    @FXML
    private void refreshStats() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                JSONArray statsArray = makeApiGetRequest(STATS_ENDPOINT).getJSONArray("records");
                List<IndexerStatsItem> stats = new ArrayList<>();

                for (int i = 0; i < statsArray.length(); i++) {
                    JSONObject stat = statsArray.getJSONObject(i);

                    String indexerName = "Unknown";
                    for (IndexerItem indexer : indexersList) {
                        if (indexer.getId() == stat.getInt("indexerId")) {
                            indexerName = indexer.getName();
                            break;
                        }
                    }

                    stats.add(new IndexerStatsItem(
                            stat.getInt("indexerId"),
                            indexerName,
                            stat.getInt("numberOfQueries"),
                            stat.getInt("numberOfGrabs"),
                            stat.getInt("numberOfFailures"),
                            stat.getDouble("averageResponseTime")
                    ));
                }

                Platform.runLater(() -> {
                    indexerStatsList.clear();
                    indexerStatsList.addAll(stats);
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to load statistics: " + e.getMessage())
                );
            }
        });
    }

    @FXML
    private void showAddIndexerDialog() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        // Fetch available indexer types
        CompletableFuture.runAsync(() -> {
            try {
                JSONArray definitionsArray = makeApiGetRequest("/api/v1/indexer/schema").getJSONArray("records");
                List<String> availableTypes = new ArrayList<>();

                for (int i = 0; i < definitionsArray.length(); i++) {
                    JSONObject definition = definitionsArray.getJSONObject(i);
                    availableTypes.add(definition.getString("protocol"));
                }

                Platform.runLater(() -> createAddIndexerDialog(availableTypes));

            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Error", "Failed to load indexer definitions: " + e.getMessage())
                );
            }
        });
    }

    private void createAddIndexerDialog(List<String> availableTypes) {
        Dialog<IndexerItem> dialog = new Dialog<>();
        dialog.setTitle("Add Indexer");
        dialog.setHeaderText("Enter indexer details");

        // Set button types
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Form fields
        TextField nameField = new TextField();
        nameField.setPromptText("Indexer Name");

        ComboBox<String> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll(availableTypes);
        typeComboBox.setPromptText("Select Type");

        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);

        TextField priorityField = new TextField("25");
        priorityField.setPromptText("Priority (1-50)");

        // Add fields to grid
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeComboBox, 1, 1);
        grid.add(new Label("Priority:"), 0, 2);
        grid.add(priorityField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the name field
        Platform.runLater(nameField::requestFocus);

        // Convert the result to an indexer item when the add button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    String name = nameField.getText().trim();
                    String type = typeComboBox.getValue();
                    int priority = Integer.parseInt(priorityField.getText().trim());
                    boolean enabled = enabledCheckBox.isSelected();

                    if (name.isEmpty() || type == null) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Name and type are required");
                        return null;
                    }

                    if (priority < 1 || priority > 50) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Priority must be between 1 and 50");
                        return null;
                    }

                    // Create JSON for the new indexer
                    JSONObject indexerJson = new JSONObject();
                    indexerJson.put("name", name);
                    indexerJson.put("protocol", type);
                    indexerJson.put("enable", enabled);
                    indexerJson.put("priority", priority);
                    indexerJson.put("tags", new JSONArray());

                    // Send API request to create the indexer
                    JSONObject response = makeApiPostRequest(INDEXERS_ENDPOINT, indexerJson);

                    if (response != null && response.has("id")) {
                        int id = response.getInt("id");

                        return new IndexerItem(
                                id,
                                name,
                                getIndexerTypeDisplayName(type),
                                enabled,
                                priority,
                                new ArrayList<>(),
                                "{}"
                        );
                    } else {
                        return null;
                    }

                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Input Error", "Priority must be a number");
                    return null;
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to add indexer: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<IndexerItem> result = dialog.showAndWait();

        result.ifPresent(indexer -> {
            indexersList.add(indexer);
            indexersCountLabel.setText(String.valueOf(indexersList.size()));
            enabledIndexersLabel.setText(String.valueOf(
                    indexersList.stream().filter(IndexerItem::isEnabled).count()));
            showAlert(Alert.AlertType.INFORMATION, "Success", "Indexer added successfully");
        });
    }

    private void showEditIndexerDialog(IndexerItem indexer) {
        Dialog<IndexerItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Indexer");
        dialog.setHeaderText("Edit indexer details for: " + indexer.getName());

        // Set button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Form fields
        TextField nameField = new TextField(indexer.getName());
        nameField.setPromptText("Indexer Name");

        Label typeLabel = new Label(indexer.getType());

        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(indexer.isEnabled());

        TextField priorityField = new TextField(String.valueOf(indexer.getPriority()));
        priorityField.setPromptText("Priority (1-50)");

        // Add fields to grid
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        // Continue the showEditIndexerDialog method
        grid.add(typeLabel, 1, 1);
        grid.add(new Label("Priority:"), 0, 2);
        grid.add(priorityField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the name field
        Platform.runLater(nameField::requestFocus);

        // Convert the result to an indexer item when the save button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String name = nameField.getText().trim();
                    int priority = Integer.parseInt(priorityField.getText().trim());
                    boolean enabled = enabledCheckBox.isSelected();

                    if (name.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Name is required");
                        return null;
                    }

                    if (priority < 1 || priority > 50) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Priority must be between 1 and 50");
                        return null;
                    }

                    // Create JSON for updating the indexer
                    JSONObject indexerJson = new JSONObject();
                    indexerJson.put("id", indexer.getId());
                    indexerJson.put("name", name);
                    indexerJson.put("protocol", getOriginalIndexerType(indexer.getType()));
                    indexerJson.put("enable", enabled);
                    indexerJson.put("priority", priority);
                    indexerJson.put("tags", new JSONArray(indexer.getTagIds()));

                    // Send API request to update the indexer
                    JSONObject response = makeApiPutRequest(INDEXERS_ENDPOINT + "/" + indexer.getId(), indexerJson);

                    if (response != null && response.has("id")) {
                        // Update the indexer object
                        indexer.setName(name);
                        indexer.setEnabled(enabled);
                        indexer.setPriority(priority);
                        return indexer;
                    } else {
                        return null;
                    }

                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Input Error", "Priority must be a number");
                    return null;
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to update indexer: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<IndexerItem> result = dialog.showAndWait();

        result.ifPresent(updatedIndexer -> {
            // Refresh the table view
            indexersTableView.refresh();
            enabledIndexersLabel.setText(String.valueOf(
                    indexersList.stream().filter(IndexerItem::isEnabled).count()));
            showAlert(Alert.AlertType.INFORMATION, "Success", "Indexer updated successfully");
        });
    }

    private void showDeleteIndexerConfirmation(IndexerItem indexer) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Indexer");
        alert.setHeaderText("Delete Indexer: " + indexer.getName());
        alert.setContentText("Are you sure you want to delete this indexer? This action cannot be undone.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            CompletableFuture.runAsync(() -> {
                try {
                    boolean success = makeApiDeleteRequest(INDEXERS_ENDPOINT + "/" + indexer.getId());

                    if (success) {
                        Platform.runLater(() -> {
                            indexersList.remove(indexer);
                            indexersCountLabel.setText(String.valueOf(indexersList.size()));
                            enabledIndexersLabel.setText(String.valueOf(
                                    indexersList.stream().filter(IndexerItem::isEnabled).count()));
                            showAlert(Alert.AlertType.INFORMATION, "Success", "Indexer deleted successfully");
                        });
                    } else {
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete indexer")
                        );
                    }
                } catch (Exception e) {
                    Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete indexer: " + e.getMessage())
                    );
                }
            });
        }
    }

    private void testIndexer(int indexerId) {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        statusLabel.setText("Testing indexer...");
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject response = makeApiPostRequest(INDEXERS_ENDPOINT + "/test/" + indexerId, new JSONObject());

                Platform.runLater(() -> {
                    if (response != null && response.has("isValid") && response.getBoolean("isValid")) {
                        statusLabel.setText("Indexer test successful");
                        showAlert(Alert.AlertType.INFORMATION, "Test Result", "Indexer test successful!");
                    } else {
                        String errorMessage = "Unknown error";
                        if (response != null && response.has("errorMessage")) {
                            errorMessage = response.getString("errorMessage");
                        }
                        statusLabel.setText("Indexer test failed");
                        showAlert(Alert.AlertType.ERROR, "Test Result", "Indexer test failed: " + errorMessage);
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Indexer test failed");
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to test indexer: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    private void testAllIndexers() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        statusLabel.setText("Testing all indexers...");
        int totalIndexers = indexersList.size();
        final int[] successCount = {0};
        final int[] failCount = {0};

        for (IndexerItem indexer : indexersList) {
            CompletableFuture.runAsync(() -> {
                try {
                    JSONObject response = makeApiPostRequest(INDEXERS_ENDPOINT + "/test/" + indexer.getId(), new JSONObject());

                    Platform.runLater(() -> {
                        if (response != null && response.has("isValid") && response.getBoolean("isValid")) {
                            successCount[0]++;
                        } else {
                            failCount[0]++;
                        }

                        // Update status
                        int completed = successCount[0] + failCount[0];
                        if (completed == totalIndexers) {
                            statusLabel.setText("Test complete: " + successCount[0] + " successful, " + failCount[0] + " failed");
                            showAlert(Alert.AlertType.INFORMATION, "Test Results",
                                    "Test complete: " + successCount[0] + " successful, " + failCount[0] + " failed");
                        } else {
                            statusLabel.setText("Testing indexers: " + completed + "/" + totalIndexers);
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        failCount[0]++;
                        int completed = successCount[0] + failCount[0];
                        if (completed == totalIndexers) {
                            statusLabel.setText("Test complete: " + successCount[0] + " successful, " + failCount[0] + " failed");
                            showAlert(Alert.AlertType.INFORMATION, "Test Results",
                                    "Test complete: " + successCount[0] + " successful, " + failCount[0] + " failed");
                        } else {
                            statusLabel.setText("Testing indexers: " + completed + "/" + totalIndexers);
                        }
                    });
                }
            });
        }
    }

    @FXML
    private void showAddTagDialog() {
        if (!connected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to Prowlarr first");
            return;
        }

        Dialog<TagItem> dialog = new Dialog<>();
        dialog.setTitle("Add Tag");
        dialog.setHeaderText("Enter tag details");

        // Set button types
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Form fields
        TextField labelField = new TextField();
        labelField.setPromptText("Tag Label");

        // Add fields to grid
        grid.add(new Label("Label:"), 0, 0);
        grid.add(labelField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the label field
        Platform.runLater(labelField::requestFocus);

        // Convert the result to a tag item when the add button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    String label = labelField.getText().trim();

                    if (label.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Label is required");
                        return null;
                    }

                    // Create JSON for the new tag
                    JSONObject tagJson = new JSONObject();
                    tagJson.put("label", label);

                    // Send API request to create the tag
                    JSONObject response = makeApiPostRequest(TAG_ENDPOINT, tagJson);

                    if (response != null && response.has("id")) {
                        int id = response.getInt("id");
                        return new TagItem(id, label);
                    } else {
                        return null;
                    }

                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to add tag: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<TagItem> result = dialog.showAndWait();

        result.ifPresent(tag -> {
            tagsList.add(tag);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Tag added successfully");
        });
    }

    private void showEditTagDialog(TagItem tag) {
        Dialog<TagItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Tag");
        dialog.setHeaderText("Edit tag details");

        // Set button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Form fields
        TextField labelField = new TextField(tag.getLabel());
        labelField.setPromptText("Tag Label");

        // Add fields to grid
        grid.add(new Label("Label:"), 0, 0);
        grid.add(labelField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the label field
        Platform.runLater(labelField::requestFocus);

        // Convert the result to a tag item when the save button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String label = labelField.getText().trim();

                    if (label.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Input Error", "Label is required");
                        return null;
                    }

                    // Create JSON for updating the tag
                    JSONObject tagJson = new JSONObject();
                    tagJson.put("id", tag.getId());
                    tagJson.put("label", label);

                    // Send API request to update the tag
                    JSONObject response = makeApiPutRequest(TAG_ENDPOINT + "/" + tag.getId(), tagJson);

                    if (response != null && response.has("id")) {
                        // Update the tag object
                        tag.setLabel(label);
                        return tag;
                    } else {
                        return null;
                    }

                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to update tag: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<TagItem> result = dialog.showAndWait();

        result.ifPresent(updatedTag -> {
            // Refresh the table view
            tagsTableView.refresh();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Tag updated successfully");
        });
    }

    private void showDeleteTagConfirmation(TagItem tag) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Tag");
        alert.setHeaderText("Delete Tag: " + tag.getLabel());
        alert.setContentText("Are you sure you want to delete this tag? This may affect indexers that use this tag.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            CompletableFuture.runAsync(() -> {
                try {
                    boolean success = makeApiDeleteRequest(TAG_ENDPOINT + "/" + tag.getId());

                    if (success) {
                        Platform.runLater(() -> {
                            tagsList.remove(tag);
                            showAlert(Alert.AlertType.INFORMATION, "Success", "Tag deleted successfully");
                        });
                    } else {
                        Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete tag")
                        );
                    }
                } catch (Exception e) {
                    Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete tag: " + e.getMessage())
                    );
                }
            });
        }
    }

    @FXML
    private void saveSettings() {
        try {
            Properties properties = new Properties();
            properties.setProperty("timeout", timeoutField.getText());
            properties.setProperty("cacheDuration", cacheDurationField.getText());

            try (OutputStream output = new FileOutputStream("prowlarr-settings.properties")) {
                properties.store(output, "Prowlarr Integration Settings");
                showAlert(Alert.AlertType.INFORMATION, "Success", "Settings saved successfully");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to save settings: " + e.getMessage());
        }
    }

    // Helper methods

    private void loadApiSettings() {
        try {
            Properties properties = new Properties();
            File settingsFile = new File("prowlarr-settings.properties");

            if (settingsFile.exists()) {
                try (InputStream input = new FileInputStream(settingsFile)) {
                    properties.load(input);

                    apiUrlField.setText(properties.getProperty("apiUrl", ""));
                    apiKeyField.setText(properties.getProperty("apiKey", ""));
                    timeoutField.setText(properties.getProperty("timeout", "30"));
                    cacheDurationField.setText(properties.getProperty("cacheDuration", "10"));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }

    private void saveApiSettings() {
        try {
            Properties properties = new Properties();
            File settingsFile = new File("prowlarr-settings.properties");

            if (settingsFile.exists()) {
                try (InputStream input = new FileInputStream(settingsFile)) {
                    properties.load(input);
                }
            }

            properties.setProperty("apiUrl", apiUrlField.getText());
            properties.setProperty("apiKey", apiKeyField.getText());

            try (OutputStream output = new FileOutputStream(settingsFile)) {
                properties.store(output, "Prowlarr Integration Settings");
            }
        } catch (Exception e) {
            System.err.println("Failed to save API settings: " + e.getMessage());
        }
    }

    private String getIndexerTypeDisplayName(String type) {
        return indexerDefinitions.getOrDefault(type, type);
    }

    private String getOriginalIndexerType(String displayName) {
        for (Map.Entry<String, String> entry : indexerDefinitions.entrySet()) {
            if (entry.getValue().equals(displayName)) {
                return entry.getKey();
            }
        }
        return displayName;
    }

    private List<Integer> extractTagIds(JSONArray tagsArray) {
        List<Integer> tagIds = new ArrayList<>();
        for (int i = 0; i < tagsArray.length(); i++) {
            tagIds.add(tagsArray.getInt(i));
        }
        return tagIds;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // API Request Methods

    private JSONObject makeApiGetRequest(String endpoint) throws Exception {
        URL url = new URL(apiUrlField.getText().trim() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Api-Key", apiKeyField.getText().trim());
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String response = readResponse(connection.getInputStream());
            if (response.startsWith("[")) {
                JSONObject wrapper = new JSONObject();
                wrapper.put("records", new JSONArray(response));
                return wrapper;
            } else {
                return new JSONObject(response);
            }
        } else {
            throw new Exception("API request failed with status: " + responseCode);
        }
    }

    private JSONObject makeApiPostRequest(String endpoint, JSONObject data) throws Exception {
        URL url = new URL(apiUrlField.getText().trim() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Api-Key", apiKeyField.getText().trim());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = data.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
            String response = readResponse(connection.getInputStream());
            return new JSONObject(response);
        } else {
            String errorResponse = readResponse(connection.getErrorStream());
            throw new Exception("API request failed with status: " + responseCode + " - " + errorResponse);
        }
    }

    private JSONObject makeApiPutRequest(String endpoint, JSONObject data) throws Exception {
        URL url = new URL(apiUrlField.getText().trim() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("X-Api-Key", apiKeyField.getText().trim());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = data.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED) {
            String response = readResponse(connection.getInputStream());
            return new JSONObject(response);
        } else {
            String errorResponse = readResponse(connection.getErrorStream());
            throw new Exception("API request failed with status: " + responseCode + " - " + errorResponse);
        }
    }

    private boolean makeApiDeleteRequest(String endpoint) throws Exception {
        URL url = new URL(apiUrlField.getText().trim() + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("X-Api-Key", apiKeyField.getText().trim());
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);

        int responseCode = connection.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
}