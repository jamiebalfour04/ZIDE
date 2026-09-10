package jamiebalfour.zide.editor;

import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zpe.core.types.ZPEList;
import jamiebalfour.zpe.core.types.ZPEMap;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Graphical editor for ZenLang language definitions. */
public final class ZIDELanguageBuilder {
  private static final Map<String, List<String>> ACTION_PARAMETERS = actions();

  private final BorderPane root = new BorderPane();
  private final Window owner;
  private final Consumer<Path> trainAction;
  private final BiConsumer<Path, Path> testAction;
  private final Runnable closeAction;
  private final TextField languageName = new TextField();
  private final ObservableList<Rule> rules = FXCollections.observableArrayList();
  private final ListView<Rule> ruleList = new ListView<>(rules);
  private final TextField ruleName = new TextField();
  private final TextArea pattern = new TextArea();
  private final ComboBox<String> action = new ComboBox<>();
  private final TextField parameters = new TextField();
  private final Label parameterHint = new Label();
  private final Label status = new Label("Ready");
  private final TextArea preview = new TextArea();
  private Path file;
  private Rule selected;
  private boolean updating;

  public ZIDELanguageBuilder(Window owner, Path initialFile, Consumer<Path> trainAction,
                             BiConsumer<Path, Path> testAction, Runnable closeAction) {
    this.owner = owner;
    this.trainAction = trainAction;
    this.testAction = testAction;
    this.closeAction = closeAction;
    root.getStyleClass().add("language-builder");
    root.setTop(buildToolbar());
    root.setCenter(buildWorkspace());
    root.setBottom(status);
    status.getStyleClass().add("language-builder-status");
    root.setMinSize(820, 560);
    bindEditor();
    newDefinition();
    if (initialFile != null && Files.isRegularFile(initialFile)) {
      try {
        if (Files.size(initialFile) == 0) {
          file = initialFile.toAbsolutePath().normalize();
          languageName.setText(languageNameFor(initialFile));
          refreshPreview();
          save(false);
          status.setText("Created " + initialFile.getFileName());
        } else {
          load(initialFile);
          status.setText("Opened " + initialFile.getFileName());
        }
      } catch (Exception exception) {
        status.setText("Could not open definition: " + exception.getMessage());
      }
    }
  }

  public Node getView() { return root; }

  private ToolBar buildToolbar() {
    Label heading = new Label("ZenLang Builder");
    heading.getStyleClass().add("language-builder-heading");
    languageName.setPromptText("Language name");
    languageName.setPrefWidth(190);
    languageName.textProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
    Button fresh = new Button("New");
    fresh.setOnAction(event -> newDefinition());
    Button open = new Button("Open");
    open.setOnAction(event -> open());
    Button save = new Button("Save");
    save.setOnAction(event -> save(false));
    Button train = new Button("Train");
    train.getStyleClass().add("language-builder-primary");
    train.setOnAction(event -> {
      Path saved = save(true);
      if (saved != null && trainAction != null) trainAction.accept(saved);
    });
    Button test = new Button("Test Script");
    test.setOnAction(event -> testScript());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button close = new Button("Close");
    close.setOnAction(event -> { if (closeAction != null) closeAction.run(); });
    return new ToolBar(heading, new Separator(), new Label("Name"), languageName,
            new Separator(), fresh, open, save, test, train, spacer, close);
  }

  private Node buildWorkspace() {
    SplitPane split = new SplitPane(buildRuleList(), buildRuleEditor(), buildPreview());
    split.setDividerPositions(0.23, 0.69);
    return split;
  }

