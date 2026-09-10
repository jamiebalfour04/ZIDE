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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JavaFX-native document tab.  The previous Swing editor remains available
 * to compatibility-only integrations elsewhere in ZIDE, but is never mounted
 * by this class.
 */
public class EditorTab extends Tab {
  private static final int ANALYSIS_DELAY_MS = 400;
  private static final int INFORMATION_DELAY_MS = 500;
  private static final Pattern MARKDOWN_INLINE = Pattern.compile(
          "(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)|(\\[([^]]+)]\\((https?://[^ )]+)\\))");
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
  private final PauseTransition markdownTimer = new PauseTransition(Duration.millis(180));
  private final Popup infoPopup = new Popup();
  private final AtomicInteger analysisVersion = new AtomicInteger();
  private final Set<Integer> breakpointLines = new HashSet<>();
  private final HBox errorIndicator = new HBox(4);
  private final Label errorCountLabel = new Label();
  private final HBox warningIndicator = new HBox(4);
  private final Label warningCountLabel = new Label();
  private final HBox diagnosticOverlay = new HBox(7);
  private final VBox editorTopRight = new VBox(6);
  private final VBox findReplacePanel = new VBox(6);
  private final TextField findField = new TextField();
  private final TextField replaceField = new TextField();
  private final CheckBox matchCase = new CheckBox("Match case");
  private final Label findResult = new Label();
  private final Node editorContent;
  private final StackPane editorContainer;
  private SplitPane markdownSplit;
  private ScrollPane markdownPreview;
  private VBox markdownPreviewContent;
  private Label tabTitleLabel;
  private boolean changes;
  private String lastDiskContent;
  private String languageId;
  private String currentInfoToken;
  private boolean pointerOverInfoPopup;
  private final List<DiagnosticHint> diagnosticHints = new ArrayList<>();
  private List<YASSDiagnostic> diagnostics = List.of();

  public EditorTab(ZIDEEditor owner, String title, String path, CodeEditorViewFX editor, Node content) {
    super(title, content);
    this.owner = owner;
    this.path = path;
    this.editor = editor;
    this.editorContent = content;
    this.lastDiskContent = editor.getText();
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
    diagnosticOverlay.setVisible(false);
    diagnosticOverlay.setManaged(false);

    buildFindReplacePanel();
    editorTopRight.getChildren().addAll(diagnosticOverlay, findReplacePanel);
    editorTopRight.setAlignment(Pos.TOP_RIGHT);
    editorTopRight.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    editorContainer = new StackPane(content, editorTopRight);
    StackPane.setAlignment(editorTopRight, Pos.TOP_RIGHT);
    StackPane.setMargin(editorTopRight, new Insets(10, 18, 0, 0));
    setContent(editorContainer);

    analysisTimer.setOnFinished(e -> analyseCurrentSource());
    markdownTimer.setOnFinished(e -> renderMarkdownPreview());
    symbolTimer.setOnFinished(e -> owner.refreshDocumentSymbols(this));
    editor.getEditor().textProperty().addListener((observable, oldText, newText) -> {
      changes = true;
      scheduleAnalysis();
      symbolTimer.playFromStart();
      if (markdownPreview != null) markdownTimer.playFromStart();
    });
    symbolTimer.playFromStart();
    scheduleAnalysis();
  }

