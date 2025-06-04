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
import org.json.JSONException;
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

    private static final String PROPERTIES_FILE = "src/main/resources/com/tfg/dashboard_tfg/connection.properties";
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
        apiUrlField.setText(appProperties.getProperty("prowlarr-apiUrl"));
        apiKeyField.setText(appProperties.getProperty("prowlarr-apiKey"));
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
        if ((apiUrlField.getText() == null || apiUrlField.getText().isEmpty()) ||
                (apiKeyField.getText() == null || apiKeyField.getText().isEmpty())) {
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
        dialog.setHeaderText("Add a new indexer");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<JSONObject> indexerComboBox = new ComboBox<>();
        indexerComboBox.getItems().addAll(availableDefinitions);
        indexerComboBox.setConverter(new javafx.util.StringConverter<JSONObject>() {
            @Override
            public String toString(JSONObject object) {
                return object == null ? null : object.optString("name", "Unknown");
            }

            @Override
            public JSONObject fromString(String string) {
                return null;
            }
        });
        indexerComboBox.setPromptText("Select Indexer Type");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter indexer name (Optional)");

        CheckBox enabledCheckBox = new CheckBox("Enable this indexer");
        enabledCheckBox.setSelected(true);

        grid.add(new Label("Indexer:"), 0, 0);
        grid.add(indexerComboBox, 1, 0);
        grid.add(new Label("Name:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(enabledCheckBox, 1, 2);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(indexerComboBox::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    JSONObject selectedIndexer = indexerComboBox.getValue();
                    if (selectedIndexer == null) {
                        showError("Please select an indexer type");
                        return null;
                    }
                    String name = nameField.getText().trim();
                    if (name.isEmpty()) {
                        name = selectedIndexer.optString("name", "Unnamed Indexer");
                    }
                    boolean enabled = enabledCheckBox.isSelected();
                    JSONObject indexerConfig = new JSONObject(selectedIndexer.toString());

                    indexerConfig.put("name", name);
                    indexerConfig.put("enable", enabled);
                    indexerConfig.put("priority", 25);

                    if (!indexerConfig.has("implementation")) {
                        indexerConfig.put("implementation", selectedIndexer.optString("implementation", ""));
                    }
                    if (!indexerConfig.has("configContract")) {
                        indexerConfig.put("configContract", selectedIndexer.optString("configContract", ""));
                    }
                    if (!indexerConfig.has("fields")) {
                        JSONArray fields = selectedIndexer.optJSONArray("fields");
                        if (fields != null) {
                            JSONArray configFields = new JSONArray();
                            for (int i = 0; i < fields.length(); i++) {
                                JSONObject field = fields.getJSONObject(i);
                                JSONObject configField = new JSONObject();
                                configField.put("name", field.getString("name"));
                                configField.put("value", field.optString("value", ""));
                                configFields.put(configField);
                            }
                            indexerConfig.put("fields", configFields);
                        } else {
                            indexerConfig.put("fields", new JSONArray());
                        }
                    }
                    JSONArray profiles = makeApiGetRequest("/api/v1/appProfile").getJSONArray("records");
                    int defaultProfileId = profiles.length() > 0 ? profiles.getJSONObject(0).getInt("id") : 1;
                    indexerConfig.put("appProfileId", defaultProfileId);

                    indexerConfig.remove("id");

                    JSONObject response = makeApiPostRequest("/api/v1/indexer", indexerConfig);

                    if (response != null && response.has("id")) {
                        showSuccess("Indexer added successfully");

                        return new IndexerItem(
                                response.getInt("id"),
                                name,
                                selectedIndexer.optString("implementationName", ""),
                                enabled,
                                25,
                                new ArrayList<>(),
                                indexerConfig.toString()
                        );
                    } else {
                        String errorMsg = response != null && response.has("errors")
                                ? response.getJSONArray("errors").join(", ")
                                : "Failed to add indexer";
                        showError(errorMsg);
                        return null;
                    }

                } catch (Exception e) {
                    showError("Failed to add indexer: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<IndexerItem> result = dialog.showAndWait();
        result.ifPresent(indexersList::add);
    }

    private void showError(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void showSuccess(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: green;");
        }
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
                        showError("Name cannot be empty");
                        return null;
                    }

                    if (priority < 1 || priority > 50) {
                        showError("Priority must be between 1 and 50");
                        return null;
                    }

                    JSONObject currentConfig = makeApiGetRequest(INDEXERS_ENDPOINT + "/" + indexer.getId());
                    if (currentConfig == null) {
                        showError("Failed to get current indexer configuration");
                        return null;
                    }

                    JSONObject updateJson = new JSONObject(currentConfig.toString());

                    updateJson.put("name", name);
                    updateJson.put("enable", enabled);
                    updateJson.put("priority", priority);
                    updateJson.put("id", indexer.getId());

                    if (!updateJson.has("implementation")) {
                        updateJson.put("implementation", indexer.getType());
                    }
                    if (!updateJson.has("configContract")) {
                        updateJson.put("configContract", indexer.getType() + "Settings");
                    }
                    if (!updateJson.has("implementationName")) {
                        updateJson.put("implementationName", indexer.getType());
                    }

                    if (!updateJson.has("appProfileId")) {
                        JSONArray profiles = makeApiGetRequest("/api/v1/appProfile").getJSONArray("records");
                        int defaultProfileId = profiles.length() > 0 ? profiles.getJSONObject(0).getInt("id") : 1;
                        updateJson.put("appProfileId", defaultProfileId);
                    }

                    if (!updateJson.has("protocol")) {
                        updateJson.put("protocol", getProtocolFromDisplayName(indexer.getType()));
                    }

                    if (!updateJson.has("fields")) {
                        updateJson.put("fields", new JSONArray());
                    }

                    System.out.println("Sending update request for indexer " + indexer.getId());
                    System.out.println("Request body: " + updateJson.toString(2));

                    JSONObject response = makeApiPutRequest(INDEXERS_ENDPOINT + "/" + indexer.getId(), updateJson);

                    if (response != null && !response.has("errors")) {
                        showSuccess("Indexer updated successfully");

                        indexer.setName(name);
                        indexer.setEnabled(enabled);
                        indexer.setPriority(priority);

                        indexersTableView.refresh();
                        return indexer;
                    } else {
                        String errorMsg = response != null && response.has("errors")
                                ? response.getJSONArray("errors").join(", ")
                                : "Failed to update indexer";
                        showError(errorMsg);
                        return null;
                    }

                } catch (NumberFormatException e) {
                    showError("Priority must be a number");
                    return null;
                } catch (Exception e) {
                    showError("Failed to update indexer: " + e.getMessage());
                    e.printStackTrace();
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
                JSONObject indexerConfig = makeApiGetRequest("/api/v1/indexer/" + indexerId);

                JSONObject testResponse = makeApiPostRequest("/api/v1/indexer/test", indexerConfig);
                Platform.runLater(() -> {
                    if (testResponse != null) {
                        if (testResponse.has("success") && testResponse.getBoolean("success")) {
                            statusLabel.setText("Indexer test successful");
                            statusLabel.setStyle("-fx-text-fill: green;");
                        } else {
                            String error = testResponse.optString("errors", "Unknown error");
                            statusLabel.setText("Indexer test failed: " + error);
                            statusLabel.setStyle("-fx-text-fill: red;");
                        }
                    } else {
                        statusLabel.setText("Test failed: No response received");
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
            List<String> failures = new ArrayList<>();

            for (IndexerItem indexer : indexersList) {
                if (indexer.isEnabled()) {
                    try {
                        JSONObject indexerConfig = makeApiGetRequest("/api/v1/indexer/" + indexer.getId());
                        JSONObject testResponse = makeApiPostRequest("/api/v1/indexer/test", indexerConfig);

                        if (testResponse != null) {
                            if (testResponse.has("success") && testResponse.getBoolean("success")) {
                                successCount++;
                            } else {
                                String error = testResponse.optString("errors", "Unknown error");
                                failures.add(indexer.getName() + ": " + error);
                            }
                        } else {
                            failures.add(indexer.getName() + ": No response received");
                        }

                        // Add a small delay between tests
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        failures.add(indexer.getName() + ": " + e.getMessage());
                    }
                }
            }

            final int finalSuccessCount = successCount;
            final List<String> finalFailures = failures;

            Platform.runLater(() -> {
                StringBuilder statusMessage = new StringBuilder();
                statusMessage.append(String.format("Test completed: %d/%d indexers passed", finalSuccessCount, totalCount));

                if (!finalFailures.isEmpty()) {
                    statusMessage.append("\nFailures:");
                    for (String failure : finalFailures) {
                        statusMessage.append("\n- ").append(failure);
                    }
                }

                statusLabel.setText(statusMessage.toString());
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

        try {
            conn.setRequestMethod(method);
            conn.setRequestProperty("X-Api-Key", apiKey.get());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
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

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 200 && responseCode < 300
                                    ? conn.getInputStream()
                                    : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {

                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                String responseStr = response.toString();

//                System.out.println("Request URL: " + urlString);
//                System.out.println("Request Method: " + method);
//                System.out.println("Request Data: " + (data != null ? data.toString() : "null"));
//                System.out.println("Response Code: " + responseCode);
//                System.out.println("Response Body: " + responseStr);

                if (responseCode >= 200 && responseCode < 300) {
                    if (responseStr.isEmpty()) {
                        return new JSONObject().put("success", true);
                    }

                    try {
                        if (responseStr.startsWith("[")) {
                            return new JSONObject().put("records", new JSONArray(responseStr));
                        } else {
                            return new JSONObject(responseStr);
                        }
                    } catch (JSONException e) {
                        return new JSONObject()
                                .put("rawResponse", responseStr)
                                .put("success", true);
                    }
                } else {
                    throw new Exception(String.format("HTTP %d: %s\nResponse: %s",
                            responseCode,
                            conn.getResponseMessage(),
                            responseStr));
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private void loadProperties() {
        File propertiesFile = new File(PROPERTIES_FILE);
        try (FileInputStream in = new FileInputStream(propertiesFile)) {
            appProperties.load(in);
        } catch (Exception e) {
            System.out.println("Error loading property: " + e.getMessage());
        }
    }

    private void updateProperty() {
        loadProperties();
        try (FileOutputStream out = new FileOutputStream(PROPERTIES_FILE)) {
            appProperties.setProperty("prowlarr-apiUrl", apiUrlField.getText());
            appProperties.setProperty("prowlarr-apiKey", apiKeyField.getText());
            appProperties.setProperty("timeout", timeoutField.getText());
            appProperties.setProperty("cacheDuration", cacheDurationField.getText());
            appProperties.store(out, "Updated by user");
        } catch (Exception e) {
            statusLabel.setText("Failed to save settings: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }
}