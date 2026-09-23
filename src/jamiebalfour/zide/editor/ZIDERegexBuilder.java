package jamiebalfour.zide.editor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Full-screen regular-expression workbench with named local patterns. */
public final class ZIDERegexBuilder {
  private final BorderPane root = new BorderPane();
  private final VBox sidebar = new VBox(8);
  private final ListView<SavedPattern> savedList = new ListView<>();
  private final ObservableList<SavedPattern> patterns = FXCollections.observableArrayList();
  private final TextField name = new TextField();
  private final TextField expression = new TextField();
  private final TextArea sample = new TextArea();
  private final ListView<String> matches = new ListView<>();
  private final Label result = new Label();
  private final Label status = new Label("Ready");
  private final CheckBox caseInsensitive = new CheckBox("Case insensitive");
  private final CheckBox multiline = new CheckBox("Multiline");
  private final CheckBox dotall = new CheckBox("Dot matches line breaks");
  private final Path storage;
  private final Runnable closeAction;

  public ZIDERegexBuilder(Path storage, Runnable closeAction) {
    this.storage = storage;
    this.closeAction = closeAction;
    root.getStyleClass().add("regex-builder");
    root.setTop(buildToolbar());
    root.setLeft(buildSidebar());
    root.setCenter(buildWorkspace());
    root.setBottom(status);
    status.getStyleClass().add("regex-builder-status");
    loadSavedPatterns();
    expression.textProperty().addListener((obs, oldValue, newValue) -> refreshMatches());
    sample.textProperty().addListener((obs, oldValue, newValue) -> refreshMatches());
    caseInsensitive.selectedProperty().addListener((obs, oldValue, newValue) -> refreshMatches());
    multiline.selectedProperty().addListener((obs, oldValue, newValue) -> refreshMatches());
    dotall.selectedProperty().addListener((obs, oldValue, newValue) -> refreshMatches());
    newPattern();
  }

  public Node getView() { return root; }

  public void setDarkMode(boolean enabled) {
    root.getStyleClass().remove("dark");
    if (enabled) root.getStyleClass().add("dark");
  }

  private Node buildToolbar() {
    Label title = new Label("RegExp Builder");
    title.getStyleClass().add("regex-builder-title");
    name.setPromptText("Pattern name");
    name.setPrefWidth(190);
    Button close = new Button("✕");
    close.getStyleClass().add("layout-builder-close");
    close.setTooltip(new Tooltip("Close regex builder"));
    close.setOnAction(event -> { if (closeAction != null) closeAction.run(); });
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button newButton = new Button("New");
    newButton.setOnAction(event -> newPattern());
    Button saveButton = new Button("Save");
    saveButton.setOnAction(event -> savePattern());
    HBox toolbar = new HBox(10, title, name, spacer, newButton, saveButton, close);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("regex-builder-toolbar");
    return toolbar;
  }

