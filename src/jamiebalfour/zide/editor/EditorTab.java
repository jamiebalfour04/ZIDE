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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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
  private static final int INFORMATION_DELAY_MS = 500;
  private static final ExecutorService ANALYSER = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "zide-syntax-analyser");
    thread.setDaemon(true);
    return thread;
  });

  private final ZIDEEditor owner;
  private String path;
  private final CodeEditorViewFX editor;
  private final PauseTransition analysisTimer = new PauseTransition(Duration.millis(ANALYSIS_DELAY_MS));
  private final PauseTransition symbolTimer = new PauseTransition(Duration.millis(250));
  private final PauseTransition infoTimer = new PauseTransition(Duration.millis(INFORMATION_DELAY_MS));
  private final PauseTransition infoHideTimer = new PauseTransition(Duration.millis(350));
  private final Popup infoPopup = new Popup();
  private final AtomicInteger analysisVersion = new AtomicInteger();
  private final Set<Integer> breakpointLines = new HashSet<>();
  private final HBox errorIndicator = new HBox(4);
  private final Label errorCountLabel = new Label();
  private final HBox warningIndicator = new HBox(4);
  private final Label warningCountLabel = new Label();
  private final HBox diagnosticOverlay = new HBox(7);
  private Label tabTitleLabel;
  private boolean changes;
  private String languageId;
  private String currentInfoToken;
  private boolean pointerOverInfoPopup;

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
    symbolTimer.setOnFinished(e -> owner.refreshDocumentSymbols(this));
    editor.getEditor().textProperty().addListener((observable, oldText, newText) -> {
      changes = true;
      scheduleAnalysis();
      symbolTimer.playFromStart();
    });
    symbolTimer.playFromStart();
    scheduleAnalysis();
  }

  private void installInformationPopup() {
    infoPopup.setAutoFix(true);
    infoPopup.setAutoHide(true);
    infoPopup.setHideOnEscape(true);
    infoHideTimer.setOnFinished(event -> {
      if (!pointerOverInfoPopup) hideInformation();
    });
    infoPopup.setOnHidden(event -> {
      currentInfoToken = null;
      pointerOverInfoPopup = false;
    });

    editor.getEditor().addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
      infoHideTimer.stop();
      int offset = editor.getEditor().hit(event.getX(), event.getY()).getInsertionIndex();
      String token = tokenAt(editor.getText(), offset);
      ZIDEEditor.EditorInfo information = owner.editorInfo(languageId, path, editor.getText(), token);
      infoTimer.stop();
      if (information == null) {
        scheduleInformationHide();
        return;
      }
      if (token.equals(currentInfoToken) && infoPopup.isShowing()) return;
      double screenX = event.getScreenX();
      double screenY = event.getScreenY();
      infoTimer.setOnFinished(ignored -> {
        if (!pointerStillOverToken(token)) return;
        if (infoPopup.isShowing()) infoPopup.hide();
        currentInfoToken = token;
        showInformation(information, screenX, screenY);
      });
      infoTimer.playFromStart();
    });
    editor.getEditor().addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
      infoTimer.stop();
      scheduleInformationHide();
    });
    editor.getEditor().addEventHandler(KeyEvent.KEY_PRESSED, event -> hideInformation());
  }

  private void showInformation(ZIDEEditor.EditorInfo information, double screenX, double screenY) {
    TextFlow title = createSignature(information.title);
    TextFlow body = createDescription(information.body);
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
    java.net.URL stylesheet = getClass().getResource("/zide.css");
    if (stylesheet != null) card.getStylesheets().add(stylesheet.toExternalForm());
    card.setOnMouseEntered(event -> {
      pointerOverInfoPopup = true;
      infoTimer.stop();
      infoHideTimer.stop();
    });
    card.setOnMouseExited(event -> {
      pointerOverInfoPopup = false;
      scheduleInformationHide();
    });
    card.setPrefWidth(560);
    card.setMaxWidth(600);
    infoPopup.getContent().setAll(card);
    infoPopup.show(editor.getEditor(), screenX + 12, screenY + 18);
  }

  private void hideInformation() {
    infoTimer.stop();
    infoHideTimer.stop();
    infoPopup.hide();
    currentInfoToken = null;
    pointerOverInfoPopup = false;
  }

  private void scheduleInformationHide() {
    if (!pointerOverInfoPopup) infoHideTimer.playFromStart();
  }

  private boolean pointerStillOverToken(String expectedToken) {
    javafx.geometry.Point2D screen = new javafx.scene.robot.Robot().getMousePosition();
    if (infoPopup.isShowing()
            && screen.getX() >= infoPopup.getX()
            && screen.getX() <= infoPopup.getX() + infoPopup.getWidth()
            && screen.getY() >= infoPopup.getY()
            && screen.getY() <= infoPopup.getY() + infoPopup.getHeight()) {
      return false;
    }
    javafx.geometry.Point2D local = editor.getEditor().screenToLocal(screen);
    if (!editor.getEditor().getBoundsInLocal().contains(local)) return false;
    int offset = editor.getEditor().hit(local.getX(), local.getY()).getInsertionIndex();
    return expectedToken.equals(tokenAt(editor.getText(), offset));
  }

  private TextFlow createSignature(String signature) {
    TextFlow flow = new TextFlow();
    flow.getStyleClass().add("editor-info-title");
    String value = signature == null ? "" : signature.replaceAll("\\s+\\(", "(");
    int functionEnd = value.indexOf('(');
    if (functionEnd < 0) functionEnd = value.indexOf(' ');
    if (functionEnd < 0) functionEnd = value.length();

    addSignatureText(flow, value.substring(0, functionEnd), "editor-info-function");
    int position = functionEnd;
    while (position < value.length()) {
      int typeStart = value.indexOf('{', position);
      if (typeStart < 0) {
        addSignatureText(flow, value.substring(position), "editor-info-signature-text");
        break;
      }
      addSignatureText(flow, value.substring(position, typeStart), "editor-info-signature-text");
      int typeEnd = value.indexOf('}', typeStart + 1);
      if (typeEnd < 0) {
        addSignatureText(flow, value.substring(typeStart + 1), "editor-info-type");
        break;
      }
      addSignatureText(flow, value.substring(typeStart + 1, typeEnd), "editor-info-type");
      position = typeEnd + 1;
      int comma = value.indexOf(',', position);
      int close = value.indexOf(')', position);
      int parameterEnd = comma < 0 ? close : close < 0 ? comma : Math.min(comma, close);
      if (parameterEnd > position && !value.substring(position, parameterEnd).trim().isEmpty()) {
        addSignatureText(flow, value.substring(position, parameterEnd), "editor-info-parameter");
        position = parameterEnd;
      }
    }
    return flow;
  }

  private TextFlow createDescription(String description) {
    TextFlow flow = new TextFlow();
    flow.getStyleClass().add("editor-info-body");
    String value = description == null ? "" : description;
    int position = 0;
    while (position < value.length()) {
      int markerStart = value.indexOf("__", position);
      if (markerStart < 0) {
        addSignatureText(flow, value.substring(position), "editor-info-body-text");
        break;
      }
      addSignatureText(flow, value.substring(position, markerStart), "editor-info-body-text");
      int markerEnd = value.indexOf("__", markerStart + 2);
      if (markerEnd < 0) {
        addSignatureText(flow, value.substring(markerStart + 2), "editor-info-body-text");
        break;
      }
      addSignatureText(flow, value.substring(markerStart + 2, markerEnd), "editor-info-body-parameter");
      position = markerEnd + 2;
    }
    return flow;
  }

  private void addSignatureText(TextFlow flow, String value, String styleClass) {
    if (value.isEmpty()) return;
    Text text = new Text(value);
    text.getStyleClass().add(styleClass);
    flow.getChildren().add(text);
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
    if (!"yass".equals(languageId) && !"python".equals(languageId)) {
      analysisVersion.incrementAndGet();
      analysisTimer.stop();
      owner.updateProblems(this, List.of());
      return;
    }
    analysisVersion.incrementAndGet();
    analysisTimer.playFromStart();
  }

  private void analyseCurrentSource() {
    final int version = analysisVersion.get();
    final String source = editor.getText();
    ANALYSER.submit(() -> {
      List<YASSDiagnostic> diagnostics = owner.analyseCodeForTab(this, source);
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
  void setTabTitleLabel(Label label) { tabTitleLabel = label; }
  void setDisplayTitle(String title) {
    setText("");
    if (tabTitleLabel != null) tabTitleLabel.setText(title);
  }
  String getDisplayTitle() {
    return tabTitleLabel == null ? getText() : tabTitleLabel.getText();
  }
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
