package jamiebalfour.zide.editor;

import jamiebalfour.FileHelperFunctions;
import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfPanel;
import jamiebalfour.ui.components.BalfScrollbarPane;
import jamiebalfour.ui.components.BalfSearchBox;
import jamiebalfour.zide.ZIDEHelperFunctions;
import jamiebalfour.zide.core.ZIDE;
import jamiebalfour.zpe.core.*;
import jamiebalfour.zpe.editor.ConsoleOutputTextArea;
import jamiebalfour.zpe.interfaces.ZPEType;
import jamiebalfour.zpe.types.ZPEMap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

public class ZIDEEditor extends Application {

  Stage _stage;
  ZPERuntimeEnvironment runtime;
  Label rightFooterLabel;
  ConsoleOutputTextArea consoleOutputTextArea;
  BalfScrollbarPane consoleScrollbar;
  Button runBtn;
  Button debugBtn;
  Button stopExecutionBtn;
  Button stepOverButton;
  Button continueButton;
  Separator debugSeparator;
  CheckMenuItem toggleTheme;
  private TableView<VarRow> varTable;
  private ObservableList<VarRow> varRows;
  private SplitPane terminalSplit;
  private VBox variablesPane;
  private ConsoleOutputTextArea.BreakPoint currentBreakpoint;
  private CheckMenuItem variablesPaneOption;
  MenuItem runProject;


