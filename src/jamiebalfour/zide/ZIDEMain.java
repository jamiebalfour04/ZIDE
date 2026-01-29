package jamiebalfour.zide;

import jamiebalfour.FileHelperFunctions;
import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.balflaf_fx.WindowResizer;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfPanel;
import jamiebalfour.ui.components.BalfScrollbar;
import jamiebalfour.ui.components.BalfSearchBox;
import jamiebalfour.zpe.core.ZPEHelperFunctions;
import jamiebalfour.zpe.core.ZPEInstance;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;
import jamiebalfour.zpe.editor.ConsoleOutputTextArea;
import javafx.application.Application;
import javafx.embed.swing.SwingNode;
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
import javafx.scene.control.TextArea;
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

public class ZIDEMain extends Application {

  Stage _stage;
  ZPERuntimeEnvironment runtime;
  Label rightFooterLabel;
  ConsoleOutputTextArea consoleOutputTextArea;
  BalfScrollbar consoleScrollbar;
  Button runBtn;
  CheckMenuItem toggleTheme;



  public static Font loadAndRegister(String resourcePath) {
    try (InputStream in = ZIDEMain.class.getResourceAsStream(resourcePath)) {
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

  @Override
  public void start(Stage stage) {
    stage.initStyle(StageStyle.UNDECORATED);

    _stage = stage;

    stage.getIcons().add(
            new Image(Objects.requireNonNull(getClass().getResourceAsStream("/files/balflaf_fx/icons/jb.png")))
    );


    runtime = new ZPERuntimeEnvironment();

    var root = new BorderPane();
    root.getStyleClass().add("app-root");

    WindowResizer resizer = new WindowResizer();
    resizer.install(stage, root);

    // Top: menu + toolbar
    var top = new VBox(new BalfTitleBar(stage, "ZIDE"), buildMenuBar(), buildToolBar());
    root.setTop(top);


    // Left: project tree
    Node projectTree = buildProjectTree();
    Node leftPane = wrapTitled("Project", projectTree);
    //leftPane.setMinWidth(260);

    // Center: editor tabs
    var editors = buildEditorTabs();

    // Bottom: terminal + status bar
    var terminal = buildTerminal();
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

  private static final Preferences PREFS = Preferences.userNodeForPackage(ZIDEMain.class);
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
    newFile.setOnAction(_ -> newFile());

    var open = new MenuItem("Open…");
    open.setAccelerator(KeyCombination.keyCombination("Shortcut+O"));

    var save = new MenuItem("Save");
    save.setAccelerator(KeyCombination.keyCombination("Shortcut+S"));

    var exit = new MenuItem("Exit");
    exit.setOnAction(_ -> System.exit(0));

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
    toggleTheme.setOnAction(_ -> {
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
    view.getItems().add(toggleTheme);

    var run = new Menu("_Run");
    var runProject = new MenuItem("Run");
    runProject.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
    runProject.setOnAction(_ -> runCode());
    run.getItems().add(runProject);

    var help = new Menu("_Help");
    help.getItems().add(new MenuItem("About"));

    var bar = new MenuBar(file, edit, view, run, help);
    bar.getStyleClass().add("app-menubar");
    return bar;
  }

  private void runCode(){
    try {
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = (EditorTab) editorTabs.getSelectionModel().getSelectedItem();
      runBtn.getStyleClass().add("running");

      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), tab.getEditor().getText(), false);
      consoleOutputTextArea.runAsProcess(tempPath, false, "");

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ToolBar buildToolBar() {
    runBtn = new Button("Run");
    runBtn.getStyleClass().add("run");
    runBtn.setOnAction(_ -> {
      runCode();

    });

    var buildBtn = new Button("Build");
    var debugBtn = new Button("Debug");
    var sep1 = new Separator(Orientation.VERTICAL);

    var search = new TextField();
    search.setPromptText("Search…");
    search.getStyleClass().add("search-field");
    search.setMaxWidth(280);

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    var toolbar = new ToolBar(runBtn, buildBtn, debugBtn, sep1, new Label(" "), spacer, search);
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

  private Node buildEditorTabs() {
    editorTabs = new TabPane();
    editorTabs.getStyleClass().add("editor-tabs");
    editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

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
          rightFooterLabel.setText("YAS");
        } else{
          rightFooterLabel.setText("Text");
        }
      }

    });

    openTab("Untitled");

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


        BalfScrollbar scrollPane = new BalfScrollbar();
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

  private Tab createEditorTab(String title, CodeEditorView syntax, BalfScrollbar scrollPane, String path, Node content, Supplier<Boolean> hasContent) {
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

    SwingNode terminalNode = new SwingNode();

    SwingUtilities.invokeLater(() -> {
      consoleOutputTextArea =
              new ConsoleOutputTextArea("", Color.WHITE);

      consoleOutputTextArea.addProcessFinishedListener(new ConsoleOutputTextArea.ProcessFinishedListener() {

        @Override
        public void onProcessFinished() {
          runBtn.getStyleClass().remove("running");
        }
      });

      //output.setEditable(false);
      //output.setText("ZIDE terminal ready.\n");

      // Swing scrolling (important!)
      consoleScrollbar = new BalfScrollbar(consoleOutputTextArea);
      consoleScrollbar.setLightColour(Color.WHITE);
      consoleScrollbar.setDarkColour(Color.BLACK);
      consoleScrollbar.setBorder(BorderFactory.createEmptyBorder());
      consoleScrollbar.getVerticalScrollBar().setUnitIncrement(16);

      terminalNode.setContent(consoleScrollbar);
    });

    /*var input = new TextField();
    input.setPromptText("Type a command…");
    input.getStyleClass().add("terminal-input");
    input.setOnAction(e -> {
      var cmd = input.getText();
      if (cmd != null && !cmd.isBlank()) {
        output.appendText("> " + cmd + "\n");
        // TODO: wire to real process execution
        output.appendText("…not implemented yet\n");
        input.clear();
      }
    });*/

    var header = new HBox(title);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("pane-header");

    var box = new VBox(header, terminalNode);
    VBox.setVgrow(terminalNode, Priority.ALWAYS);
    box.getStyleClass().add("terminal-pane");
    box.setMinHeight(180);
    return box;
  }

  private Node buildStatusBar() {
    var left = new Label("Ready");
    var centre = new Label("Ln 1, Col 1");
    rightFooterLabel = new Label("Text");

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

  public static void main(String[] args) {

    if(args.length > 0) {
      if(args[0].equals("-h")){
        System.out.println("Usage: java -jar ZIDE.jar [-h]");
        System.out.println("--module-path /Users/jamiebalfour/Downloads/javafx-sdk-25.0.2/lib --add-modules javafx.controls,javafx.fxml,javafx.swing");
      } else if (args[0].equals("-g")) {
        launch(args);
      }
    } else{
      launch(args);
    }

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
}
