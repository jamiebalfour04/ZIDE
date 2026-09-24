package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfComboBox;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the preferences view independently from the main editor window. */
final class ZIDESettingsPanel extends HBox {
  private final BalfComboBox<String> theme;
  private final BalfComboBox<String> lightEditorTheme;
  private final BalfComboBox<String> darkEditorTheme;
  private final BalfComboBox<String> fontFamily;
  private final Spinner<Integer> fontSize;
  private final Spinner<Integer> indentationSpaces;
  private final CheckBox wordWrap;
  private final CheckBox codeMap;
  private final CheckBox useZpex;
  private final CheckBox showInputPrompt;
  private final CheckBox groupProjectTabs;
  private final CheckBox blockClosures;
  private final CheckBox autoOpenCsvSpreadsheet;
  private final TextField url;
  private final PasswordField key;
  private final BalfComboBox<String> model;
  private final TextField collaborationServer;
  private final TextField collaborationPort;
  private final TextField collaborationName;
  private final PasswordField collaborationPassword;
  private final TextField collaborationAvatar;
  private final Map<String, TextField> runtimeFields = new LinkedHashMap<>();

  ZIDESettingsPanel(boolean darkMode, String themeName, String lightTheme, String darkTheme,
                    String fontName, int fontSizeValue, int indentationSpacesValue, boolean wrapLines, boolean codeMapValue, boolean preferZpex, boolean showInputPromptValue, boolean groupProjectTabsValue, boolean blockClosuresValue, boolean autoOpenCsvSpreadsheetValue,
                    String chatGPTUrl, String chatGPTKey, String chatGPTModel,
                    String collaborationServerName, String collaborationPortNumber, String collaborationDisplayName,
                    String collaborationPasswordValue, String collaborationAvatarValue,
                    Map<String, String> runtimePaths) {
    super(18);

    ListView<String> sections = new ListView<>(FXCollections.observableArrayList("GUI", "Editor", "Execution", "Runtimes & Compilers", "ChatGPT", "Collaboration", "Experimental"));
    sections.getStyleClass().add("settings-section-list");
    sections.setPrefWidth(190);
    sections.setMinWidth(190);
    sections.setMaxWidth(190);
    sections.getSelectionModel().selectFirst();

    theme = combo(List.of("Light", "Dark"), darkMode);
    theme.setValue(themeName);
    GridPane guiFields = fields();
    guiFields.addRow(0, new Label("Theme"), theme);
    groupProjectTabs = new CheckBox("Group tabs by project");
    groupProjectTabs.setSelected(groupProjectTabsValue);
    guiFields.add(groupProjectTabs, 1, 1);
    GridPane.setHgrow(theme, Priority.ALWAYS);
    VBox gui = section("GUI", guiFields);

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
    editorFields.add(wordWrap, 1, 5);
    editorFields.add(codeMap, 1, 6);
    editorFields.add(autoOpenCsvSpreadsheet, 1, 7);
    for (Node control : List.of(lightEditorTheme, darkEditorTheme, fontFamily, fontSize, indentationSpaces)) {
      GridPane.setHgrow(control, Priority.ALWAYS);
    }
    VBox editor = section("Editor", editorFields);

    useZpex = new CheckBox("Use ZPEX when available");
    useZpex.setSelected(preferZpex);
    showInputPrompt = new CheckBox("Show a language prompt when input is required");
    showInputPrompt.setSelected(showInputPromptValue);
    Label yassTitle = new Label("YASS");
    yassTitle.getStyleClass().add("settings-group-title");
    VBox yassGroup = new VBox(10, yassTitle, useZpex, showInputPrompt);
    yassGroup.getStyleClass().add("settings-option-group");
    Label executionTitle = new Label("Execution");
    executionTitle.getStyleClass().add("settings-section-title");
    VBox execution = new VBox(16, executionTitle, yassGroup);
    execution.getStyleClass().add("settings-section-content");

    blockClosures = new CheckBox("Block closures");
    blockClosures.setSelected(blockClosuresValue);
    VBox experimental = section("Experimental", new VBox(10, blockClosures));

    VBox runtimeContent = new VBox(10);
    if (runtimePaths.isEmpty()) {
      Label empty = new Label("Runtime and compiler paths will appear here after a language is run for the first time.");
      empty.setWrapText(true);
      empty.getStyleClass().add("settings-help-text");
      runtimeContent.getChildren().add(empty);
    } else {
      GridPane runtimeFieldsGrid = fields();
      int row = 0;
      for (Map.Entry<String, String> entry : runtimePaths.entrySet()) {
        TextField path = new TextField(entry.getValue());
        path.setMaxWidth(Double.MAX_VALUE);
        runtimeFields.put(entry.getKey(), path);
        runtimeFieldsGrid.addRow(row++, new Label(runtimeLabel(entry.getKey())), path);
        GridPane.setHgrow(path, Priority.ALWAYS);
      }
      runtimeContent.getChildren().add(runtimeFieldsGrid);
    }
    ScrollPane runtimeScroll = new ScrollPane(runtimeContent);
    runtimeScroll.setFitToWidth(true);
    runtimeScroll.setFitToHeight(false);
    runtimeScroll.setPrefViewportHeight(420);
    runtimeScroll.setPrefHeight(420);
    runtimeScroll.setMaxHeight(420);
    runtimeScroll.setMinHeight(0);
    runtimeScroll.getStyleClass().add("code-editor-scroll-pane");
    VBox.setVgrow(runtimeScroll, Priority.ALWAYS);
    VBox runtimes = section("Runtimes & Compilers", runtimeScroll);

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
    ImageView avatarPreview = new ImageView();
    avatarPreview.setFitWidth(36); avatarPreview.setFitHeight(36); avatarPreview.setPreserveRatio(true);
    HBox avatarField = new HBox(8, collaborationAvatar, avatarPreview);
    HBox.setHgrow(collaborationAvatar, Priority.ALWAYS);
    collaborationAvatar.textProperty().addListener((observable, oldValue, newValue) -> updateAvatarPreview(avatarPreview, newValue));
    updateAvatarPreview(avatarPreview, collaborationAvatar.getText());
    collaborationFields.addRow(4, new Label("Avatar image"), avatarField);
    Label hostedServerNote = new Label("jamiebalfour.scot provides a free hosted collaboration server with limited resources. For larger sessions, use your own server.");
    hostedServerNote.setWrapText(true);
    hostedServerNote.getStyleClass().add("settings-help-text");
    collaborationFields.add(hostedServerNote, 0, 5, 2, 1);
    GridPane.setHgrow(collaborationServer, Priority.ALWAYS);
    GridPane.setHgrow(collaborationPort, Priority.ALWAYS);
    GridPane.setHgrow(collaborationName, Priority.ALWAYS);
    VBox collaboration = section("Collaboration", collaborationFields);

    StackPane page = new StackPane(gui);
    page.setMinWidth(590);
    HBox.setHgrow(page, Priority.ALWAYS);
    sections.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
      Node selectedPage = "ChatGPT".equals(selected) ? chatGPT
              : "Collaboration".equals(selected) ? collaboration
              : "Runtimes & Compilers".equals(selected) ? runtimes
              : "Experimental".equals(selected) ? experimental
              : "Execution".equals(selected) ? execution
              : "Editor".equals(selected) ? editor : gui;
      page.getChildren().setAll(selectedPage);
    });
    getChildren().addAll(sections, page);
    getStyleClass().add("settings-content");
    setPrefWidth(840);
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
  boolean isWordWrapEnabled() { return wordWrap.isSelected(); }
  boolean isCodeMapEnabled() { return codeMap.isSelected(); }
  boolean isZpexPreferred() { return useZpex.isSelected(); }
  boolean isInputPromptEnabled() { return showInputPrompt.isSelected(); }
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
    String label = key.replace("RUNTIME_", "").replace("_PATH", "").replace('_', ' ');
    return label.substring(0, 1).toUpperCase() + label.substring(1).toLowerCase();
  }

  private static void updateAvatarPreview(ImageView preview, String path) {
    try {
      if (path == null || path.isBlank()) { preview.setImage(null); return; }
      Image image = new Image(java.nio.file.Path.of(path.trim()).toUri().toString(), 128, 128, true, true, true);
      preview.setImage(image.isError() ? null : image);
    } catch (Exception ignored) { preview.setImage(null); }
  }

  private static BalfComboBox<String> combo(List<String> choices, boolean darkMode) {
    BalfComboBox<String> result = new BalfComboBox<>(FXCollections.observableArrayList(choices));
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