  public static Font loadAndRegister(String resourcePath) {
    try (InputStream in = ZIDEEditor.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Font resource not found: " + resourcePath);
      }

      Font base = Font.createFont(Font.TRUETYPE_FONT, in);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
      return base;
    } catch (Exception e) {
      throw new RuntimeException("Failed to load font: " + resourcePath, e);
    }
  }

  public static void begin(String[] args){
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    stage.initStyle(StageStyle.UNDECORATED);

    _stage = stage;

    stage.getIcons().add(
            new Image(Objects.requireNonNull(getClass().getResourceAsStream("/files/balflaf_fx/icons/jb.png")))
    );


    runtime = new ZPERuntimeEnvironment();

    editorTabs = new TabPane();

    var root = new BorderPane();
    root.getStyleClass().add("app-root");

    EventHandler<ActionEvent> aboutHandler = e -> {
      ZIDEAboutWindow.show(_stage);
    };

    // Top: menu + toolbar
    var top = new VBox(new BalfTitleBar(stage, "ZIDE", aboutHandler), buildMenuBar(), buildToolBar());
    root.setTop(top);


    BalfTitleBar.addWindowResizing(stage, root);


    // Left: project tree
    Node projectTree = buildProjectTree();
    Node leftPane = wrapTitled("Project", projectTree);
    //leftPane.setMinWidth(260);

    // Center: editor tabs
    var editors = buildEditorTabs();

    // Bottom: terminal + status bar
    var terminal = buildTerminal();

    consoleOutputTextArea.addProcessFinishedListener(new ConsoleOutputTextArea.ProcessFinishedListener() {

      @Override
      public void onProcessFinished() {
        stopExecutionBtn.setVisible(false);
        stepOverButton.setVisible(false);
        continueButton.setVisible(false);
        debugSeparator.setVisible(false);
        clearRows();
      }
    });
    var bottom = new VBox(terminal, buildStatusBar());
    VBox.setVgrow(terminal, Priority.ALWAYS);

    // Split layout: left + center, then center + bottom
    var horizontalSplit = new SplitPane(leftPane, editors);
    horizontalSplit.setDividerPositions(0.22);

    var verticalSplit = new SplitPane(horizontalSplit, bottom);
    verticalSplit.setOrientation(Orientation.VERTICAL);
    verticalSplit.setDividerPositions(0.72);

    root.setCenter(verticalSplit);

    var scene = new Scene(root, 1280, 800);
    //scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(getClass().getResource("/zide.css").toExternalForm());

    stage.setTitle("ZIDE");
    stage.setScene(scene);
    stage.show();

    boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

    if (isMac) {
      root.getStyleClass().add("mac-window");
    }
  }

  private static final Preferences PREFS = Preferences.userNodeForPackage(ZIDEEditor.class);
  private static final String KEY_LAST_DIR = System.getProperty("user.home");//"/Users/jamiebalfour/Documents/";

  private void newFile(){
    String suggestedName = "Untitled";
    FileChooser chooser = new FileChooser();
    chooser.setTitle("New file");

    chooser.setInitialFileName(suggestedName);

    // Example filters — add/remove as you like
    chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("ZPE Files (*.yas)", "*.yas"),
            new FileChooser.ExtensionFilter("YASS Webpage Files (*.ywp)", "*.ywp"),
            new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"),
            new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
    );


    // Restore last directory if it still exists
    String lastDir = PREFS.get(KEY_LAST_DIR, null);
    if (lastDir != null) {
      File dir = new File(lastDir);
      if (dir.exists() && dir.isDirectory()) {
        chooser.setInitialDirectory(dir);
      }
    }

    File chosen = chooser.showSaveDialog(_stage);

    // Persist directory for next time
    if (chosen != null) {
      File parent = chosen.getParentFile();
      if (parent != null && parent.exists()) {
        PREFS.put(KEY_LAST_DIR, parent.getAbsolutePath());
      }
      openTab(chosen.getName());
    }


  }

  private MenuBar buildMenuBar() {
    var file = new Menu("_File");
    var newFile = new MenuItem("New File");
    newFile.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
    newFile.setOnAction(e -> newFile());

    var open = new MenuItem("Open…");
    open.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));

    var save = new MenuItem("Save");
    save.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));

    var exit = new MenuItem("Exit");
    exit.setOnAction(e -> System.exit(0));

    file.getItems().addAll(newFile, open, new SeparatorMenuItem(), save, new SeparatorMenuItem(), exit);

    var edit = new Menu("_Edit");
    edit.getItems().addAll(
            new MenuItem("Undo"),
            new MenuItem("Redo"),
            new SeparatorMenuItem(),
            new MenuItem("Find…"),
            new MenuItem("Replace…")
    );

    var view = new Menu("_View");
    toggleTheme = new CheckMenuItem("Dark theme");
    toggleTheme.setOnAction(e -> {
      BalfLafManager.getInstance().toggleDarkMode(toggleTheme.isSelected());
      var scene = toggleTheme.getParentPopup().getOwnerWindow().getScene();
      scene.getRoot().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("dark"),
              toggleTheme.isSelected());

      for (Tab t : editorTabs.getTabs()) {
        EditorTab tab = (EditorTab) t;
        if(toggleTheme.isSelected()) {
          tab.switchOnDarkMode();
        } else{
          tab.switchOffDarkMode();
        }
        int scrollPosition = tab.getScrollPane().getVerticalScrollBar().getValue();
        SwingUtilities.invokeLater(() -> {
          tab.getScrollPane().getVerticalScrollBar().setValue(scrollPosition);
        });

      }




    });

    var panesMenu = new Menu("Panes");
    variablesPaneOption = new CheckMenuItem("Variables Pane");
    variablesPaneOption.setOnAction(e -> {
      if(variablesPaneOption.isSelected()) {
        showVariablesPane();
      } else{
        hideVariablesPane();
      }
    });

    panesMenu.getItems().addAll(variablesPaneOption);

    view.getItems().add(toggleTheme);
    view.getItems().add(panesMenu);

    var run = new Menu("_Run");

    runProject = new MenuItem("Run Project");



    runProject.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
    runProject.setOnAction(e -> runCode());
    run.getItems().add(runProject);

    var help = new Menu("_Help");


    var downloadZPERuntimeItem = new MenuItem("Download ZPE Runtime Environment");
    downloadZPERuntimeItem.setOnAction(e -> {
      downloadZPERuntime();
    });

    var downloadZPENative = new MenuItem("Download ZPE Native");
    downloadZPENative.setOnAction(e -> {
      //
      try {
        downloadWithPopup("Latest ZPEX Native Binary", _stage, "https://www.jamiebalfour.scot/downloads/1-zpe/zpe-native-aarch64", Path.of(ZPEInstance.getInstallPath() + "/zpe-aarch64"), false, true);
      } catch(Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error downloading ZPE Native");
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
      }
    });

    MenuItem aboutMenuItem = new MenuItem("About");
    help.getItems().add(aboutMenuItem);
    aboutMenuItem.setOnAction(e -> {
      ZIDEAboutWindow.show(_stage);
    });

    help.getItems().add(new SeparatorMenuItem());
    help.getItems().add(downloadZPERuntimeItem);
    help.getItems().add(downloadZPENative);

    var bar = new MenuBar(file, edit, view, run, help);
    bar.getStyleClass().add("app-menubar");
    return bar;
  }

  private void downloadZPERuntime(){
    //
    try {
      downloadWithPopup("Latest ZPE Runtime Environment", _stage, "https://www.jamiebalfour.scot/downloads/1-zpe/zpe", Path.of(ZPEInstance.getInstallPath() + "/zpe.jar"), false, false);
    } catch(Exception ex) {
      Alert alert = new Alert(Alert.AlertType.ERROR);
      alert.setTitle("Error");
      alert.setHeaderText("Error downloading ZPE Native");
      alert.setContentText(ex.getMessage());
      alert.showAndWait();
    }
  }

  private void runCode(){

    if(!new File(ZPEInstance.getInstallPath() + "/zpe.jar").exists()) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("ZPE Runtime Environment missing");
      alert.setHeaderText("ZPE Runtime Environment required.");
      alert.setContentText("ZIDE needs to download a copy of the latest ZPE Runtime Environment before it can run your code. Do you want to continue?");

      ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
      ButtonType no  = new ButtonType("No", ButtonBar.ButtonData.NO);

      alert.getButtonTypes().setAll(yes, no);

      Optional<ButtonType> result = alert.showAndWait();

      if (result.isPresent() && result.get() == yes) {
        downloadZPERuntime();
        return;
      } else {
        return;
      }
    }

    try {

      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = (EditorTab) editorTabs.getSelectionModel().getSelectedItem();
      runBtn.getStyleClass().add("running");

      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), tab.getEditor().getText(), false);
      consoleOutputTextArea.runAsProcess(tempPath, false, true, "");

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void debugCode(){

    if(!new File(ZPEInstance.getInstallPath() + "/zpe.jar").exists()) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("ZPE Runtime Environment missing");
      alert.setHeaderText("ZPE Runtime Environment required.");
      alert.setContentText("ZIDE needs to download a copy of the latest ZPE Runtime Environment before it can run your code. Do you want to continue?");

      ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
      ButtonType no  = new ButtonType("No", ButtonBar.ButtonData.NO);

      alert.getButtonTypes().setAll(yes, no);

      Optional<ButtonType> result = alert.showAndWait();

      if (result.isPresent() && result.get() == yes) {
        downloadZPERuntime();
        return;
      } else {
        return;
      }
    }

    try {
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = (EditorTab) editorTabs.getSelectionModel().getSelectedItem();
      debugBtn.getStyleClass().add("running");

      stopExecutionBtn.setVisible(true);
      stepOverButton.setVisible(true);
      continueButton.setVisible(true);
      debugSeparator.setVisible(true);


      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), tab.getEditor().getText(), false);
      consoleOutputTextArea.runAsProcess(tempPath, true, false, "");

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ToolBar buildToolBar() {
    runBtn = new Button("Run");
    runBtn.getStyleClass().add("run");
    runBtn.setOnAction(e -> {
      runCode();

    });

    var buildBtn = new Button("Build");

    debugBtn = new Button("Debug");
    debugBtn.getStyleClass().add("debug");
    debugBtn.setOnAction(e -> {
      consoleOutputTextArea.addBreakPointReachedListener(new ConsoleOutputTextArea.BreakPointReachedListener() {

        @Override
        public void onBreakPointReached(ConsoleOutputTextArea.BreakPoint b, ZPEMap varData) {
          currentBreakpoint = b;
          showVariablesPane();
          setVariables(varData);
        }
      });

      debugCode();
    });

    var sep1 = new Separator(Orientation.VERTICAL);

    stopExecutionBtn = new Button("Stop");
    stopExecutionBtn.setOnAction(e -> {
      if(currentBreakpoint == null){
        consoleOutputTextArea.destroyCurrentProcess();
      } else{
        currentBreakpoint.stopExecution();
      }

    });
    stopExecutionBtn.setVisible(false);

    stepOverButton = new Button("Step Over");
    stepOverButton.setOnAction(e -> {
      if(currentBreakpoint == null){
        return;
      }
      currentBreakpoint.stepOver();
    });
    stepOverButton.setVisible(false);

    continueButton = new Button("Continue");
    continueButton.setOnAction(e -> {
      if(currentBreakpoint == null){
        return;
      }
      currentBreakpoint.resume();
    });
    continueButton.setVisible(false);

    debugSeparator = new Separator(Orientation.VERTICAL);
    debugSeparator.setVisible(false);

    var search = new TextField();
    search.setPromptText("Search…");
    search.getStyleClass().add("search-field");
    search.setMaxWidth(280);

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);



    var toolbar = new ToolBar(runBtn, buildBtn, debugBtn, sep1, stopExecutionBtn, stepOverButton, continueButton, debugSeparator, new Label(" "), spacer, search);
    toolbar.getStyleClass().add("app-toolbar");
    return toolbar;
  }

  private Node buildProjectTree() {
    var root = new TreeItem<>("MyProject");
    root.setExpanded(true);


    File projectDir = new File(System.getProperty("user.home") + "/Documents/");

    var tree = new TreeView<>(loadDirectory(projectDir));
    tree.setShowRoot(true);
    tree.getStyleClass().add("project-tree");
    tree.setCellFactory(tv -> new TreeCell<>() {
      @Override
      protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);

        if (empty || file == null) {
          setText(null);
          setGraphic(null);
          return;
        }

        // show just the name (root special-cased if you want)
        String name = file.getName();
        setText(name.isEmpty() ? file.getPath() : name);
      }
    });

    // Simple behaviour: double-click opens a new tab (demo)
    tree.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        var item = tree.getSelectionModel().getSelectedItem();
        if (item != null && item.isLeaf()) {
          System.out.println("Opening " + item.getValue());
          openTab(item.getValue().getName(), item.getValue().getPath());
        }
      }
    });

    return tree;
  }

  private TabPane editorTabs;

  // Call this whenever you want the label refreshed
  Runnable refreshRunText = () -> {
    Tab t = editorTabs.getSelectionModel().getSelectedItem();
    String tabName = (t == null) ? "" : t.getText();
    runProject.setText(tabName.isBlank() ? "Run" : "Run " + tabName);
  };


  private Node buildEditorTabs() {
    editorTabs.getStyleClass().add("editor-tabs");
    editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);



    editorTabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> {
      while (c.next()) {
        if (c.wasAdded()) {
          for (Tab t : c.getAddedSubList()) {
            t.textProperty().addListener((o, oldText, newText) -> refreshRunText.run());
          }
        }
      }
    });

    editorTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
      if (newTab == null) return;

      Object ud = newTab.getUserData();
      String path = (ud == null) ? null : ud.toString();

      String lang = newTab.idProperty().getValue();

      String ext = "";

      if (lang != null) {
        int dot = lang.lastIndexOf('.');
        if (dot >= 0 && dot < lang.length() - 1) {
          ext = lang.substring(dot + 1).toLowerCase();
        }
      }
      if(rightFooterLabel != null){
        if(ext.equals("yas")) {
          rightFooterLabel.setText("YASS");
        } else{
          rightFooterLabel.setText("Text");
        }
      }

    });

    return editorTabs;
  }

  private void openTab(String name) {
    openTab(name, null);
  }

  private boolean activeTabStylingInstalled = false;

  private void installActiveTabStyling() {
    if (activeTabStylingInstalled) return;
    activeTabStylingInstalled = true;

    editorTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
      if (oldTab != null && oldTab.getGraphic() != null) {
        oldTab.getGraphic().getStyleClass().remove("active-tab");
      }
      if (newTab != null && newTab.getGraphic() != null) {
        newTab.getGraphic().getStyleClass().add("active-tab");
      }
    });
  }

  private void openTab(String name, String file) {
    // Ensure active-tab behaviour is installed once
    installActiveTabStyling();

    // If tab already exists, select it
    for (Tab t : editorTabs.getTabs()) {
      String txt = t.getId();
      if (file.equals(txt)) {
        editorTabs.getSelectionModel().select(t);
        return;
      }
    }

    SwingNode swingNode = new SwingNode();
    swingNode.getStyleClass().add("swing-editor-host");

    // Holders that are safe to access from JavaFX side
    AtomicReference<CodeEditorView> editorRef = new AtomicReference<>();
    AtomicBoolean hasNonBlankContent = new AtomicBoolean(false);

    // Build Swing UI on EDT


      try {
        jamiebalfour.codeeditor.CodeEditorView mainSyntax = new jamiebalfour.codeeditor.CodeEditorView();

        mainSyntax.setFontSize(14);


        Font jbMono = loadAndRegister("/files/JetBrainsMono-Regular.ttf");
        Font editorFont = jbMono.deriveFont(Font.PLAIN, 14);
        mainSyntax.setFont(editorFont);

        String lang = "txt";

        if(file != null && file.endsWith(".yas")) {
          lang = "yass";
        }

        setLanguage(lang, mainSyntax);





        Color dark = Color.decode("#282D37");
        // Wrapper + padding
        BalfPanel wrapper = new BalfPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        wrapper.setBackground(Color.white);
        wrapper.setDarkColour(dark);
        wrapper.setLightColour(Color.white);


        BalfScrollbarPane scrollPane = new BalfScrollbarPane();
        scrollPane.setLightColour(Color.white);
        scrollPane.setDarkColour(dark);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        mainSyntax.repaint();
        mainSyntax.requestFocus();


        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> mainSyntax.hideTooltip());

        mainSyntax.setWrapper(scrollPane);

        wrapper.add(scrollPane, BorderLayout.CENTER);


        // Load file (still on EDT)
        if (file != null) {
          mainSyntax.setText(FileHelperFunctions.readFileAsString(file));
        }

        mainSyntax.setCaretPosition(0);

        // Track “has content” safely
        hasNonBlankContent.set(!mainSyntax.getText().isBlank());

        mainSyntax.getDocument().addDocumentListener(new DocumentListener() {
          private void update() {
            // always EDT already, but keep it simple
            hasNonBlankContent.set(!mainSyntax.getText().isBlank());
          }
          @Override public void insertUpdate(DocumentEvent e) { update(); }
          @Override public void removeUpdate(DocumentEvent e) { update(); }
          @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        editorRef.set(mainSyntax);
        swingNode.setContent(wrapper);
        swingNode.getStyleClass().add("swing-editor-host");

        mainSyntax.setFont(new Font(mainSyntax.getFont().getFontName(), mainSyntax.getFont().getStyle(), mainSyntax.getFont().getSize()));
        mainSyntax.repaint();


        // Create the tab using the boolean (no cross-thread Swing calls)
        Tab tab = createEditorTab(name, mainSyntax, scrollPane, file, swingNode, hasNonBlankContent::get);
        if(file != null){
          tab.setId(file);
        }


        editorTabs.getTabs().add(tab);
        editorTabs.getSelectionModel().select(tab);

        // Helps focus when you click into the editor region
        swingNode.setOnMousePressed(e -> swingNode.requestFocus());

      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }


  }

  private void setLanguage(String lang, CodeEditorView mainSyntax) {
    if(lang.equals("yass")) {
      String[] keywords = ZPEKit.getBuiltInFunctions();

      for (String s : keywords) {
        mainSyntax.setTooltipInfo(s, getTooltipFunctionInfo(s));
      }

      mainSyntax.resetKeywords(ZPEKit.getKeywordSet());
      mainSyntax.setQuotes("\"'`");
      mainSyntax.setVariableSymbol("$");

      for (String keyword : ZPEKit.getKeywords()) {
        mainSyntax.addAutoCompleteItem(keyword, CodeEditorView.AutoCompleteItemType.Keyword);
      }
      for (String keyword : ZPEKit.getTypeKeywords()) {
        mainSyntax.addAutoCompleteItem(keyword, CodeEditorView.AutoCompleteItemType.Type);
      }
      for (String function : keywords) {
        mainSyntax.addAutoCompleteItem(function, CodeEditorView.AutoCompleteItemType.Function);
      }
      for (String s : ZPEInstance.getBuiltInStructuresNames()) {
        mainSyntax.addAutoCompleteItem(s, CodeEditorView.AutoCompleteItemType.Type);
      }


      for (String s : ZPEKit.getAllCommands()) {
        BalfSearchBox.SearchSuggestion suggestion = new BalfSearchBox.SearchSuggestion(s + " " + ZPEKit.getFunctionManualEntry(s), s);
      }

      rightFooterLabel.setText("YAS");
    }
  }

  private boolean confirmClose(String tabTitle) {

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Close Tab");
    alert.setHeaderText("Close \"" + tabTitle + "\"?");
    alert.setContentText("You have unsaved content. Are you sure you want to close this tab?");

    ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancel = ButtonType.CANCEL;

    alert.getButtonTypes().setAll(close, cancel);

    return alert.showAndWait().orElse(cancel) == close;
  }

  private Tab createEditorTab(String title, CodeEditorView syntax, BalfScrollbarPane scrollPane, String path, Node content, Supplier<Boolean> hasContent) {
    EditorTab tab = new EditorTab(title, path, syntax, scrollPane, content);
    tab.setContent(content);

    // Disable JavaFX built-in close button
    tab.setClosable(false);

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("tab-title");

    Button closeBtn = new Button();
    closeBtn.getStyleClass().add("tab-close-button");
    closeBtn.setFocusTraversable(false);


    closeBtn.setMinSize(10, 10);
    closeBtn.setPrefSize(10, 10);
    closeBtn.setMaxSize(10, 10);

    if(toggleTheme.isSelected()){
      tab.switchOnDarkMode();
    }



    closeBtn.setOnAction(e -> {
      if (hasContent.get() && !confirmClose(title)) {
        return;
      }
      TabPane pane = tab.getTabPane();
      if (pane != null) pane.getTabs().remove(tab);
    });

    HBox header = new HBox(titleLabel, closeBtn);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setSpacing(6);
    header.getStyleClass().add("tab-header");

    tab.setText("");          // IMPORTANT: text comes from our label now
    tab.setGraphic(header);   // Graphic is the whole header (so X can be right)


    return tab;
  }

  private Node buildTerminal() {
    var title = new Label("Terminal");
    title.getStyleClass().add("pane-title");

    // --- LEFT: Swing console ---
    SwingNode terminalNode = new SwingNode();


      consoleOutputTextArea = new ConsoleOutputTextArea("", Color.WHITE);



      consoleOutputTextArea.addProcessFinishedListener(() ->
              Platform.runLater(() -> {
                runBtn.getStyleClass().remove("running");
                debugBtn.getStyleClass().remove("running");
              })
      );

      consoleScrollbar = new BalfScrollbarPane(consoleOutputTextArea);
      consoleScrollbar.setLightColour(Color.WHITE);
      consoleScrollbar.setDarkColour(Color.BLACK);
      consoleScrollbar.setBorder(BorderFactory.createEmptyBorder());
      consoleScrollbar.getVerticalScrollBar().setUnitIncrement(16);

      terminalNode.setContent(consoleScrollbar);


    VBox terminalContainer = new VBox(terminalNode);
    VBox.setVgrow(terminalNode, Priority.ALWAYS);

    // --- RIGHT: Variables pane (created but NOT added yet) ---
    variablesPane = buildVariablesPane(); // method below

    // --- SplitPane ---
    terminalSplit = new SplitPane();
    terminalSplit.setOrientation(Orientation.HORIZONTAL);
    terminalSplit.getItems().add(terminalContainer); // 👈 only terminal initially

    // --- Header ---
    var header = new HBox(title);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("pane-header");

    var box = new VBox(header, terminalSplit);
    VBox.setVgrow(terminalSplit, Priority.ALWAYS);
    box.getStyleClass().add("terminal-pane");
    box.setMinHeight(180);

    return box;
  }

  private VBox buildVariablesPane() {
    varRows = FXCollections.observableArrayList();
    varTable = new TableView<>(varRows);
    varTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    TableColumn<VarRow, String> cName = new TableColumn<>("Name");
    cName.setCellValueFactory(v -> v.getValue().nameProperty());
    cName.setMinWidth(70);
    cName.setPrefWidth(70);

    TableColumn<VarRow, String> cType = new TableColumn<>("Type");
    cType.setCellValueFactory(v -> v.getValue().typeProperty());
    cType.setMinWidth(50);
    cType.setPrefWidth(50);
    cType.setMaxWidth(90);

    TableColumn<VarRow, String> cFunc = new TableColumn<>("Function");
    cFunc.setCellValueFactory(v -> v.getValue().functionProperty());
    cFunc.setMinWidth(80);
    cFunc.setPrefWidth(80);

    TableColumn<VarRow, String> cValue = new TableColumn<>("Value");
    cValue.setCellValueFactory(v -> v.getValue().valueProperty());

    varTable.getColumns().setAll(cName, cType, cFunc, cValue);

    var title = new Label("Variables");
    title.getStyleClass().add("pane-title");

    var header = new HBox(title);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("pane-header");

    var box = new VBox(header, varTable);
    VBox.setVgrow(varTable, Priority.ALWAYS);
    box.getStyleClass().add("variables-pane");
    box.setMinWidth(320);

    return box;
  }

  private void showVariablesPane() {
    Platform.runLater(() -> {
      if (!terminalSplit.getItems().contains(variablesPane)) {
        terminalSplit.getItems().add(variablesPane);
        terminalSplit.setDividerPositions(0.72);
      }
    });
    variablesPaneOption.setSelected(true);
  }

  private void hideVariablesPane() {
    Platform.runLater(() -> {
      terminalSplit.getItems().remove(variablesPane);
    });
    variablesPaneOption.setSelected(false);
  }

  public void setVariables(ZPEMap vars) {
    java.util.List<VarRow> rows = new java.util.ArrayList<>();

    for (ZPEType o : vars) {
      ZPEMap m = (ZPEMap) vars.get(o);

      String type = ZPEHelperFunctions.getTypeString(m.get("type"));

      rows.add(new VarRow(
              String.valueOf(m.get("id")),
              type,
              String.valueOf(m.get("function")),
              String.valueOf(m.get("value"))
      ));
    }

    Platform.runLater(() -> {
      varRows.setAll(rows);   // clear + repopulate in one go
      showVariablesPane();    // if you're hiding it until needed
    });
  }

  private void clearRows(){
    Platform.runLater(() -> {
      varRows.clear();
    });
  }

  private Node buildStatusBar() {
    var left = new Label("Ready");
    var centre = new Label("ZIDE " + ZIDE.getMajorVersion() + "." + ZIDE.getMinorVersion() + " build " + ZIDE.getBuildNumber() + " © Jamie Balfour 2024 - 2026.");
    rightFooterLabel = new Label("Text");

    centre.setStyle("-fx-font-size: 13px;");

    rightFooterLabel.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
      CodeEditorView c = ((EditorTab) editorTabs.getSelectionModel().getSelectedItem()).getEditor();
      setLanguage("yass", c);
    });

    var bar = new HBox(left, new Region(), centre, new Region(), rightFooterLabel);
    HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
    HBox.setHgrow(bar.getChildren().get(3), Priority.ALWAYS);

    bar.setPadding(new Insets(6, 10, 6, 10));
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private Node wrapTitled(String title, Node content) {
    var header = new Label(title);
    header.getStyleClass().add("pane-title");

    var headerBox = new HBox(header);
    headerBox.setAlignment(Pos.CENTER_LEFT);
    headerBox.getStyleClass().add("pane-header");

    var wrapper = new VBox(headerBox, content);
    VBox.setVgrow(content, Priority.ALWAYS);
    wrapper.getStyleClass().add("side-pane");
    return wrapper;
  }

  public static TreeItem<File> loadDirectory(File dir) {
    TreeItem<File> root = makeItem(dir);

    root.setExpanded(true);          // optional
    // If you want root loaded immediately, uncomment:
    // loadChildrenIfNeeded(root);
    return root;
  }

  private static TreeItem<File> makeItem(File f) {
    TreeItem<File> item = new TreeItem<>(f);

    if (f != null && f.isDirectory()) {
      // Put in a dummy child so the expand arrow appears


      item.getChildren().add(new TreeItem<>(null));

      // Load children the FIRST time it expands
      item.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
        if (isNowExpanded) {
          loadChildrenIfNeeded(item);
        }
      });
    }

    return item;
  }

  /** Loads children only once, using the dummy-child marker. */
  private static void loadChildrenIfNeeded(TreeItem<File> item) {
    // Not a directory → nothing to load
    File dir = item.getValue();
    if (dir == null || !dir.isDirectory()) return;

    // Already loaded? (no dummy marker)
    if (!hasDummyChild(item)) return;

    // Remove dummy, then populate
    item.getChildren().clear();

    File[] files = dir.listFiles();
    if (files == null) return;

    Arrays.stream(files)
            .filter(f -> !f.isHidden()) // remove if you want hidden files
            .sorted(Comparator
                    .comparing((File f) -> !f.isDirectory())          // folders first
                    .thenComparing(f -> f.getName().toLowerCase()))   // A→Z
            .forEach(f -> item.getChildren().add(makeItem(f)));
  }

  private static boolean hasDummyChild(TreeItem<File> item) {
    return item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == null;
  }

  private String getTooltipFunctionInfo(String functionName) {
    String output = "";


    if (toggleTheme.isSelected()) {
      output += "<html><div style='padding:10px;width:300px;color:#ddd;'>";
    } else {
      output += "<html><div style='padding:10px;width:300px;color:#333;'>";
    }

    ArrayList<AbstractMap.SimpleEntry<String, String>> params = getParams(ZPEKit.getFunctionManualHeader(functionName));

    StringBuilder header = new StringBuilder();
    for (int i = 0; i < params.size(); i++) {
      AbstractMap.SimpleEntry<String, String> param = params.get(i);
      String name = param.getKey();
      String type = param.getValue();

      //new Color(105, 143, 163) new Color(150, 0, 150)

      if (toggleTheme.isSelected()) {
        header.append("<span style='color: rgb(105, 143, 163);font-style:italic;'>").append(type).append("</span>").append(" <span style='color:#f60'>").append(name).append("</span>");
      } else {
        header.append("<span style='color: rgb(2, 87, 172);font-style:italic;'>").append(type).append("</span>").append(" <span style='color:#f60'>").append(name).append("</span>");
      }

      if (i + 1 < params.size()) {
        header.append(", ");
      }

    }



    if (toggleTheme.isSelected()) {
      output += "<div style='margin-bottom:5px;'><code style='font-size:10px;'><span style='font-weight:bold;color:rgb(198, 120, 222)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</code></div>";
    } else {
      output += "<div style='margin-bottom:5px;'><code style='font-size:10px;'><span style='font-weight:bold;margin-bottom:20px;color:rgb(135, 16, 148)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</code></div>";
    }

    output += ZPEKit.getFunctionManualEntryAsStyledHtml(functionName, toggleTheme.isSelected());

    if (!toggleTheme.isSelected()) {
      output += "<div style='margin:10px 0; color:#222;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>";
    } else {
      output += "<div style='margin:10px 0; color:#bbb;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>";
    }

    output += "<div style='font-weight:100;margin-bottom:10px;'>Category: " + ZPEKit.getFunctionCategory(functionName) + "</div>";

    output += "<div style='color:#0af'>Click for more information online.</div>";

    output += "</div></html>";

    return output;
  }

  static ArrayList<AbstractMap.SimpleEntry<String, String>> getParams(String function) {

    //Proper parameter parser
    ArrayList<AbstractMap.SimpleEntry<String, String>> array = new ArrayList<>();

    int i = 0;

    while (i < function.length()) {
      if (function.charAt(i) == '(') {
        i++;
        break;
      }
      i++;
    }

    boolean optional = false;

    while (i < function.length() && function.charAt(i) != ')') {


      if (function.charAt(i) == '[') {
        optional = true;
        i++;
      }

      if (function.charAt(i) == '{') {


        //This is a data type
        StringBuilder dataType = new StringBuilder();
        StringBuilder name = new StringBuilder();
        i++;
        while (i < function.length() && function.charAt(i) != '}') {
          dataType.append(function.charAt(i));
          i++;
        }
        i++;

        while (i < function.length() && function.charAt(i) == ' ') {
          i++;
        }

        while (i < function.length() && (function.charAt(i) != ',' && function.charAt(i) != ')' && function.charAt(i) != '[' && function.charAt(i) != ']' && function.charAt(i) != '.')) {
          name.append(function.charAt(i));
          i++;
        }

        while (i < function.length() && function.charAt(i) == '.') {
          i++;
        }

        if (function.charAt(i) == '[') {

          i--;
        }

        AbstractMap.SimpleEntry<String, String> entry;

        /*if(optional){
          entry = new AbstractMap.SimpleEntry<>("[" + name.toString() + "]", dataType.toString());
        } else{*/
        entry = new AbstractMap.SimpleEntry<>(name.toString(), dataType.toString());
        //}


        array.add(entry);


        if (function.charAt(i) == ']') {
          optional = false;
          i++;
        }

      }
      //System.out.println(function.substring(i));


      i++;


    }
    return array;
  }

  public void downloadWithPopup(String title, javafx.stage.Window owner, String url, Path target, boolean unzipOnComplete, boolean executable) {

    DownloadDialog dlg = new DownloadDialog(owner, "Downloading " + title, "Starting…");
    dlg.show();

    Path location = target;

    if(unzipOnComplete){
      try {
        location = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".zip");
      } catch (IOException e) {
        Alert a = new Alert(Alert.AlertType.ERROR, "Download failed: " + e.getMessage());
      }
    }

    var task = ZIDEHelperFunctions.downloadToFileTask(url, location);

    // Bind UI to task
    dlg.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    task.messageProperty().addListener((obs, oldV, newV) -> Platform.runLater(() -> dlg.setMessage(newV)));
    task.progressProperty().addListener((obs, oldV, newV) -> Platform.runLater(() -> dlg.setProgress(newV.doubleValue())));

    Path finalLocation = location;
    task.setOnSucceeded(e -> {
      dlg.close();
      Path downloaded = task.getValue();

      if(unzipOnComplete){
        try {
          FileHelperFunctions.unzip(finalLocation, target);
        } catch (IOException ex) {
          Alert a = new Alert(Alert.AlertType.ERROR, "Extraction failed: " + ex.getMessage());
        }
      }
      if(executable){
        try {
          FileHelperFunctions.makeExecutable(target);
        } catch (Exception ex) {
          Alert a = new Alert(Alert.AlertType.ERROR, "Could not make the file executable: " + ex.getMessage());
        }
      }
      // Use the file
      System.out.println("Downloaded to " + downloaded);
    });

    task.setOnFailed(e -> {
      dlg.close();
      Throwable ex = task.getException();
      Alert a = new Alert(Alert.AlertType.ERROR, "Download failed: " + ex.getMessage());
      a.initOwner(owner);
      a.showAndWait();
    });

    Thread t = new Thread(task, "zide-downloader");
    t.setDaemon(true);
    t.start();
  }

  public static final class VarRow {
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty function = new SimpleStringProperty();
    private final SimpleStringProperty value = new SimpleStringProperty();

    public VarRow(String name, String type, String function, String value) {
      this.name.set(name);
      this.type.set(type);
      this.function.set(function);
      this.value.set(value);
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty typeProperty() { return type; }
    public StringProperty functionProperty() { return function; }
    public StringProperty valueProperty() { return value; }
  }
}