  private Node buildSidebar() {
    Label title = new Label("Saved regexps");
    title.getStyleClass().add("regex-builder-section-title");
    savedList.setItems(patterns);
    savedList.setCellFactory(list -> new ListCell<>() {
      @Override protected void updateItem(SavedPattern item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name);
      }
    });
    savedList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
      if (selected != null) openPattern(selected);
    });
    VBox.setVgrow(savedList, Priority.ALWAYS);
    sidebar.getChildren().addAll(title, savedList);
    sidebar.getStyleClass().add("regex-builder-sidebar");
    sidebar.setPrefWidth(220);
    return sidebar;
  }

  private Node buildWorkspace() {
    Label expressionTitle = new Label("Expression");
    expressionTitle.getStyleClass().add("regex-builder-section-title");
    expression.setPromptText("Type a regular expression");
    expression.getStyleClass().add("regex-builder-expression");
    HBox tokens = new HBox(6,
            token("\\d", "Digit"), token("\\s", "Whitespace"), token("\\w", "Word character"),
            token(".*", "Anything"), token("(?<name>...)" , "Named capture"));
    tokens.getStyleClass().add("regex-builder-tokens");
    HBox options = new HBox(14, caseInsensitive, multiline, dotall);
    options.getStyleClass().add("regex-builder-options");
    VBox expressionPane = new VBox(8, expressionTitle, expression, tokens, options);
    expressionPane.getStyleClass().add("regex-builder-expression-pane");

    Label sampleTitle = new Label("Test text");
    sampleTitle.getStyleClass().add("regex-builder-section-title");
    sample.setPromptText("Type or paste text to test against the expression");
    sample.setWrapText(true);
    sample.setPrefRowCount(10);
    VBox samplePane = new VBox(8, sampleTitle, sample);
    VBox.setVgrow(sample, Priority.ALWAYS);
    samplePane.getStyleClass().add("regex-builder-sample-pane");

    Label matchesTitle = new Label("Matches");
    matchesTitle.getStyleClass().add("regex-builder-section-title");
    result.getStyleClass().add("regex-builder-result");
    matches.setPlaceholder(new Label("Matches will appear here"));
    VBox.setVgrow(matches, Priority.ALWAYS);
    VBox matchesPane = new VBox(8, new HBox(10, matchesTitle, result), matches);
    matchesPane.getStyleClass().add("regex-builder-matches-pane");

    SplitPane split = new SplitPane(samplePane, matchesPane);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.58);
    VBox workspace = new VBox(12, expressionPane, split);
    workspace.setPadding(new Insets(16));
    VBox.setVgrow(split, Priority.ALWAYS);
    return workspace;
  }

  private Button token(String value, String tooltip) {
    Button button = new Button(value);
    button.setTooltip(new Tooltip(tooltip));
    button.setOnAction(event -> {
      int caret = expression.getCaretPosition();
      expression.insertText(caret, value);
      expression.requestFocus();
      expression.positionCaret(caret + value.length());
    });
    return button;
  }

  private void newPattern() {
    name.clear();
    expression.clear();
    sample.clear();
    savedList.getSelectionModel().clearSelection();
    status.setText("New expression");
    refreshMatches();
  }

  private void refreshMatches() {
    matches.getItems().clear();
    String source = sample.getText();
    String expressionText = expression.getText();
    if (expressionText.isBlank()) {
      result.setText("");
      return;
    }
    try {
      Matcher matcher = Pattern.compile(expressionText, flags()).matcher(source);
      int count = 0;
      while (matcher.find()) {
        count++;
        String value = matcher.group();
        matches.getItems().add((count) + ": " + display(value) + "  [" + matcher.start() + "–" + matcher.end() + "]");
        if (count >= 500) {
          matches.getItems().add("…additional matches hidden");
          break;
        }
      }
      result.setText(count + (count == 1 ? " match" : " matches"));
      status.setText("Expression is valid");
    } catch (PatternSyntaxException exception) {
      result.setText("Invalid expression");
      status.setText("Column " + exception.getIndex() + ": " + exception.getDescription());
    }
  }

  private int flags() {
    int value = 0;
    if (caseInsensitive.isSelected()) value |= Pattern.CASE_INSENSITIVE;
    if (multiline.isSelected()) value |= Pattern.MULTILINE;
    if (dotall.isSelected()) value |= Pattern.DOTALL;
    return value;
  }

  private void savePattern() {
    String patternName = name.getText().trim();
    if (!patternName.matches("[A-Za-z0-9][A-Za-z0-9 _.-]*")) {
      status.setText("Give the expression a name first.");
      name.requestFocus();
      return;
    }
    if (expression.getText().isBlank()) {
      status.setText("Enter an expression first.");
      expression.requestFocus();
      return;
    }
    try {
      Files.createDirectories(storage);
      Path target = storage.resolve(safeFileName(patternName) + ".regex");
      String content = "# ZIDE regular expression\n" + patternName + "\n"
              + Integer.toString(flags()) + "\n" + expression.getText();
      Files.writeString(target, content, StandardCharsets.UTF_8);
      loadSavedPatterns();
      selectByName(patternName);
      status.setText("Saved " + patternName);
    } catch (IOException exception) {
      status.setText("Could not save expression: " + exception.getMessage());
    }
  }

  private void loadSavedPatterns() {
    patterns.clear();
    try {
      if (!Files.isDirectory(storage)) return;
      try (var files = Files.list(storage)) {
        files.filter(path -> path.getFileName().toString().endsWith(".regex"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> {
                  try { patterns.add(readPattern(path)); }
                  catch (IOException ignored) { }
                });
      }
    } catch (IOException exception) {
      status.setText("Could not read saved expressions: " + exception.getMessage());
    }
  }

  private SavedPattern readPattern(Path path) throws IOException {
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    String patternName = lines.size() > 1 ? lines.get(1) : path.getFileName().toString().replaceFirst("\\.regex$", "");
    int storedFlags = lines.size() > 2 ? parseFlags(lines.get(2)) : 0;
    String expressionText = lines.size() > 3 ? String.join("\n", lines.subList(3, lines.size())) : "";
    return new SavedPattern(patternName, expressionText, storedFlags, path);
  }

  private void openPattern(SavedPattern pattern) {
    name.setText(pattern.name);
    expression.setText(pattern.expression);
    caseInsensitive.setSelected((pattern.flags & Pattern.CASE_INSENSITIVE) != 0);
    multiline.setSelected((pattern.flags & Pattern.MULTILINE) != 0);
    dotall.setSelected((pattern.flags & Pattern.DOTALL) != 0);
    status.setText("Opened " + pattern.name);
  }

  private void selectByName(String patternName) {
    for (SavedPattern pattern : patterns) {
      if (pattern.name.equals(patternName)) {
        savedList.getSelectionModel().select(pattern);
        return;
      }
    }
  }

  private static int parseFlags(String value) {
    try { return Integer.parseInt(value.trim()); }
    catch (NumberFormatException ignored) { return 0; }
  }

  private static String safeFileName(String value) {
    return value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
  }

  private static String display(String value) {
    return value.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  private record SavedPattern(String name, String expression, int flags, Path path) { }
}
