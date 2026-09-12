package jamiebalfour.zide.editor;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

final class ZIDEScratchPadPanel extends VBox {
  private static final String BOLD = "-fx-font-weight: bold;";
  private static final String ITALIC = "-fx-font-style: italic;";
  private static final String UNDERLINE = "-fx-underline: true;";
  private final InlineCssTextArea notes = new InlineCssTextArea();
  private final VirtualizedScrollPane<InlineCssTextArea> notesScroll = new VirtualizedScrollPane<>(notes);
  private final Label saveStatus = new Label("Saved");
  private final PauseTransition saveDelay = new PauseTransition(Duration.seconds(1.5));
  private final Consumer<String> reportStatus;
  private Path file;
  private boolean opened;
  private boolean loading;
  private String typingStyle = "";
  private int lastCaretPosition = -1;
  private boolean followsActiveFile = true;
  private Button boldButton;
  private Button italicButton;
  private Button underlineButton;
  private Button h1Button;
  private Button h2Button;
  private Button h3Button;

  ZIDEScratchPadPanel(Consumer<String> reportStatus) {
    this.reportStatus = reportStatus;
    getStyleClass().add("scratch-pad-panel");
    setMinWidth(0);
    setPrefWidth(0);
    setPrefWidth(340);
    setMaxWidth(Double.MAX_VALUE);
    setVisible(false);
    setManaged(false);

    boldButton = formatButton("B", "Bold", () -> applyStyle(BOLD));
    italicButton = formatButton("I", "Italic", () -> applyStyle(ITALIC));
    underlineButton = formatButton("U", "Underline", () -> applyStyle(UNDERLINE));
    h1Button = formatButton("H1", "Heading 1", () -> applyHeading(1));
    h2Button = formatButton("H2", "Heading 2", () -> applyHeading(2));
    h3Button = formatButton("H3", "Heading 3", () -> applyHeading(3));
    HBox formatting = new HBox(4, boldButton, italicButton, underlineButton, h1Button, h2Button, h3Button);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    formatting.getChildren().addAll(spacer, saveStatus);
    formatting.getStyleClass().add("scratch-pad-formatting");
    formatting.setPadding(new Insets(10, 8, 8, 8));

    notes.getStyleClass().add("scratch-pad-editor");
    notesScroll.getStyleClass().addAll("code-editor-scroll-pane", "scratch-pad-scroll-pane");
    notes.setWrapText(true);
    notes.setUseInitialStyleForInsertion(false);
    notes.setTextInsertionStyle("");
    notes.textProperty().addListener((observable, before, after) -> changed());
    notes.caretPositionProperty().addListener((observable, before, after) -> {
      if (loading || after.intValue() == lastCaretPosition) return;
      lastCaretPosition = after.intValue();
      int position = Math.min(after.intValue(), notes.getLength());
      typingStyle = notes.getLength() == 0 ? "" : notes.getStyleOfChar(Math.max(0, position - 1));
      notes.setTextInsertionStyle(typingStyle);
      refreshToolbarState();
    });
    notes.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    notes.addEventFilter(KeyEvent.KEY_RELEASED, event -> Platform.runLater(this::refreshToolbarState));
    notes.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, event -> Platform.runLater(this::refreshToolbarState));
    saveDelay.setOnFinished(event -> save());
    VBox.setVgrow(notesScroll, Priority.ALWAYS);
    VBox.setMargin(notesScroll, new Insets(0, 8, 8, 8));
    getChildren().addAll(formatting, notesScroll);
  }

  private Button formatButton(String text, String tooltip, Runnable action) {
    Button button = new Button(text);
    button.getStyleClass().add("scratch-pad-format-button");
    button.setTooltip(new Tooltip(tooltip));
    button.setFocusTraversable(false);
    button.setOnAction(event -> action.run());
    return button;
  }

  private void applyStyle(String css) {
    int start = notes.getSelection().getStart();
    int end = notes.getSelection().getEnd();
    String current = start == end ? typingStyle : notes.getStyleOfChar(start);
    String updated = toggleStyle(current, css);
    if (start == end) {
      typingStyle = updated;
      notes.setTextInsertionStyle(updated);
    } else {
      notes.setStyle(start, end, updated);
      typingStyle = updated;
      notes.setTextInsertionStyle(updated);
    }
    changed();
    refreshToolbarState();
    notes.requestFocus();
  }

  private static String toggleStyle(String current, String css) {
    return current.contains(css) ? current.replace(css, "") : current + " " + css;
  }

  private void handleShortcut(KeyEvent event) {
    if (!event.isShortcutDown()) return;
    String style = switch (event.getCode()) {
      case B -> BOLD;
      case I -> ITALIC;
      case U -> UNDERLINE;
      default -> null;
    };
    if (style != null) {
      applyStyle(style);
      event.consume();
      return;
    }
    int heading = switch (event.getCode()) {
      case DIGIT1, NUMPAD1 -> 1;
      case DIGIT2, NUMPAD2 -> 2;
      case DIGIT3, NUMPAD3 -> 3;
      default -> 0;
    };
    if (heading > 0) {
      applyHeading(heading);
      event.consume();
    }
  }

  private void setHeading(int level, int start, int end) {
    String size = switch (level) {
      case 1 -> "22px";
      case 2 -> "18px";
      default -> "16px";
    };
    String style = "-fx-font-size: " + size + ";";
    for (int position = start; position < end; position++) {
      String existing = notes.getStyleOfChar(position).replaceAll("-fx-font-size\\s*:\\s*[^;]+;?", "");
      notes.setStyle(position, position + 1, existing + " " + style);
    }
    String current = typingStyle.replaceAll("-fx-font-size\\s*:\\s*[^;]+;?", "");
    typingStyle = current + " " + style;
    notes.setTextInsertionStyle(typingStyle);
    changed();
    refreshToolbarState();
    notes.requestFocus();
  }

  private void applyHeading(int level) {
    int caret = notes.getCaretPosition();
    String before = notes.getText(0, caret);
    int start = before.lastIndexOf('\n') + 1;
    int end = notes.getText().indexOf('\n', caret);
    if (end < 0) end = notes.getLength();
    setHeading(level, start, end);
  }

  private void changed() {
    if (loading) return;
    saveStatus.setText("Unsaved");
    saveDelay.playFromStart();
    refreshToolbarState();
  }

  private void refreshToolbarState() {
    if (boldButton == null) return;
    int start = notes.getSelection().getStart();
    int end = notes.getSelection().getEnd();
    String style = start < end && start < notes.getLength()
            ? notes.getStyleOfChar(start)
            : typingStyle;
    setActive(boldButton, style.contains("font-weight: bold"));
    setActive(italicButton, style.contains("font-style: italic"));
    setActive(underlineButton, style.contains("-fx-underline: true"));
    setActive(h1Button, style.contains("22px"));
    setActive(h2Button, style.contains("18px"));
    setActive(h3Button, style.contains("16px"));
  }

  private static void setActive(Button button, boolean active) {
    if (active && !button.getStyleClass().contains("format-active")) button.getStyleClass().add("format-active");
    else if (!active) button.getStyleClass().remove("format-active");
  }

  boolean isOpen() {
    return opened;
  }

  void toggle(Path targetFile) {
    if (opened) close();
    else open(targetFile);
  }

  void open(Path targetFile) {
    if (targetFile == null) return;
    followsActiveFile = true;
    openTarget(targetFile);
  }

  void openFile(Path targetFile) {
    if (targetFile == null) return;
    followsActiveFile = false;
    openTarget(targetFile);
  }

  private void openTarget(Path targetFile) {
    if (opened) {
      loadTarget(targetFile);
      return;
    }
    opened = true;
    setVisible(true);
    setManaged(true);
    loadTarget(targetFile);
    Platform.runLater(notes::requestFocus);
  }

  void follow(Path targetFile) {
    if (!opened || !followsActiveFile || targetFile == null) return;
    loadTarget(targetFile);
  }

  private void loadTarget(Path targetFile) {
    if (targetFile == null) return;
    Path normalized = targetFile.toAbsolutePath().normalize();
    if (normalized.equals(file)) return;
    saveDelay.stop();
    if (file != null) save();
    file = normalized;
    loading = true;
    try {
      loadMarkdown(Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "");
      saveStatus.setText("Saved");
    } catch (IOException exception) {
      notes.clear();
      saveStatus.setText("Could not load");
      reportStatus.accept("Scratch Pad could not be loaded: " + exception.getMessage());
    } finally {
      loading = false;
      notes.moveTo(0);
      typingStyle = notes.getLength() == 0 ? "" : notes.getStyleOfChar(0);
      notes.setTextInsertionStyle(typingStyle);
      refreshToolbarState();
    }
  }

  private void loadMarkdown(String markdown) {
    notes.clear();
    String[] lines = markdown.split("\\R", -1);
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String line = lines[lineIndex];
      int heading = 0;
      while (heading < 3 && heading < line.length() && line.charAt(heading) == '#') heading++;
      if (heading > 0 && heading < line.length() && line.charAt(heading) == ' ') line = line.substring(heading + 1);
      else heading = 0;
      int start = notes.getLength();
      appendInlineMarkdown(line, headingStyle(heading), 0);
      if (lineIndex < lines.length - 1) notes.replaceText(notes.getLength(), notes.getLength(), "\n");
      if (heading > 0 && notes.getLength() > start) setHeading(heading, start, notes.getLength() - (lineIndex < lines.length - 1 ? 1 : 0));
    }
    notes.moveTo(notes.getLength());
  }

  private static String headingStyle(int level) {
    return switch (level) {
      case 1 -> "-fx-font-size: 22px;";
      case 2 -> "-fx-font-size: 18px;";
      case 3 -> "-fx-font-size: 16px;";
      default -> "";
    };
  }

  private void appendInlineMarkdown(String text, String style, int depth) {
    if (text.isEmpty()) return;
    if (depth > 5) {
      appendStyled(text, style);
      return;
    }
    int markerAt = -1;
    String open = null;
    String close = null;
    for (int i = 0; i < text.length(); i++) {
      if (text.startsWith("**", i)) { markerAt = i; open = "**"; close = "**"; break; }
      if (text.startsWith("<u>", i)) { markerAt = i; open = "<u>"; close = "</u>"; break; }
      if (text.charAt(i) == '*') { markerAt = i; open = "*"; close = "*"; break; }
    }
    if (markerAt < 0) {
      appendStyled(text, style);
      return;
    }
    appendStyled(text.substring(0, markerAt), style);
    int contentStart = markerAt + open.length();
    int closeAt = text.indexOf(close, contentStart);
    if (closeAt < 0) {
      appendStyled(text.substring(markerAt), style);
      return;
    }
    String decoration = open.equals("**") ? BOLD : open.equals("*") ? ITALIC : UNDERLINE;
    appendInlineMarkdown(text.substring(contentStart, closeAt), style + " " + decoration, depth + 1);
    appendInlineMarkdown(text.substring(closeAt + close.length()), style, depth);
  }

  private void appendStyled(String text, String style) {
    if (text.isEmpty()) return;
    int start = notes.getLength();
    notes.replaceText(start, start, text);
    if (!style.isBlank()) notes.setStyle(start, start + text.length(), style);
  }

  void close() {
    if (!opened) return;
    saveDelay.stop();
    save();
    opened = false;
    setVisible(false);
    setManaged(false);
  }

  private void save() {
    if (file == null) return;
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, toMarkdown(), StandardCharsets.UTF_8);
      saveStatus.setText("Saved");
    } catch (IOException exception) {
      saveStatus.setText("Save failed");
      reportStatus.accept("Scratch Pad could not be saved: " + exception.getMessage());
    }
  }

  private String toMarkdown() {
    String[] lines = notes.getText().split("\\n", -1);
    StringBuilder result = new StringBuilder();
    int offset = 0;
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String line = lines[lineIndex];
      int heading = headingAt(offset, line.length());
      if (heading > 0) result.append("#".repeat(heading)).append(' ');
      appendStyledMarkdown(result, offset, line.length());
      offset += line.length();
      if (lineIndex < lines.length - 1) {
        result.append('\n');
        offset++;
      }
    }
    return result.toString();
  }

  private int headingAt(int start, int length) {
    if (length == 0 || start >= notes.getLength()) return 0;
    String style = notes.getStyleOfChar(start);
    if (style.contains("22px")) return 1;
    if (style.contains("18px")) return 2;
    if (style.contains("16px")) return 3;
    return 0;
  }

  private void appendStyledMarkdown(StringBuilder result, int start, int length) {
    boolean bold = false, italic = false, underline = false;
    for (int i = 0; i < length; i++) {
      String style = notes.getStyleOfChar(start + i);
      boolean nextBold = style.contains("font-weight: bold");
      boolean nextItalic = style.contains("font-style: italic");
      boolean nextUnderline = style.contains("-fx-underline: true");
      if (underline && !nextUnderline) result.append("</u>");
      if (italic && !nextItalic) result.append('*');
      if (bold && !nextBold) result.append("**");
      if (!bold && nextBold) result.append("**");
      if (!italic && nextItalic) result.append('*');
      if (!underline && nextUnderline) result.append("<u>");
      bold = nextBold;
      italic = nextItalic;
      underline = nextUnderline;
      result.append(notes.getText(start + i, start + i + 1));
    }
    if (underline) result.append("</u>");
    if (italic) result.append('*');
    if (bold) result.append("**");
  }

}
