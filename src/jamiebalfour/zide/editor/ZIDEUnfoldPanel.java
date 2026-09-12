package jamiebalfour.zide.editor;

import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.YASSUnfoldChunk;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Live descriptions tied to the exact source snapshot used for compilation. */
final class ZIDEUnfoldPanel extends VBox {
  private final VBox rows = new VBox(4);
  private final VBox staleOverlay = new VBox(12);
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "zide-unfold"); t.setDaemon(true); return t;
  });
  private final ChangeListener<String> edits = (o, before, after) -> {
    if (!Objects.equals(before, after)) invalidate();
  };
  private EditorTab tab;
  private long version;
  private int highlighted = -1;
  private String previousStyle;
  private boolean opened;

  private final java.util.function.BiConsumer<String, String> showDescription;

  ZIDEUnfoldPanel(java.util.function.BiConsumer<String, String> showDescription) {
    this.showDescription = showDescription;
    javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
    clip.widthProperty().bind(widthProperty());
    clip.heightProperty().bind(heightProperty());
    setClip(clip);
    getStyleClass().add("unfold-panel");
    ScrollPane scroll = new ScrollPane(rows);
    scroll.getStyleClass().add("code-editor-scroll-pane");
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    Label changed = new Label("The code has changed. Unfold needs to be regenerated.");
    changed.setWrapText(true);
    changed.setStyle("-fx-text-fill: white;");
    Hyperlink regenerate = new Hyperlink("Click here to regenerate.");
    regenerate.setWrapText(true);
    regenerate.setStyle("-fx-text-fill: #75c9ff;");
    regenerate.setOnAction(e -> refresh());
    staleOverlay.getChildren().addAll(changed, regenerate);
    staleOverlay.setPadding(new Insets(20));
    staleOverlay.setAlignment(javafx.geometry.Pos.CENTER);
    staleOverlay.setStyle("-fx-background-color: black;");
    staleOverlay.setVisible(false);
    staleOverlay.setManaged(false);
    StackPane body = new StackPane(scroll, staleOverlay);
    VBox.setVgrow(body, Priority.ALWAYS);
    rows.setPadding(new Insets(8));
    getChildren().add(body);
    setMinWidth(0); setPrefWidth(340); setMaxWidth(Double.MAX_VALUE);
    setVisible(false); setManaged(false);
    setOnMouseExited(e -> clearHighlight());
  }

  static boolean supports(EditorTab tab) {
    return tab != null && ("yass".equals(tab.getLanguageId()) || "zpeedy".equals(tab.getLanguageId()));
  }

  void toggle(EditorTab selected) {
    if (opened) close();
    else if (supports(selected)) {
      opened = true; setVisible(true); setManaged(true);
      follow(selected);
    }
  }

  boolean isOpen() { return opened; }

  void follow(EditorTab selected) {
    if (!opened) return;
    if (tab == selected) return;
    clearHighlight();
    if (tab != null) tab.getEditor().getEditor().textProperty().removeListener(edits);
    tab = selected;
    if (!supports(tab)) { close(); return; }
    tab.getEditor().getEditor().textProperty().addListener(edits);
    refresh();
  }

  void close() {
    if (!opened) return;
    opened = false; version++; clearHighlight();
    if (tab != null) tab.getEditor().getEditor().textProperty().removeListener(edits);
    tab = null;
    setVisible(false);
    setManaged(false);
  }

  private void invalidate() {
    version++; clearHighlight();
    rows.setDisable(true);
    staleOverlay.setManaged(true);
    staleOverlay.setVisible(true);
  }

  private void refresh() {
    if (!supports(tab)) return;
    version++; clearHighlight();
    rows.setDisable(false);
    staleOverlay.setVisible(false);
    staleOverlay.setManaged(false);
    rows.getChildren().setAll(new Label("Updating..."));
    EditorTab target = tab;
    String source = target.getEditor().getText();
    String language = target.getLanguageId();
    long request = version;
    worker.submit(() -> {
      try {
        List<YASSUnfoldChunk> chunks = "yass".equals(language)
                ? ZPEKit.unfoldStructured(source) : unfoldZpeedy(source);
        Platform.runLater(() -> {
          if (request != version || target != tab || !opened) return;
          rows.getChildren().clear();
          addRows(rows, chunks, source);
          if (rows.getChildren().isEmpty()) rows.getChildren().add(new Label("No executable code."));
        });
      } catch (Exception | LinkageError failure) {
        Platform.runLater(() -> {
          if (request != version || target != tab || !opened) return;
          Label error = new Label("Unable to unfold: " + failure.getMessage());
          error.setWrapText(true); rows.getChildren().setAll(error);
        });
      }
    });
  }

  private void addRows(VBox container, List<YASSUnfoldChunk> chunks, String source) {
    for (YASSUnfoldChunk chunk : chunks) {
      int offset = chunk.getProgramStart();
      boolean located = chunk.getProgramEnd() > offset && offset >= 0 && offset <= source.length();
      int line = located ? (int) source.substring(0, offset).chars().filter(c -> c == '\n').count() : -1;
      String prefix = located ? "Line " + (line + 1) + "  " : "";
      Label description = new Label(prefix + chunk.getLongDescription());
      description.setWrapText(true);
      description.setMaxWidth(Double.MAX_VALUE);
      description.setMinWidth(0);
      description.setPadding(new Insets(8));
      description.getStyleClass().add("unfold-description");
      if (!chunk.getShortDescription().equals(chunk.getLongDescription())) {
        MenuItem summary = new MenuItem("Show full description");
        summary.setOnAction(e -> {
          clearHighlight();
          showDescription.accept(prefix + chunk.getShortDescription(), chunk.getLongDescription());
        });
        ContextMenu menu = new ContextMenu(summary);
        menu.getStyleClass().add("glass-context-menu");
        description.setContextMenu(menu);
      }
      if (located) {
        description.setOnMouseEntered(e -> highlight(line));
        description.setOnMouseExited(e -> clearHighlight());
      }
      HBox header = new HBox(description);
      HBox.setHgrow(description, Priority.ALWAYS);
      VBox section = new VBox(header);
      section.setMinWidth(0);
      container.getChildren().add(section);
      List<YASSUnfoldChunk> children = chunk.getChildren();
      if (!children.isEmpty()) {
        VBox nested = new VBox(4);
        nested.setMinWidth(0);
        nested.getStyleClass().add("unfold-children");
        VBox.setMargin(nested, new Insets(0, 0, 0, 8));
        section.getChildren().add(nested);
        nested.setVisible(false);
        nested.setManaged(false);
        ToggleButton expand = new ToggleButton("\u25b8");
        expand.setTooltip(new Tooltip("Expand section"));
        expand.setMinSize(28, 28);
        expand.setPrefSize(28, 28);
        expand.setMaxSize(28, 28);
        expand.setFocusTraversable(false);
        HBox.setMargin(expand, new Insets(8, 4, 0, 0));
        header.getChildren().add(expand);
        expand.selectedProperty().addListener((o, wasExpanded, expanded) -> {
          clearHighlight();
          if (expanded && nested.getChildren().isEmpty()) addRows(nested, children, source);
          nested.setVisible(expanded);
          nested.setManaged(expanded);
          description.setText(prefix + (expanded ? chunk.getShortDescription() : chunk.getLongDescription()));
          expand.setText(expanded ? "\u25be" : "\u25b8");
          expand.getTooltip().setText(expanded ? "Collapse section" : "Expand section");
        });
      }
    }
  }

  private void highlight(int line) {
    clearHighlight();
    if (tab == null) return;
    var editor = tab.getEditor().getEditor();
    if (line < 0 || line >= editor.getParagraphs().size()) return;
    highlighted = line;
    previousStyle = editor.getParagraph(line).getParagraphStyle();
    // Paragraph styles belong to the TextFlow, not its individual text segments.
    editor.setParagraphStyle(line, (previousStyle == null ? "" : previousStyle)
            + ";-fx-background-color: rgba(0,122,204,0.28);");
    editor.showParagraphInViewport(line);
  }

  private void clearHighlight() {
    if (tab != null && highlighted >= 0) {
      var editor = tab.getEditor().getEditor();
      if (highlighted < editor.getParagraphs().size()) editor.setParagraphStyle(highlighted, previousStyle);
    }
    highlighted = -1;
  }

  private static List<YASSUnfoldChunk> unfoldZpeedy(String source) throws Exception {
    Path directory = Files.createTempDirectory("zide-unfold-");
    Path input = directory.resolve("source.zps"), output = directory.resolve("source.iast"), log = directory.resolve("compiler.log");
    Process process = null;
    try {
      Files.writeString(input, source);
      boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
      List<String> command = new ArrayList<>();
      if (windows) command.addAll(List.of("cmd.exe", "/c"));
      command.addAll(List.of("zpeedy", "--iast", input.toString(), output.toString()));
      process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
      if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("Zpeedy compilation timed out.");
      if (process.exitValue() != 0) throw new IOException(Files.readString(log));
      return ZPEKit.unfoldStructured(IAST.fromBytes(Files.readAllBytes(output)));
    } finally {
      if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
      Files.deleteIfExists(input); Files.deleteIfExists(output); Files.deleteIfExists(log); Files.deleteIfExists(directory);
    }
  }
}
