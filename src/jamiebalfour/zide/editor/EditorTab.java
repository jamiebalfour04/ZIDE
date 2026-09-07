package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zpe.core.YASSDiagnostic;
import jamiebalfour.zpe.core.ZPEKit;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
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
  private String path;
  private final CodeEditorViewFX editor;
  private final PauseTransition analysisTimer = new PauseTransition(Duration.millis(ANALYSIS_DELAY_MS));
  private final PauseTransition infoTimer = new PauseTransition(Duration.millis(450));
  private final Popup infoPopup = new Popup();
  private final AtomicInteger analysisVersion = new AtomicInteger();
  private final Set<Integer> breakpointLines = new HashSet<>();
  private final HBox errorIndicator = new HBox(4);
  private final Label errorCountLabel = new Label();
  private final HBox warningIndicator = new HBox(4);
  private final Label warningCountLabel = new Label();
  private final HBox diagnosticOverlay = new HBox(7);
  private boolean changes;
  private String languageId;

  public EditorTab(ZIDEEditor owner, String title, String path, CodeEditorViewFX editor, Node content) {
    super(title, content);
    this.owner = owner;
    this.path = path;
    this.editor = editor;
    editor.setLineNumberClickListener(this::toggleSpecialLine);
    installInformationPopup();

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

  private void installInformationPopup() {
    infoPopup.setAutoFix(true);
    infoPopup.setAutoHide(true);
    infoPopup.setHideOnEscape(true);

    editor.getEditor().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
      int offset = editor.getEditor().hit(event.getX(), event.getY()).getInsertionIndex();
      String token = tokenAt(editor.getText(), offset);
      ZIDEEditor.EditorInfo information = owner.editorInfo(languageId, path, token);
      infoTimer.stop();
      infoPopup.hide();
      if (information == null) return;
      double screenX = event.getScreenX();
      double screenY = event.getScreenY();
      infoTimer.setOnFinished(ignored -> showInformation(information, screenX, screenY));
      infoTimer.playFromStart();
    });
    editor.getEditor().addEventHandler(KeyEvent.KEY_PRESSED, event -> hideInformation());
  }

  private void showInformation(ZIDEEditor.EditorInfo information, double screenX, double screenY) {
    Label title = new Label(information.title);
    title.getStyleClass().add("editor-info-title");
    Label body = new Label(information.body);
    body.setWrapText(true);
    body.getStyleClass().add("editor-info-body");
    VBox card = new VBox(9, title, body);
    if (information.version != null) {
      Label version = new Label(information.version);
      version.getStyleClass().add("editor-info-meta");
      card.getChildren().add(version);
    }
    if (information.category != null) {
      Label category = new Label(information.category);
      category.getStyleClass().add("editor-info-meta");
      card.getChildren().add(category);
    }
    if (information.url != null) {
      Hyperlink more = new Hyperlink("More information online");
      more.getStyleClass().add("editor-info-link");
      more.setOnAction(event -> {
        try {
          jamiebalfour.helpers.HelperFunctions.openWebsite(information.url);
        } catch (Exception ignored) {
          // A documentation link should never interrupt editing.
        }
        infoPopup.hide();
      });
      card.getChildren().add(more);
    }
    card.getStyleClass().add("editor-info-popup");
    if (editor.isDarkMode()) card.getStyleClass().add("editor-info-popup-dark");
    card.setPrefWidth(500);
    card.setMaxWidth(520);
    infoPopup.getContent().setAll(card);
    infoPopup.show(editor.getEditor(), screenX + 12, screenY + 18);
  }

  private void hideInformation() {
    infoTimer.stop();
    infoPopup.hide();
  }

  private static String tokenAt(String text, int offset) {
    if (text == null || text.isEmpty()) return "";
    int position = Math.max(0, Math.min(offset, text.length() - 1));
    if (!isTokenCharacter(text.charAt(position)) && position > 0) position--;
    if (!isTokenCharacter(text.charAt(position))) return "";
    int start = position;
    int end = position + 1;
    while (start > 0 && isTokenCharacter(text.charAt(start - 1))) start--;
    while (end < text.length() && isTokenCharacter(text.charAt(end))) end++;
    return text.substring(start, end);
  }

  private static boolean isTokenCharacter(char character) {
    return Character.isLetterOrDigit(character) || character == '_' || character == ':';
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

  void showDiagnostics(List<YASSDiagnostic> diagnostics) {
    var area = editor.getEditor();
    int length = area.getLength();
    if (length == 0) return;

    int position = 0;
    for (var span : area.getStyleSpans(0, length)) {
      int end = position + span.getLength();
      area.setStyle(position, end, withoutDiagnosticStyle(span.getStyle()));
      position = end;
    }

    for (YASSDiagnostic diagnostic : diagnostics) {
      int start = diagnostic.getStartOffset();
      int end = diagnostic.getEndOffset();
      if (start < 0 || start >= length || end <= start) {
        int[] range = rangeAt(diagnostic.getLine(), diagnostic.getColumn());
        start = range[0];
        end = range[1];
      }
      start = Math.max(0, Math.min(start, length - 1));
      end = Math.max(start + 1, Math.min(end, length));
      addDiagnosticStyle(start, end, "ERROR".equals(diagnostic.getSeverity().toString()));
    }
  }

  private void addDiagnosticStyle(int start, int end, boolean error) {
    var area = editor.getEditor();
    String colour = error ? "#d93025" : "#d97706";
    int position = start;
    for (var span : area.getStyleSpans(start, end)) {
      int spanEnd = position + span.getLength();
      String base = withoutDiagnosticStyle(span.getStyle());
      area.setStyle(position, spanEnd, base + " -rtfx-underline-color: " + colour
              + "; -rtfx-underline-width: 1.4; -rtfx-underline-wave-radius: 1.5;");
      position = spanEnd;
    }
  }

  private static String withoutDiagnosticStyle(String style) {
    if (style == null) return "";
    return style.replaceAll("\\s*-rtfx-underline-(?:color|width|wave-radius)\\s*:[^;]+;?", "");
  }

  private int[] rangeAt(int line, int column) {
    String text = editor.getText();
    int offset = 0;
    int currentLine = 1;
    while (currentLine < Math.max(1, line) && offset < text.length()) {
      if (text.charAt(offset++) == '\n') currentLine++;
    }
    offset = Math.min(text.length(), offset + Math.max(0, column - 1));
    if (offset == text.length() && offset > 0) offset--;
    int start = offset;
    int end = Math.min(text.length(), offset + 1);
    if (offset < text.length() && isTokenCharacter(text.charAt(offset))) {
      while (start > 0 && isTokenCharacter(text.charAt(start - 1))) start--;
      while (end < text.length() && isTokenCharacter(text.charAt(end))) end++;
    }
    return new int[]{start, end};
  }

  void navigateToDiagnostic(int line, int column, int start, int end) {
    editor.goToLine(line);
    int length = editor.getText().length();
    if (start < 0 || start >= length || end <= start) {
      int[] range = rangeAt(line, column);
      start = range[0];
      end = range[1];
    }
    start = Math.max(0, Math.min(start, length));
    end = Math.max(start, Math.min(end, length));
    if (end > start) editor.getEditor().selectRange(start, end);
    editor.getEditor().requestFollowCaret();
    editor.requestFocus();
  }

  void dispose() { analysisVersion.incrementAndGet(); analysisTimer.stop(); hideInformation(); }
  void setDiagnosticCounts(int errors, int warnings) {
    errorCountLabel.setText(String.valueOf(errors));
    errorIndicator.setVisible(errors > 0);
    errorIndicator.setManaged(errors > 0);
    warningCountLabel.setText(String.valueOf(warnings));
    warningIndicator.setVisible(warnings > 0);
    warningIndicator.setManaged(warnings > 0);
  }

  public String getPath() { return path; }
  /** Updates the backing path after a project-tree rename or move. */
  void setPath(String path) {
    this.path = path;
    owner.applyLanguageForPath(this);
  }
  String getLanguageId() { return languageId; }
  void setLanguageId(String languageId) { this.languageId = languageId; }
  public CodeEditorViewFX getEditor() { return editor; }
  public boolean hasSpecialLine(int line) { return breakpointLines.contains(line); }
  public void toggleSpecialLine(int line) {
    if (!breakpointLines.add(line)) breakpointLines.remove(line);
  }
  void switchOnDarkMode() { editor.setDarkMode(true); }
  void switchOffDarkMode() { editor.setDarkMode(false); }
  void setHasChanges(boolean hasChanges) { changes = hasChanges; }
  boolean hasChanges() { return changes; }
}