  private Node buildRuleList() {
    Label title = new Label("Rules");
    title.getStyleClass().add("language-builder-section-title");
    ruleList.setPlaceholder(new Label("No rules"));
    ruleList.setCellFactory(view -> new ListCell<>() {
      @Override protected void updateItem(Rule item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name);
      }
    });
    ruleList.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> select(newValue));
    Button add = new Button("+");
    add.setTooltip(new Tooltip("Add rule"));
    add.setOnAction(event -> addRule());
    Button remove = new Button("-");
    remove.setTooltip(new Tooltip("Delete rule"));
    remove.setOnAction(event -> removeRule());
    Button up = new Button("↑");
    up.setTooltip(new Tooltip("Move rule up"));
    up.setOnAction(event -> moveRule(-1));
    Button down = new Button("↓");
    down.setTooltip(new Tooltip("Move rule down"));
    down.setOnAction(event -> moveRule(1));
    HBox controls = new HBox(6, add, remove, up, down);
    VBox box = new VBox(8, title, ruleList, controls);
    box.getStyleClass().add("language-builder-rule-list");
    VBox.setVgrow(ruleList, Priority.ALWAYS);
    return box;
  }

  private Node buildRuleEditor() {
    Label title = new Label("Rule");
    title.getStyleClass().add("language-builder-section-title");
    ruleName.setPromptText("Unique rule name");
    pattern.setPromptText("Pattern with named captures, for example: echo\\s+(?<values>[^;\\n]+);");
    pattern.setWrapText(true);
    pattern.setPrefRowCount(5);
    action.setItems(FXCollections.observableArrayList(ACTION_PARAMETERS.keySet()));
    action.setMaxWidth(Double.MAX_VALUE);
    parameters.setPromptText("Comma-separated capture names");
    parameterHint.getStyleClass().add("language-builder-hint");
    parameterHint.setWrapText(true);
    Label orderHint = new Label("Rules are matched from top to bottom. Put specific forms before general ones.");
    orderHint.getStyleClass().add("language-builder-hint");
    orderHint.setWrapText(true);
    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    ColumnConstraints labels = new ColumnConstraints();
    labels.setMinWidth(82);
    ColumnConstraints fields = new ColumnConstraints();
    fields.setHgrow(Priority.ALWAYS);
    form.getColumnConstraints().addAll(labels, fields);
    form.addRow(0, new Label("Name"), ruleName);
    form.addRow(1, new Label("Pattern"), pattern);
    form.addRow(2, new Label("Action"), action);
    form.addRow(3, new Label("Parameters"), parameters);
    form.add(parameterHint, 1, 4);
    VBox box = new VBox(12, title, orderHint, form);
    box.getStyleClass().add("language-builder-rule-editor");
    return box;
  }

  private Node buildPreview() {
    Label title = new Label("Definition");
    title.getStyleClass().add("language-builder-section-title");
    preview.setEditable(false);
    preview.setWrapText(false);
    preview.getStyleClass().add("language-builder-preview");
    VBox box = new VBox(8, title, preview);
    box.getStyleClass().add("language-builder-preview-pane");
    VBox.setVgrow(preview, Priority.ALWAYS);
    return box;
  }

  private void bindEditor() {
    ruleName.textProperty().addListener((observable, oldValue, newValue) -> updateSelected());
    pattern.textProperty().addListener((observable, oldValue, newValue) -> updateSelected());
    parameters.textProperty().addListener((observable, oldValue, newValue) -> updateSelected());
    action.valueProperty().addListener((observable, oldValue, newValue) -> {
      updateParameterHint();
      updateSelected();
    });
  }

  private void newDefinition() {
    file = null;
    languageName.setText("my-language");
    rules.clear();
    rules.add(new Rule("comment", "//[^\\n]*", "ignore", ""));
    rules.add(new Rule("print", "print\\s+(?<values>[^;\\n]+);", "print", "values"));
    ruleList.getSelectionModel().selectFirst();
    status.setText("New language definition");
    refreshPreview();
  }

  private void addRule() {
    Rule rule = new Rule("rule-" + (rules.size() + 1), "", "expression", "expression");
    int selectedIndex = ruleList.getSelectionModel().getSelectedIndex();
    int insertion = selectedIndex < 0 ? rules.size() : selectedIndex + 1;
    rules.add(insertion, rule);
    ruleList.getSelectionModel().select(rule);
  }

  private void removeRule() {
    int index = ruleList.getSelectionModel().getSelectedIndex();
    if (index < 0) return;
    rules.remove(index);
    if (!rules.isEmpty()) ruleList.getSelectionModel().select(Math.min(index, rules.size() - 1));
    refreshPreview();
  }

  private void moveRule(int direction) {
    int index = ruleList.getSelectionModel().getSelectedIndex();
    int destination = index + direction;
    if (index < 0 || destination < 0 || destination >= rules.size()) return;
    Rule rule = rules.remove(index);
    rules.add(destination, rule);
    ruleList.getSelectionModel().select(destination);
    refreshPreview();
  }

  private void select(Rule rule) {
    selected = rule;
    updating = true;
    boolean disabled = rule == null;
    ruleName.setDisable(disabled);
    pattern.setDisable(disabled);
    action.setDisable(disabled);
    parameters.setDisable(disabled);
    ruleName.setText(disabled ? "" : rule.name);
    pattern.setText(disabled ? "" : rule.pattern);
    action.setValue(disabled ? null : rule.action);
    parameters.setText(disabled ? "" : rule.parameters);
    updating = false;
    updateParameterHint();
  }

  private void updateSelected() {
    if (updating || selected == null) return;
    selected.name = ruleName.getText();
    selected.pattern = pattern.getText();
    selected.action = action.getValue();
    selected.parameters = parameters.getText();
    ruleList.refresh();
    refreshPreview();
  }

  private void updateParameterHint() {
    List<String> expected = ACTION_PARAMETERS.get(action.getValue());
    parameterHint.setText(expected == null || expected.isEmpty()
            ? "This action does not consume captures."
            : "Expected bindings: " + String.join(", ", expected));
  }

  private void open() {
    FileChooser chooser = chooser("Open ZenLang definition");
    File chosen = chooser.showOpenDialog(owner);
    if (chosen == null) return;
    try {
      load(chosen.toPath());
      status.setText("Opened " + chosen.getName());
    } catch (Exception exception) {
      status.setText("Could not open definition: " + exception.getMessage());
    }
  }

  private void testScript() {
    Path definition = save(true);
    if (definition == null || testAction == null) return;
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose a script to test");
    Path parent = definition.toAbsolutePath().getParent();
    if (parent != null && Files.isDirectory(parent)) chooser.setInitialDirectory(parent.toFile());
    chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Source files", "*.txt", "*.script", "*.*"),
            new FileChooser.ExtensionFilter("All files", "*.*"));
    File script = chooser.showOpenDialog(owner);
    if (script == null) return;
    status.setText("Testing " + script.getName());
    testAction.accept(definition, script.toPath());
  }

  private void load(Path source) throws Exception {
    Object decoded = new ZenithJSONParser().jsonDecode(Files.readString(source), false);
    if (!(decoded instanceof ZPEMap rootMap)) throw new IllegalArgumentException("Definition must be a JSON object.");
    Object name = rootMap.get("name");
    Object ruleValue = rootMap.get("rules");
    if (name == null || !(ruleValue instanceof ZPEList list)) throw new IllegalArgumentException("Definition needs name and rules.");
    languageName.setText(decode(name.toString()));
    rules.clear();
    for (Object value : list) {
      if (!(value instanceof ZPEMap map)) continue;
      rules.add(new Rule(value(map, "name"), value(map, "pattern"),
              value(map, "action"), parameters(map.get("parameters"))));
    }
    file = source.toAbsolutePath().normalize();
    ruleList.getSelectionModel().selectFirst();
    refreshPreview();
  }

  private Path save(boolean training) {
    String problem = validateDefinition();
    if (problem != null) {
      status.setText(problem);
      return null;
    }
    if (file == null) {
      FileChooser chooser = chooser(training ? "Save definition before training" : "Save ZenLang definition");
      chooser.setInitialFileName(languageName.getText() + ".zlang");
      File chosen = chooser.showSaveDialog(owner);
      if (chosen == null) return null;
      file = ensureExtension(chosen.toPath());
    }
    try {
      Files.writeString(file, definitionJson(), StandardCharsets.UTF_8);
      status.setText("Saved " + file.getFileName());
      return file;
    } catch (Exception exception) {
      status.setText("Could not save definition: " + exception.getMessage());
      return null;
    }
  }

  private String validateDefinition() {
    if (!languageName.getText().matches("[A-Za-z][A-Za-z0-9_-]*")) {
      return "Language name must begin with a letter and contain only letters, numbers, '-' or '_'.";
    }
    if (rules.isEmpty()) return "Add at least one syntax rule.";
    for (Rule rule : rules) {
      if (rule.name == null || rule.name.isBlank()) return "Every rule needs a name.";
      if (rule.pattern == null || rule.pattern.isBlank()) return "Rule '" + rule.name + "' needs a pattern.";
      if (!ACTION_PARAMETERS.containsKey(rule.action)) return "Rule '" + rule.name + "' needs an action.";
    }
    return null;
  }

  private void refreshPreview() { preview.setText(definitionJson()); }

  private String definitionJson() {
    StringBuilder json = new StringBuilder();
    json.append("{\n  \"name\": \"").append(escape(languageName.getText())).append("\",\n  \"rules\": [\n");
    for (int i = 0; i < rules.size(); i++) {
      Rule rule = rules.get(i);
      json.append("    { \"name\": \"").append(escape(rule.name)).append("\", \"pattern\": \"")
              .append(escape(rule.pattern)).append("\", \"action\": \"")
              .append(escape(rule.action)).append("\", \"parameters\": [");
      List<String> names = parameterNames(rule.parameters);
      for (int p = 0; p < names.size(); p++) {
        if (p > 0) json.append(", ");
        json.append('"').append(escape(names.get(p))).append('"');
      }
      json.append("] }").append(i + 1 == rules.size() ? "\n" : ",\n");
    }
    return json.append("  ]\n}\n").toString();
  }

  private static FileChooser chooser(String title) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("ZenLang definitions", "*.zenlang", "*.zlang"));
    return chooser;
  }

  private static Path ensureExtension(Path path) {
    return path.getFileName().toString().toLowerCase().endsWith(".zenlang")
            ? path : path.resolveSibling(path.getFileName() + ".zenlang");
  }

  private static String languageNameFor(Path path) {
    String name = path.getFileName().toString().replaceFirst("(?i)\\.zenlang$", "");
    name = name.replaceAll("[^A-Za-z0-9_-]", "-");
    if (name.isEmpty() || !Character.isLetter(name.charAt(0))) name = "language-" + name;
    return name;
  }

  private static String value(ZPEMap map, String key) {
    Object value = map.get(key);
    return value == null ? "" : decode(value.toString());
  }

  private static String parameters(Object value) {
    if (!(value instanceof ZPEList list)) return "";
    List<String> names = new ArrayList<>();
    for (Object item : list) names.add(decode(item.toString()));
    return String.join(", ", names);
  }

  private static List<String> parameterNames(String value) {
    List<String> names = new ArrayList<>();
    if (value == null) return names;
    for (String name : value.split(",")) if (!name.trim().isEmpty()) names.add(name.trim());
    return names;
  }

  private static String escape(String value) {
    if (value == null) return "";
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  private static String decode(String value) {
    StringBuilder decoded = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current != '\\' || i + 1 >= value.length()) { decoded.append(current); continue; }
      char next = value.charAt(++i);
      decoded.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next == 't' ? '\t' : next);
    }
    return decoded.toString();
  }

  private static Map<String, List<String>> actions() {
    Map<String, List<String>> actions = new LinkedHashMap<>();
    actions.put("ignore", List.of());
    actions.put("function", List.of("name", "parameters", "body"));
    actions.put("ifStatement", List.of("condition", "body", "elseIfs", "else"));
    actions.put("elseIf", List.of("condition", "body"));
    actions.put("else", List.of("body"));
    actions.put("whileLoop", List.of("condition", "body"));
    actions.put("forLoop", List.of("initialiser", "condition", "increment", "body"));
    actions.put("eachLoop", List.of("iterable", "variable", "body"));
    actions.put("blockEnd", List.of());
    actions.put("assignment", List.of("assignment"));
    actions.put("print", List.of("values"));
    actions.put("returnValue", List.of("value"));
    actions.put("breakStatement", List.of());
    actions.put("expression", List.of("expression"));
    return actions;
  }

  private static final class Rule {
    private String name;
    private String pattern;
    private String action;
    private String parameters;

    private Rule(String name, String pattern, String action, String parameters) {
      this.name = name;
      this.pattern = pattern;
      this.action = action;
      this.parameters = parameters;
    }
  }
}
