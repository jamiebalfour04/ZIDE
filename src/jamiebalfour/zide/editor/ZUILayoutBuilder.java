package jamiebalfour.zide.editor;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JavaFX visual designer for ZUI constraint layouts. */
public final class ZUILayoutBuilder {
  private enum Kind {
    LABEL("Label", 120, 26), BUTTON("Button", 110, 34), IMAGE("Image", 160, 120), TEXT_FIELD("Text field", 180, 30),
    TEXT_AREA("Text area", 220, 90), CHECKBOX("Checkbox", 150, 26), LIST("List", 200, 110),
    TOGGLE("Toggle", 60, 28), TABS("Tabs", 260, 140), BAR_CHART("Bar chart", 280, 170),
    PIE_CHART("Pie chart", 280, 170);

    final String title;
    final double width;
    final double height;
    Kind(String title, double width, double height) { this.title = title; this.width = width; this.height = height; }
  }

  private static final class Item {
    final Kind kind;
    final StackPane shell;
    final Region resizeHandle;
    String name;
    String text;
    String event;
    HandlerOption handler;
    Item(Kind kind, StackPane shell, Region resizeHandle, String name) {
      this.kind = kind; this.shell = shell; this.resizeHandle = resizeHandle;
      this.name = name; this.text = kind.title;
    }
  }

  private static final class HandlerOption {
    final String label;
    final String reference;
    HandlerOption(String label, String reference) { this.label = label; this.reference = reference; }
    @Override public String toString() { return label; }
  }

  private final BorderPane root = new BorderPane();
  private final Window owner;
  private final Pane canvas = new Pane();
  private final TextArea generatedCode = new TextArea();
  private final TextField variableField = new TextField();
  private final TextField textField = new TextField();
  private final Spinner<Integer> xField = spinner(0, 2000, 0);
  private final Spinner<Integer> yField = spinner(0, 2000, 0);
  private final Spinner<Integer> widthField = spinner(20, 2000, 120);
  private final Spinner<Integer> heightField = spinner(20, 2000, 30);
  private final ComboBox<String> eventField = new ComboBox<>();
  private final ComboBox<HandlerOption> handlerField = new ComboBox<>();
  private final List<HandlerOption> handlers = new ArrayList<>();
  private final List<Item> items = new ArrayList<>();
  private final Path file;
  private final Runnable savedAction;
  private final Runnable closeAction;
  private Item selected;
  private boolean updating;
  private int nextId = 1;

