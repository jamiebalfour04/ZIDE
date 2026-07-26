package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfScrollbarPane;
import jamiebalfour.zpe.core.YASSDiagnostic;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.gui.YASSCodeEditor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class EditorTab extends Tab {
  private static final int ANALYSIS_DELAY_MS = 400;
  private static final ExecutorService ANALYSER = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "zide-syntax-analyser");
    thread.setDaemon(true);
    return thread;
  });

  private final ZIDEEditor owner;
  private final String path;
  private final YASSCodeEditor editor;
  private final BalfScrollbarPane pane;
  private final Timer analysisTimer;
  private final AtomicInteger analysisVersion = new AtomicInteger();
  private final HBox errorIndicator = new HBox(4);
  private final Label errorCountLabel = new Label();
  private final HBox warningIndicator = new HBox(4);
  private final Label warningCountLabel = new Label();
  private final HBox diagnosticOverlay = new HBox(7);
  private boolean changes = false;

  public EditorTab(ZIDEEditor owner, String title, String path, YASSCodeEditor editor, BalfScrollbarPane pane, Node content) {
    super(title, content);
    this.owner = owner;
    this.path = path;
    this.editor = editor;
    this.pane = pane;
    pane.setLightColour(Color.white);

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
    setContent(editorContainer);
    updateDiagnosticOverlayPosition();

    editor.getMinimapComponent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        Platform.runLater(() -> updateDiagnosticOverlayPosition());
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        Platform.runLater(() -> updateDiagnosticOverlayPosition());
      }
    });

    analysisTimer = new Timer(ANALYSIS_DELAY_MS, e -> analyseCurrentSource());
    analysisTimer.setRepeats(false);

    editor.getDocument().addDocumentListener(new DocumentListener() {

      @Override
      public void insertUpdate(DocumentEvent e) {
        changes = true;
        scheduleAnalysis();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        changes = true;
        scheduleAnalysis();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        changes = true;
        scheduleAnalysis();
      }
    });

    BalfLafManager.getInstance().addThemeChangeListener(() -> {
      if(BalfLafManager.getInstance().isDarkModeEnabled()){
        editor.setBackground(new Color(0, 0, 0));
        editor.setForeground(Color.white);
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.white);
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote,    new Color(198, 120, 221)); // Soft purple
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword,  new Color(255, 85, 114));  // Strong coral pink
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 175, 239));  // Bright sky blue
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var,      new Color(102, 217, 239)); // Aqua cyan
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type,     new Color(229, 192, 123)); // Sand/gold
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool,     new Color(189, 147, 249)); // Light lavender
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special,     new Color(0, 200, 0)); // Green
        editor.repaint();
        editor.setText(editor.getText());
      }
    });

    scheduleAnalysis();
  }

  void scheduleAnalysis() {
    if (path != null && !path.toLowerCase().endsWith(".yas")) {
      return;
    }

    analysisVersion.incrementAndGet();
    analysisTimer.restart();
  }

  private void analyseCurrentSource() {
    final int version = analysisVersion.get();
    final String source = editor.getText();

    ANALYSER.submit(() -> {
      List<YASSDiagnostic> diagnostics = ZPEKit.analyseCode(source, 0, source.length());
      if (version != analysisVersion.get()) {
        return;
      }

      SwingUtilities.invokeLater(() -> {
        if (version != analysisVersion.get() || !source.equals(editor.getText())) {
          return;
        }

        Platform.runLater(() -> {
          if (version == analysisVersion.get()) {
            owner.updateProblems(this, diagnostics);
          }
        });
      });
    });
  }

  void dispose() {
    analysisVersion.incrementAndGet();
    analysisTimer.stop();
  }

  void setDiagnosticCounts(int errorCount, int warningCount) {
    errorCountLabel.setText(String.valueOf(errorCount));
    boolean hasErrors = errorCount > 0;
    errorIndicator.setVisible(hasErrors);
    errorIndicator.setManaged(hasErrors);

    warningCountLabel.setText(String.valueOf(warningCount));
    boolean hasWarnings = warningCount > 0;
    warningIndicator.setVisible(hasWarnings);
    warningIndicator.setManaged(hasWarnings);
    updateDiagnosticOverlayPosition();
  }

  private void updateDiagnosticOverlayPosition() {
    double rightInset = 18;
    if (editor.isMinimapEnabled() && editor.getMinimapComponent().isVisible()) {
      rightInset += editor.getMinimapComponent().getPreferredSize().getWidth();
    }
    StackPane.setMargin(diagnosticOverlay, new Insets(10, rightInset, 0, 0));
  }
  

  public String getPath() {
    return path;
  }

  public YASSCodeEditor getEditor() { return editor; }
  public BalfScrollbarPane getScrollPane() { return pane; }

  void switchOnDarkMode() {


    Color dark = Color.decode("#282D37");


    BalfLafManager.getInstance().toggleDarkMode(true);

    pane.setDarkColour(dark);

    editor.setBackground(dark);
    editor.setForeground(Color.WHITE);
    editor.setCaretColor(Color.WHITE);

    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.white);
    /*editor.setAttributeColor(ATTR_TYPE.Quote, new Color(152, 195, 119));
    editor.setAttributeColor(ATTR_TYPE.Keyword, new Color(255, 123, 114));
    editor.setAttributeColor(ATTR_TYPE.Function, new Color(210, 168, 255));
    editor.setAttributeColor(ATTR_TYPE.Var, new Color(0, 106, 44));
    editor.setAttributeColor(ATTR_TYPE.Type, new Color(121, 192, 255));
    editor.setAttributeColor(ATTR_TYPE.Bool, new Color(208, 154, 102));*/

    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote,    new Color(198, 120, 221)); // Soft purple
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword,  new Color(255, 85, 114));  // Strong coral pink
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 175, 239));  // Bright sky blue
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var,      new Color(102, 217, 239)); // Aqua cyan
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type,     new Color(229, 192, 123)); // Sand/gold
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool,     new Color(189, 147, 249)); // Light lavender
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special,     new Color(0, 200, 0)); // Green


    //autoCompleteItemForeColor = new Color(255, 255, 255, 255);
    editor.setAutoCompleteItemBackgroundColor(new Color(30, 39, 75, 255));
    resetScroll();



  }

  void switchOffDarkMode() {

    BalfLafManager.getInstance().toggleDarkMode(false);


    Color light = new Color(255, 255, 255);
    pane.setLightColour(Color.white);
    editor.setBackground(Color.white);
    editor.setForeground(Color.black);
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.black);
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote, new Color(0, 128, 0));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword, new Color(135, 16, 148));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 172, 231));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var, new Color(255, 138, 0));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type, new Color(2, 87, 172));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special, new Color(255, 0, 0)); // Pink
    editor.setCaretColor(Color.black);
    //autoCompleteItemForeColor = new Color(31, 31, 31, 255);
    //autoCompleteItemBackgroundColor = new Color(241, 241, 241, 255);

    editor.setAutoCompleteItemBackgroundColor(new Color(241, 241, 241, 255));

    resetScroll();


  }

  void setHasChanges(boolean hasChanges) {
    this.changes = hasChanges;
  }

  boolean hasChanges() {
    return changes;
  }

  private void resetScroll() {
    int caretPosition = editor.getCaretPosition();
    int scrollPosition = pane.getVerticalScrollBar().getValue();
    editor.setText(editor.getText());
    SwingUtilities.invokeLater(() -> {
      editor.requestFocus();
      editor.setCaretPosition(caretPosition);
      pane.getVerticalScrollBar().setValue(scrollPosition);
    });
  }

}
