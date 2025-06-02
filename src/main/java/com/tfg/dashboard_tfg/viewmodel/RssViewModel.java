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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RssViewModel implements Initializable {

    @FXML
    private TextField apiUrlField;
    @FXML
    private TextField apiKeyField;
    @FXML
    private Label statusLabel;

    @FXML
    private TextField indexerSearchField;
    @FXML
    private TableView<IndexerItem> indexersTableView;
    @FXML
    private TableColumn<IndexerItem, Integer> indexerIdColumn;
    @FXML
    private TableColumn<IndexerItem, String> indexerNameColumn;
    @FXML
    private TableColumn<IndexerItem, String> indexerTypeColumn;
    @FXML
    private TableColumn<IndexerItem, Boolean> indexerEnabledColumn;
    @FXML
    private TableColumn<IndexerItem, Integer> indexerPriorityColumn;
    @FXML
    private TableColumn<IndexerItem, Void> indexerActionColumn;

    @FXML
    private TextField tagSearchField;
    @FXML
    private TableView<TagItem> tagsTableView;
    @FXML
    private TableColumn<TagItem, Integer> tagIdColumn;
    @FXML
    private TableColumn<TagItem, String> tagLabelColumn;
    @FXML
    private TableColumn<TagItem, Void> tagActionColumn;

    @FXML
    private Label versionLabel;
    @FXML
    private Label indexersCountLabel;
    @FXML
    private Label enabledIndexersLabel;
    @FXML
    private TableView<IndexerStatsItem> indexerStatsTableView;
    @FXML
    private TableColumn<IndexerStatsItem, String> statsIndexerNameColumn;
    @FXML
    private TableColumn<IndexerStatsItem, Integer> statsSuccessCountColumn;
    @FXML
    private TableColumn<IndexerStatsItem, Integer> statsFailureCountColumn;
    @FXML
    private TableColumn<IndexerStatsItem, Double> statsAvgResponseColumn;

    @FXML
    private TextField timeoutField;
    @FXML
    private TextField cacheDurationField;

    private final ObservableList<IndexerItem> indexersList = FXCollections.observableArrayList();
    private final ObservableList<TagItem> tagsList = FXCollections.observableArrayList();
    private final ObservableList<IndexerStatsItem> indexerStatsList = FXCollections.observableArrayList();

    private FilteredList<IndexerItem> filteredIndexers;
    private FilteredList<TagItem> filteredTags;

    private final StringProperty apiUrl = new SimpleStringProperty();
    private final StringProperty apiKey = new SimpleStringProperty();
    private final BooleanProperty connected = new SimpleBooleanProperty(false);

    private static final String PROPERTIES_FILE = "connection.properties";
    private static final int CONNECTION_TIMEOUT = 30000;
    private static final String INDEXERS_ENDPOINT = "/api/v1/indexer";
    private static final String TAG_ENDPOINT = "/api/v1/tag";
    private static final String STATUS_ENDPOINT = "/api/v1/system/status";
    private static final String STATS_ENDPOINT = "/api/v1/indexerstats";
    private final Map<String, String> indexerDefinitions = new HashMap<>();
    private final Properties appProperties = new Properties();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadProperties();

        filteredIndexers = new FilteredList<>(indexersList);
        filteredTags = new FilteredList<>(tagsList);

        apiUrl.bind(apiUrlField.textProperty());
        apiKey.bind(apiKeyField.textProperty());

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

        tagSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredTags.setPredicate(tag -> {
                if (newVal == null || newVal.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newVal.toLowerCase();
                return tag.getLabel().toLowerCase().contains(lowerCaseFilter);
            });
        });

        setupIndexerTableView();
        setupTagsTableView();
        setupStatsTableView();

        connected.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                statusLabel.setText("Connected to Prowlarr");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("Not connected");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
        });
        populateIndexerDefinitions();
        connectToProwlarr();
    }

    private void populateIndexerDefinitions() {
        indexerDefinitions.put("newznab", "Newznab");
        indexerDefinitions.put("torznab", "Torznab");
        indexerDefinitions.put("torrentRss", "Torrent RSS");
        indexerDefinitions.put("cardigann", "Cardigann");
        indexerDefinitions.put("usenetIndexer", "Usenet Indexer");
        indexerDefinitions.put("torrentIndexer", "Torrent Indexer");
    }

    private void setupIndexerTableView() {
        indexerIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        indexerNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        indexerTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        indexerEnabledColumn.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        indexerPriorityColumn.setCellValueFactory(new PropertyValueFactory<>("priority"));
        indexerIdColumn.setCellFactory(column -> new TableCell<IndexerItem, Integer>() {
            @Override
            protected void updateItem(Integer id, boolean empty) {
                super.updateItem(id, empty);
                if (id == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(id.toString());
                    setAlignment(Pos.CENTER);
                }
            }
        });

        indexerNameColumn.setCellFactory(column -> new TableCell<IndexerItem, String>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (name == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        indexerTypeColumn.setCellFactory(column -> new TableCell<IndexerItem, String>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);

                if (type == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(type);
                    setAlignment(Pos.CENTER);

                    switch (type.toLowerCase()) {
                        case "usenetindexer":
                        case "newznab":
                            setStyle("-fx-text-fill: blue;");
                            break;
                        case "torrentindexer":
                        case "torznab":
                        case "torrentrss":
                            setStyle("-fx-text-fill: purple;");
                            break;
                        case "cardigann":
                            setStyle("-fx-text-fill: teal;");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });

        indexerEnabledColumn.setCellFactory(column -> new TableCell<IndexerItem, Boolean>() {
            @Override
            protected void updateItem(Boolean enabled, boolean empty) {
                super.updateItem(enabled, empty);

                if (empty || enabled == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(enabled.toString());
                    setAlignment(Pos.CENTER);
                    if (enabled) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });
        indexerPriorityColumn.setCellFactory(column -> new TableCell<IndexerItem, Integer>() {
            @Override
            protected void updateItem(Integer priority, boolean empty) {
                super.updateItem(priority, empty);

                if (priority == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setAlignment(Pos.CENTER);
                    setText(priority.toString());
                    if (priority <= 25) {
                        setStyle("-fx-text-fill: green;");
                    } else if (priority <= 75) {
                        setStyle("-fx-text-fill: orange;");
                    } else {
                        setStyle("-fx-text-fill: gray;");
                    }
                }
            }
        });
        setupIndexerActionColumn();

        indexersTableView.setItems(filteredIndexers);
    }

    private void setupTagsTableView() {
        tagIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        tagLabelColumn.setCellValueFactory(new PropertyValueFactory<>("label"));

        setupTagActionColumn();

        tagsTableView.setItems(filteredTags);
    }

    private void setupStatsTableView() {
        statsIndexerNameColumn.setCellValueFactory(new PropertyValueFactory<>("indexerName"));
        statsSuccessCountColumn.setCellValueFactory(new PropertyValueFactory<>("successCount"));
        statsFailureCountColumn.setCellValueFactory(new PropertyValueFactory<>("failureCount"));
        statsAvgResponseColumn.setCellValueFactory(new PropertyValueFactory<>("avgResponseTime"));

        statsSuccessCountColumn.setCellFactory(column -> new TableCell<IndexerStatsItem, Integer>() {
            @Override
            protected void updateItem(Integer count, boolean empty) {
                super.updateItem(count, empty);
                if (count == null || empty) {
                    setText(null);
                } else {
                    setText(count.toString());
                    setAlignment(Pos.CENTER);
                }
            }
        });

        statsFailureCountColumn.setCellFactory(column -> new TableCell<IndexerStatsItem, Integer>() {
            @Override
            protected void updateItem(Integer count, boolean empty) {
                super.updateItem(count, empty);
                if (count == null || empty) {
                    setText(null);
                } else {
                    setText(count.toString());
                    setAlignment(Pos.CENTER);
                    if (count > 0) {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        statsAvgResponseColumn.setCellFactory(column -> new TableCell<IndexerStatsItem, Double>() {
            @Override
            protected void updateItem(Double time, boolean empty) {
                super.updateItem(time, empty);
                if (time == null || empty) {
                    setText(null);
                } else {
                    setText(String.format("%.2f ms", time));
                    setAlignment(Pos.CENTER);
                    if (time > 1000) {
                        setStyle("-fx-text-fill: orange;");
                    } else if (time > 2000) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle("-fx-text-fill: green;");
                    }
                }
            }
        });
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

                editButton.getStyleClass().add("table-button");
                deleteButton.getStyleClass().add("table-button");
                testButton.getStyleClass().add("table-button");

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
                    box.setAlignment(Pos.CENTER);
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

                editButton.getStyleClass().add("table-button");
                deleteButton.getStyleClass().add("table-button");

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
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    @FXML
    private void connectToProwlarr() {
        if (apiUrlField.getText().isEmpty() || apiKeyField.getText().isEmpty()) {
            statusLabel.setText("Please provide both API URL and API Key");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        if (!apiUrlField.getText().startsWith("http://")) {
            apiUrlField.setText("http://" + apiUrlField.getText());
        }
        updateProperty();

        statusLabel.setText("Connecting...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                JSONObject statusResponse = makeApiGetRequest(STATUS_ENDPOINT);
                if (statusResponse != null) {
                    Platform.runLater(() -> {
                        connected.set(true);
                        versionLabel.setText(statusResponse.getString("version"));
                        statusLabel.setText("Connected to Prowlarr v" + statusResponse.getString("version"));
                        statusLabel.setStyle("-fx-text-fill: green;");

                        refreshIndexers();
                        refreshTags();
                        refreshStats();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connected.set(false);
                    statusLabel.setText("Failed to connect: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void refreshIndexers() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        statusLabel.setText("Loading indexers...");
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
                    statusLabel.setText("Indexers loaded successfully");
                    statusLabel.setStyle("-fx-text-fill: green;");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to load indexers: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void refreshTags() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        statusLabel.setText("Loading tags...");
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
                    statusLabel.setText("Tags loaded successfully");
                    statusLabel.setStyle("-fx-text-fill: green;");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to load tags: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void showAddIndexerDialog() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        statusLabel.setText("Loading indexer definitions...");

        CompletableFuture.runAsync(() -> {
            try {
                JSONArray definitionsArray = makeApiGetRequest("/api/v1/indexer/schema").getJSONArray("records");
                List<JSONObject> availableDefinitions = new ArrayList<>();

                for (int i = 0; i < definitionsArray.length(); i++) {
                    availableDefinitions.add(definitionsArray.getJSONObject(i));
                }

                Platform.runLater(() -> {
                    statusLabel.setText("Ready to add indexer");
                    statusLabel.setStyle("-fx-text-fill: green;");
                    createAddIndexerDialog(availableDefinitions);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to load indexer definitions: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    private void createAddIndexerDialog(List<JSONObject> availableDefinitions) {
        Dialog<IndexerItem> dialog = new Dialog<>();
        dialog.setTitle("Add Indexer");
        dialog.setHeaderText("Select an indexer to add");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<JSONObject> definitionComboBox = new ComboBox<>();
        definitionComboBox.getItems().addAll(availableDefinitions);
        definitionComboBox.setConverter(new javafx.util.StringConverter<JSONObject>() {
            @Override
            public String toString(JSONObject object) {
                if (object == null) return null;
                return object.optString("name", "Unknown");
            }

            @Override
            public JSONObject fromString(String string) {
                return null;
            }
        });
        definitionComboBox.setPromptText("Select Indexer Type");

        TextField nameField = new TextField();
        nameField.setPromptText("Custom Name (optional)");

        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);

        TextField priorityField = new TextField("25");
        priorityField.setPromptText("Priority (1-50)");

        grid.add(new Label("Indexer Type:"), 0, 0);
        grid.add(definitionComboBox, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(new Label("Priority:"), 0, 2);
        grid.add(priorityField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(definitionComboBox::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    JSONObject selectedDefinition = definitionComboBox.getValue();
                    if (selectedDefinition == null) {
                        statusLabel.setText("Please select an indexer type");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                    String name = nameField.getText().trim();
                    if (name.isEmpty()) {
                        name = selectedDefinition.getString("name");
                    }

                    int priority = Integer.parseInt(priorityField.getText().trim());
                    boolean enabled = enabledCheckBox.isSelected();

                    if (priority < 1 || priority > 50) {
                        statusLabel.setText("Priority must be between 1 and 50");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                    JSONObject indexerJson = new JSONObject(selectedDefinition.toString());
                    indexerJson.put("name", name);
                    indexerJson.put("enable", enabled);
                    indexerJson.put("priority", priority);
                    indexerJson.put("tags", new JSONArray());

                    JSONObject response = makeApiPostRequest(INDEXERS_ENDPOINT, indexerJson);

                    if (response != null && response.has("id")) {
                        int id = response.getInt("id");
                        statusLabel.setText("Indexer added successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");

                        return new IndexerItem(
                                id,
                                name,
                                getIndexerTypeDisplayName(selectedDefinition.getString("protocol")),
                                enabled,
                                priority,
                                new ArrayList<>(),
                                "{}"
                        );
                    } else {
                        statusLabel.setText("Failed to add indexer");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                } catch (NumberFormatException e) {
                    statusLabel.setText("Priority must be a number");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return null;
                } catch (Exception e) {
                    statusLabel.setText("Failed to add indexer: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
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
        });
    }

    private void showEditIndexerDialog(IndexerItem indexer) {
        Dialog<IndexerItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Indexer");
        dialog.setHeaderText("Edit indexer details for: " + indexer.getName());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(indexer.getName());
        Label typeLabel = new Label(indexer.getType());
        CheckBox enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(indexer.isEnabled());
        TextField priorityField = new TextField(String.valueOf(indexer.getPriority()));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeLabel, 1, 1);
        grid.add(new Label("Priority:"), 0, 2);
        grid.add(priorityField, 1, 2);
        grid.add(enabledCheckBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String name = nameField.getText().trim();
                    int priority = Integer.parseInt(priorityField.getText().trim());
                    boolean enabled = enabledCheckBox.isSelected();

                    if (name.isEmpty()) {
                        statusLabel.setText("Name cannot be empty");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                    if (priority < 1 || priority > 50) {
                        statusLabel.setText("Priority must be between 1 and 50");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                    JSONObject updateJson = new JSONObject();
                    updateJson.put("id", indexer.getId());
                    updateJson.put("name", name);
                    updateJson.put("enable", enabled);
                    updateJson.put("priority", priority);
                    updateJson.put("protocol", getProtocolFromDisplayName(indexer.getType()));
                    updateJson.put("tags", new JSONArray(indexer.getTagIds()));

                    JSONObject response = makeApiPutRequest(INDEXERS_ENDPOINT + "/" + indexer.getId(), updateJson);

                    if (response != null) {
                        statusLabel.setText("Indexer updated successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");

                        indexer.setName(name);
                        indexer.setEnabled(enabled);
                        indexer.setPriority(priority);
                        indexersTableView.refresh();

                        return indexer;
                    } else {
                        statusLabel.setText("Failed to update indexer");
                        statusLabel.setStyle("-fx-text-fill: red;");
                        return null;
                    }

                } catch (NumberFormatException e) {
                    statusLabel.setText("Priority must be a number");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return null;
                } catch (Exception e) {
                    statusLabel.setText("Failed to update indexer: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void testIndexer(int indexerId) {
        statusLabel.setText("Testing indexer...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                JSONObject testResponse = makeApiPostRequest("/api/v1/indexer/" + indexerId + "/test", new JSONObject());

                Platform.runLater(() -> {
                    if (testResponse != null && testResponse.optBoolean("isValid", false)) {
                        statusLabel.setText("Indexer test successful");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    } else {
                        String error = testResponse != null ? testResponse.optString("errors", "Unknown error") : "Test failed";
                        statusLabel.setText("Indexer test failed: " + error);
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Test failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void testAllIndexers() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        statusLabel.setText("Testing all indexers...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            int successCount = 0;
            int totalCount = indexersList.size();

            for (IndexerItem indexer : indexersList) {
                if (indexer.isEnabled()) {
                    try {
                        JSONObject testResponse = makeApiPostRequest("/api/v1/indexer/" + indexer.getId() + "/test", new JSONObject());
                        if (testResponse != null && testResponse.optBoolean("isValid", false)) {
                            successCount++;
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {

                    }
                }
            }

            final int finalSuccessCount = successCount;
            Platform.runLater(() -> {
                statusLabel.setText(String.format("Test completed: %d/%d indexers passed", finalSuccessCount, totalCount));
                statusLabel.setStyle(finalSuccessCount == totalCount ? "-fx-text-fill: green;" : "-fx-text-fill: orange;");
            });
        });
    }

    @FXML
    private void refreshStats() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        statusLabel.setText("Loading statistics...");
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject historyResponse = makeApiGetRequest("/api/v1/history?page=1&pageSize=1000");
                JSONArray historyArray = historyResponse.optJSONArray("records");

                JSONArray indexersArray = makeApiGetRequest(INDEXERS_ENDPOINT).getJSONArray("records");

                Map<Integer, IndexerStatsItem> statsMap = new HashMap<>();

                for (int i = 0; i < indexersArray.length(); i++) {
                    JSONObject indexer = indexersArray.getJSONObject(i);
                    int id = indexer.getInt("id");
                    String name = indexer.getString("name");
                    statsMap.put(id, new IndexerStatsItem(id, name, 0, 0, 0, 0.0));
                }

                if (historyArray != null) {
                    Map<Integer, List<Double>> responseTimes = new HashMap<>();

                    for (int i = 0; i < historyArray.length(); i++) {
                        JSONObject record = historyArray.getJSONObject(i);
                        int indexerId = record.optInt("indexerId", -1);

                        if (indexerId != -1 && statsMap.containsKey(indexerId)) {
                            IndexerStatsItem stats = statsMap.get(indexerId);
                            String eventType = record.optString("eventType", "");

                            if ("grabEnd".equals(eventType) || "releaseGrabbed".equals(eventType)) {
                                stats.setSuccessCount(stats.getSuccessCount() + 1);
                            } else if ("grabFailed".equals(eventType) || "indexerQuery".equals(eventType)) {
                                boolean successful = record.optBoolean("successful", true);
                                if (successful) {
                                    stats.setSuccessCount(stats.getSuccessCount() + 1);
                                } else {
                                    stats.setFailureCount(stats.getFailureCount() + 1);
                                }
                            }

                            double responseTime = record.optDouble("responseTime", -1);
                            if (responseTime > 0) {
                                responseTimes.computeIfAbsent(indexerId, k -> new ArrayList<>()).add(responseTime);
                            }
                        }
                    }

                    for (Map.Entry<Integer, List<Double>> entry : responseTimes.entrySet()) {
                        List<Double> times = entry.getValue();
                        double avgTime = times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        if (statsMap.containsKey(entry.getKey())) {
                            statsMap.get(entry.getKey()).setAvgResponseTime(avgTime);
                        }
                    }
                }

                Platform.runLater(() -> {
                    indexerStatsList.clear();
                    indexerStatsList.addAll(statsMap.values());
                    statusLabel.setText("Statistics loaded successfully");
                    statusLabel.setStyle("-fx-text-fill: green;");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to load statistics: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    private void showDeleteIndexerConfirmation(IndexerItem indexer) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Indexer");
        alert.setHeaderText("Delete indexer: " + indexer.getName());
        alert.setContentText("Are you sure you want to delete this indexer? This action cannot be undone.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteIndexer(indexer);
        }
    }

    private void deleteIndexer(IndexerItem indexer) {
        statusLabel.setText("Deleting indexer...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                boolean success = makeApiDeleteRequest(INDEXERS_ENDPOINT + "/" + indexer.getId());

                Platform.runLater(() -> {
                    if (success) {
                        indexersList.remove(indexer);
                        indexersCountLabel.setText(String.valueOf(indexersList.size()));
                        enabledIndexersLabel.setText(String.valueOf(
                                indexersList.stream().filter(IndexerItem::isEnabled).count()));
                        statusLabel.setText("Indexer deleted successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    } else {
                        statusLabel.setText("Failed to delete indexer");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to delete indexer: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void showAddTagDialog() {
        if (!connected.get()) {
            statusLabel.setText("Please connect to Prowlarr first");
            statusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Tag");
        dialog.setHeaderText("Add a new tag");
        dialog.setContentText("Tag label:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(label -> {
            if (!label.trim().isEmpty()) {
                addTag(label.trim());
            }
        });
    }

    private void addTag(String label) {
        statusLabel.setText("Adding tag...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                JSONObject tagJson = new JSONObject();
                tagJson.put("label", label);

                JSONObject response = makeApiPostRequest(TAG_ENDPOINT, tagJson);

                if (response != null && response.has("id")) {
                    int id = response.getInt("id");
                    TagItem newTag = new TagItem(id, label);

                    Platform.runLater(() -> {
                        tagsList.add(newTag);
                        statusLabel.setText("Tag added successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    });
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to add tag");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to add tag: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    private void showEditTagDialog(TagItem tag) {
        TextInputDialog dialog = new TextInputDialog(tag.getLabel());
        dialog.setTitle("Edit Tag");
        dialog.setHeaderText("Edit tag");
        dialog.setContentText("Tag label:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(label -> {
            if (!label.trim().isEmpty() && !label.equals(tag.getLabel())) {
                updateTag(tag, label.trim());
            }
        });
    }

    private void updateTag(TagItem tag, String newLabel) {
        statusLabel.setText("Updating tag...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                JSONObject tagJson = new JSONObject();
                tagJson.put("id", tag.getId());
                tagJson.put("label", newLabel);

                JSONObject response = makeApiPutRequest(TAG_ENDPOINT + "/" + tag.getId(), tagJson);

                if (response != null) {
                    Platform.runLater(() -> {
                        tag.setLabel(newLabel);
                        tagsTableView.refresh();
                        statusLabel.setText("Tag updated successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    });
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to update tag");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to update tag: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    private void showDeleteTagConfirmation(TagItem tag) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Tag");
        alert.setHeaderText("Delete tag: " + tag.getLabel());
        alert.setContentText("Are you sure you want to delete this tag?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteTag(tag);
        }
    }

    private void deleteTag(TagItem tag) {
        statusLabel.setText("Deleting tag...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        CompletableFuture.runAsync(() -> {
            try {
                boolean success = makeApiDeleteRequest(TAG_ENDPOINT + "/" + tag.getId());

                Platform.runLater(() -> {
                    if (success) {
                        tagsList.remove(tag);
                        statusLabel.setText("Tag deleted successfully");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    } else {
                        statusLabel.setText("Failed to delete tag");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to delete tag: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        });
    }

    @FXML
    private void saveSettings() {
        try {
            String timeout = timeoutField.getText().trim();
            String cacheDuration = cacheDurationField.getText().trim();

            if (!timeout.isEmpty()) {
                int timeoutValue = Integer.parseInt(timeout);
                if (timeoutValue < 5 || timeoutValue > 300) {
                    statusLabel.setText("Timeout must be between 5 and 300 seconds");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return;
                }
            }

            if (!cacheDuration.isEmpty()) {
                int cacheValue = Integer.parseInt(cacheDuration);
                if (cacheValue < 1 || cacheValue > 1440) {
                    statusLabel.setText("Cache duration must be between 1 and 1440 minutes");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    return;
                }
            }

            updateProperty();

            statusLabel.setText("Settings saved successfully");
            statusLabel.setStyle("-fx-text-fill: green;");

        } catch (NumberFormatException e) {
            statusLabel.setText("Please enter valid numbers for settings");
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private String getIndexerTypeDisplayName(String protocol) {
        return indexerDefinitions.getOrDefault(protocol.toLowerCase(), protocol);
    }

    private String getProtocolFromDisplayName(String displayName) {
        return indexerDefinitions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(displayName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(displayName.toLowerCase());
    }

    private List<Integer> extractTagIds(JSONArray tagsArray) {
        List<Integer> tagIds = new ArrayList<>();
        for (int i = 0; i < tagsArray.length(); i++) {
            tagIds.add(tagsArray.getInt(i));
        }
        return tagIds;
    }


    private JSONObject makeApiGetRequest(String endpoint) throws Exception {
        return makeApiRequest("GET", endpoint, null);
    }

    private JSONObject makeApiPostRequest(String endpoint, JSONObject data) throws Exception {
        return makeApiRequest("POST", endpoint, data);
    }

    private JSONObject makeApiPutRequest(String endpoint, JSONObject data) throws Exception {
        return makeApiRequest("PUT", endpoint, data);
    }

    private boolean makeApiDeleteRequest(String endpoint) throws Exception {
        JSONObject response = makeApiRequest("DELETE", endpoint, null);
        return response != null;
    }

    private JSONObject makeApiRequest(String method, String endpoint, JSONObject data) throws Exception {
        String urlString = apiUrl.get().replaceAll("/$", "") + endpoint;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Api-Key", apiKey.get());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(CONNECTION_TIMEOUT);

        if (data != null && ("POST".equals(method) || "PUT".equals(method))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = data.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();

        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                String responseStr = response.toString();
                if (responseStr.isEmpty()) {
                    return new JSONObject();
                }

                return responseStr.startsWith("[") ?
                        new JSONObject().put("records", new JSONArray(responseStr)) :
                        new JSONObject(responseStr);
            }
        } else {
            throw new Exception("HTTP " + responseCode + ": " + conn.getResponseMessage());
        }
    }

    private void loadProperties() {
        File propertiesFile = new File(PROPERTIES_FILE);
        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            appProperties.load(in);
            apiUrlField.setText(appProperties.getProperty("prowlarr-apiUrl", "http://localhost:9696"));
            apiKeyField.setText(appProperties.getProperty("prowlarr-apiKey", ""));
            timeoutField.setText(appProperties.getProperty("timeout", "30"));
            cacheDurationField.setText(appProperties.getProperty("cacheDuration", "10"));
        } catch (Exception e) {
            apiUrlField.setText("http://localhost:9696");
        }
    }

    private void updateProperty() {
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.setProperty("prowlarr-apiUrl", apiUrlField.getText());
            appProperties.setProperty("prowlarr-apiKey", apiKeyField.getText());
            appProperties.setProperty("timeout", timeoutField.getText());
            appProperties.setProperty("cacheDuration", cacheDurationField.getText());
            appProperties.store(out, "Dashboard Settings");
        } catch (Exception e) {
            statusLabel.setText("Failed to save settings: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }
}