  public ZUILayoutBuilder(Window owner, Path file, String source, Runnable savedAction, Runnable closeAction) {
    this.owner = owner;
    this.file = file;
    this.savedAction = savedAction;
    this.closeAction = closeAction;
    root.getStyleClass().add("layout-builder");
    root.setTop(new VBox(buildMenuBar(), buildToolbar()));
    root.setCenter(buildWorkspace());
    root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)
          && selected != null && isFocusInsideCanvas()) {
        deleteSelected();
        event.consume();
      }
    });
    root.setMinSize(760, 520);
    discoverHandlers();
    bindProperties();
    if (source != null && !source.isBlank()) loadSource(source); else updateCode();
    if (!Files.exists(file)) save();
  }

  public Node getView() { return root; }

  private MenuBar buildMenuBar() {
    Menu edit = new Menu("Edit");
    MenuItem undo = editItem("Undo", KeyCode.Z, false, control -> control.undo());
    MenuItem redo = editItem("Redo", KeyCode.Z, true, control -> control.redo());
    MenuItem cut = editItem("Cut", KeyCode.X, false, TextInputControl::cut);
    MenuItem copy = editItem("Copy", KeyCode.C, false, TextInputControl::copy);
    MenuItem paste = editItem("Paste", KeyCode.V, false, TextInputControl::paste);
    MenuItem selectAll = editItem("Select All", KeyCode.A, false, TextInputControl::selectAll);
    edit.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste,
        new SeparatorMenuItem(), selectAll);
    edit.setOnShowing(e -> {
      TextInputControl control = focusedTextInput();
      boolean absent = control == null;
      undo.setDisable(absent || !control.isUndoable());
      redo.setDisable(absent || !control.isRedoable());
      cut.setDisable(absent || !control.isEditable() || control.getSelectedText().isEmpty());
      copy.setDisable(absent || control.getSelectedText().isEmpty());
      paste.setDisable(absent || !control.isEditable() || !Clipboard.getSystemClipboard().hasString());
      selectAll.setDisable(absent || control.getLength() == 0);
    });
    return new MenuBar(edit);
  }

  private MenuItem editItem(String title, KeyCode key, boolean shift,
                            java.util.function.Consumer<TextInputControl> action) {
    MenuItem item = new MenuItem(title);
    item.setAccelerator(shift
        ? new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
        : new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN));
    item.setOnAction(e -> {
      TextInputControl control = focusedTextInput();
      if (control != null) action.accept(control);
    });
    return item;
  }

  private TextInputControl focusedTextInput() {
    if (root.getScene() == null) return null;
    Node focus = root.getScene().getFocusOwner();
    return focus instanceof TextInputControl ? (TextInputControl) focus : null;
  }

  private boolean isFocusInsideCanvas() {
    if (root.getScene() == null) return false;
    Node focus = root.getScene().getFocusOwner();
    while (focus != null) {
      if (focus == canvas) return true;
      focus = focus.getParent();
    }
    return false;
  }

  private ToolBar buildToolbar() {
    Label heading = new Label("ZUI Layout Builder");
    heading.setStyle("-fx-font-weight: bold;");
    Button insert = new Button("Save layout");
    insert.setOnAction(e -> save());
    Button copy = new Button("Copy code");
    copy.setOnAction(e -> {
      ClipboardContent content = new ClipboardContent();
      content.putString(generatedCode.getText());
      Clipboard.getSystemClipboard().setContent(content);
    });
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button close = new Button("Close");
    close.setOnAction(e -> { if (closeAction != null) closeAction.run(); });
    return new ToolBar(heading, new Separator(), insert, copy, spacer, close);
  }

  private void save() {
    try {
      Files.writeString(file, generatedCode.getText(), StandardCharsets.UTF_8);
      if (savedAction != null) savedAction.run();
    } catch (IOException exception) {
      Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
      alert.initOwner(owner);
      alert.setHeaderText("The layout could not be saved.");
      alert.showAndWait();
    }
  }

  private Node buildWorkspace() {
    BorderPane design = new BorderPane();
    design.setLeft(buildPalette());
    design.setCenter(buildCanvas());
    TabPane inspector = new TabPane();
    inspector.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    inspector.getStyleClass().add("layout-builder-inspector");
    inspector.getTabs().add(new Tab("Properties", buildProperties()));
    generatedCode.setEditable(false);
    generatedCode.setStyle("-fx-font-family: 'JetBrains Mono', monospace;");
    inspector.getTabs().add(new Tab("YASS", generatedCode));
    SplitPane workspace = new SplitPane(design, inspector);
    workspace.setDividerPositions(0.76);
    return workspace;
  }

  private Node buildPalette() {
    VBox palette = new VBox(7);
    palette.setPadding(new Insets(10, 7, 10, 7));
    palette.setAlignment(Pos.TOP_CENTER);
    for (Kind kind : Kind.values()) {
      Button button = new Button();
      button.getStyleClass().add("layout-builder-tool-button");
      button.setGraphic(paletteIcon(kind));
      button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      button.setTooltip(new Tooltip(kind.title));
      button.setAccessibleText(kind.title);
      button.setPrefSize(42, 38);
      button.setMinSize(42, 38);
      button.setMaxSize(42, 38);
      button.setOnAction(e -> addItem(kind));
      palette.getChildren().add(button);
    }
    ScrollPane scroll = new ScrollPane(palette);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setPrefWidth(60);
    scroll.setMinWidth(60);
    scroll.setMaxWidth(60);
    return scroll;
  }

  private static Node paletteIcon(Kind kind) {
    Pane icon = new Pane();
    icon.setMinSize(28, 28);
    icon.setPrefSize(28, 28);
    Color ink = Color.web("#52606D");
    switch (kind) {
      case LABEL: {
        Label glyph = new Label("T");
        glyph.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #52606D;");
        glyph.relocate(8, 1);
        icon.getChildren().add(glyph);
        break;
      }
      case BUTTON: {
        Rectangle box = outline(3, 6, 22, 16, ink); box.setArcWidth(6); box.setArcHeight(6);
        icon.getChildren().addAll(box, colouredLine(9, 14, 19, 14, ink));
        break;
      }
      case IMAGE: {
        Rectangle frame = outline(3, 4, 22, 20, ink);
        Circle sun = new Circle(19, 9, 2.5, ink);
        Line hillOne = colouredLine(6, 20, 12, 13, ink);
        Line hillTwo = colouredLine(12, 13, 23, 21, ink);
        icon.getChildren().addAll(frame, sun, hillOne, hillTwo);
        break;
      }
      case TEXT_FIELD: {
        icon.getChildren().addAll(outline(2, 7, 24, 15, ink), colouredLine(7, 18, 20, 18, ink));
        break;
      }
      case TEXT_AREA: {
        icon.getChildren().add(outline(3, 3, 22, 22, ink));
        for (int y = 9; y <= 19; y += 5) icon.getChildren().add(colouredLine(7, y, 21, y, ink));
        break;
      }
      case CHECKBOX: {
        icon.getChildren().addAll(outline(3, 7, 14, 14, ink), colouredLine(6, 14, 9, 18, ink), colouredLine(9, 18, 15, 10, ink));
        break;
      }
      case LIST: {
        for (int y = 7; y <= 21; y += 7) {
          Circle bullet = new Circle(5, y, 1.7, ink);
          icon.getChildren().addAll(bullet, colouredLine(10, y, 24, y, ink));
        }
        break;
      }
      case TOGGLE: {
        Rectangle track = outline(3, 7, 23, 14, ink); track.setArcWidth(14); track.setArcHeight(14);
        icon.getChildren().addAll(track, new Circle(19, 14, 5, ink));
        break;
      }
      case TABS: {
        icon.getChildren().addAll(outline(3, 8, 22, 17, ink), outline(4, 3, 9, 7, ink), outline(13, 3, 9, 7, ink));
        break;
      }
      case BAR_CHART: {
        Rectangle one = filledRectangle(4, 15, 5, 10, ink);
        Rectangle two = filledRectangle(12, 8, 5, 17, ink);
        Rectangle three = filledRectangle(20, 12, 5, 13, ink);
        icon.getChildren().addAll(one, two, three);
        break;
      }
      case PIE_CHART: {
        Arc pie = new Arc(14, 14, 10, 10, 0, 275); pie.setType(ArcType.ROUND); pie.setFill(ink);
        Arc slice = new Arc(14, 14, 10, 10, 282, 70); slice.setType(ArcType.ROUND); slice.setFill(Color.web("#26734D"));
        icon.getChildren().addAll(pie, slice);
        break;
      }
    }
    return icon;
  }

  private static Rectangle outline(double x, double y, double width, double height, Color colour) {
    Rectangle shape = new Rectangle(x, y, width, height);
    shape.setFill(Color.TRANSPARENT);
    shape.setStroke(colour);
    shape.setStrokeWidth(1.6);
    return shape;
  }

  private static Rectangle filledRectangle(double x, double y, double width, double height, Color colour) {
    Rectangle shape = new Rectangle(x, y, width, height);
    shape.setFill(colour);
    return shape;
  }

  private static Line colouredLine(double startX, double startY, double endX, double endY, Color colour) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(colour);
    line.setStrokeWidth(1.7);
    return line;
  }

  private Node buildCanvas() {
    canvas.setPrefSize(640, 560);
    canvas.setMinSize(640, 560);
    canvas.setFocusTraversable(true);
    canvas.getStyleClass().add("layout-builder-canvas");
    canvas.setOnMousePressed(e -> {
      if (e.getTarget() == canvas) {
        select(null);
        canvas.requestFocus();
      }
    });
    StackPane surround = new StackPane(canvas);
    surround.setPadding(new Insets(22));
    surround.getStyleClass().add("layout-builder-canvas-surround");
    ScrollPane scroll = new ScrollPane(surround);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    return scroll;
  }

  private Node buildProperties() {
    GridPane grid = new GridPane();
    grid.setPadding(new Insets(14));
    grid.setHgap(10); grid.setVgap(10);
    ColumnConstraints label = new ColumnConstraints();
    label.setMinWidth(72);
    label.setPrefWidth(72);
    ColumnConstraints field = new ColumnConstraints();
    field.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(label, field);
    addProperty(grid, 0, "Variable", variableField);
    addProperty(grid, 1, "Text", textField);
    addProperty(grid, 2, "X", xField);
    addProperty(grid, 3, "Y", yField);
    addProperty(grid, 4, "Width", widthField);
    addProperty(grid, 5, "Height", heightField);
    eventField.setMaxWidth(Double.MAX_VALUE);
    handlerField.setMaxWidth(Double.MAX_VALUE);
    handlerField.setPromptText("Choose a function");
    addProperty(grid, 6, "Event", eventField);
    addProperty(grid, 7, "Handler", handlerField);
    setPropertiesEnabled(false);
    return grid;
  }

  private static void addProperty(GridPane grid, int row, String label, Node field) {
    grid.add(new Label(label), 0, row);
    grid.add(field, 1, row);
    GridPane.setHgrow(field, Priority.ALWAYS);
  }

  private static Spinner<Integer> spinner(int minimum, int maximum, int value) {
    Spinner<Integer> spinner = new Spinner<>(minimum, maximum, value);
    spinner.setEditable(true);
    spinner.setMaxWidth(Double.MAX_VALUE);
    return spinner;
  }

  private void discoverHandlers() {
    handlers.add(new HandlerOption("None", null));
    handlerField.getItems().setAll(handlers);
    Path script = companionScript();
    if (script == null || !Files.isRegularFile(script)) return;
    try {
      String source = Files.readString(script, StandardCharsets.UTF_8);
      Pattern moduleStart = Pattern.compile("^\\s*module\\s+([A-Za-z_][A-Za-z0-9_]*)\\b", Pattern.CASE_INSENSITIVE);
      Pattern objectStart = Pattern.compile("^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*(?:class|structure|interface)\\s+", Pattern.CASE_INSENSITIVE);
      Pattern functionStart = Pattern.compile("^\\s*(?:(?:public|private|protected|static)\\s+)*function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(", Pattern.CASE_INSENSITIVE);
      String module = null;
      int objectDepth = 0;
      int functionDepth = 0;
      boolean inBlockComment = false;
      for (String rawLine : source.split("\\R", -1)) {
        String line = rawLine;
        if (inBlockComment) {
          int end = line.indexOf("*/");
          if (end < 0) continue;
          line = line.substring(end + 2);
          inBlockComment = false;
        }
        int blockStart = line.indexOf("/*");
        if (blockStart >= 0) {
          int blockEnd = line.indexOf("*/", blockStart + 2);
          if (blockEnd < 0) {
            line = line.substring(0, blockStart);
            inBlockComment = true;
          } else {
            line = line.substring(0, blockStart) + line.substring(blockEnd + 2);
          }
        }
        int comment = line.indexOf("//");
        if (comment >= 0) line = line.substring(0, comment);
        String trimmed = line.trim();
        if (trimmed.matches("(?i)^end\\s+(class|structure|interface)\\b.*")) {
          objectDepth = Math.max(0, objectDepth - 1);
          continue;
        }
        if (trimmed.matches("(?i)^end\\s+function\\b.*")) {
          functionDepth = Math.max(0, functionDepth - 1);
          continue;
        }
        if (trimmed.matches("(?i)^end\\s+module\\b.*")) { module = null; continue; }
        Matcher moduleDeclaration = moduleStart.matcher(line);
        if (moduleDeclaration.find() && objectDepth == 0 && functionDepth == 0) {
          module = moduleDeclaration.group(1);
          continue;
        }
        if (objectStart.matcher(line).find()) {
          objectDepth++;
          continue;
        }
        Matcher function = functionStart.matcher(line);
        if (function.find()) {
          if (objectDepth == 0 && functionDepth == 0) {
            String name = function.group(1);
            String qualifiedName = module == null ? name : module + "::" + name;
            handlers.add(new HandlerOption(qualifiedName, "&" + qualifiedName));
          }
          functionDepth++;
        }
      }
      handlerField.getItems().setAll(handlers);
    } catch (IOException ignored) {
      // The builder remains usable when the companion script is unavailable.
    }
  }

  private Path companionScript() {
    String name = file.getFileName().toString();
    if (!name.toLowerCase().endsWith(".ui.yas")) return null;
    return file.resolveSibling(name.substring(0, name.length() - 7) + ".yas");
  }

  private static List<String> eventsFor(Kind kind) {
    switch (kind) {
      case BUTTON: return List.of("UI::Button::Action.click", "UI::Button::Action.mouseover", "UI::Button::Action.mouseout");
      case IMAGE: return List.of("UI::Button::Action.click", "UI::Button::Action.mouseover", "UI::Button::Action.mouseout");
      case TEXT_FIELD:
      case TEXT_AREA: return List.of("UI::Control::Action.keypress");
      case LIST: return List.of("UI::List::Action.change");
      case CHECKBOX:
      case TOGGLE: return List.of("UI::Toggle::Action.change");
      case TABS: return List.of("UI::TabContainer::Action.change");
      default: return List.of();
    }
  }

  private void addItem(Kind kind) {
    Node preview = createPreview(kind);
    preview.getStyleClass().addAll("zui-native-preview", "zui-native-" + kind.name().toLowerCase().replace('_', '-'));
    if (preview instanceof Region) ((Region) preview).setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    Region resizeHandle = new Region();
    resizeHandle.getStyleClass().add("layout-builder-resize-handle");
    resizeHandle.setMinSize(10, 10);
    resizeHandle.setPrefSize(10, 10);
    resizeHandle.setMaxSize(10, 10);
    resizeHandle.setCursor(javafx.scene.Cursor.SE_RESIZE);
    StackPane.setAlignment(resizeHandle, Pos.BOTTOM_RIGHT);
    StackPane shell = new StackPane(preview, resizeHandle);
    if (preview instanceof ImageView) {
      ((ImageView) preview).fitWidthProperty().bind(shell.widthProperty().subtract(4));
      ((ImageView) preview).fitHeightProperty().bind(shell.heightProperty().subtract(4));
    }
    shell.getStyleClass().add("layout-builder-control");
    shell.setFocusTraversable(true);
    shell.setAlignment(Pos.CENTER);
    shell.setPrefSize(kind.width, kind.height);
    shell.resize(kind.width, kind.height);
    double offset = 24 + (items.size() * 18) % 180;
    shell.relocate(offset, offset);
    Item item = new Item(kind, shell, resizeHandle, variableBase(kind) + nextId++);
    ContextMenu menu = new ContextMenu();
    MenuItem delete = new MenuItem("Delete");
    delete.setOnAction(e -> { select(item); deleteSelected(); });
    menu.getItems().add(delete);
    shell.setOnContextMenuRequested(e -> {
      select(item);
      menu.show(shell, e.getScreenX(), e.getScreenY());
      e.consume();
    });
    installDrag(item);
    installResize(item);
    items.add(item);
    canvas.getChildren().add(shell);
    select(item);
    updateCode();
  }

  private void loadSource(String source) {
    Pattern itemPattern = Pattern.compile(
        "\\$(\\w+)\\s*=\\s*\\$layout->add\\((.*?),\\s*\\[\\s*\\\"left\\\"\\s*=>\\s*(\\d+)\\s*,\\s*\\\"top\\\"\\s*=>\\s*(\\d+)\\s*,\\s*\\\"width\\\"\\s*=>\\s*(\\d+)\\s*,\\s*\\\"height\\\"\\s*=>\\s*(\\d+)\\s*\\]\\)",
        Pattern.DOTALL);
    Matcher matcher = itemPattern.matcher(source);
    while (matcher.find()) {
      Kind kind = kindForFactory(matcher.group(2));
      if (kind == null) continue;
      addItem(kind);
      Item item = items.get(items.size() - 1);
      item.name = matcher.group(1);
      item.text = textForFactory(kind, matcher.group(2));
      item.shell.relocate(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
      item.shell.setPrefSize(Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)));
      item.shell.resize(Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)));
      updatePreviewText(item);
    }
    Pattern bindingPattern = Pattern.compile("\\$(\\w+)\\s*->\\s*on\\((UI::[A-Za-z0-9_:]+),\\s*&([A-Za-z_][A-Za-z0-9_]*(?:::[A-Za-z_][A-Za-z0-9_]*)*)\\s*\\)");
    Matcher binding = bindingPattern.matcher(source);
    while (binding.find()) {
      Item item = items.stream().filter(candidate -> candidate.name.equals(binding.group(1))).findFirst().orElse(null);
      if (item == null) continue;
      item.event = binding.group(2);
      String reference = "&" + binding.group(3);
      item.handler = handlers.stream().filter(option -> Objects.equals(option.reference, reference)).findFirst().orElse(null);
      if (item.handler == null && !binding.group(3).contains("::")) {
        List<HandlerOption> moduleMatches = handlers.stream()
            .filter(option -> option.reference != null && option.reference.endsWith("::" + binding.group(3)))
            .toList();
        if (moduleMatches.size() == 1) item.handler = moduleMatches.get(0);
      }
      if (item.handler == null) item.handler = new HandlerOption(binding.group(3), reference);
      if (!handlerField.getItems().contains(item.handler)) handlerField.getItems().add(item.handler);
    }
    if (items.isEmpty()) updateCode(); else { select(items.get(items.size() - 1)); updateCode(); }
  }

  private static Kind kindForFactory(String factory) {
    if (factory.contains("->label(")) return Kind.LABEL;
    if (factory.contains("->button(")) return Kind.BUTTON;
    if (factory.contains("->image(")) return Kind.IMAGE;
    if (factory.contains("->textField(")) return Kind.TEXT_FIELD;
    if (factory.contains("->textArea(")) return Kind.TEXT_AREA;
    if (factory.contains("->checkbox(")) return Kind.CHECKBOX;
    if (factory.contains("->listView(")) return Kind.LIST;
    if (factory.contains("->toggle(")) return Kind.TOGGLE;
    if (factory.contains("->tabContainer(")) return Kind.TABS;
    if (factory.contains("Chart::Type.pie")) return Kind.PIE_CHART;
    if (factory.contains("Chart::Type.bar")) return Kind.BAR_CHART;
    return null;
  }

  private static String textForFactory(Kind kind, String factory) {
    if (kind != Kind.LABEL && kind != Kind.BUTTON && kind != Kind.IMAGE && kind != Kind.CHECKBOX) return kind.title;
    Matcher quoted = Pattern.compile("\\(\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(factory);
    if (!quoted.find()) return kind.title;
    return quoted.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private Node createPreview(Kind kind) {
    switch (kind) {
      case LABEL: return new Label("Label");
      case BUTTON: return new Button("Button");
      case IMAGE: ImageView image = new ImageView(); image.setPreserveRatio(true); image.setMouseTransparent(true); return image;
      case TEXT_FIELD: TextField input = new TextField("Text field"); input.setMouseTransparent(true); return input;
      case TEXT_AREA: TextArea area = new TextArea("Text area"); area.setMouseTransparent(true); return area;
      case CHECKBOX: return new CheckBox("Checkbox");
      case LIST: ListView<String> list = new ListView<>(); list.getItems().addAll("First item", "Second item"); list.setMouseTransparent(true); return list;
      case TOGGLE: return new ToggleButton("On");
      case TABS: TabPane tabs = new TabPane(new Tab("Tab", new Pane())); tabs.setMouseTransparent(true); return tabs;
      case BAR_CHART: return chartPreview(false);
      case PIE_CHART: return chartPreview(true);
      default: throw new IllegalArgumentException(kind.name());
    }
  }

  private Node chartPreview(boolean pie) {
    Pane chart = new Pane();
    chart.setMouseTransparent(true);
    Label heading = new Label(pie ? "Pie chart" : "Bar chart");
    heading.relocate(8, 5);
    chart.getChildren().add(heading);
    if (pie) {
      Arc first = new Arc(140, 92, 58, 58, 0, 235); first.setType(ArcType.ROUND); first.setFill(Color.web("#26734D"));
      Arc second = new Arc(140, 92, 58, 58, 235, 125); second.setType(ArcType.ROUND); second.setFill(Color.web("#3367D6"));
      chart.getChildren().addAll(first, second);
    } else {
      chart.getChildren().addAll(bar(35, 94, 44), bar(95, 55, 83), bar(155, 75, 63));
    }
    return chart;
  }

  private static Rectangle bar(double x, double y, double height) {
    Rectangle bar = new Rectangle(x, y, 40, height);
    bar.setFill(Color.web("#26734D"));
    return bar;
  }

  private void installDrag(Item item) {
    final double[] drag = new double[4];
    item.shell.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
      select(item);
      item.shell.requestFocus();
      if (e.getTarget() == item.resizeHandle) return;
      if (!e.isPrimaryButtonDown()) return;
      drag[0] = e.getSceneX(); drag[1] = e.getSceneY(); drag[2] = item.shell.getLayoutX(); drag[3] = item.shell.getLayoutY(); e.consume();
    });
    item.shell.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
      item.shell.relocate(Math.max(0, drag[2] + e.getSceneX() - drag[0]), Math.max(0, drag[3] + e.getSceneY() - drag[1]));
      loadSelection(); updateCode(); e.consume();
    });
  }

  private void installResize(Item item) {
    final double[] resize = new double[4];
    item.resizeHandle.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
      if (!e.isPrimaryButtonDown()) return;
      select(item);
      item.shell.requestFocus();
      resize[0] = e.getSceneX(); resize[1] = e.getSceneY();
      resize[2] = item.shell.getWidth(); resize[3] = item.shell.getHeight();
      e.consume();
    });
    item.resizeHandle.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
      double width = Math.max(20, resize[2] + e.getSceneX() - resize[0]);
      double height = Math.max(20, resize[3] + e.getSceneY() - resize[1]);
      item.shell.setPrefSize(width, height);
      item.shell.resize(width, height);
      loadSelection();
      updateCode();
      e.consume();
    });
  }

  private void select(Item item) {
    if (selected != null) {
      selected.shell.setStyle("");
      selected.resizeHandle.setVisible(false);
    }
    selected = item;
    if (selected != null) {
      selected.shell.setStyle("-fx-border-color: #26734D; -fx-border-width: 2;");
      selected.resizeHandle.setVisible(true);
    }
    setPropertiesEnabled(item != null);
    loadSelection();
  }

  private void loadSelection() {
    if (selected == null) return;
    updating = true;
    variableField.setText(selected.name); textField.setText(selected.text);
    xField.getValueFactory().setValue((int) Math.round(selected.shell.getLayoutX()));
    yField.getValueFactory().setValue((int) Math.round(selected.shell.getLayoutY()));
    widthField.getValueFactory().setValue((int) Math.round(selected.shell.getWidth()));
    heightField.getValueFactory().setValue((int) Math.round(selected.shell.getHeight()));
    eventField.getItems().setAll(eventsFor(selected.kind));
    eventField.setValue(selected.event == null && !eventField.getItems().isEmpty() ? eventField.getItems().get(0) : selected.event);
    handlerField.setValue(selected.handler);
    handlerField.setDisable(eventField.getItems().isEmpty());
    updating = false;
  }

  private void bindProperties() {
    variableField.textProperty().addListener((o, oldValue, newValue) -> applyProperties());
    textField.textProperty().addListener((o, oldValue, newValue) -> applyProperties());
    variableField.focusedProperty().addListener((o, was, focused) -> { if (!focused) applyProperties(); });
    textField.focusedProperty().addListener((o, was, focused) -> { if (!focused) applyProperties(); });
    ChangeListener<Integer> bounds = (o, oldValue, newValue) -> applyProperties();
    xField.valueProperty().addListener(bounds); yField.valueProperty().addListener(bounds);
    widthField.valueProperty().addListener(bounds); heightField.valueProperty().addListener(bounds);
    eventField.valueProperty().addListener((o, oldValue, newValue) -> applyProperties());
    handlerField.valueProperty().addListener((o, oldValue, newValue) -> applyProperties());
  }

  private void applyProperties() {
    if (updating || selected == null) return;
    selected.name = sanitiseName(variableField.getText()); selected.text = textField.getText();
    selected.shell.relocate(xField.getValue(), yField.getValue());
    selected.shell.setPrefSize(widthField.getValue(), heightField.getValue());
    selected.shell.resize(widthField.getValue(), heightField.getValue());
    selected.event = eventField.getValue(); selected.handler = handlerField.getValue();
    updatePreviewText(selected); updateCode();
  }

  private static void updatePreviewText(Item item) {
    Node child = item.shell.getChildren().get(0);
    if (child instanceof Labeled) ((Labeled) child).setText(item.text);
    if (child instanceof ImageView) {
      try {
        Path source = Path.of(item.text).toAbsolutePath().normalize();
        ((ImageView) child).setImage(Files.isRegularFile(source)
            ? new javafx.scene.image.Image(source.toUri().toString()) : null);
      } catch (Exception ignored) {
        ((ImageView) child).setImage(null);
      }
    }
  }

  private void deleteSelected() {
    if (selected == null) return;
    canvas.getChildren().remove(selected.shell); items.remove(selected); selected = null;
    setPropertiesEnabled(false); updateCode();
  }

  private void setPropertiesEnabled(boolean enabled) {
    variableField.setDisable(!enabled); textField.setDisable(!enabled); xField.setDisable(!enabled);
    yField.setDisable(!enabled); widthField.setDisable(!enabled); heightField.setDisable(!enabled);
    eventField.setDisable(!enabled); handlerField.setDisable(!enabled || eventField.getItems().isEmpty());
  }

  private void updateCode() {
    StringBuilder out = new StringBuilder("$ui = new UI()\n$window = $ui->window(\"My ZUI App\", 640, 560)\n")
        .append("$window->enable_default_keybindings()\n$layout = $window->layout()\n\n");
    for (Item item : items) {
      out.append('$').append(item.name).append(" = $layout->add(").append(factory(item)).append(", [\n")
          .append("  \"left\" => ").append((int) Math.round(item.shell.getLayoutX()))
          .append(", \"top\" => ").append((int) Math.round(item.shell.getLayoutY())).append(",\n")
          .append("  \"width\" => ").append((int) Math.round(item.shell.getWidth()))
          .append(", \"height\" => ").append((int) Math.round(item.shell.getHeight())).append("\n])\n\n");
    }
    for (Item item : items) {
      if (item.event != null && item.handler != null && item.handler.reference != null) {
        out.append('$').append(item.name).append("->on(").append(item.event).append(", ")
            .append(item.handler.reference).append(")\n");
      }
    }
    if (items.stream().anyMatch(item -> item.event != null && item.handler != null && item.handler.reference != null)) out.append('\n');
    out.append("$window->show()\n$ui->run()\n");
    generatedCode.setText(out.toString());
    generatedCode.positionCaret(0);
  }

  private static String factory(Item item) {
    String value = item.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    switch (item.kind) {
      case LABEL: return "$window->label(\"" + value + "\")";
      case BUTTON: return "$window->button(\"" + value + "\")";
      case IMAGE: return "$window->image(\"" + value + "\")";
      case TEXT_FIELD: return "$window->textField()";
      case TEXT_AREA: return "$window->textArea()";
      case CHECKBOX: return "$window->checkbox(\"" + value + "\")";
      case LIST: return "$window->listView([\"First item\", \"Second item\"])";
      case TOGGLE: return "$window->toggle(true)";
      case TABS: return "$window->tabContainer()";
      case BAR_CHART: return "$window->chart(UI::Chart::Type.bar)";
      case PIE_CHART: return "$window->chart(UI::Chart::Type.pie)";
      default: throw new IllegalArgumentException(item.kind.name());
    }
  }

  private static String variableBase(Kind kind) { return kind.name().toLowerCase().replace("_", ""); }
  private static String sanitiseName(String value) {
    String clean = value == null ? "control" : value.replaceAll("[^A-Za-z0-9_]", "");
    if (clean.isEmpty()) clean = "control";
    if (Character.isDigit(clean.charAt(0))) clean = "control" + clean;
    return clean;
  }
}
