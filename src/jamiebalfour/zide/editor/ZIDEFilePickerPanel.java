package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfComboBox;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
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
  private final ListView<File> entries = new ListView<>();
  private final TextField location = new TextField();
  private final TextField fileName = new TextField();
  private final BalfComboBox<FileChooser.ExtensionFilter> fileType = new BalfComboBox<>();
  private final List<FileChooser.ExtensionFilter> filters;
  private final CheckBox replaceExisting = new CheckBox("Replace existing file");
  private final Label validation = new Label();
  private File currentDirectory;

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

    Button up = new Button("Up");
    up.setOnAction(event -> {
      File parent = currentDirectory.getParentFile();
      if (parent != null) navigateTo(parent);
    });
    Button go = new Button("Go");
    go.setOnAction(event -> navigateFromLocation());
    location.setOnAction(event -> navigateFromLocation());
    HBox locationRow = new HBox(8, new Label("Location"), location, up, go);
    locationRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(location, Priority.ALWAYS);
    locationRow.getStyleClass().add("file-picker-location");

    entries.setItems(FXCollections.observableArrayList());
    entries.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);
        setText(empty || file == null ? null : file.getName() + (file.isDirectory() ? "/" : ""));
      }
    });
    entries.setOnMouseClicked(event -> {
      if (event.getClickCount() != 2) return;
      File selected = entries.getSelectionModel().getSelectedItem();
      if (selected == null) return;
      if (selected.isDirectory()) navigateTo(selected);
      else if (!foldersOnly) fileName.setText(selected.getName());
    });
    entries.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
      if (newFile != null && newFile.isFile() && !foldersOnly) fileName.setText(newFile.getName());
      clearValidation();
    });
    entries.setPrefHeight(330);
    entries.setMinHeight(240);
    entries.getStyleClass().add("file-picker-list");
    VBox.setVgrow(entries, Priority.ALWAYS);

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
      getChildren().addAll(locationRow, entries);
      if (this.filters.size() > 1) getChildren().add(typeRow);
      getChildren().addAll(new Label("File name"), fileName, replaceExisting);
    } else {
      getChildren().addAll(locationRow, entries);
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
    File target = new File(entered);
    if (!target.isAbsolute()) target = new File(currentDirectory, entered);
    if (target.isDirectory()) navigateTo(target);
    else showValidation("That folder could not be opened.");
  }

  private void navigateTo(File directory) {
    if (directory == null || !directory.isDirectory()) return;
    currentDirectory = directory.toPath().toAbsolutePath().normalize().toFile();
    location.setText(currentDirectory.getAbsolutePath());
    entries.getSelectionModel().clearSelection();
    refreshEntries();
  }

  private void refreshEntries() {
    if (currentDirectory == null) return;
    File[] files = currentDirectory.listFiles();
    List<File> visible = new ArrayList<>();
    if (files != null) {
      for (File file : files) {
        if (foldersOnly ? file.isDirectory() : file.isDirectory() || matchesSelectedFilter(file)) visible.add(file);
      }
    }
    visible.sort(Comparator.comparing(File::isFile).thenComparing(file -> file.getName().toLowerCase(java.util.Locale.ROOT)));
    entries.getItems().setAll(visible);
    refreshOverwriteState();
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
}
