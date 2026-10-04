package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfComboBoxFX;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Builds the preferences view independently from the main editor window. */
final class ZIDESettingsPanel extends VBox {
  private final BalfComboBoxFX<String> theme;
  private final BalfComboBoxFX<String> lightEditorTheme;
  private final BalfComboBoxFX<String> darkEditorTheme;
  private final BalfComboBoxFX<String> fontFamily;
  private final Spinner<Integer> fontSize;
  private final Spinner<Integer> indentationSpaces;
  private final TextField projectsPath;
  private final CheckBox wordWrap;
  private final CheckBox codeMap;
  private final CheckBox useZpex;
  private final CheckBox showInputPrompt;
  private final TextField maximumMemory;
  private final CheckBox groupProjectTabs;
  private final CheckBox blockClosures;
  private final CheckBox autoOpenCsvSpreadsheet;
  private final TextField url;
  private final PasswordField key;
  private final BalfComboBoxFX<String> model;
  private final TextField collaborationServer;
  private final TextField collaborationPort;
  private final TextField collaborationName;
  private final PasswordField collaborationPassword;
  private final TextField collaborationAvatar;
  private final Map<String, TextField> runtimeFields = new LinkedHashMap<>();
  private final Runnable resetLayoutAction;

  ZIDESettingsPanel(boolean darkMode, String themeName, String lightTheme, String darkTheme,
                    String fontName, int fontSizeValue, int indentationSpacesValue, boolean wrapLines, boolean codeMapValue, boolean preferZpex, boolean showInputPromptValue, String maximumMemoryValue, boolean groupProjectTabsValue, boolean blockClosuresValue, boolean autoOpenCsvSpreadsheetValue,
                    String projectsPathValue, Function<String, String> chooseProjectsPath,
                    String chatGPTUrl, String chatGPTKey, String chatGPTModel,
                    String collaborationServerName, String collaborationPortNumber, String collaborationDisplayName,
                    String collaborationPasswordValue, String collaborationAvatarValue,
                    Map<String, String> runtimePaths, Function<String, String> runtimeRedetector,
                    Runnable resetLayoutAction) {
    super(18);
    this.resetLayoutAction = resetLayoutAction;

    FlowPane sections = new FlowPane(Orientation.VERTICAL);
    sections.getStyleClass().add("settings-section-list");
    sections.setVgap(4);
    sections.setHgap(4);
    sections.setPrefWidth(190);
    sections.setMinWidth(190);
    sections.setMaxWidth(190);
    ToggleGroup sectionGroup = new ToggleGroup();
    for (String sectionName : List.of("GUI", "Editor", "Execution", "Runtimes & Compilers", "ChatGPT", "Collaboration", "Experimental")) {
      ToggleButton sectionButton = new ToggleButton(sectionName);
      sectionButton.setUserData(sectionName);
      sectionButton.setToggleGroup(sectionGroup);
      sectionButton.setMaxWidth(Double.MAX_VALUE);
      sectionButton.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
      sectionButton.getStyleClass().add("settings-section-tab");
      sections.getChildren().add(sectionButton);
    }
    sectionGroup.selectToggle(sections.getChildren().getFirst() instanceof ToggleButton first ? first : null);

    theme = combo(List.of("Light", "Dark"), darkMode);
    theme.setValue(themeName);
    GridPane guiFields = fields();
    guiFields.addRow(0, new Label("Theme"), theme);
    groupProjectTabs = new CheckBox("Group tabs by project");
    groupProjectTabs.setSelected(groupProjectTabsValue);
    guiFields.add(groupProjectTabs, 1, 1);
    Button resetLayout = new Button("Reset ZIDE layout");
    resetLayout.setOnAction(event -> {
      if (this.resetLayoutAction != null) this.resetLayoutAction.run();
    });
    resetLayout.setTooltip(new javafx.scene.control.Tooltip("Restore the default panel, window, and workspace layout."));
    guiFields.add(resetLayout, 1, 2);
    GridPane.setHgrow(theme, Priority.ALWAYS);
    VBox gui = section("GUI", guiFields);
    gui.setMinWidth(0);

    List<String> editorThemes = List.of("ZIDE", "Solarized", "GitHub", "Dracula", "Monokai", "Nord", "Purples and Greens");
    lightEditorTheme = combo(editorThemes, darkMode);
    lightEditorTheme.setValue(lightTheme);
    darkEditorTheme = combo(editorThemes, darkMode);
    darkEditorTheme.setValue(darkTheme);
    fontFamily = combo(List.of("Menlo", "JetBrains Mono", "Cascadia Mono", "Consolas", "Monospace"), darkMode);
    fontFamily.setEditable(true);
    fontFamily.setValue(fontName);
    fontSize = new Spinner<>(8, 32, fontSizeValue);
    fontSize.setEditable(true);
    indentationSpaces = new Spinner<>(1, 8, indentationSpacesValue);
    indentationSpaces.setEditable(true);
    wordWrap = new CheckBox("Wrap long lines");
    wordWrap.setSelected(wrapLines);
    codeMap = new CheckBox("Show code map");
    codeMap.setSelected(codeMapValue);
    autoOpenCsvSpreadsheet = new CheckBox("Open CSV files as spreadsheets");
    autoOpenCsvSpreadsheet.setSelected(autoOpenCsvSpreadsheetValue);
    GridPane editorFields = fields();
    editorFields.addRow(0, new Label("Light theme"), lightEditorTheme);
    editorFields.addRow(1, new Label("Dark theme"), darkEditorTheme);
    editorFields.addRow(2, new Label("Font"), fontFamily);
    editorFields.addRow(3, new Label("Font size"), fontSize);
    editorFields.addRow(4, new Label("Indentation spaces"), indentationSpaces);
    projectsPath = new TextField(projectsPathValue == null ? "" : projectsPathValue);
    projectsPath.getStyleClass().add("modal-rounded-text-field");
    projectsPath.setMaxWidth(Double.MAX_VALUE);
    Button setProjectsPath = new Button("Set");
    setProjectsPath.setOnAction(event -> {
      if (chooseProjectsPath == null) return;
      String selected = chooseProjectsPath.apply(projectsPath.getText().trim());
      if (selected != null && !selected.isBlank()) projectsPath.setText(selected);
    });
    HBox projectsPathRow = new HBox(8, projectsPath, setProjectsPath);
    projectsPathRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    HBox.setHgrow(projectsPath, Priority.ALWAYS);
    editorFields.addRow(5, new Label("Projects path"), projectsPathRow);
    editorFields.add(wordWrap, 1, 6);
    editorFields.add(codeMap, 1, 7);
    editorFields.add(autoOpenCsvSpreadsheet, 1, 8);
    for (Node control : List.of(lightEditorTheme, darkEditorTheme, fontFamily, fontSize, indentationSpaces)) {
      GridPane.setHgrow(control, Priority.ALWAYS);
    }
    VBox editor = section("Editor", editorFields);

    useZpex = new CheckBox("Use ZPEX when available");
    useZpex.setSelected(preferZpex);
    showInputPrompt = new CheckBox("Show a language prompt when input is required");
    showInputPrompt.setSelected(showInputPromptValue);
    maximumMemory = new TextField(maximumMemoryValue);
    maximumMemory.setPromptText("Optional, in MB, e.g. 4096");
    maximumMemory.setMaxWidth(Double.MAX_VALUE);
    GridPane executionFields = fields();
    executionFields.addRow(0, new Label("ZPE maximum memory"), maximumMemory);
    GridPane.setHgrow(maximumMemory, Priority.ALWAYS);
    Label yassTitle = new Label("YASS");
    yassTitle.getStyleClass().add("settings-group-title");
    VBox yassGroup = new VBox(10, yassTitle, useZpex, showInputPrompt, executionFields);
    yassGroup.getStyleClass().add("settings-option-group");
    Label executionTitle = new Label("Execution");
    executionTitle.getStyleClass().add("settings-section-title");
    VBox execution = new VBox(16, executionTitle, yassGroup);
    execution.getStyleClass().add("settings-section-content");

    blockClosures = new CheckBox("Block closures");
    blockClosures.setSelected(blockClosuresValue);
    VBox experimental = section("Experimental", new VBox(10, blockClosures));

    VBox runtimeContent = new VBox(10);
    runtimeContent.getStyleClass().add("settings-runtime-content");
    runtimeContent.setPadding(new javafx.geometry.Insets(12));
    if (runtimePaths.isEmpty()) {
      Label empty = new Label("Runtime and compiler paths will appear here after a language is run for the first time.");
      empty.setWrapText(true);
      empty.getStyleClass().add("settings-help-text");
      runtimeContent.getChildren().add(empty);
    } else {
      GridPane runtimeFieldsGrid = fields();
      runtimeFieldsGrid.getStyleClass().add("settings-runtime-fields");
      int row = 0;
      for (Map.Entry<String, String> entry : runtimePaths.entrySet()) {
        TextField path = new TextField(entry.getValue());
        path.setMaxWidth(Double.MAX_VALUE);
        path.setPromptText("Runtime not found");
        runtimeFields.put(entry.getKey(), path);
        Button redetect = new Button("Re-find");
        redetect.setOnAction(event -> {
          String detected = runtimeRedetector == null ? null : runtimeRedetector.apply(entry.getKey());
          if (detected != null && !detected.isBlank()) path.setText(detected);
        });
        redetect.setAccessibleText("Re-find " + runtimeLabel(entry.getKey()));
        runtimeFieldsGrid.addRow(row++, new Label(runtimeLabel(entry.getKey())), path, redetect);
        GridPane.setHgrow(path, Priority.ALWAYS);
      }
      runtimeContent.getChildren().add(runtimeFieldsGrid);
    }
    ScrollPane runtimeScroll = new ScrollPane(runtimeContent);
    runtimeScroll.setFitToWidth(true);
    runtimeScroll.setFitToHeight(true);
    //runtimeScroll.setPrefViewportHeight(420);
    //runtimeScroll.setPrefHeight(420);
    runtimeScroll.setMaxHeight(Double.MAX_VALUE);
    runtimeScroll.setMinHeight(0);
    runtimeScroll.getStyleClass().addAll("code-editor-scroll-pane", "roundedArea");
    VBox.setVgrow(runtimeScroll, Priority.ALWAYS);
    VBox runtimes = section("Runtimes & Compilers", runtimeScroll);
    VBox.setVgrow(runtimes, Priority.ALWAYS);
    runtimes.setMaxHeight(Double.MAX_VALUE);
    runtimes.getStyleClass().add("settings-runtime-section");

    url = new TextField(chatGPTUrl);
    key = new PasswordField();
    key.setText(chatGPTKey);
    model = combo(List.of("gpt-5-mini", "gpt-5", "gpt-4.1-mini", "gpt-4o-mini"), darkMode);
    model.setEditable(true);
    model.getEditor().setText(chatGPTModel);
    GridPane chatFields = fields();
    chatFields.addRow(0, new Label("API URL"), url);
    chatFields.addRow(1, new Label("API key"), key);
    chatFields.addRow(2, new Label("Model"), model);
    GridPane.setHgrow(url, Priority.ALWAYS);
    GridPane.setHgrow(key, Priority.ALWAYS);
    GridPane.setHgrow(model, Priority.ALWAYS);
    VBox chatGPT = section("ChatGPT", chatFields);

    collaborationServer = new TextField(collaborationServerName);
    collaborationPort = new TextField(collaborationPortNumber);
    collaborationName = new TextField(collaborationDisplayName);
    collaborationPassword = new PasswordField();
    collaborationPassword.setText(collaborationPasswordValue);
    collaborationAvatar = new TextField(collaborationAvatarValue);
    collaborationAvatar.setPromptText("Optional image path");
    GridPane collaborationFields = fields();
    collaborationServer.setPromptText("Hostname or https:// address");
    collaborationFields.addRow(0, new Label("Server address"), collaborationServer);
    collaborationFields.addRow(1, new Label("Port"), collaborationPort);
    collaborationFields.addRow(2, new Label("Your name"), collaborationName);
    collaborationFields.addRow(3, new Label("Server password"), collaborationPassword);
    ImageView avatarPreviewImage = new ImageView();
    StackPane avatarPreview = new StackPane(avatarPreviewImage);
    avatarPreview.setMinSize(36, 36);
    avatarPreview.setPrefSize(36, 36);
    avatarPreview.setMaxSize(36, 36);
    avatarPreview.setShape(new Circle(18, 18, 18));
    HBox avatarField = new HBox(8, collaborationAvatar, avatarPreview);
    HBox.setHgrow(collaborationAvatar, Priority.ALWAYS);
    avatarField.setMaxWidth(Double.MAX_VALUE);
    GridPane.setHgrow(avatarField, Priority.ALWAYS);
    collaborationAvatar.textProperty().addListener((observable, oldValue, newValue) -> updateAvatarPreview(avatarPreview, avatarPreviewImage, newValue));
    updateAvatarPreview(avatarPreview, avatarPreviewImage, collaborationAvatar.getText());
    GridPane avatarChoices = new GridPane();
    avatarChoices.setHgap(6);
    avatarChoices.setVgap(6);
    ToggleGroup avatarGroup = new ToggleGroup();
    for (CollaborationAvatarCatalog.Avatar avatar : CollaborationAvatarCatalog.avatars()) {
      ToggleButton choice = new ToggleButton();
      choice.setUserData(avatar.value());
      choice.setToggleGroup(avatarGroup);
      choice.setTooltip(new javafx.scene.control.Tooltip(avatar.name()));
      choice.setMinSize(52, 52);
      choice.setPrefSize(52, 52);
      choice.setMaxSize(52, 52);
      choice.getStyleClass().add("collaboration-avatar-choice");
      choice.setShape(new Circle(26, 26, 26));
      choice.setStyle("-fx-background-color: " + avatar.background() + "; -fx-background-radius: 50%;");
      javafx.scene.image.ImageView icon = CollaborationAvatarCatalog.view(avatar.value(), 44);
      if (icon != null) choice.setGraphic(icon);
      choice.setSelected(CollaborationAvatarCatalog.is(collaborationAvatarValue, avatar));
      choice.setOnAction(event -> collaborationAvatar.setText(avatar.value()));
      avatarChoices.add(choice, avatar.index() % 8, avatar.index() / 8);
    }
    Label hostedServerNote = new Label("jamiebalfour.scot provides a free hosted collaboration server with limited resources. For larger sessions, use your own server. Avatars produced by ChatGPT.");
    hostedServerNote.setWrapText(true);
    hostedServerNote.getStyleClass().add("settings-help-text");
    collaborationFields.add(hostedServerNote, 0, 5, 2, 1);

    Label builtInLabel = new Label("Built-in avatars");
    builtInLabel.getStyleClass().add("settings-help-text");
    VBox avatarOptions = new VBox(8, avatarField, builtInLabel, avatarChoices);
    avatarOptions.setMaxWidth(Double.MAX_VALUE);
    GridPane.setHgrow(avatarOptions, Priority.ALWAYS);
    collaborationFields.addRow(4, new Label("Avatar"), avatarOptions);

    GridPane.setHgrow(collaborationServer, Priority.ALWAYS);
    GridPane.setHgrow(collaborationPort, Priority.ALWAYS);
    GridPane.setHgrow(collaborationName, Priority.ALWAYS);
    VBox collaboration = section("Collaboration", collaborationFields);

    StackPane page = new StackPane(gui);
    // The modal may become narrower than the original desktop layout. Let
    // the active page take the width it is given instead of forcing the
    // modal beyond the window.
    page.setMinWidth(0);
    page.setMaxWidth(Double.MAX_VALUE);
    ScrollPane settingsScroll = new ScrollPane(page);
    settingsScroll.setFitToWidth(true);
    settingsScroll.setFitToHeight(false);
    settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    settingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    settingsScroll.setPannable(true);
    settingsScroll.setMinWidth(0);
    settingsScroll.setMaxWidth(Double.MAX_VALUE);
    settingsScroll.getStyleClass().add("settings-page-scroll");
    String settingsSurface = darkMode ? "#1e1e1e" : "#ffffff";
    settingsScroll.setStyle("-fx-background-color: " + settingsSurface + ";"
        + " -fx-background: " + settingsSurface + ";"
        + " -fx-control-inner-background: " + settingsSurface + ";");
    page.setStyle("-fx-background-color: " + settingsSurface + ";");
    HBox.setHgrow(settingsScroll, Priority.ALWAYS);
    sectionGroup.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
      String selected = selectedToggle == null ? "GUI" : String.valueOf(selectedToggle.getUserData());
      Node selectedPage = "ChatGPT".equals(selected) ? chatGPT
              : "Collaboration".equals(selected) ? collaboration
              : "Runtimes & Compilers".equals(selected) ? runtimes
              : "Experimental".equals(selected) ? experimental
              : "Execution".equals(selected) ? execution
              : "Editor".equals(selected) ? editor : gui;
      if (selectedPage instanceof javafx.scene.layout.Region region) {
        region.setMinWidth(0);
        region.setMaxWidth(Double.MAX_VALUE);
      }
      page.getChildren().setAll(selectedPage);
    });
    getChildren().addAll(sections, page);
    HBox settingsBody = new HBox(18, sections, settingsScroll);
    getChildren().setAll(settingsBody);
    VBox.setVgrow(settingsBody, Priority.ALWAYS);
    getStyleClass().add("settings-content");
    setMinWidth(0);
    setMaxWidth(Double.MAX_VALUE);
    widthProperty().addListener((observable, oldWidth, newWidth) -> {
      boolean compact = newWidth.doubleValue() > 0 && newWidth.doubleValue() < 820;
      if (compact == (getChildren().size() == 2 && getChildren().getFirst() == sections)) return;
      if (compact) {
        sections.setOrientation(Orientation.HORIZONTAL);
        sections.setPrefWidth(Double.MAX_VALUE);
        sections.setMinWidth(0);
        sections.setMaxWidth(Double.MAX_VALUE);
        sections.setMinHeight(54);
        sections.setPrefHeight(54);
        sections.setMaxHeight(54);
        getChildren().setAll(sections, settingsScroll);
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);
      } else {
        sections.setOrientation(Orientation.VERTICAL);
        sections.setMinWidth(190);
        sections.setPrefWidth(190);
        sections.setMaxWidth(190);
        sections.setMinHeight(0);
        sections.setPrefHeight(Control.USE_COMPUTED_SIZE);
        sections.setMaxHeight(Double.MAX_VALUE);
        getChildren().setAll(settingsBody);
        VBox.setVgrow(settingsBody, Priority.ALWAYS);
      }
    });
    if (darkMode) getStyleClass().add("settings-dark");
    setPrefWidth(840);
    // The settings view can be embedded as a full workspace surface; its
    // internal pages and scroll panes should consume the available height.
    setMinHeight(560);
    setPrefHeight(560);
    setMaxHeight(Double.MAX_VALUE);
    String settingsBackground = darkMode ? "#1e1e1e" : "#ffffff";
    String settingsText = darkMode ? "#f3f3f3" : "#202020";
    setStyle("-fx-background-color: " + settingsBackground + ";"
        + "-fx-text-background-color: " + settingsText + ";"
        + "-fx-text-base-color: " + settingsText + ";");
  }

  boolean hasValidChatGPTSettings() {
    String selectedModel = getChatGPTModel();
    return getChatGPTKey().isEmpty() || (!getChatGPTUrl().isEmpty() && !selectedModel.isEmpty());
  }

  boolean hasValidCollaborationSettings() {
    if (getCollaborationServer().isEmpty() || getCollaborationName().isEmpty()) {
      return false;
    }
    try {
      int port = Integer.parseInt(getCollaborationPort());
      return port >= 1 && port <= 65535;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  String getTheme() { return theme.getValue(); }
  String getLightEditorTheme() { return lightEditorTheme.getValue(); }
  String getDarkEditorTheme() { return darkEditorTheme.getValue(); }
  String getFontFamily() { return fontFamily.getEditor().getText().trim(); }
  int getFontSize() { return fontSize.getValue(); }
  int getIndentationSpaces() { return indentationSpaces.getValue(); }
  String getProjectsPath() { return projectsPath.getText().trim(); }
  boolean isWordWrapEnabled() { return wordWrap.isSelected(); }
  boolean isCodeMapEnabled() { return codeMap.isSelected(); }
  boolean isZpexPreferred() { return useZpex.isSelected(); }
  boolean isInputPromptEnabled() { return showInputPrompt.isSelected(); }
  String getMaximumMemory() { return maximumMemory.getText().trim(); }
  boolean isProjectTabGroupingEnabled() { return groupProjectTabs.isSelected(); }
  boolean isBlockClosuresEnabled() { return blockClosures.isSelected(); }
  boolean isAutoOpenCsvSpreadsheetEnabled() { return autoOpenCsvSpreadsheet.isSelected(); }
  String getChatGPTUrl() { return url.getText().trim(); }
  String getChatGPTKey() { return key.getText().trim(); }
  String getChatGPTModel() { return model.getEditor().getText().trim(); }
  String getCollaborationServer() { return collaborationServer.getText().trim(); }
  String getCollaborationPort() { return collaborationPort.getText().trim(); }
  String getCollaborationName() { return collaborationName.getText().trim(); }
  String getCollaborationPassword() { return collaborationPassword.getText(); }
  String getCollaborationAvatar() { return collaborationAvatar.getText().trim(); }
  Map<String, String> getRuntimePaths() {
    Map<String, String> values = new LinkedHashMap<>();
    runtimeFields.forEach((key, field) -> values.put(key, field.getText().trim()));
    return values;
  }

  private static String runtimeLabel(String key) {
    if ("YASS_RUNTIME_PATH".equals(key)) return "YASS (ZPE)";
    if ("RUNTIME_SQARL_PATH".equals(key)) return "SQARL";
    if ("RUNTIME_JAVASCRIPT_PATH".equals(key)) return "JavaScript";
    if ("RUNTIME_TYPESCRIPT_PATH".equals(key)) return "TypeScript";
    String label = key.replace("RUNTIME_", "").replace("_PATH", "").replace('_', ' ');
    return label.substring(0, 1).toUpperCase() + label.substring(1).toLowerCase();
  }

  private static void updateAvatarPreview(StackPane frame, ImageView preview, String path) {
    try {
      if (path == null || path.isBlank()) {
        preview.setImage(null);
        frame.setVisible(false);
        frame.setManaged(false);
        return;
      }
      String trimmed = path.trim();
      if (CollaborationAvatarCatalog.isBuiltIn(trimmed)) {
        ImageView builtIn = CollaborationAvatarCatalog.view(trimmed, 30);
        preview.setImage(builtIn == null ? null : builtIn.getImage());
        preview.setViewport(builtIn == null ? null : builtIn.getViewport());
        preview.setFitWidth(30);
        preview.setFitHeight(30);
        preview.setPreserveRatio(true);
        preview.setClip(new Circle(15, 15, 15));
        frame.setStyle("-fx-background-color: " + CollaborationAvatarCatalog.background(trimmed) + "; -fx-background-radius: 50%;");
        frame.setVisible(builtIn != null);
        frame.setManaged(builtIn != null);
        return;
      }
      String source = trimmed.startsWith("http://") || trimmed.startsWith("https://")
          ? trimmed : java.nio.file.Path.of(trimmed).toUri().toString();
      Image image = new Image(source, 128, 128, true, true, true);
      boolean valid = !image.isError();
      preview.setImage(valid ? image : null);
      preview.setViewport(null);
      preview.setFitWidth(30);
      preview.setFitHeight(30);
      preview.setPreserveRatio(true);
      preview.setClip(new Circle(15, 15, 15));
      frame.setStyle("-fx-background-color: transparent;");
      frame.setVisible(valid);
      frame.setManaged(valid);
    } catch (Exception ignored) {
      preview.setImage(null);
      frame.setVisible(false);
      frame.setManaged(false);
    }
  }

  private static BalfComboBoxFX<String> combo(List<String> choices, boolean darkMode) {
    BalfComboBoxFX<String> result = new BalfComboBoxFX<>(FXCollections.observableArrayList(choices));
    result.setDarkMode(darkMode);
    result.setMaxWidth(Double.MAX_VALUE);
    return result;
  }

  private static GridPane fields() {
    GridPane fields = new GridPane();
    fields.setHgap(12);
    fields.setVgap(12);
    return fields;
  }

  private static VBox section(String title, Node content) {
    Label heading = new Label(title);
    heading.getStyleClass().add("settings-section-title");
    VBox section = new VBox(12, heading, content);
    section.getStyleClass().add("settings-section-content");
    return section;
  }
}
