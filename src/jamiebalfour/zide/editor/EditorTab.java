package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zpe.core.YASSDiagnostic;
import jamiebalfour.zpe.core.ZPEKit;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A JavaFX-native document tab.  The previous Swing editor remains available
 * to compatibility-only integrations elsewhere in ZIDE, but is never mounted
 * by this class.
 */
public class EditorTab extends Tab {
  private static final int ANALYSIS_DELAY_MS = 400;
  private static final ExecutorService ANALYSER = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "zide-syntax-analyser");
    thread.setDaemon(true);
    return thread;
  });

  private final ZIDEEditor owner;
  private final String path;
  private final CodeEditorViewFX editor;
  private final PauseTransition analysisTimer = new PauseTransition(Duration.millis(ANALYSIS_DELAY_MS));
  private final AtomicInteger analysisVersion = new AtomicInteger();
  private final Set<Integer> breakpointLines = new HashSet<>();
  private final HBox errorIndicator = new HBox(4);
  private final Label errorCountLabel = new Label();
  private final HBox warningIndicator = new HBox(4);
  private final Label warningCountLabel = new Label();
  private final HBox diagnosticOverlay = new HBox(7);
  private boolean changes;

  public EditorTab(ZIDEEditor owner, String title, String path, CodeEditorViewFX editor, Node content) {
    super(title, content);
    this.owner = owner;
    this.path = path;
    this.editor = editor;
    editor.setLineNumberClickListener(this::toggleSpecialLine);

    Label errorIcon = new Label("❗");
    errorIcon.getStyleClass().add("editor-error-icon");
    errorCountLabel.getStyleClass().add("editor-error-count");
    errorIndicator.getStyleClass().add("editor-error-indicator");
    errorIndicator.setAlignment(Pos.CENTER);
    errorIndicator.getChildren().addAll(errorIcon, errorCountLabel);
    errorIndicator.setVisible(false);
    errorIndicator.setManaged(false);
    errorIndicator.setOnMouseClicked(e -> owner.showProblemsPane());

    Label warningIcon = new Label("⚠");
    warningIcon.getStyleClass().add("editor-warning-icon");
    warningCountLabel.getStyleClass().add("editor-warning-count");
    warningIndicator.getStyleClass().add("editor-warning-indicator");
    warningIndicator.setAlignment(Pos.CENTER);
    warningIndicator.getChildren().addAll(warningIcon, warningCountLabel);
    warningIndicator.setVisible(false);
    warningIndicator.setManaged(false);
    warningIndicator.setOnMouseClicked(e -> owner.showProblemsPane());

    diagnosticOverlay.getChildren().addAll(errorIndicator, warningIndicator);
    diagnosticOverlay.getStyleClass().add("editor-diagnostic-overlay");
    diagnosticOverlay.setAlignment(Pos.CENTER_RIGHT);
    diagnosticOverlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    StackPane editorContainer = new StackPane(content, diagnosticOverlay);
    StackPane.setAlignment(diagnosticOverlay, Pos.TOP_RIGHT);
    StackPane.setMargin(diagnosticOverlay, new Insets(10, 18, 0, 0));
    setContent(editorContainer);

    analysisTimer.setOnFinished(e -> analyseCurrentSource());
    editor.getEditor().textProperty().addListener((observable, oldText, newText) -> {
      changes = true;
      scheduleAnalysis();
    });
    scheduleAnalysis();
  }

  void scheduleAnalysis() {
    if (path != null && !path.toLowerCase().endsWith(".yas")) return;
    analysisVersion.incrementAndGet();
    analysisTimer.playFromStart();
  }

  private void analyseCurrentSource() {
    final int version = analysisVersion.get();
    final String source = editor.getText();
    ANALYSER.submit(() -> {
      List<YASSDiagnostic> diagnostics = ZPEKit.analyseCode(source, 0, source.length());
      if (version != analysisVersion.get()) return;
      Platform.runLater(() -> {
        if (version == analysisVersion.get() && source.equals(editor.getText())) {
          owner.updateProblems(this, diagnostics);
        }
      });
    });
  }

  void dispose() { analysisVersion.incrementAndGet(); analysisTimer.stop(); }
  void setDiagnosticCounts(int errors, int warnings) {
    errorCountLabel.setText(String.valueOf(errors));
    errorIndicator.setVisible(errors > 0);
    errorIndicator.setManaged(errors > 0);
    warningCountLabel.setText(String.valueOf(warnings));
    warningIndicator.setVisible(warnings > 0);
    warningIndicator.setManaged(warnings > 0);
  }

  public String getPath() { return path; }
  public CodeEditorViewFX getEditor() { return editor; }
  public boolean hasSpecialLine(int line) { return breakpointLines.contains(line); }
  public void toggleSpecialLine(int line) { if (!breakpointLines.add(line)) breakpointLines.remove(line); }
  void switchOnDarkMode() { editor.setDarkMode(true); }
  void switchOffDarkMode() { editor.setDarkMode(false); }
  void setHasChanges(boolean hasChanges) { changes = hasChanges; }
  boolean hasChanges() { return changes; }
}