  private void buildFindReplacePanel() {
    findField.setPromptText("Find");
    replaceField.setPromptText("Replace with");
    findField.setPrefColumnCount(18);
    replaceField.setPrefColumnCount(18);
    findResult.getStyleClass().add("editor-find-result");

    Button previous = new Button("Previous");
    Button next = new Button("Next");
    Button close = new Button("Close");
    Button replace = new Button("Replace");
    Button replaceAll = new Button("Replace all");
    previous.setOnAction(event -> find(false));
    next.setOnAction(event -> find(true));
    close.setOnAction(event -> hideFindReplace());
    replace.setOnAction(event -> replaceCurrent());
    replaceAll.setOnAction(event -> replaceAll());

    HBox findRow = new HBox(5, findField, previous, next, findResult, close);
    HBox replaceRow = new HBox(5, replaceField, replace, replaceAll, matchCase);
    findRow.setAlignment(Pos.CENTER_LEFT);
    replaceRow.setAlignment(Pos.CENTER_LEFT);
    findReplacePanel.getChildren().addAll(findRow, replaceRow);
    findReplacePanel.getStyleClass().add("editor-find-replace");
    findReplacePanel.setVisible(false);
    findReplacePanel.setManaged(false);
    findField.textProperty().addListener((observable, oldValue, newValue) -> updateFindResult());
    matchCase.selectedProperty().addListener((observable, oldValue, newValue) -> updateFindResult());
    findField.setOnAction(event -> find(true));
    replaceField.setOnAction(event -> replaceCurrent());
    findReplacePanel.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
        hideFindReplace();
        event.consume();
      }
    });
  }

  void showFindReplace(boolean focusReplacement) {
    findReplacePanel.setVisible(true);
    findReplacePanel.setManaged(true);
    String selected = editor.getEditor().getSelectedText();
    if (selected != null && !selected.isBlank() && !selected.contains("\n")) findField.setText(selected);
    Platform.runLater(() -> {
      TextField target = focusReplacement ? replaceField : findField;
      target.requestFocus();
      target.selectAll();
      updateFindResult();
    });
  }

  private void hideFindReplace() {
    findReplacePanel.setVisible(false);
    findReplacePanel.setManaged(false);
    editor.requestFocus();
  }

  private void find(boolean forwards) {
    String query = findField.getText();
    if (query == null || query.isEmpty()) return;
    String source = editor.getText();
    String haystack = matchCase.isSelected() ? source : source.toLowerCase(java.util.Locale.ROOT);
    String needle = matchCase.isSelected() ? query : query.toLowerCase(java.util.Locale.ROOT);
    int caret = editor.getEditor().getCaretPosition();
    int index = forwards ? haystack.indexOf(needle, caret) : haystack.lastIndexOf(needle, Math.max(0, caret - query.length() - 1));
    if (index < 0) index = forwards ? haystack.indexOf(needle) : haystack.lastIndexOf(needle);
    if (index < 0) { updateFindResult(); return; }
    editor.getEditor().selectRange(index, index + query.length());
    editor.getEditor().requestFollowCaret();
    updateFindResult();
  }

  private void replaceCurrent() {
    String query = findField.getText();
    if (query == null || query.isEmpty()) return;
    String selected = editor.getEditor().getSelectedText();
    boolean matches = matchCase.isSelected() ? query.equals(selected) : query.equalsIgnoreCase(selected);
    if (matches) {
      int start = editor.getEditor().getSelection().getStart();
      editor.getEditor().replaceText(start, editor.getEditor().getSelection().getEnd(), replaceField.getText());
    }
    find(true);
  }

  private void replaceAll() {
    String query = findField.getText();
    if (query == null || query.isEmpty()) return;
    String replacement = replaceField.getText();
    String source = editor.getText();
    String updated = matchCase.isSelected()
            ? source.replace(query, replacement)
            : Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    editor.setText(updated);
    updateFindResult();
  }

  private void updateFindResult() {
    String query = findField.getText();
    if (query == null || query.isEmpty()) { findResult.setText(""); return; }
    String source = matchCase.isSelected() ? editor.getText() : editor.getText().toLowerCase(java.util.Locale.ROOT);
    String needle = matchCase.isSelected() ? query : query.toLowerCase(java.util.Locale.ROOT);
    int count = 0;
    for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += Math.max(1, needle.length())) count++;
    findResult.setText(count + (count == 1 ? " match" : " matches"));
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
      HoverTarget target = hoverTargetAt(event.getScreenX(), event.getScreenY());
      infoTimer.stop();
      if (target == null) {
        scheduleInformationHide();
        return;
      }
      if (target.key.equals(currentInfoToken) && infoPopup.isShowing()) return;
      double screenX = event.getScreenX();
      double screenY = event.getScreenY();
      infoTimer.setOnFinished(ignored -> {
        if (!pointerStillOverTarget(target.key)) return;
        if (infoPopup.isShowing()) infoPopup.hide();
        currentInfoToken = target.key;
        showInformation(target.information, screenX, screenY);
      });
      infoTimer.playFromStart();
    });
    editor.getEditor().addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
      infoTimer.stop();
      scheduleInformationHide();
    });
    editor.getEditor().addEventHandler(MouseEvent.MOUSE_PRESSED, event -> hideInformation());
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

  private boolean pointerStillOverTarget(String expectedKey) {
    javafx.geometry.Point2D screen = new javafx.scene.robot.Robot().getMousePosition();
    if (infoPopup.isShowing()
            && screen.getX() >= infoPopup.getX()
            && screen.getX() <= infoPopup.getX() + infoPopup.getWidth()
            && screen.getY() >= infoPopup.getY()
            && screen.getY() <= infoPopup.getY() + infoPopup.getHeight()) {
      return true;
    }
    HoverTarget target = hoverTargetAt(screen.getX(), screen.getY());
    return target != null && expectedKey.equals(target.key);
  }

  /** Returns information only when the pointer is actually over a rendered character. */
  private HoverTarget hoverTargetAt(double screenX, double screenY) {
    javafx.geometry.Point2D local = editor.getEditor().screenToLocal(screenX, screenY);
    if (!editor.getEditor().getBoundsInLocal().contains(local)) return null;
    String source = editor.getText();
    if (source == null || source.isEmpty()) return null;
    int offset = Math.min(editor.getEditor().hit(local.getX(), local.getY()).getInsertionIndex(), source.length() - 1);
    if (!characterContainsScreenPoint(offset, screenX, screenY) &&
            (offset == 0 || !characterContainsScreenPoint(offset - 1, screenX, screenY))) return null;
    if (!characterContainsScreenPoint(offset, screenX, screenY)) offset--;

    for (DiagnosticHint diagnostic : diagnosticHints) {
      if (offset >= diagnostic.lineStart && offset < diagnostic.lineEnd) {
        return new HoverTarget("diagnostic:" + diagnostic.line + ":" + diagnostic.message,
                new ZIDEEditor.EditorInfo(diagnostic.severity + " on line " + diagnostic.line,
                        diagnostic.message));
      }
    }

    String token = tokenAt(source, offset);
    ZIDEEditor.EditorInfo information = owner.editorInfo(languageId, path, source, token, offset);
    return information == null ? null : new HoverTarget("token:" + token, information);
  }

  private boolean characterContainsScreenPoint(int offset, double screenX, double screenY) {
    if (offset < 0 || offset >= editor.getEditor().getLength()) return false;
    return editor.getEditor().getCharacterBoundsOnScreen(offset, offset + 1)
            .map(bounds -> bounds.contains(screenX, screenY)).orElse(false);
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
    return Character.isLetterOrDigit(character) || character == '_' || character == ':' || character == '$';
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
    this.diagnostics = List.copyOf(diagnostics);
    var area = editor.getEditor();
    int length = area.getLength();
    diagnosticHints.clear();
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
      int lineStart = start;
      int lineEnd = end;
      String source = editor.getText();
      while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
      while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
      diagnosticHints.add(new DiagnosticHint(lineStart, Math.max(lineStart + 1, lineEnd),
              diagnostic.getLine(), diagnostic.getSeverity().toString(), diagnostic.getMessage()));
    }
  }

  private static final class HoverTarget {
    final String key;
    final ZIDEEditor.EditorInfo information;
    HoverTarget(String key, ZIDEEditor.EditorInfo information) {
      this.key = key;
      this.information = information;
    }
  }

  private static final class DiagnosticHint {
    final int lineStart;
    final int lineEnd;
    final int line;
    final String severity;
    final String message;
    DiagnosticHint(int lineStart, int lineEnd, int line, String severity, String message) {
      this.lineStart = lineStart;
      this.lineEnd = lineEnd;
      this.line = line;
      this.severity = severity;
      this.message = message;
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
    diagnosticOverlay.setVisible(errors > 0 || warnings > 0);
    diagnosticOverlay.setManaged(errors > 0 || warnings > 0);
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
  void setLanguageId(String languageId) {
    this.languageId = languageId;
    setMarkdownPreviewEnabled("md".equals(languageId));
  }
  public CodeEditorViewFX getEditor() { return editor; }
  List<YASSDiagnostic> getDiagnostics() { return diagnostics; }
  public boolean hasSpecialLine(int line) { return breakpointLines.contains(line); }
  public void toggleSpecialLine(int line) {
    if (!breakpointLines.add(line)) breakpointLines.remove(line);
  }
  void switchOnDarkMode() {
    if (editor.isDarkMode()) return;
    editor.setDarkMode(true);
    renderMarkdownPreview();
  }
  void switchOffDarkMode() {
    if (!editor.isDarkMode()) return;
    editor.setDarkMode(false);
    renderMarkdownPreview();
  }
  void setHasChanges(boolean hasChanges) { changes = hasChanges; }
  boolean hasChanges() { return changes; }
  String getLastDiskContent() { return lastDiskContent; }
  void setLastDiskContent(String content) { lastDiskContent = content; }

  private void setMarkdownPreviewEnabled(boolean enabled) {
    if (enabled) {
      if (markdownPreview == null) {
        markdownPreviewContent = new VBox(8);
        markdownPreviewContent.getStyleClass().add("markdown-preview-content");
        markdownPreview = new ScrollPane(markdownPreviewContent);
        markdownPreview.setFitToWidth(true);
        markdownPreview.getStyleClass().add("markdown-preview");
        markdownSplit = new SplitPane();
        markdownSplit.setDividerPositions(0.52);
      }
      if (editorContainer.getChildren().get(0) != markdownSplit) {
        editorContainer.getChildren().remove(editorContent);
        markdownSplit.getItems().setAll(editorContent, markdownPreview);
        editorContainer.getChildren().add(0, markdownSplit);
        markdownSplit.setDividerPositions(0.52);
      }
      renderMarkdownPreview();
    } else if (!editorContainer.getChildren().isEmpty()
            && editorContainer.getChildren().get(0) != editorContent) {
      editorContainer.getChildren().remove(markdownSplit);
      markdownSplit.getItems().remove(editorContent);
      editorContainer.getChildren().add(0, editorContent);
    }
  }

  private void renderMarkdownPreview() {
    if (markdownPreview == null || !"md".equals(languageId)) return;
    markdownPreviewContent.getChildren().setAll(markdownNodes(editor.getText()));
  }

  /** Builds a lightweight preview without requiring the optional javafx.web module. */
  private static List<Node> markdownNodes(String markdown) {
    List<Node> nodes = new ArrayList<>();
    boolean codeBlock = false;
    StringBuilder code = new StringBuilder();
    for (String raw : (markdown == null ? "" : markdown).split("\\R", -1)) {
      if (raw.trim().startsWith("```")) {
        if (codeBlock) {
          nodes.add(markdownCodeBlock(code.toString()));
          code.setLength(0);
        }
        codeBlock = !codeBlock;
        continue;
      }
      if (codeBlock) { code.append(raw).append('\n'); continue; }
      if (raw.matches("^\\s*[-*]\\s+.*")) {
        String value = raw.replaceFirst("^\\s*[-*]\\s+", "");
        Text bullet = new Text("\u2022  ");
        bullet.getStyleClass().add("markdown-text");
        TextFlow item = inlineMarkdownFlow(value);
        HBox row = new HBox(bullet, item);
        row.getStyleClass().add("markdown-list-item");
        nodes.add(row);
        continue;
      }
      int heading = 0;
      while (heading < raw.length() && heading < 6 && raw.charAt(heading) == '#') heading++;
      if (heading > 0 && heading < raw.length() && raw.charAt(heading) == ' ') {
        TextFlow title = inlineMarkdownFlow(raw.substring(heading + 1));
        title.getStyleClass().add("markdown-heading-" + heading);
        nodes.add(title);
      } else if (raw.startsWith("> ")) {
        Region rule = new Region();
        rule.getStyleClass().add("markdown-quote-rule");
        HBox quote = new HBox(10, rule, inlineMarkdownFlow(raw.substring(2)));
        quote.getStyleClass().add("markdown-quote");
        nodes.add(quote);
      } else if (raw.isBlank()) {
        Region gap = new Region();
        gap.setMinHeight(4);
        nodes.add(gap);
      } else {
        TextFlow paragraph = inlineMarkdownFlow(raw);
        paragraph.getStyleClass().add("markdown-paragraph");
        nodes.add(paragraph);
      }
    }
    if (codeBlock && !code.isEmpty()) nodes.add(markdownCodeBlock(code.toString()));
    return nodes;
  }

  private static Label markdownCodeBlock(String value) {
    Label label = new Label(value.stripTrailing());
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    label.getStyleClass().add("markdown-code-block");
    return label;
  }

  private static TextFlow inlineMarkdownFlow(String value) {
    TextFlow flow = new TextFlow();
    flow.getStyleClass().add("markdown-flow");
    Matcher matcher = MARKDOWN_INLINE.matcher(value);
    int offset = 0;
    while (matcher.find()) {
      addMarkdownText(flow, value.substring(offset, matcher.start()), null);
      if (matcher.group(2) != null) {
        addMarkdownText(flow, matcher.group(2), "markdown-bold");
      } else if (matcher.group(4) != null) {
        addMarkdownText(flow, matcher.group(4), "markdown-inline-code");
      } else {
        Hyperlink link = new Hyperlink(matcher.group(6));
        String url = matcher.group(7);
        link.setOnAction(e -> {
          try {
            jamiebalfour.helpers.HelperFunctions.openWebsite(url);
          } catch (Exception ignored) {
            // A preview link must not interrupt editing when the desktop cannot open it.
          }
        });
        link.getStyleClass().add("markdown-link");
        flow.getChildren().add(link);
      }
      offset = matcher.end();
    }
    addMarkdownText(flow, value.substring(offset), null);
    return flow;
  }

  private static void addMarkdownText(TextFlow flow, String value, String styleClass) {
    if (value.isEmpty()) return;
    Text text = new Text(value);
    text.getStyleClass().add("markdown-text");
    if (styleClass != null) text.getStyleClass().add(styleClass);
    flow.getChildren().add(text);
  }
}
