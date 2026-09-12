package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfGlassMenuBar;
import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zpe.core.types.ZPEList;
import jamiebalfour.zpe.core.types.ZPEMap;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
  private static final List<RuleTemplate> RULE_TEMPLATES = templates();
  private static final String TEMPLATE_DRAG = "zenlang-template:";
  private static final String RULE_DRAG = "zenlang-rule:";

  private final BorderPane root = new BorderPane();
  private final Window owner;
  private final Consumer<Path> trainAction;
  private final BiConsumer<Path, Path> testAction;
  private final Runnable closeAction;
  private BalfGlassMenuBar glassMenuBar;
  private final TextField languageName = new TextField();
  private final ObservableList<Rule> rules = FXCollections.observableArrayList();
  private final ListView<Rule> ruleList = new ListView<>(rules);
  private final TextField ruleName = new TextField();
  private final TextField friendlySyntax = new TextField();
  private final TextArea pattern = new TextArea();
  private final Label syntaxSummary = new Label();
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
    root.setTop(new VBox(buildMenuBar(), buildToolbar()));
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

  public void setDarkMode(boolean enabled) {
    if (glassMenuBar != null) glassMenuBar.setDarkMode(enabled);
  }

  private Node buildMenuBar() {
    glassMenuBar = new BalfGlassMenuBar();
    BalfGlassMenuBar.GlassMenu fileMenu = glassMenuBar.menu("File");
    fileMenu.createItem("New", "", this::newDefinition);
    fileMenu.createItem("Open...", "", this::open);
    fileMenu.separator();
    fileMenu.createItem("Save", "", () -> save(false));

    BalfGlassMenuBar.GlassMenu languageMenu = glassMenuBar.menu("Language");
    languageMenu.createItem("Test Script...", "", this::testScript);
    languageMenu.createItem("Train...", "", this::train);
    return glassMenuBar;
  }

  private Node buildToolbar() {
    Label heading = new Label("ZenLang Builder");
    heading.getStyleClass().add("language-builder-heading");
    languageName.setPromptText("Language name");
    languageName.setPrefWidth(190);
    languageName.textProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button close = new Button("✕");
    close.getStyleClass().add("layout-builder-close");
    close.setOnAction(event -> { if (closeAction != null) closeAction.run(); });
    HBox toolbar = new HBox(10, heading, new Label("Name"), languageName, spacer, close);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("language-builder-toolbar");
    return toolbar;
  }

  private void train() {
    Path saved = save(true);
    if (saved != null && trainAction != null) trainAction.accept(saved);
  }

  private Node buildWorkspace() {
    SplitPane split = new SplitPane(buildRuleList(), buildRuleEditor(), buildPreview());
    split.setDividerPositions(0.23, 0.69);
    return split;
  }

  private Node buildRuleList() {
    Label title = new Label("Rules");
    title.getStyleClass().add("language-builder-section-title");
    Label paletteTitle = new Label("Drag a building block into the rule list");
    paletteTitle.getStyleClass().add("language-builder-hint");
    FlowPane palette = new FlowPane(6, 6);
    palette.getStyleClass().add("language-builder-palette");
    for (int index = 0; index < RULE_TEMPLATES.size(); index++) {
      RuleTemplate template = RULE_TEMPLATES.get(index);
      Label chip = new Label(template.label);
      chip.getStyleClass().add("language-builder-template-chip");
      Tooltip.install(chip, new Tooltip(template.description));
      int templateIndex = index;
      chip.setOnMouseClicked(event -> addTemplate(templateIndex, rules.size()));
      chip.setOnDragDetected(event -> {
        Dragboard board = chip.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putString(TEMPLATE_DRAG + templateIndex);
        board.setContent(content);
        event.consume();
      });
      palette.getChildren().add(chip);
    }
    ruleList.setPlaceholder(new Label("No rules"));
    ruleList.setCellFactory(view -> new ListCell<>() {
      @Override protected void updateItem(Rule item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name);
      }

      {
        setOnDragDetected(event -> {
          if (isEmpty()) return;
          Dragboard board = startDragAndDrop(TransferMode.MOVE);
          ClipboardContent content = new ClipboardContent();
          content.putString(RULE_DRAG + getIndex());
          board.setContent(content);
          event.consume();
        });
        setOnDragOver(event -> {
          String value = event.getDragboard().getString();
          if (value != null && (value.startsWith(TEMPLATE_DRAG) || value.startsWith(RULE_DRAG))) {
            event.acceptTransferModes(value.startsWith(TEMPLATE_DRAG) ? TransferMode.COPY : TransferMode.MOVE);
          }
          event.consume();
        });
        setOnDragDropped(event -> {
          int destination = isEmpty() ? rules.size() : getIndex();
          event.setDropCompleted(handleRuleDrop(event.getDragboard().getString(), destination));
          event.consume();
        });
      }
    });
    ruleList.setOnDragOver(event -> {
      String value = event.getDragboard().getString();
      if (value != null && (value.startsWith(TEMPLATE_DRAG) || value.startsWith(RULE_DRAG))) {
        event.acceptTransferModes(value.startsWith(TEMPLATE_DRAG) ? TransferMode.COPY : TransferMode.MOVE);
      }
      event.consume();
    });
    ruleList.setOnDragDropped(event -> {
      event.setDropCompleted(handleRuleDrop(event.getDragboard().getString(), rules.size()));
      event.consume();
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
    VBox box = new VBox(8, title, paletteTitle, palette, new Separator(), ruleList, controls);
    box.getStyleClass().add("language-builder-rule-list");
    VBox.setVgrow(ruleList, Priority.ALWAYS);
    return box;
  }

  private Node buildRuleEditor() {
    Label title = new Label("Rule");
    title.getStyleClass().add("language-builder-section-title");
    ruleName.setPromptText("Unique rule name");
    friendlySyntax.setPromptText("For example: if (${condition:expression}) {");
    pattern.setPromptText("Pattern with named captures, for example: echo\\s+(?<values>[^;\\n]+);");
    pattern.setWrapText(true);
    pattern.setPrefRowCount(5);
    syntaxSummary.getStyleClass().add("language-builder-syntax-summary");
    syntaxSummary.setWrapText(true);
    action.setItems(FXCollections.observableArrayList(ACTION_PARAMETERS.keySet()));
    action.setMaxWidth(Double.MAX_VALUE);
    action.setCellFactory(list -> behaviourCell());
    action.setButtonCell(behaviourCell());
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
    form.addRow(1, new Label("Syntax"), friendlySyntax);
    form.addRow(2, new Label("Matches"), syntaxSummary);
    form.addRow(3, new Label("Behaviour"), action);
    form.add(parameterHint, 1, 4);
    Label syntaxHint = new Label("Use named values such as ${condition:expression}, ${name:identifier}, "
            + "${variable:variable}, ${values:values}, ${parameters:parameters}, ${count:number} or ${text:text}.");
    syntaxHint.getStyleClass().add("language-builder-hint");
    syntaxHint.setWrapText(true);
    GridPane advancedForm = new GridPane();
    advancedForm.setHgap(10);
    advancedForm.setVgap(10);
    advancedForm.addRow(0, new Label("Pattern"), pattern);
    advancedForm.addRow(1, new Label("Parameters"), parameters);
    GridPane.setHgrow(pattern, Priority.ALWAYS);
    GridPane.setHgrow(parameters, Priority.ALWAYS);
    TitledPane advanced = new TitledPane("Advanced Pattern", advancedForm);
    advanced.getStyleClass().add("language-builder-advanced-pattern");
    advanced.setExpanded(false);
    VBox box = new VBox(12, title, orderHint, form, syntaxHint, advanced);
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
    friendlySyntax.textProperty().addListener((observable, oldValue, newValue) -> updateFriendlySyntax());
    pattern.textProperty().addListener((observable, oldValue, newValue) -> updateSelected());
    parameters.textProperty().addListener((observable, oldValue, newValue) -> updateSelected());
    action.valueProperty().addListener((observable, oldValue, newValue) -> {
      if (!updating) {
        List<String> expected = ACTION_PARAMETERS.get(newValue);
        parameters.setText(expected == null ? "" : String.join(", ", expected));
      }
      updateParameterHint();
      updateSelected();
    });
  }

  private void newDefinition() {
    file = null;
    languageName.setText("my-language");
    rules.clear();
    rules.add(RULE_TEMPLATES.get(0).create(uniqueRuleName(RULE_TEMPLATES.get(0).name)));
    rules.add(RULE_TEMPLATES.get(1).create(uniqueRuleName(RULE_TEMPLATES.get(1).name)));
    ruleList.getSelectionModel().selectFirst();
    status.setText("New language definition");
    refreshPreview();
  }

  private void addRule() {
    Rule rule = new Rule("rule-" + (rules.size() + 1), "${expression:expression};", "(?<expression>[^;\\n]+);",
            "expression", "expression", "An expression ending with a semicolon");
    int selectedIndex = ruleList.getSelectionModel().getSelectedIndex();
    int insertion = selectedIndex < 0 ? rules.size() : selectedIndex + 1;
    rules.add(insertion, rule);
    ruleList.getSelectionModel().select(rule);
  }

  private void addTemplate(int templateIndex, int insertion) {
    if (templateIndex < 0 || templateIndex >= RULE_TEMPLATES.size()) return;
    RuleTemplate template = RULE_TEMPLATES.get(templateIndex);
    Rule rule = template.create(uniqueRuleName(template.name));
    rules.add(Math.max(0, Math.min(insertion, rules.size())), rule);
    ruleList.getSelectionModel().select(rule);
    refreshPreview();
  }

  private boolean handleRuleDrop(String value, int destination) {
    if (value == null) return false;
    try {
      if (value.startsWith(TEMPLATE_DRAG)) {
        addTemplate(Integer.parseInt(value.substring(TEMPLATE_DRAG.length())), destination);
        return true;
      }
      if (value.startsWith(RULE_DRAG)) {
        int source = Integer.parseInt(value.substring(RULE_DRAG.length()));
        if (source < 0 || source >= rules.size()) return false;
        Rule rule = rules.remove(source);
        if (source < destination) destination--;
        rules.add(Math.max(0, Math.min(destination, rules.size())), rule);
        ruleList.getSelectionModel().select(rule);
        refreshPreview();
        return true;
      }
    } catch (NumberFormatException ignored) {
      return false;
    }
    return false;
  }

  private String uniqueRuleName(String base) {
    String candidate = base;
    int suffix = 2;
    while (hasRuleName(candidate)) candidate = base + "-" + suffix++;
    return candidate;
  }

  private boolean hasRuleName(String name) {
    for (Rule rule : rules) if (rule.name.equals(name)) return true;
    return false;
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
    friendlySyntax.setDisable(disabled);
    pattern.setDisable(disabled);
    action.setDisable(disabled);
    parameters.setDisable(disabled);
    ruleName.setText(disabled ? "" : rule.name);
    friendlySyntax.setText(disabled ? "" : rule.syntax);
    pattern.setText(disabled ? "" : rule.pattern);
    action.setValue(disabled ? null : rule.action);
    parameters.setText(disabled ? "" : rule.parameters);
    syntaxSummary.setText(disabled ? "Select a rule" : rule.description);
    updating = false;
    updateParameterHint();
  }

  private void updateSelected() {
    if (updating || selected == null) return;
    selected.name = ruleName.getText();
    selected.pattern = pattern.getText();
    selected.action = action.getValue();
    selected.parameters = parameters.getText();
    selected.description = describe(selected.pattern, selected.action);
    syntaxSummary.setText(selected.description);
    ruleList.refresh();
    refreshPreview();
  }

  private void updateFriendlySyntax() {
    if (updating || selected == null) return;
    selected.syntax = friendlySyntax.getText();
    try {
      selected.pattern = friendlySyntaxToPattern(selected.syntax);
      pattern.setText(selected.pattern);
      selected.description = friendlyDescription(selected.syntax);
      syntaxSummary.setText(selected.description);
      status.setText("Syntax updated");
    } catch (IllegalArgumentException exception) {
      status.setText(exception.getMessage());
    }
    refreshPreview();
  }

  private void updateParameterHint() {
    List<String> expected = ACTION_PARAMETERS.get(action.getValue());
    parameterHint.setText(expected == null || expected.isEmpty()
            ? "This action does not consume captures."
            : "Expected bindings: " + String.join(", ", expected));
  }

  private ListCell<String> behaviourCell() {
    return new ListCell<>() {
      @Override protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : behaviourName(item));
      }
    };
  }

  private static String behaviourName(String action) {
    if (action == null) return "";
    return switch (action) {
      case "ignore" -> "Ignore / comment";
      case "function" -> "Define a function";
      case "ifStatement" -> "Start an if statement";
      case "elseIf" -> "Add an else-if branch";
      case "else" -> "Add an else branch";
      case "whileLoop" -> "Loop while true";
      case "untilLoop" -> "Loop until true";
      case "forLoop" -> "Counted for loop";
      case "eachLoop" -> "Loop over values";
      case "repeatLoop" -> "Repeat a number of times";
      case "repeatForever" -> "Repeat forever";
      case "blockEnd" -> "End the current body";
      case "assignment" -> "Assign a value";
      case "print" -> "Print values";
      case "returnValue" -> "Return one value";
      case "returnValues" -> "Return several values";
      case "breakStatement" -> "Break out of a loop";
      case "assertion" -> "Assert a condition";
      case "importLibrary" -> "Import a library";
      case "unset" -> "Unset a value";
      case "breakpoint" -> "Pause at a breakpoint";
      case "expression" -> "Evaluate an expression";
      default -> action;
    };
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
      String patternText = value(map, "pattern");
      String actionName = value(map, "action");
      String syntaxText = value(map, "syntax");
      rules.add(new Rule(value(map, "name"), syntaxText, patternText, actionName,
              parameters(map.get("parameters")), describe(patternText, actionName)));
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
      chooser.setInitialFileName(languageName.getText() + ".zenlang");
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
      json.append("    { \"name\": \"").append(escape(rule.name)).append("\"");
      if (rule.syntax != null && !rule.syntax.isBlank()) {
        json.append(", \"syntax\": \"").append(escape(rule.syntax)).append("\"");
      }
      json.append(", \"pattern\": \"")
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

  private static String describe(String pattern, String action) {
    for (RuleTemplate template : RULE_TEMPLATES) {
      if (template.pattern.equals(pattern) && template.action.equals(action)) return template.description;
    }
    return "Custom pattern. Open Advanced pattern to edit it.";
  }

  private static String friendlyDescription(String syntax) {
    return syntax == null || syntax.isBlank() ? "Custom pattern. Open Advanced pattern to edit it."
            : "Matches " + syntax;
  }

  private static String friendlySyntaxToPattern(String syntax) {
    if (syntax == null || syntax.isBlank()) throw new IllegalArgumentException("Enter the syntax for this rule.");
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < syntax.length();) {
      if (syntax.charAt(index) == '$' && index + 1 < syntax.length() && syntax.charAt(index + 1) == '{') {
        int end = syntax.indexOf('}', index + 2);
        if (end < 0) throw new IllegalArgumentException("A syntax value is missing its closing }.");
        String[] value = syntax.substring(index + 2, end).trim().split(":", 2);
        if (value.length != 2 || !value[0].matches("[A-Za-z_][A-Za-z0-9_]*")) {
          throw new IllegalArgumentException("Use values in the form ${name:type}.");
        }
        result.append("(?<").append(value[0]).append(">").append(capturePattern(value[1].trim())).append(')');
        index = end + 1;
        continue;
      }
      char current = syntax.charAt(index);
      if (Character.isWhitespace(current)) {
        while (index < syntax.length() && Character.isWhitespace(syntax.charAt(index))) index++;
        result.append("\\s+");
        continue;
      }
      if ("\\.^$|?*+()[]{}".indexOf(current) >= 0) result.append('\\');
      result.append(current);
      index++;
    }
    return result.toString();
  }

  private static String capturePattern(String type) {
    return switch (type.toLowerCase()) {
      case "identifier" -> "[A-Za-z_][A-Za-z0-9_]*";
      case "variable" -> "\\$?[A-Za-z_][A-Za-z0-9_]*";
      case "number" -> "[0-9]+(?:\\.[0-9]+)?";
      case "parameters" -> "[^)\\n]*";
      case "values", "assignment" -> "[^;\\n]+";
      case "expression", "text" -> ".+?";
      case "string" -> "(?:\"[^\"\\n]*\"|'[^'\\n]*')";
      default -> throw new IllegalArgumentException("Unknown syntax value type '" + type + "'.");
    };
  }

  private static List<RuleTemplate> templates() {
    return List.of(
            new RuleTemplate("comment", "Comment", "//${text:text}", "//[^\\n]*", "ignore", "",
                    "A // comment running to the end of the line"),
            new RuleTemplate("print", "Print", "print ${values:values};", "print\\s+(?<values>[^;\\n]+);", "print", "values",
                    "print followed by one or more values and a semicolon"),
            new RuleTemplate("assignment", "Assignment", "${assignment:assignment};",
                    "(?<assignment>\\$[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=)\\s*[^;\\n]+);",
                    "assignment", "assignment", "A variable assignment ending with a semicolon"),
            new RuleTemplate("function", "Function", "function ${name:identifier}(${parameters:parameters}) {",
                    "function\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\((?<parameters>[^)\\n]*)\\)\\s*{",
                    "function", "name, parameters, body", "function name(parameters) followed by a body"),
            new RuleTemplate("if", "If", "if (${condition:expression}) {", "if\\s*\\((?<condition>[^)\\n]+)\\)\\s*{",
                    "ifStatement", "condition, body, elseIfs, else", "if (condition) followed by a body"),
            new RuleTemplate("else-if", "Else if", "else if (${condition:expression}) {", "else\\s+if\\s*\\((?<condition>[^)\\n]+)\\)\\s*{",
                    "elseIf", "condition, body", "else if (condition) followed by a body"),
            new RuleTemplate("else", "Else", "else {", "else\\s*{", "else", "body",
                    "else followed by a body"),
            new RuleTemplate("while", "While", "while (${condition:expression}) {", "while\\s*\\((?<condition>[^)\\n]+)\\)\\s*{",
                    "whileLoop", "condition, body", "while (condition) followed by a body"),
            new RuleTemplate("for", "For loop", "for (${initialiser:assignment};${condition:expression};${increment:expression}) {",
                    "for\\s*\\((?<initialiser>[^;\\n]+);(?<condition>[^;\\n]+);(?<increment>[^)\\n]+)\\)\\s*{",
                    "forLoop", "initialiser, condition, increment, body", "A C-style for loop followed by a body"),
            new RuleTemplate("foreach", "For each", "foreach (${iterable:expression} as ${variable:variable}) {",
                    "foreach\\s*\\((?<iterable>.+?)\\s+as\\s+(?<variable>\\$[A-Za-z_][A-Za-z0-9_]*)\\)\\s*{",
                    "eachLoop", "iterable, variable, body", "foreach (collection as variable) followed by a body"),
            new RuleTemplate("return", "Return", "return ${value:expression};", "return\\s+(?<value>[^;\\n]+);",
                    "returnValue", "value", "return followed by a value and a semicolon"),
            new RuleTemplate("return-many", "Return values", "return ${values:values};",
                    "return\\s+(?<values>[^;\\n]+);", "returnValues", "values",
                    "return multiple comma-separated values"),
            new RuleTemplate("break", "Break", "break;", "break;", "breakStatement", "",
                    "The break statement"),
            new RuleTemplate("repeat", "Repeat", "repeat ${count:expression} times {",
                    "repeat\\s+(?<count>.+?)\\s+times\\s*{", "repeatLoop", "count, body",
                    "repeat a body a calculated number of times"),
            new RuleTemplate("forever", "Repeat forever", "forever {", "forever\\s*{",
                    "repeatForever", "body", "repeat a body until it breaks or returns"),
            new RuleTemplate("until", "Until", "until (${condition:expression}) {",
                    "until\\s*\\((?<condition>[^)\\n]+)\\)\\s*{", "untilLoop", "condition, body",
                    "repeat a body until its condition becomes true"),
            new RuleTemplate("assert", "Assert", "assert ${condition:expression};",
                    "assert\\s+(?<condition>[^;\\n]+);", "assertion", "condition",
                    "stop execution when a condition is false"),
            new RuleTemplate("import", "Import", "import ${library:string};",
                    "import\\s+(?<library>(?:\"[^\"\\n]*\"|'[^'\\n]*'));", "importLibrary", "library",
                    "import a ZPE library by name"),
            new RuleTemplate("unset", "Unset", "unset ${target:expression};",
                    "unset\\s+(?<target>[^;\\n]+);", "unset", "target",
                    "remove a variable or indexed value"),
            new RuleTemplate("breakpoint", "Breakpoint", "breakpoint;", "breakpoint;",
                    "breakpoint", "", "pause at a debugger breakpoint"),
            new RuleTemplate("block-end", "End body", "}", "}", "blockEnd", "",
                    "A closing brace that ends the current body"),
            new RuleTemplate("expression", "Expression", "${expression:expression};", "(?<expression>[^;\\n]+);",
                    "expression", "expression", "Any expression ending with a semicolon")
    );
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
    actions.put("returnValues", List.of("values"));
    actions.put("breakStatement", List.of());
    actions.put("repeatLoop", List.of("count", "body"));
    actions.put("repeatForever", List.of("body"));
    actions.put("untilLoop", List.of("condition", "body"));
    actions.put("assertion", List.of("condition"));
    actions.put("importLibrary", List.of("library"));
    actions.put("unset", List.of("target"));
    actions.put("breakpoint", List.of());
    actions.put("expression", List.of("expression"));
    return actions;
  }

  private static final class Rule {
    private String name;
    private String syntax;
    private String pattern;
    private String action;
    private String parameters;
    private String description;

    private Rule(String name, String syntax, String pattern, String action, String parameters, String description) {
      this.name = name;
      this.syntax = syntax;
      this.pattern = pattern;
      this.action = action;
      this.parameters = parameters;
      this.description = description;
    }
  }

  private static final class RuleTemplate {
    private final String name;
    private final String label;
    private final String syntax;
    private final String pattern;
    private final String action;
    private final String parameters;
    private final String description;

    private RuleTemplate(String name, String label, String syntax, String pattern, String action,
                         String parameters, String description) {
      this.name = name;
      this.label = label;
      this.syntax = syntax;
      this.pattern = pattern;
      this.action = action;
      this.parameters = parameters;
      this.description = description;
    }

    private Rule create(String ruleName) {
      return new Rule(ruleName, syntax, pattern, action, parameters, description);
    }
  }
}
