package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfComboBoxFX;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** In-window folder and output-file picker used by ZIDE's modal flows. */
final class ZIDEFilePickerPanel extends VBox {
  private final boolean foldersOnly;
  private final TableView<File> entries = new TableView<>();
  private final TextField location = new TextField();
  private final TextField fileName = new TextField();
  private final BalfComboBoxFX<FileChooser.ExtensionFilter> fileType = new BalfComboBoxFX<>();
  private final List<FileChooser.ExtensionFilter> filters;
  private final CheckBox replaceExisting = new CheckBox("Replace existing file");
  private final Label validation = new Label();
  private final FlowPane iconEntries = new FlowPane(14, 14);
  private final ScrollPane iconScroll = new ScrollPane(iconEntries);
  private boolean iconView;
  private File currentDirectory;
  private final List<File> history = new ArrayList<>();
  private int historyIndex = -1;
  private boolean showHidden;

  ZIDEFilePickerPanel(File startDirectory, String initialFileName,
                      List<FileChooser.ExtensionFilter> filters, boolean foldersOnly) {
    super(10);
    this.foldersOnly = foldersOnly;
    this.filters = filters == null ? List.of() : List.copyOf(filters);
    getStyleClass().add("zide-file-picker");
    setPadding(new Insets(2, 0, 0, 0));

    File starting = startDirectory;
    if (starting == null || !starting.isDirectory()) starting = new File(System.getProperty("user.home"));
    navigateTo(starting);

    Button back = new Button("‹");
    Button forward = new Button("›");
    Button up = new Button("↑");
    Button refresh = new Button("↻");
    Button newFolder = new Button("New Folder");
    ToggleButton view = new ToggleButton("Icon View");
    ToggleButton hidden = new ToggleButton("Show Hidden");
    back.setTooltip(new javafx.scene.control.Tooltip("Back"));
    forward.setTooltip(new javafx.scene.control.Tooltip("Forward"));
    up.setTooltip(new javafx.scene.control.Tooltip("Up one folder"));
    refresh.setTooltip(new javafx.scene.control.Tooltip("Refresh"));
    newFolder.setTooltip(new javafx.scene.control.Tooltip("Create a new folder"));
    view.setTooltip(new javafx.scene.control.Tooltip("Switch between details and icon view"));
    back.setOnAction(event -> navigateHistory(-1));
    forward.setOnAction(event -> navigateHistory(1));
    up.setOnAction(event -> {
      File parent = currentDirectory.getParentFile();
      if (parent != null) navigateTo(parent);
    });
    refresh.setOnAction(event -> refreshEntries());
    newFolder.setOnAction(event -> createNewFolder());
    view.selectedProperty().addListener((obs, oldValue, selected) -> {
      iconView = selected;
      view.setText(selected ? "Details View" : "Icon View");
      entries.setVisible(!selected);
      entries.setManaged(!selected);
      iconScroll.setVisible(selected);
      iconScroll.setManaged(selected);
      if (selected) refreshIconEntries();
    });
    hidden.selectedProperty().addListener((obs, oldValue, selected) -> {
      showHidden = selected;
      refreshEntries();
    });
    Button go = new Button("Go");
    go.setOnAction(event -> navigateFromLocation());
    location.setOnAction(event -> navigateFromLocation());
    HBox locationRow = new HBox(6, back, forward, up, refresh, newFolder, hidden, view,
        new Separator(javafx.geometry.Orientation.VERTICAL), new Label("Location"), location, go);
    locationRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(location, Priority.ALWAYS);
    locationRow.getStyleClass().add("file-picker-location");

    entries.setItems(FXCollections.observableArrayList());
    TableColumn<File, String> nameColumn = new TableColumn<>("Name");
    nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper((cell.getValue().isDirectory() ? "📁  " : "📄  ") + cell.getValue().getName()));
    TableColumn<File, String> kindColumn = new TableColumn<>("Kind");
    kindColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().isDirectory() ? "Folder" : "File"));
    TableColumn<File, String> modifiedColumn = new TableColumn<>("Modified");
    modifiedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
        new java.text.SimpleDateFormat("dd MMM yyyy HH:mm").format(new java.util.Date(cell.getValue().lastModified()))));
    TableColumn<File, String> sizeColumn = new TableColumn<>("Size");
    sizeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
        cell.getValue().isDirectory() ? "Folder" : formatSize(cell.getValue().length())));
    nameColumn.setPrefWidth(300);
    kindColumn.setPrefWidth(80);
    modifiedColumn.setPrefWidth(150);
    sizeColumn.setPrefWidth(90);
    entries.getColumns().setAll(nameColumn, kindColumn, modifiedColumn, sizeColumn);
    entries.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    entries.setPlaceholder(new Label("This folder is empty"));
    entries.setOnMouseClicked(event -> {
      File selected = entries.getSelectionModel().getSelectedItem();
      if (selected == null) return;
      if (selected.isDirectory()) {
        // Folder browsing is intentionally single-click, matching the fast
        // BalfLaf chooser. The current directory remains the folder that can
        // be approved with "Open folder".
        if (event.getClickCount() == 1) navigateTo(selected);
      } else if (!foldersOnly) {
        fileName.setText(selected.getName());
      }
    });
    entries.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
      if (newFile != null && newFile.isFile() && !foldersOnly) fileName.setText(newFile.getName());
      clearValidation();
    });
    entries.setPrefHeight(330);
    entries.setMinHeight(240);
    entries.getStyleClass().add("file-picker-list");
    VBox.setVgrow(entries, Priority.ALWAYS);
    ListView<File> shortcuts = new ListView<>();
    shortcuts.getStyleClass().add("file-picker-shortcuts");
    shortcuts.setPrefWidth(150);
    List<File> shortcutFiles = new ArrayList<>();
    addShortcut(shortcutFiles, "Home", new File(System.getProperty("user.home")));
    addShortcut(shortcutFiles, "Desktop", new File(System.getProperty("user.home"), "Desktop"));
    addShortcut(shortcutFiles, "Documents", new File(System.getProperty("user.home"), "Documents"));
    addShortcut(shortcutFiles, "Downloads", new File(System.getProperty("user.home"), "Downloads"));
    addShortcut(shortcutFiles, "Macintosh HD", new File("/"));
    addShortcut(shortcutFiles, "ZPE", new File(System.getProperty("user.home"), "Documents/ZPE Projects"));
    shortcuts.setItems(FXCollections.observableArrayList(shortcutFiles));
    shortcuts.setCellFactory(list -> new ListCell<>() {
      @Override protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);
        setText(empty || file == null ? null : shortcutName(file));
      }
    });
    shortcuts.setOnMouseClicked(event -> {
      File selected = shortcuts.getSelectionModel().getSelectedItem();
      if (selected != null) navigateTo(selected);
    });
    iconEntries.getStyleClass().add("file-picker-icon-view");
    iconScroll.getStyleClass().add("file-picker-icon-scroll");
    iconScroll.setFitToWidth(true);
    iconScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    iconScroll.setVisible(false);
    iconScroll.setManaged(false);
    VBox fileArea = new VBox(0, entries, iconScroll);
    fileArea.getStyleClass().add("file-picker-file-area");
    VBox.setVgrow(fileArea, Priority.ALWAYS);
    HBox browserBody = new HBox(10, shortcuts, fileArea);
    HBox.setHgrow(entries, Priority.ALWAYS);
    HBox.setHgrow(fileArea, Priority.ALWAYS);
    VBox.setVgrow(browserBody, Priority.ALWAYS);

    if (!foldersOnly) {
      fileName.setText(initialFileName == null ? "" : initialFileName);
      fileName.setPromptText("File name");
      fileName.textProperty().addListener((observable, oldValue, newValue) -> refreshOverwriteState());
      replaceExisting.setVisible(false);
      replaceExisting.setManaged(false);
      replaceExisting.selectedProperty().addListener((observable, oldValue, selected) -> {
        if (selected) validation.setText("");
      });
      fileType.setItems(FXCollections.observableArrayList(this.filters));
      if (!fileType.getItems().isEmpty()) fileType.getSelectionModel().selectFirst();
      fileType.setConverter(new StringConverter<>() {
        @Override
        public String toString(FileChooser.ExtensionFilter filter) {
          return filter == null ? "" : filter.getDescription();
        }

        @Override
        public FileChooser.ExtensionFilter fromString(String value) {
          return fileType.getItems().stream().filter(filter -> filter.getDescription().equals(value)).findFirst().orElse(null);
        }
      });
      fileType.valueProperty().addListener((observable, oldFilter, newFilter) -> refreshEntries());
      fileType.setMaxWidth(Double.MAX_VALUE);
      HBox typeRow = new HBox(8, new Label("File type"), fileType);
      typeRow.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(fileType, Priority.ALWAYS);
      getChildren().addAll(locationRow, browserBody);
      if (this.filters.size() > 1) getChildren().add(typeRow);
      getChildren().addAll(new Label("File name"), fileName, replaceExisting);
    } else {
      getChildren().addAll(locationRow, browserBody);
    }

    validation.getStyleClass().add("file-picker-validation");
    validation.setWrapText(true);
    getChildren().add(validation);
    refreshEntries();
  }

  File getSelection() {
    if (foldersOnly) {
      File selected = entries.getSelectionModel().getSelectedItem();
      return selected != null && selected.isDirectory() ? selected : currentDirectory;
    }
    String name = fileName.getText() == null ? "" : fileName.getText().trim();
    if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
      return null;
    }
    return new File(currentDirectory, name);
  }

  boolean isOverwriteConfirmed() {
    File selection = getSelection();
    return selection == null || !selection.exists() || replaceExisting.isSelected();
  }

  void showValidation(String message) {
    validation.setText(message == null ? "" : message);
  }

  void setDarkMode(boolean enabled) {
    fileType.setDarkMode(enabled);
  }

  private void navigateFromLocation() {
    String entered = location.getText().trim();
    if (entered.isEmpty()) return;
    if (entered.equals("~")) entered = System.getProperty("user.home");
    else if (entered.startsWith("~/")) entered = System.getProperty("user.home") + entered.substring(1);
    File target = new File(entered);
    if (!target.isAbsolute()) target = new File(currentDirectory, entered);
    if (target.isDirectory()) {
      navigateTo(target.getAbsoluteFile().toPath().normalize().toFile());
      location.setText(currentDirectory.getAbsolutePath());
      clearValidation();
    } else {
      showValidation("That folder could not be opened.");
      location.requestFocus();
      location.selectAll();
    }
  }

  private void navigateTo(File directory) {
    navigateTo(directory, true);
  }

  private void navigateTo(File directory, boolean recordHistory) {
    if (directory == null || !directory.isDirectory()) return;
    currentDirectory = directory.toPath().toAbsolutePath().normalize().toFile();
    if (recordHistory) {
      while (history.size() > historyIndex + 1) history.removeLast();
      if (history.isEmpty() || !history.getLast().equals(currentDirectory)) history.add(currentDirectory);
      historyIndex = history.size() - 1;
    }
    location.setText(currentDirectory.getAbsolutePath());
    entries.getSelectionModel().clearSelection();
    refreshEntries();
  }

  private void navigateHistory(int direction) {
    int target = historyIndex + direction;
    if (target < 0 || target >= history.size()) return;
    historyIndex = target;
    navigateTo(history.get(historyIndex), false);
  }

  private void refreshEntries() {
    if (currentDirectory == null) return;
    File[] files = currentDirectory.listFiles();
    List<File> visible = new ArrayList<>();
    if (files != null) {
      for (File file : files) {
        if (!showHidden && file.isHidden()) continue;
        if (foldersOnly ? file.isDirectory() : file.isDirectory() || matchesSelectedFilter(file)) visible.add(file);
      }
    }
    visible.sort(Comparator.comparing(File::isFile).thenComparing(file -> file.getName().toLowerCase(java.util.Locale.ROOT)));
    entries.getItems().setAll(visible);
    if (iconView) refreshIconEntries();
    refreshOverwriteState();
  }

  private void refreshIconEntries() {
    iconEntries.getChildren().clear();
    for (File file : entries.getItems()) {
      Label icon = new Label(file.isDirectory() ? "📁" : "📄");
      icon.getStyleClass().add("file-picker-icon");
      Label name = new Label(file.getName());
      name.getStyleClass().add("file-picker-icon-name");
      name.setWrapText(true);
      name.setMaxWidth(112);
      name.setAlignment(Pos.CENTER);
      VBox tile = new VBox(5, icon, name);
      tile.setAlignment(Pos.TOP_CENTER);
      tile.setPrefSize(124, 82);
      tile.getStyleClass().add("file-picker-icon-tile");
      tile.setOnMouseClicked(event -> {
        if (file.isDirectory()) navigateTo(file);
        else if (!foldersOnly) fileName.setText(file.getName());
      });
      iconEntries.getChildren().add(tile);
    }
  }

  private boolean matchesSelectedFilter(File file) {
    FileChooser.ExtensionFilter filter = fileType.getValue();
    if (filter == null && !filters.isEmpty()) filter = filters.get(0);
    if (filter == null || filter.getExtensions().isEmpty()) {
      return true;
    }
    String name = file.getName().toLowerCase(java.util.Locale.ROOT);
    for (String pattern : filter.getExtensions()) {
      String value = pattern.toLowerCase(java.util.Locale.ROOT);
      if ("*".equals(value) || "*.*".equals(value)) {
        return true;
      }
      if (value.startsWith("*.") && name.endsWith(value.substring(1))) {
        return true;
      }
    }
    return false;
  }

  private void refreshOverwriteState() {
    if (foldersOnly || fileName.getText() == null) return;
    File target = getSelection();
    boolean exists = target != null && target.isFile();
    replaceExisting.setVisible(exists);
    replaceExisting.setManaged(exists);
    if (!exists) replaceExisting.setSelected(false);
    if (exists && !replaceExisting.isSelected()) {
      validation.setText("This file already exists. Confirm replacement to continue.");
    } else if (validation.getText().startsWith("This file already exists.")) {
      validation.setText("");
    }
  }

  private void clearValidation() {
    if (!validation.getText().startsWith("This file already exists.")) validation.setText("");
  }

  private void createNewFolder() {
    TextInputDialog dialog = new TextInputDialog("New Folder");
    dialog.setTitle("New Folder");
    dialog.setHeaderText("Create a folder in " + currentDirectory.getName());
    dialog.setContentText("Folder name:");
    dialog.showAndWait().ifPresent(name -> {
      String trimmed = name.trim();
      if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")) {
        showValidation("Enter a valid folder name.");
        return;
      }
      File folder = new File(currentDirectory, trimmed);
      if (!folder.mkdir()) {
        showValidation("That folder could not be created.");
        return;
      }
      refreshEntries();
      entries.getSelectionModel().select(folder);
    });
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
    return (bytes / (1024 * 1024 * 1024)) + " GB";
  }

  private static void addShortcut(List<File> shortcuts, String name, File directory) {
    if (directory.isDirectory()) {
      directory = new File(directory, "");
      directory.setReadable(true);
      shortcuts.add(new ShortcutFile(directory, name));
    }
  }

  private static String shortcutName(File file) {
    return file instanceof ShortcutFile shortcut ? shortcut.label : file.getName();
  }

  private static final class ShortcutFile extends File {
    private final String label;
    private ShortcutFile(File path, String label) {
      super(path.getAbsolutePath());
      this.label = label;
    }
  }
}
