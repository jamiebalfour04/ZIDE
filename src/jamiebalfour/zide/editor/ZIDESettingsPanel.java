package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfComboBox;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Builds the preferences view independently from the main editor window. */
final class ZIDESettingsPanel extends HBox {
  private final BalfComboBox<String> theme;
  private final BalfComboBox<String> lightEditorTheme;
  private final BalfComboBox<String> darkEditorTheme;
  private final BalfComboBox<String> fontFamily;
  private final Spinner<Integer> fontSize;
  private final CheckBox wordWrap;
  private final CheckBox useZpex;
  private final TextField url;
  private final PasswordField key;
  private final BalfComboBox<String> model;
  private final TextField collaborationServer;
  private final TextField collaborationPort;
  private final TextField collaborationName;

  ZIDESettingsPanel(boolean darkMode, String themeName, String lightTheme, String darkTheme,
                    String fontName, int fontSizeValue, boolean wrapLines, boolean preferZpex,
                    String chatGPTUrl, String chatGPTKey, String chatGPTModel,
                    String collaborationServerName, String collaborationPortNumber, String collaborationDisplayName) {
    super(18);

    ListView<String> sections = new ListView<>(FXCollections.observableArrayList("GUI", "Editor", "Execution", "ChatGPT", "Collaboration"));
    sections.getStyleClass().add("settings-section-list");
    sections.setPrefWidth(190);
    sections.setMinWidth(190);
    sections.setMaxWidth(190);
    sections.getSelectionModel().selectFirst();

    theme = combo(List.of("Light", "Dark"), darkMode);
    theme.setValue(themeName);
    GridPane guiFields = fields();
    guiFields.addRow(0, new Label("Theme"), theme);
    GridPane.setHgrow(theme, Priority.ALWAYS);
    VBox gui = section("GUI", guiFields);

    List<String> editorThemes = List.of("ZIDE", "Solarized", "GitHub", "Dracula", "Monokai", "Nord");
    lightEditorTheme = combo(editorThemes, darkMode);
    lightEditorTheme.setValue(lightTheme);
    darkEditorTheme = combo(editorThemes, darkMode);
    darkEditorTheme.setValue(darkTheme);
    fontFamily = combo(List.of("Menlo", "JetBrains Mono", "Cascadia Mono", "Consolas", "Monospace"), darkMode);
    fontFamily.setEditable(true);
    fontFamily.setValue(fontName);
    fontSize = new Spinner<>(8, 32, fontSizeValue);
    fontSize.setEditable(true);
    wordWrap = new CheckBox("Wrap long lines");
    wordWrap.setSelected(wrapLines);
    GridPane editorFields = fields();
    editorFields.addRow(0, new Label("Light theme"), lightEditorTheme);
    editorFields.addRow(1, new Label("Dark theme"), darkEditorTheme);
    editorFields.addRow(2, new Label("Font"), fontFamily);
    editorFields.addRow(3, new Label("Font size"), fontSize);
    editorFields.add(wordWrap, 1, 4);
    for (Node control : List.of(lightEditorTheme, darkEditorTheme, fontFamily, fontSize)) {
      GridPane.setHgrow(control, Priority.ALWAYS);
    }
    VBox editor = section("Editor", editorFields);

    useZpex = new CheckBox("Use ZPEX when available");
    useZpex.setSelected(preferZpex);
    Label yassTitle = new Label("YASS");
    yassTitle.getStyleClass().add("settings-group-title");
    VBox yassGroup = new VBox(10, yassTitle, useZpex);
    yassGroup.getStyleClass().add("settings-option-group");
    Label executionTitle = new Label("Execution");
    executionTitle.getStyleClass().add("settings-section-title");
    VBox execution = new VBox(16, executionTitle, yassGroup);
    execution.getStyleClass().add("settings-section-content");

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
    GridPane collaborationFields = fields();
    collaborationServer.setPromptText("Hostname or https:// address");
    collaborationFields.addRow(0, new Label("Server address"), collaborationServer);
    collaborationFields.addRow(1, new Label("Port"), collaborationPort);
    collaborationFields.addRow(2, new Label("Your name"), collaborationName);
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
    if (getCollaborationServer().isEmpty() || getCollaborationName().isEmpty()) return false;
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
  boolean isWordWrapEnabled() { return wordWrap.isSelected(); }
  boolean isZpexPreferred() { return useZpex.isSelected(); }
  String getChatGPTUrl() { return url.getText().trim(); }
  String getChatGPTKey() { return key.getText().trim(); }
  String getChatGPTModel() { return model.getEditor().getText().trim(); }
  String getCollaborationServer() { return collaborationServer.getText().trim(); }
  String getCollaborationPort() { return collaborationPort.getText().trim(); }
  String getCollaborationName() { return collaborationName.getText().trim(); }

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
