package jamiebalfour.zide.editor;

import jamiebalfour.helpers.FileHelperFunctions;
import jamiebalfour.helpers.HelperFunctions;
import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.balflaf_fx.BalfGlassMenuBar;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.console.InteractiveConsoleFX;
import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.parsers.json.MalformedJSONException;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfPanel;
import jamiebalfour.ui.components.BalfScrollbarPane;
import jamiebalfour.ui.components.BalfSearchBox;
import jamiebalfour.zide.ZIDEHelperFunctions;
import jamiebalfour.zide.core.ZIDE;
import jamiebalfour.zpe.core.*;
import jamiebalfour.zpe.core.ai.ChatGPT;
import jamiebalfour.zpe.core.exceptions.CompileException;
import jamiebalfour.zpe.core.types.ZPEList;
import jamiebalfour.zpe.core.types.ZPEString;
import jamiebalfour.zpe.gui.YASSCodeEditor;
import jamiebalfour.zpe.gui.ZPEMacroEditor;
import jamiebalfour.zpe.core.interfaces.ZPEType;
import jamiebalfour.zpe.core.types.ZPEMap;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.awt.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZIDEEditor extends Application {

  Stage _stage;
  ZPERuntimeEnvironment runtime;
  MenuButton languageSelector;
  InteractiveConsoleFX consoleOutputTextArea;
  private ZIDESystemTerminal systemTerminal;
  Button runBtn;
  Button buildBtn;
  Button debugBtn;
  Button stopExecutionBtn;
  Button stepOverButton;
  Button continueButton;
  Separator debugSeparator;
  BalfGlassMenuBar.GlassCheckMenuItem darkThemeMenuItem;
  private BalfGlassMenuBar applicationMenuBar;
  private TextField editorSearchField;
  private boolean darkThemeEnabled;
  BalfGlassMenuBar.GlassCheckMenuItem wordWrapMenuItem;
  private TableView<VarRow> varTable;
  private ObservableList<VarRow> varRows;
  private VBox variablesPane;
  private volatile ZPEDebugger.BreakPoint currentBreakpoint;
  private final Map<String, RuntimeVariable> breakpointVariables = new java.util.concurrent.ConcurrentHashMap<>();
  MenuItem runProject;
  MenuItem debugProject;
  MenuItem stopExecution = new MenuItem("Stop Execution");
  private Node formatDocumentMenuItem;
  private Node runScriptMenuItem;
  private Node debugScriptMenuItem;
  private Node stopScriptMenuItem;
  private Node compileScriptMenuItem;
  private Node compileNativeMenuItem;
  private Node scriptCompileSeparator;
  private Node scriptTranspileSeparator;
  private Node layoutBuilderMenuItem;
  private Node aiBuilderMenuItem;
  private final List<Node> transpileMenuItems = new ArrayList<>();
  private File currentProjectRoot;
  File projectDir;
  final Label statusLabel = new Label("Ready");
  final static String INSTALL_PATH = HelperFunctions.getAppDataDirectory("jamiebalfour/zide", System.getProperty("user.home") + "/jb/zide").getAbsolutePath() + "/"; //;
  Properties MAIN_PROPERTIES;
  boolean USE_WORD_WRAP = false;
  Node loadFromOnline;
  Node saveToOnline;
  String username = null;
  String password = null;
  boolean loggedIn = false;
  /** True only while a debug launch owns the debugger controls and profiler session. */
  private volatile boolean debuggingSession;
  private WatchService projectWatchService;
  private Thread projectWatchThread;
  private StackPane workspaceStack;
  private Node layoutBuilderOverlay;
  private volatile boolean watchingProjectDirectory;
  private final AtomicBoolean projectTreeRefreshQueued = new AtomicBoolean(false);
  private String cloudFileName = "";
  private final Map<String, LanguageSupport> languageSupports = new LinkedHashMap<>();
  private boolean languageSupportsRegistered;
  private List<String> zpeedyKeywords;
  private double normalWindowX = Double.NaN;
  private double normalWindowY = Double.NaN;
  private double normalWindowWidth = 1280;
  private double normalWindowHeight = 800;
  private boolean windowSettingsSaved;



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
    Application.launch(ZIDEEditor.class, args);
  }

  private File chooseOutputFile(Stage owner, File currentFile, FileChooser.ExtensionFilter... filters) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Save compiled application");

    String baseName = currentFile == null
            ? "application"
            : currentFile.getName().replaceFirst("\\.[^.]+$", "");

    chooser.setInitialFileName(baseName);

    chooser.getExtensionFilters().addAll(filters);

    File selected = chooser.showSaveDialog(owner);

    if (selected == null) {
      return null;
    }

    return selected;
  }

  private void saveProps(){
    FileOutputStream output = null;
    try {
      output = new FileOutputStream(INSTALL_PATH + "/zide.properties");
      // save properties to project root folder
      MAIN_PROPERTIES.store(output, "ZIDE Properties");
    } catch (IOException e) {
      //Ignore
    }
  }

  private void restoreWindowSettings(Stage stage) {
    double width = propertyDouble("WIDTH", 1280);
    double height = propertyDouble("HEIGHT", 800);
    double x = propertyDouble("XPOS", Double.NaN);
    double y = propertyDouble("YPOS", Double.NaN);

    width = Math.max(stage.getMinWidth(), width);
    height = Math.max(stage.getMinHeight(), height);
    if (Double.isFinite(x) && Double.isFinite(y)
            && !Screen.getScreensForRectangle(x, y, width, height).isEmpty()) {
      stage.setX(x);
      stage.setY(y);
      normalWindowX = x;
      normalWindowY = y;
    }
    stage.setWidth(width);
    stage.setHeight(height);
    normalWindowWidth = width;
    normalWindowHeight = height;
    stage.setMaximized(Boolean.parseBoolean(MAIN_PROPERTIES.getProperty("MAXIMISED", "false")));
  }

  private double propertyDouble(String name, double fallback) {
    try {
      return Double.parseDouble(MAIN_PROPERTIES.getProperty(name));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private void trackWindowSettings(Stage stage) {
    javafx.beans.value.ChangeListener<Number> boundsChanged = (observable, oldValue, newValue) -> {
      if (!stage.isMaximized()) captureNormalWindowBounds(stage);
    };
    stage.xProperty().addListener(boundsChanged);
    stage.yProperty().addListener(boundsChanged);
    stage.widthProperty().addListener(boundsChanged);
    stage.heightProperty().addListener(boundsChanged);
    stage.maximizedProperty().addListener((observable, wasMaximized, maximized) -> {
      if (!maximized) Platform.runLater(() -> captureNormalWindowBounds(stage));
    });
  }

  private void captureNormalWindowBounds(Stage stage) {
    if (!stage.isShowing() || stage.isMaximized()) return;
    normalWindowX = stage.getX();
    normalWindowY = stage.getY();
    normalWindowWidth = stage.getWidth();
    normalWindowHeight = stage.getHeight();
  }

  private void saveWindowSettings() {
    if (_stage == null || windowSettingsSaved) return;
    windowSettingsSaved = true;
    if (!_stage.isMaximized()) captureNormalWindowBounds(_stage);
    if (Double.isFinite(normalWindowX)) MAIN_PROPERTIES.setProperty("XPOS", Double.toString(normalWindowX));
    if (Double.isFinite(normalWindowY)) MAIN_PROPERTIES.setProperty("YPOS", Double.toString(normalWindowY));
    MAIN_PROPERTIES.setProperty("WIDTH", Double.toString(normalWindowWidth));
    MAIN_PROPERTIES.setProperty("HEIGHT", Double.toString(normalWindowHeight));
    MAIN_PROPERTIES.setProperty("MAXIMISED", Boolean.toString(_stage.isMaximized()));
    saveProps();
  }

  @Override
  public void start(Stage stage) {

    stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream(HelperFunctions.isMac() ? "/files/zide_macos.png" : "/files/zide.png"))));
    stage.initStyle(StageStyle.UNDECORATED);

    _stage = stage;

    if(!(new File(INSTALL_PATH).exists())){
      try {
        new File(INSTALL_PATH).mkdirs();
      } catch (Exception e) {}
    }

    try{
      MAIN_PROPERTIES = HelperFunctions.readProperties(INSTALL_PATH + "/zide.properties");
    } catch (Exception ex) {
      MAIN_PROPERTIES = new Properties();
      saveProps();

    }


    if(MAIN_PROPERTIES.containsKey("ENABLE_WORD_WRAP")){
      if(MAIN_PROPERTIES.getProperty("ENABLED_WORD_WRAP").equals("true")){
        USE_WORD_WRAP = true;
      }
    }

    stage.setMinWidth(600);
    stage.setMinHeight(400);

  /*  stage.getIcons().add(
            new Image(Objects.requireNonNull(getClass().getResourceAsStream("/files/balflaf_fx/icons/jb.png")))
    );
*/
    //Application.setUserAgentStylesheet(STYLESHEET_CASPIAN);

    runtime = new ZPERuntimeEnvironment();
    darkThemeEnabled = BalfLafManager.getInstance().isDarkModeEnabled();

    editorTabs = new TabPane();

    var root = new BorderPane();
    root.getStyleClass().add("app-root");

    EventHandler<ActionEvent> aboutHandler = e -> ZIDEAboutWindow.show(_stage);

    // Keep the title bar outside the workspace stack so full-workspace tools
    // can cover the menus and editor without covering the window controls.
    root.setTop(new BalfTitleBar(stage, "ZIDE", aboutHandler));
    Node menuBar = buildMenuBar();
    Node toolBar = buildToolBar();
    updateLanguageCommands(null);


    BalfTitleBar.addWindowResizing(stage, root);

    projectDir = new File(System.getProperty("user.home") + "/Documents/YASS Projects/");

    if(!projectDir.exists()) {
      projectDir.mkdirs();
    }

    // Left: project tree
    Node projectTree = buildProjectTree(projectDir);
    currentProjectRoot = projectDir;
    startProjectDirectoryWatcher(projectDir);
    Node leftPane = wrapTitled("Project", projectTree);
    //leftPane.setMinWidth(260);

    // Center: editor tabs
    var editors = buildEditorTabs();

    // Bottom: console + status bar
    var console = buildconsole();

    consoleOutputTextArea.addProcessFinishedListener(() -> {
      stopExecutionBtn.setVisible(false);
      stepOverButton.setVisible(false);
      continueButton.setVisible(false);
      debugSeparator.setVisible(false);
      clearRows();
    });
    var bottom = new VBox(console, buildStatusBar());
    VBox.setVgrow(console, Priority.ALWAYS);

    // Split layout: left + center, then center + bottom
    var horizontalSplit = new SplitPane(leftPane, editors);
    setLeftSplitWidth(horizontalSplit, 260);

    var verticalSplit = new SplitPane(horizontalSplit, bottom);
    verticalSplit.setOrientation(Orientation.VERTICAL);

    // store height in pixels (e.g. console height)
    final double[] bottomHeight = {250};

    // set initial position after layout
    Platform.runLater(() -> {
      verticalSplit.setDividerPositions(
              1.0 - (bottomHeight[0] / verticalSplit.getHeight())
      );
    });

// when user drags → update pixel height
    verticalSplit.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
      double total = verticalSplit.getHeight();
      bottomHeight[0] = (1.0 - newVal.doubleValue()) * total;
    });

// when window resizes → keep pixel height
    verticalSplit.heightProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal.doubleValue() > 0) {
        verticalSplit.setDividerPositions(
                1.0 - (bottomHeight[0] / newVal.doubleValue())
        );
      }
    });



// store width in pixels
    final double[] leftWidth = {260};

// set initial width AFTER layout
    Platform.runLater(() -> {
      horizontalSplit.setDividerPositions(leftWidth[0] / horizontalSplit.getWidth());
    });

// when user drags divider → update pixel width
    horizontalSplit.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
      leftWidth[0] = newVal.doubleValue() * horizontalSplit.getWidth();
    });

// when window resizes → keep pixel width
    horizontalSplit.widthProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal.doubleValue() > 0) {
        horizontalSplit.setDividerPositions(leftWidth[0] / newVal.doubleValue());
      }
    });

    VBox applicationWorkspace = new VBox(menuBar, toolBar, verticalSplit);
    VBox.setVgrow(verticalSplit, Priority.ALWAYS);
    workspaceStack = new StackPane(applicationWorkspace);
    root.setCenter(workspaceStack);

    var scene = new Scene(root, 1280, 800);
    //scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/zide.css")).toExternalForm());
    root.pseudoClassStateChanged(
            javafx.css.PseudoClass.getPseudoClass("dark"),
            isDarkThemeEnabled()
    );

    stage.setTitle("ZIDE");
    stage.setScene(scene);
    restoreWindowSettings(stage);
    trackWindowSettings(stage);
    registerKeyboardShortcuts(scene);
    stage.setOnCloseRequest(event -> {
      saveWindowSettings();
      stopProjectDirectoryWatcher();
    });
    stage.show();
    Platform.runLater(() -> {
      captureNormalWindowBounds(stage);
      applyWorkspaceDarkMode(isDarkThemeEnabled());
    });

    boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

    if (isMac) {
      root.getStyleClass().add("mac-window");
    }
  }

  @Override
  public void stop() {
    saveWindowSettings();
    stopProjectDirectoryWatcher();
  }

  private void setLeftSplitWidth(SplitPane splitPane, double pixels) {
    Platform.runLater(() -> {
      double total = splitPane.getWidth();

      if (total <= 0) {
        return;
      }

      splitPane.setDividerPositions(pixels / total);
    });
  }

  private void registerKeyboardShortcuts(Scene scene) {
    scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
            this::newFile
    );
    scene.getAccelerators().put(
            new KeyCodeCombination(
                    KeyCode.N,
                    KeyCombination.SHORTCUT_DOWN,
                    KeyCombination.SHIFT_DOWN
            ),
            this::newProject
    );
    scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            this::openProjectFolder
    );
    scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
            this::saveCurrentFile
    );
    scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
            () -> {
              if (editorSearchField != null) {
                editorSearchField.requestFocus();
                editorSearchField.selectAll();
              }
            }
    );
  }

  private static final Preferences PREFS = Preferences.userNodeForPackage(ZIDEEditor.class);
  private static final String KEY_LAST_DIR = System.getProperty("user.home");//"/Users/jamiebalfour/Documents/";

  private File newFile() {
    File targetDir = getTargetDirectoryForNewFile();

    if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
      new Alert(Alert.AlertType.ERROR, "No valid target folder is selected.").showAndWait();
      return null;
    }

    Dialog<File> dialog = new Dialog<>();
    dialog.initOwner(_stage);
    dialog.setTitle("New File");
    dialog.setHeaderText("Create a new file in " + targetDir.getName());

    ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

    TextField nameField = new TextField("Untitled");

    ComboBox<String> extensionBox = new ComboBox<>();
    extensionBox.getItems().addAll(
            "yas",
            "zps",
            "py",
            "sqarl",
            "ywp",
            "txt"
    );
    extensionBox.setValue("yas");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.add(new Label("File name"), 0, 0);
    grid.add(nameField, 1, 0);
    grid.add(new Label("Extension"), 0, 1);
    grid.add(extensionBox, 1, 1);

    dialog.getDialogPane().setContent(grid);

    Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
    createButton.setDisable(false);

    nameField.textProperty().addListener((obs, oldVal, newVal) -> {
      createButton.setDisable(newVal.trim().isEmpty());
    });

    dialog.setResultConverter(button -> {
      if (button != createButtonType) {
        return null;
      }

      String baseName = nameField.getText().trim();
      String ext = extensionBox.getValue();

      if (baseName.isEmpty()) {
        return null;
      }

      String fullName = baseName.endsWith("." + ext) ? baseName : baseName + "." + ext;
      return new File(targetDir, fullName);
    });

    Optional<File> result = dialog.showAndWait();

    if (result.isEmpty()) {
      return null;
    }

    File newFile = result.get();

    if (newFile.exists()) {
      new Alert(Alert.AlertType.ERROR, "That file already exists.").showAndWait();
      return null;
    }

    try {
      if (!newFile.createNewFile()) {
        new Alert(Alert.AlertType.ERROR, "Failed to create file.").showAndWait();
        return null;
      }

      buildProjectTree(currentProjectRoot);
      openTab(newFile.getName(), newFile.getAbsolutePath());
      return newFile;

    } catch (IOException ex) {
      ex.printStackTrace();
      new Alert(Alert.AlertType.ERROR, "Failed to create file:\n" + ex.getMessage()).showAndWait();
      return null;
    }
  }

  private File getTargetDirectoryForNewFile() {
    TreeItem<File> selectedItem = projectTree.getSelectionModel().getSelectedItem();

    if (selectedItem == null || selectedItem.getValue() == null) {
      return currentProjectRoot;
    }

    File selected = selectedItem.getValue();

    if (selected.isDirectory()) {
      return selected;
    }

    return selected.getParentFile();
  }


  private BalfGlassMenuBar buildMenuBar() {
    BalfGlassMenuBar bar = new BalfGlassMenuBar();
    applicationMenuBar = bar;
    bar.getStyleClass().add("main-menu-bar");

    var file = bar.menu("File");
    file.createItem("New Project", "⇧⌘N", this::newProject, true);
    file.createItem("New File", "⌘N", this::newFile, true);
    file.createItem("Open project folder", "⌘O", this::openProjectFolder, true);
    file.separator();
    file.createItem("Save", "⌘S", this::saveCurrentFile, true);
    file.createItem("Save As...", "", this::saveCurrentFileAs, true);
    file.separator();
    file.createItem("Exit", "", () -> System.exit(0), true);

    var edit = bar.menu("Edit");

    edit.createItem("Undo", "⌘Z", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().undo();
    }, true);

    edit.createItem("Redo", "⇧⌘Z", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().redo();
    }, true);

    edit.separator();

    edit.createItem("Cut", "⌘X", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().cut();
    }, true);

    edit.createItem("Copy", "⌘C", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().copy();
    }, true);

    edit.createItem("Paste", "⌘V", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().paste();
    }, true);

    edit.separator();

    edit.createItem("Select All", "⌘A", () -> {
      if (getCurrentTab() != null) getCurrentTab().getEditor().selectAll();
    });

    formatDocumentMenuItem = edit.createItem("Format document", "", this::beautifyCurrentDocument);

    var viewMenu = bar.menu("View");

    darkThemeMenuItem = viewMenu.checkItem("Dark theme",
            darkThemeEnabled,
            selected -> {
              darkThemeEnabled = selected;
              BalfLafManager.getInstance().toggleDarkMode(selected);
              applicationMenuBar.setDarkMode(selected);
              invertImages();

              applyWorkspaceDarkMode(selected);

              for (Tab t : editorTabs.getTabs()) {
                EditorTab tab = (EditorTab) t;

                if (selected) {
                  tab.switchOnDarkMode();
                } else {
                  tab.switchOffDarkMode();
                }

              }
            });

    wordWrapMenuItem = viewMenu.checkItem("Word-wrap", USE_WORD_WRAP, selected -> {

      USE_WORD_WRAP = selected;

      MAIN_PROPERTIES.setProperty("USE_WORD_WRAP", selected.toString());

      saveProps();

    });


    var script = bar.menu("Script");

    runScriptMenuItem = script.createItem("Run", "F5", this::runCode);
    debugScriptMenuItem = script.createItem("Debug", "⇧⌘R", this::debug);
    stopScriptMenuItem = script.createItem("Stop Execution", "⇧⌘S", () -> consoleOutputTextArea.destroyCurrentProcess());
    scriptCompileSeparator = script.separatorNode();
    compileScriptMenuItem = script.createItem("Compile project to ZEX", "", this::compileCurrentLanguage);
    compileNativeMenuItem = script.createItem("Compile project Native", "", this::compileNative);
    scriptTranspileSeparator = script.separatorNode();
    List<String> transpilers = ZPEKit.listTranspilerNames();
    if (!transpilers.isEmpty()) {
      for (String language : transpilers) {
        String transpilerName = ZPEKit.getTranspilerByName(language).transpilerName();
        Node transpileItem = script.createItem(
                "Transpile to " + language + " (" + transpilerName + ")",
                "",
                () -> transpileCurrentFile(language)
        );
        transpileMenuItems.add(transpileItem);
      }
    }

    var tools = bar.menu("Tools");

    tools.createItem("Open Macro Scripting Interface", "", this::openMSI);
    layoutBuilderMenuItem = tools.createItem("Open ZUI Layout Builder", "", this::openLayoutBuilder);
    aiBuilderMenuItem = tools.createItem("Build with AI Assistant", "", this::openAIBuilder);



    var git = bar.menu("Git");

    git.createItem("Clone", "", this::cloneRepo);
    git.separator();
    git.createItem("Repository Status", "", this::showGitStatus);
    git.createItem("Commit All Changes", "", this::commitGitChanges);
    git.separator();
    git.createItem("Pull", "", this::pullGitChanges);
    git.createItem("Push", "", this::pushGitChanges);


    var zpeOnline = bar.menu("ZPE Online");

    zpeOnline.createItem("Login to ZPE Online", "", this::loginToZPEOnline);
    zpeOnline.separator();
    loadFromOnline = zpeOnline.createItem("Load from ZPE Online", "", () -> {});

    loadFromOnline.setOnMouseClicked(e -> loadFromUsersCloudFX());
    saveToOnline = zpeOnline.createItem("Save to ZPE Online", "", this::saveToZPEOnline);


    loadFromOnline.setDisable(true);
    saveToOnline.setDisable(true);

    var help = bar.menu("Help");

    help.createItem("About", "", () -> ZIDEAboutWindow.show(_stage));
    help.separator();
    help.createItem("Download ZPE Runtime Environment", "", this::downloadZPERuntime);
    help.createItem("Download ZPE Native", "", this::downloadZPENative);

    updateLanguageCommands(null);
    bar.setDarkMode(darkThemeEnabled);
    return bar;
  }

  private void applyWorkspaceDarkMode(boolean enabled) {
    Scene scene = _stage == null ? null : _stage.getScene();
    if (scene == null || scene.getRoot() == null) return;

    scene.getRoot().pseudoClassStateChanged(
            javafx.css.PseudoClass.getPseudoClass("dark"),
            enabled
    );
    applyConsoleDarkMode(enabled);
    scene.getRoot().applyCss();
  }

  /** Keeps ZIDE compatible with BalfClassLibrary builds from before console theming. */
  private void applyConsoleDarkMode(boolean enabled) {
    if (consoleOutputTextArea == null) return;
    try {
      consoleOutputTextArea.getClass()
              .getMethod("setDarkMode", boolean.class)
              .invoke(consoleOutputTextArea, enabled);
    } catch (ReflectiveOperationException ignored) {
      // Older library builds retain their original console appearance.
    }
  }

  private boolean isDarkThemeEnabled() {
    return darkThemeEnabled;
  }



  private void openProjectFolder() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Open project folder");

    File chosen = chooser.showDialog(_stage);
    if (chosen != null) {
      currentProjectRoot = chosen;
      projectDir = chosen;
      buildProjectTree(chosen);
      startProjectDirectoryWatcher(chosen);
      if (systemTerminal != null) {
        systemTerminal.setWorkingDirectory(chosen.toPath());
      }
    }
  }

  private void saveCurrentFile() {
    if (getCurrentTab() == null) return;

    try {
      FileHelperFunctions.writeFile(
              getCurrentTab().getPath(),
              getCurrentTab().getEditor().getText(),
              false
      );
      getCurrentTab().setHasChanges(false);
    } catch (IOException ex) {
      Alert alert = new Alert(Alert.AlertType.ERROR);
      alert.setTitle("Error");
      alert.setHeaderText("Error saving file");
      alert.setContentText(ex.getMessage());
      alert.showAndWait();
    }
  }

  private void saveCurrentFileAs() {
    EditorTab tab = getCurrentTab();
    if (tab == null) return;

    File current = tab.getPath() == null || tab.getPath().isBlank()
            ? null : new File(tab.getPath());
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Save As");
    if (current != null) {
      chooser.setInitialFileName(current.getName());
      File parent = current.getParentFile();
      if (parent != null && parent.isDirectory()) chooser.setInitialDirectory(parent);
    } else {
      chooser.setInitialFileName("Untitled");
    }

    FileChooser.ExtensionFilter yass = new FileChooser.ExtensionFilter("YASS (*.yas)", "*.yas");
    FileChooser.ExtensionFilter zpeedy = new FileChooser.ExtensionFilter("Zpeedy Script (*.zps)", "*.zps");
    FileChooser.ExtensionFilter python = new FileChooser.ExtensionFilter("Python (*.py)", "*.py");
    FileChooser.ExtensionFilter sqarl = new FileChooser.ExtensionFilter("SQARL (*.sqarl)", "*.sqarl");
    FileChooser.ExtensionFilter ywp = new FileChooser.ExtensionFilter("YWP (*.ywp)", "*.ywp");
    FileChooser.ExtensionFilter text = new FileChooser.ExtensionFilter("Text (*.txt)", "*.txt");
    FileChooser.ExtensionFilter all = new FileChooser.ExtensionFilter("All files", "*.*");
    chooser.getExtensionFilters().addAll(yass, zpeedy, python, sqarl, ywp, text, all);

    LanguageSupport language = languageSupports.get(tab.getLanguageId());
    if (language != null) {
      if ("python".equals(language.id)) chooser.setSelectedExtensionFilter(python);
      else if ("zpeedy".equals(language.id)) chooser.setSelectedExtensionFilter(zpeedy);
      else if ("sqarl".equals(language.id)) chooser.setSelectedExtensionFilter(sqarl);
      else if ("ywp".equals(language.id)) chooser.setSelectedExtensionFilter(ywp);
      else if ("txt".equals(language.id)) chooser.setSelectedExtensionFilter(text);
      else chooser.setSelectedExtensionFilter(yass);
    }

    File selected = chooser.showSaveDialog(_stage);
    if (selected == null) return;
    String extension = extensionForFilter(chooser.getSelectedExtensionFilter());
    if (!extension.isEmpty() && !selected.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
      selected = new File(selected.getParentFile(), selected.getName() + extension);
    }

    try {
      FileHelperFunctions.writeFile(selected.getAbsolutePath(), tab.getEditor().getText(), false);
      tab.setPath(selected.getAbsolutePath());
      tab.setDisplayTitle(selected.getName());
      tab.setHasChanges(false);
      if (currentProjectRoot != null) buildProjectTree(currentProjectRoot);
      statusLabel.setText("Saved " + selected.getName());
    } catch (IOException exception) {
      showError("Save As failed", exception.getMessage());
    }
  }

  private String extensionForFilter(FileChooser.ExtensionFilter filter) {
    if (filter == null || filter.getExtensions().isEmpty()) return "";
    String pattern = filter.getExtensions().get(0);
    return pattern.startsWith("*.") ? pattern.substring(1) : "";
  }


  private String askForRepoUrl() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Clone Repository");
    dialog.setHeaderText("Clone from GitHub");
    dialog.setContentText("Repository URL:");

    Optional<String> result = dialog.showAndWait();

    return result.orElse(null);
  }

  void loadFromUsersCloudFX() {
    HashMap<String, String> arguments = new HashMap<>();
    arguments.put("username", username);
    arguments.put("password", password);
    ZPEList files = getUsersCloudFileList();

    if (files == null) {
      return;
    }

    showZPEOnlineBrowser(arguments, files);
  }

  private void cloneRepo() {
    String repoUrl = askForRepoUrl();
    if (repoUrl == null || repoUrl.trim().isEmpty()) return;

    String repositoryName = repositoryNameFromUrl(repoUrl.trim());
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose where to clone " + repositoryName);
    chooser.setInitialDirectory(currentProjectRoot != null && currentProjectRoot.isDirectory()
            ? currentProjectRoot : new File(System.getProperty("user.home"), "Documents"));
    File parentDirectory = chooser.showDialog(_stage);
    if (parentDirectory == null) return;

    File destination = new File(parentDirectory, repositoryName);
    if (destination.exists()) {
      showError("Clone Repository", "The destination folder already exists:\n" + destination);
      return;
    }

    runGitTask("Cloning " + repositoryName, () -> {
      try (Git ignored = Git.cloneRepository().setURI(repoUrl.trim()).setDirectory(destination).call()) {
        Platform.runLater(() -> openGitProject(destination));
        return "Cloned " + repositoryName + ".";
      }
    });
  }

  private String repositoryNameFromUrl(String repositoryUrl) {
    String trimmed = repositoryUrl.endsWith("/")
            ? repositoryUrl.substring(0, repositoryUrl.length() - 1) : repositoryUrl;
    int separator = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf(':'));
    String name = separator >= 0 ? trimmed.substring(separator + 1) : trimmed;
    return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
  }

  private void openGitProject(File directory) {
    currentProjectRoot = directory;
    projectDir = directory;
    buildProjectTree(directory);
    startProjectDirectoryWatcher(directory);
    if (systemTerminal != null) systemTerminal.setWorkingDirectory(directory.toPath());
  }

  private Git openCurrentGitProject() throws Exception {
    if (currentProjectRoot == null) throw new IOException("Open a project folder first.");
    return Git.open(currentProjectRoot);
  }

  private void showGitStatus() {
    runGitTask("Checking Git status", () -> {
      try (Git git = openCurrentGitProject()) {
        org.eclipse.jgit.api.Status status = git.status().call();
        StringBuilder summary = new StringBuilder();
        appendGitStatus(summary, "Modified", status.getModified());
        appendGitStatus(summary, "Added", status.getAdded());
        appendGitStatus(summary, "Changed", status.getChanged());
        appendGitStatus(summary, "Removed", status.getRemoved());
        appendGitStatus(summary, "Missing", status.getMissing());
        appendGitStatus(summary, "Untracked", status.getUntracked());
        return summary.length() == 0 ? "Working tree is clean." : summary.toString();
      }
    });
  }

  private void appendGitStatus(StringBuilder summary, String label, Set<String> paths) {
    if (paths.isEmpty()) return;
    if (summary.length() > 0) summary.append("\n\n");
    summary.append(label).append(":\n").append(String.join("\n", paths));
  }

  private void commitGitChanges() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.initOwner(_stage);
    dialog.setTitle("Commit Changes");
    dialog.setHeaderText("Commit all project changes");
    dialog.setContentText("Commit message:");
    Optional<String> message = dialog.showAndWait();
    if (message.isEmpty() || message.get().trim().isEmpty()) return;
    if (getCurrentTab() != null && getCurrentTab().getPath() != null) saveCurrentFile();
    runGitTask("Creating commit", () -> {
      try (Git git = openCurrentGitProject()) {
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
        return "Created commit " + git.commit().setMessage(message.get().trim()).call().getName().substring(0, 8) + ".";
      }
    });
  }

  private void pullGitChanges() {
    runGitTask("Pulling changes", () -> {
      try (Git git = openCurrentGitProject()) {
        var result = git.pull().call();
        return result.isSuccessful() ? "Pull completed." : "Pull completed with conflicts; resolve them before committing.";
      }
    });
  }

  private void pushGitChanges() {
    runGitTask("Pushing changes", () -> {
      try (Git git = openCurrentGitProject()) {
        git.push().call();
        return "Push completed.";
      }
    });
  }

  /** Executes Git work away from the JavaFX application thread and reports the final outcome on it. */
  private void runGitTask(String activity, Callable<String> action) {
    statusLabel.setText(activity + "…");
    Thread worker = new Thread(() -> {
      try {
        String result = action.call();
        Platform.runLater(() -> {
          statusLabel.setText("Ready");
          Alert alert = new Alert(Alert.AlertType.INFORMATION, result);
          alert.initOwner(_stage);
          alert.setTitle("Git");
          alert.setHeaderText(activity);
          alert.showAndWait();
        });
      } catch (Exception exception) {
        Platform.runLater(() -> {
          statusLabel.setText("Ready");
          showError("Git operation failed", exception.getMessage());
        });
      }
    }, "zide-git-operation");
    worker.setDaemon(true);
    worker.start();
  }

  private ZPEList getUsersCloudFileList() {
    if (username.isEmpty() || password.isEmpty()) {
      if (!loggedIn) {
        return null;
      }
    }

    try {
      Map<String, String> arguments = new HashMap<>();
      arguments.put("username", username);
      arguments.put("password", password);

      String response = HelperFunctions.makePOSTRequest(
              ZPEInstance.getOnlinePathProperty() + "/get.php?type=list&version=10",
              arguments
      );

      ZenithJSONParser parser = new ZenithJSONParser();
      ZPEMap json = (ZPEMap) parser.jsonDecode(response, false);

      if (!json.containsKey(new ZPEString("result"))) {
        return null;
      }

      int result = HelperFunctions.stringToInteger(
              json.get(new ZPEString("result")).toString()
      );

      if (result != 1) {
        return null;
      }

      return (ZPEList) json.get(new ZPEString("list"));

    } catch (Exception e) {
      ZPE.log(e.getMessage());
      return null;
    }
  }

  private Node createZPEOnlineFileCard(String name, String id, Runnable openAction) {
    VBox card = new VBox(8);
    card.getStyleClass().add("zpe-online-file-card");
    card.setPadding(new Insets(14));
    card.setPrefWidth(220);
    card.setUserData(name);

    Label icon = new Label("☁");
    icon.getStyleClass().add("zpe-online-file-icon");

    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("zpe-online-file-name");
    nameLabel.setWrapText(true);

    Label idLabel = new Label("ID: " + id);
    idLabel.getStyleClass().add("zpe-online-file-meta");

    Button openButton = new Button("Open");
    openButton.getStyleClass().add("zpe-online-open-button");
    openButton.setMaxWidth(Double.MAX_VALUE);
    openButton.setOnAction(e -> openAction.run());

    card.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        openAction.run();
      }
    });

    card.getChildren().addAll(icon, nameLabel, idLabel, openButton);

    return card;
  }

  void loadFromCloudFile(String file, Map<String, String> arguments, ZPEMap selections, boolean publicrepo) {
    jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();


    arguments.put("id", new ZPEString(file).toString());
    String s;
    try {
      if (!publicrepo) {
        s = HelperFunctions
                .makePOSTRequest(ZPEInstance.getOnlinePathProperty() + "/get.php?type=file&version=10", arguments);
      } else {
        s = HelperFunctions
                .makePOSTRequest(ZPEInstance.getOnlinePathProperty() + "/public.php?type=file&version=10", arguments);
      }


      ZPEMap results = (ZPEMap) p.jsonDecode(s, false);

      // Turn JSON to results
      String code;
      code = URLDecoder.decode(results.get(new ZPEString("string")).toString(), "UTF-8");

      code = code.replace("\\n", System.lineSeparator());
      openTab(arguments.get("name"));
      getCurrentTab().getEditor().setText(code);
      getCurrentTab().setHasChanges(false);

      cloudFileName = arguments.getOrDefault("name", "");

      //lastCloudFileOpened = file;
      //lastFileOpened = "";

      //setTitleBarText(file);
      //setTitle("ZPE Editor");

      if (publicrepo) {
        //lastCloudFileOpened = "";
      }
    } catch (Exception e) {
      //JOptionPane.showMessageDialog(_frame, "File cannot be opened.", "Error", JOptionPane.ERROR_MESSAGE);
    }

  }

  private void loadZPEOnlineFile(Map<String, String> arguments, String id, String name) {
    try {
      Map<String, String> loadArgs = new HashMap<>(arguments);
      loadArgs.put("id", id);
      loadArgs.put("name", name);


      loadFromCloudFile(id, loadArgs, new ZPEMap(), false);

    } catch (Exception e) {
      ZPE.log(e.getMessage());
    }
  }

  private void showZPEOnlineBrowser(Map<String, String> arguments, ZPEList files) {
    Stage dialog = new Stage();
    dialog.initOwner(_stage);
    dialog.setTitle("Load from ZPE Online");

    VBox root = new VBox(14);
    root.setPadding(new Insets(18));
    root.getStyleClass().add("zpe-online-browser");

    Label title = new Label("Load from ZPE Online");
    title.getStyleClass().add("zpe-online-title");

    TextField search = new TextField();
    search.setPromptText("Search your cloud files...");
    search.getStyleClass().add("zpe-online-search");

    FlowPane fileGrid = new FlowPane();
    fileGrid.setHgap(12);
    fileGrid.setVgap(12);
    fileGrid.setPadding(new Insets(4));

    ScrollPane scrollPane = new ScrollPane(fileGrid);
    scrollPane.setFitToWidth(true);
    scrollPane.getStyleClass().add("zpe-online-scroll");

    ArrayList<Node> cards = new ArrayList<>();

    for (Object item : files) {
      ZPEMap file = (ZPEMap) item;

      String name = file.get(new ZPEString("name")).toString();
      String id = file.get(new ZPEString("id")).toString();

      Node card = createZPEOnlineFileCard(name, id, () -> {
        dialog.close();
        loadZPEOnlineFile(arguments, id, name);
      });

      cards.add(card);
      fileGrid.getChildren().add(card);
    }

    search.textProperty().addListener((obs, oldText, newText) -> {
      String q = newText == null ? "" : newText.toLowerCase();

      fileGrid.getChildren().setAll(
              cards.stream()
                      .filter(card -> {
                        Object name = card.getUserData();
                        return name != null && name.toString().toLowerCase().contains(q);
                      })
                      .toList()
      );
    });

    root.getChildren().addAll(title, search, scrollPane);

    Scene scene = new Scene(root, 760, 520);
    scene.getStylesheets().add(
            getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm()
    );

    dialog.setScene(scene);
    dialog.show();
  }

  private void newProject() {
    if (currentProjectRoot == null) {
      new Alert(Alert.AlertType.WARNING, "Please open a folder first.").showAndWait();
      return;
    }

    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("New Project");
    dialog.setHeaderText("Create a new project");
    dialog.setContentText("Project name:");

    Optional<String> result = dialog.showAndWait();

    result.ifPresent(name -> {
      String trimmed = name.trim();

      if (trimmed.isEmpty()) {
        new Alert(Alert.AlertType.ERROR, "Project name cannot be empty.").showAndWait();
        return;
      }

      File newDir = new File(currentProjectRoot, trimmed);

      if (newDir.exists()) {
        new Alert(Alert.AlertType.ERROR, "A folder with that name already exists.").showAndWait();
        return;
      }

      if (!newDir.mkdir()) {
        new Alert(Alert.AlertType.ERROR, "Failed to create project folder.").showAndWait();
        return;
      }

      buildProjectTree(currentProjectRoot);
    });
  }

  /**
   * Builds the explicitly selected project folder, or the current tab's parent
   * folder when no project folder is selected in the project tree.
   */
  private void compileProject() {
    EditorTab currentTab = getCurrentTab();
    if (currentTab == null || currentTab.getPath() == null || currentTab.getPath().isBlank()) {
      showError("Error compiling project", "Save the current file before compiling its project.");
      return;
    }

    File currentFile = new File(currentTab.getPath()).getAbsoluteFile();
    if (!currentFile.isFile()) {
      showError("Error compiling project", "The current file must exist before its project can be compiled.");
      return;
    }

    // Directory compilation reads files from disk, so include unsaved work.
    if (currentTab.hasChanges()) {
      saveCurrentFile();
      if (currentTab.hasChanges()) return;
    }

    File projectDirectory = getCompileProjectDirectory(currentFile);
    if (projectDirectory == null || !projectDirectory.isDirectory()) {
      showError("Error compiling project", "Select or open a project folder before compiling.");
      return;
    }

    File outputLocation = chooseOutputFile(
            _stage,
            currentFile,
            new FileChooser.ExtensionFilter("YASS Executable", "*.yex")
    );
    if (outputLocation == null) return;

    boolean compiled = ZPEKit.compileDirectory(
            projectDirectory.getAbsolutePath(), outputLocation.getAbsolutePath(), "", ""
    );
    if (compiled) {
      Alert alert = new Alert(Alert.AlertType.INFORMATION, "Successfully compiled " + projectDirectory.getName() + ".");
      alert.setHeaderText(null);
      alert.showAndWait();
    } else {
      showError("Error compiling project", "The selected folder could not be compiled. Check that it contains valid YASS source files.");
    }
  }

  private void compileCurrentLanguage() {
    EditorTab tab = getCurrentTab();
    LanguageSupport language = tab == null ? null : languageSupports.get(tab.getLanguageId());
    if (language == null || !language.canCompile()) return;
    if (language.isYass()) compileProject();
    else if ("zpeedy".equals(language.id)) compileZpeedy(tab);
    else if ("sqarl".equals(language.id)) compileSqarl(tab);
  }

  private void compileZpeedy(EditorTab tab) {
    File output = chooseOutputFile(_stage, sourceFile(tab),
            new FileChooser.ExtensionFilter("ZPE Executable", "*.yex"));
    if (output == null) return;
    try {
      Path source = writeTemporarySource(tab, "zide-zpeedy-", ".zps");
      runZpeedyCommand("-c", source.toString(), output.getAbsolutePath());
      showCompilationSuccess("Zpeedy Script", output);
    } catch (Exception exception) {
      showError("Zpeedy compilation failed", exception.getMessage());
    }
  }

  private void compileSqarl(EditorTab tab) {
    File output = chooseOutputFile(_stage, sourceFile(tab),
            new FileChooser.ExtensionFilter("ZPE Executable", "*.yex"));
    if (output == null) return;
    try {
      Path source = writeTemporarySource(tab, "zide-sqarl-", ".sqarl");
      Process process = commandFor("sqarl", "-e", source.toString()).start();
      String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.waitFor() != 0) throw new IOException(errors.isBlank() ? "SQARL compilation failed." : errors.trim());
      Path generated = source.resolveSibling(source.getFileName().toString().replaceFirst("\\.sqarl$", ".yex"));
      Files.move(generated, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
      showCompilationSuccess("SQARL", output);
    } catch (Exception exception) {
      showError("SQARL compilation failed", exception.getMessage());
    }
  }

  private Path writeTemporarySource(EditorTab tab, String prefix, String suffix) throws IOException {
    Path source = Files.createTempFile(prefix, suffix);
    Files.writeString(source, tab.getEditor().getText(), StandardCharsets.UTF_8);
    source.toFile().deleteOnExit();
    return source;
  }

  private ProcessBuilder commandFor(String command, String... arguments) {
    List<String> invocation = new ArrayList<>();
    if (HelperFunctions.isWindows()) {
      invocation.add("cmd.exe");
      invocation.add("/c");
    }
    invocation.add(command);
    invocation.addAll(Arrays.asList(arguments));
    return new ProcessBuilder(invocation);
  }

  private String runZpeedyCommand(String... arguments) throws Exception {
    ProcessBuilder builder = commandFor("zpeedy", arguments);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0) {
      throw new IOException(output.isBlank() ? "Zpeedy command failed." : output.trim());
    }
    return output;
  }

  private File sourceFile(EditorTab tab) {
    return tab == null || tab.getPath() == null ? null : new File(tab.getPath());
  }

  private void showCompilationSuccess(String language, File output) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION,
            "Successfully compiled " + language + " to " + output.getAbsolutePath() + ".");
    alert.setHeaderText(null);
    alert.showAndWait();
  }

  /** Uses an explicit folder selection; otherwise the current tab defines the project folder. */
  private File getCompileProjectDirectory(File currentFile) {
    if (projectTree != null) {
      TreeItem<File> selected = projectTree.getSelectionModel().getSelectedItem();
      if (selected != null && selected.getValue() != null && selected.getValue().isDirectory()) {
        return selected.getValue();
      }
    }
    return currentFile.getParentFile();
  }

  private void compileNative() {
    if (getCurrentTab() == null) {
      showError("Error compiling project", "No project open");
      return;
    }

    File outputLocation = chooseOutputFile(
            _stage,
            null,
            new FileChooser.ExtensionFilter("YASS Native", "*")
    );

    if (outputLocation == null) return;

    EditorTab tab = getCurrentTab();
    if(ZPEKit.compileNativeBinary(sourceWithLayout(tab, tab.getEditor().getText()),"", outputLocation.getAbsolutePath(),true)){
      Alert a = new Alert(Alert.AlertType.INFORMATION, "Successfully compiled native binary");
      a.setHeaderText(null);
      a.showAndWait();
    } else{
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to compile native binary");
      a.setHeaderText(null);
      a.showAndWait();
    }
  }

  private void transpileCurrentFile(String language) {
    EditorTab currentTab = getCurrentTab();
    if (currentTab == null) {
      showError("Transpilation failed", "No file is open.");
      return;
    }

    var transpiler = ZPEKit.getTranspilerByName(language);
    if (transpiler == null) {
      showError("Transpilation failed", "The " + language + " transpiler is not available.");
      return;
    }

    String extension = transpiler.getFileExtension();
    File sourceFile = currentTab.getPath() == null ? new File("program") : new File(currentTab.getPath());
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Transpile to " + language);
    chooser.setInitialFileName(
            sourceFile.getName().replaceFirst("\\.[^.]+$", "") + "." + extension
    );
    if (sourceFile.getParentFile() != null && sourceFile.getParentFile().isDirectory()) {
      chooser.setInitialDirectory(sourceFile.getParentFile());
    }
    chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                    language + " files (*." + extension + ")",
                    "*." + extension
            )
    );

    File selectedFile = chooser.showSaveDialog(_stage);
    if (selectedFile == null) {
      return;
    }

    String outputPath = selectedFile.getAbsolutePath();
    if (!outputPath.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
      outputPath += "." + extension;
    }

    try {
      String transpiledCode;
      if ("zpeedy".equals(currentTab.getLanguageId())) {
        Path source = writeTemporarySource(currentTab, "zide-zpeedy-", ".zps");
        transpiledCode = runZpeedyCommand("-t", source.toString(), language);
      } else {
        transpiledCode = ZPEKit.transpileCode(
                sourceWithLayout(currentTab, currentTab.getEditor().getText()),
                "",
                transpiler
        );
      }
      FileHelperFunctions.writeFile(outputPath, transpiledCode, false);

      Alert success = new Alert(Alert.AlertType.INFORMATION);
      success.initOwner(_stage);
      success.setTitle("Transpilation complete");
      success.setHeaderText("Code transpiled to " + language);
      success.setContentText("Saved at:\n" + outputPath);
      success.showAndWait();
    } catch (Exception ex) {
      showError("Transpilation failed", ex.getMessage());
    }
  }

  private void loginToZPEOnline() {
    ZIDELoginWindow.LoginResult result = ZIDELoginWindow.show(_stage, " ZPE Online");
    if(result == null) return;
    username = result.getUsername();
    password = result.getPassword();

    try {
      ZPEMap res = ZPEOnline.loginToZPEOnline(username, password);

      if (res == null || !res.containsKey(new ZPEString("result"))) {
        showError("Error logging in to ZPE Online", "The server returned an invalid response.");
        return;
      }
      if (Integer.parseInt(res.get(new ZPEString("result")).toString()) != 1) {
        Object message = res.get(new ZPEString("message"));
        showError("Error logging in to ZPE Online", message == null ? "Login was not accepted." : message.toString());
        return;
      }
      loadFromOnline.setDisable(false);
      saveToOnline.setDisable(false);
      loggedIn = true;

      new Alert(Alert.AlertType.INFORMATION, "Successfully logged in to ZPE Online").showAndWait();

    } catch (Exception ex) {
      showError("Error logging in to ZPE Online", ex.getMessage());
    }
  }

  /** Saves the active editor to the user's ZPE Online account using the established v10 API. */
  private void saveToZPEOnline() {
    if (!loggedIn || username == null || password == null) {
      loginToZPEOnline();
      if (!loggedIn) return;
    }
    if (getCurrentTab() == null) {
      showError("Save to ZPE Online", "Open a YASS file before saving it online.");
      return;
    }

    String code = getCurrentTab().getEditor().getText();
    try {
      if (!ZPEKit.validateCode(code)) {
        showError("Save to ZPE Online", "Validate and fix the code before uploading it.");
        return;
      }
    } catch (CompileException exception) {
      showError("Save to ZPE Online", "The code could not be validated: " + exception.getMessage());
      return;
    }

    TextInputDialog nameDialog = new TextInputDialog(cloudFileName);
    nameDialog.initOwner(_stage);
    nameDialog.setTitle("Save to ZPE Online");
    nameDialog.setHeaderText("Save the current script to your account");
    nameDialog.setContentText("File name:");
    Optional<String> selectedName = nameDialog.showAndWait();
    if (selectedName.isEmpty() || selectedName.get().trim().isEmpty()) return;

    Alert publicPrompt = new Alert(Alert.AlertType.CONFIRMATION);
    publicPrompt.initOwner(_stage);
    publicPrompt.setTitle("ZPE Online visibility");
    publicPrompt.setHeaderText("Make this script public?");
    publicPrompt.setContentText("Choose OK to publish it, or Cancel to keep it private.");
    boolean isPublic = publicPrompt.showAndWait().filter(ButtonType.OK::equals).isPresent();

    Map<String, String> arguments = new HashMap<>();
    arguments.put("username", username);
    arguments.put("password", password);
    arguments.put("content", code);
    arguments.put("content_name", selectedName.get().trim());
    arguments.put("public", isPublic ? "1" : "0");

    try {
      String response = HelperFunctions.makePOSTRequest(
              ZPEInstance.getOnlinePathProperty() + "/save.php?version=10", arguments);
      if (response == null || response.isEmpty()) {
        showError("Save to ZPE Online", "The server did not accept the file.");
        return;
      }
      ZPEMap result = (ZPEMap) new ZenithJSONParser().jsonDecode(response, false);
      String status = String.valueOf(result.get(new ZPEString("result")));
      if ("1".equals(status)) {
        cloudFileName = selectedName.get().trim();
        Alert success = new Alert(Alert.AlertType.INFORMATION, "Saved " + cloudFileName + " to ZPE Online.");
        success.initOwner(_stage);
        success.setHeaderText("Cloud save complete");
        success.showAndWait();
      } else if ("-2".equals(status)) {
        showError("Save to ZPE Online", "The script is too large to upload.");
      } else if ("-3".equals(status)) {
        showError("Save to ZPE Online", "The script contains characters that ZPE Online cannot accept.");
      } else {
        showError("Save to ZPE Online", "The server could not save this file.");
      }
    } catch (Exception exception) {
      showError("Save to ZPE Online", exception.getMessage());
    }
  }

  private void downloadZPENative() {
    try {
      downloadWithPopup(
              "Latest ZPEX Native Binary",
              _stage,
              "https://www.jamiebalfour.scot/downloads/1-zpe/zpe-native-aarch64",
              Path.of(ZPEInstance.getInstallPath() + "/zpe-aarch64"),
              false,
              true
      );
    } catch (Exception ex) {
      showError("Error downloading ZPE Native", ex.getMessage());
    }
  }

  private void showError(String header, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(header);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private boolean isChatGPTConfigured() {
    return hasText(ZPEInstance.getChatGPTKey())
            && hasText(ZPEInstance.getChatGPTURL())
            && hasText(ZPEInstance.getChatGPTModel());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void openAIBuilder() {
    EditorTab tab = getCurrentTab();
    if (tab == null || !"yass".equals(tab.getLanguageId())) {
      showError("AI Assistant", "Open a YASS file before using the AI Assistant.");
      return;
    }
    if (!isChatGPTConfigured()) {
      showError("AI Assistant", "Configure CHATGPT_URL, CHATGPT_KEY and CHATGPT_MODEL in ZPE first.");
      return;
    }

    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(_stage);
    dialog.setTitle("AI Assistant");
    dialog.setHeaderText("Describe the YASS program you want to build");
    ButtonType generateType = new ButtonType("Generate", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(generateType, ButtonType.CANCEL);

    javafx.scene.control.TextArea request = new javafx.scene.control.TextArea();
    request.setPromptText("Describe the program, its inputs and what it should do...");
    request.setWrapText(true);
    request.setPrefRowCount(12);
    request.setPrefColumnCount(60);
    Label progress = new Label();
    progress.getStyleClass().add("editor-info-meta");
    VBox content = new VBox(10, request, progress);
    VBox.setVgrow(request, Priority.ALWAYS);
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().setPrefSize(680, 430);

    Node generate = dialog.getDialogPane().lookupButton(generateType);
    generate.disableProperty().bind(request.textProperty().isEmpty());
    generate.addEventFilter(ActionEvent.ACTION, event -> {
      event.consume();
      String description = request.getText().trim();
      generate.disableProperty().unbind();
      generate.setDisable(true);
      request.setDisable(true);
      progress.setText("Generating and validating YASS code...");

      Task<String> task = new Task<>() {
        @Override
        protected String call() throws Exception {
          return generateYassWithAI(description);
        }
      };
      task.setOnSucceeded(ignored -> {
        dialog.close();
        showAIResult(tab, task.getValue());
      });
      task.setOnFailed(ignored -> {
        generate.disableProperty().bind(request.textProperty().isEmpty());
        request.setDisable(false);
        progress.setText("");
        Throwable failure = task.getException();
        showError("ChatGPT could not generate valid YASS code",
                failure == null || failure.getMessage() == null ? "Unknown error" : failure.getMessage());
      });
      Thread worker = new Thread(task, "zide-ai-builder");
      worker.setDaemon(true);
      worker.start();
    });
    Platform.runLater(request::requestFocus);
    dialog.showAndWait();
  }

  private String generateYassWithAI(String description) throws Exception {
    ChatGPT chatGPT = ChatGPT.getInstance();
    String systemMessage = "Write a program in the YASS (Yet Another Scripting Syntax) language "
            + "used in the ZPE Programming Environment.\n\n" + ZPEHelperFunctions.getAIRules();
    String generated = requestAIMessage(chatGPT, systemMessage, "Task:\n" + description);
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (validateGeneratedYass(generated)) return generated;
      } catch (CompileException ignored) {
        // The failed source is supplied to ChatGPT for the repair attempt.
      }
      generated = requestAIMessage(chatGPT, systemMessage,
              "The previous code failed to compile. Fix it and return CODE ONLY:\n" + generated);
    }
    if (!validateGeneratedYass(generated)) {
      throw new IOException("ChatGPT returned code that did not compile.");
    }
    return generated;
  }

  private boolean validateGeneratedYass(String code) throws CompileException {
    synchronized (ZPEKit.class) {
      return ZPEKit.validateCode(code);
    }
  }

  private String requestAIMessage(ChatGPT chatGPT, String systemMessage, String userMessage)
          throws IOException, MalformedJSONException {
    String response = chatGPT.sendMessage(systemMessage, userMessage, ZPEInstance.getChatGPTModel());
    return stripCodeFence(ChatGPT.parseResultsToMessage(response));
  }

  private static String stripCodeFence(String code) {
    String result = code == null ? "" : code.trim();
    if (!result.startsWith("```")) return result;
    int firstLine = result.indexOf('\n');
    int closing = result.lastIndexOf("```");
    return firstLine >= 0 && closing > firstLine
            ? result.substring(firstLine + 1, closing).trim()
            : result;
  }

  private void showAIResult(EditorTab target, String code) {
    Dialog<ButtonType> result = new Dialog<>();
    result.initOwner(_stage);
    result.setTitle("AI Generated Code");
    result.setHeaderText("Review the generated YASS code");
    ButtonType insertType = new ButtonType("Insert code", ButtonBar.ButtonData.APPLY);
    ButtonType replaceType = new ButtonType("Replace code", ButtonBar.ButtonData.OK_DONE);
    result.getDialogPane().getButtonTypes().addAll(insertType, replaceType, ButtonType.CANCEL);

    CodeEditorViewFX preview = new CodeEditorViewFX();
    preview.setDarkMode(isDarkThemeEnabled());
    preview.setFontSize(13);
    configureYass(preview);
    preview.setText(code);
    preview.setEditable(false);
    result.getDialogPane().setContent(preview.getView());
    result.getDialogPane().setPrefSize(760, 560);
    Optional<ButtonType> choice = result.showAndWait();
    if (choice.orElse(ButtonType.CANCEL) == insertType) {
      String existing = target.getEditor().getText();
      target.getEditor().setText(existing + (existing.endsWith("\n") || existing.isEmpty() ? "" : "\n") + code);
    } else if (choice.orElse(ButtonType.CANCEL) == replaceType) {
      target.getEditor().setText(code);
    }
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

  private EditorTab getCurrentTab(){
    return (EditorTab) editorTabs.getSelectionModel().getSelectedItem();
  }

  /**
   * Returns the actual folder of an opened tab. Runs use a temporary copy of
   * the source, but resource lookups must remain rooted in this folder.
   */
  private Path resourceDirectoryFor(EditorTab tab) {
    if (tab == null || tab.getPath() == null || tab.getPath().isBlank()) return null;
    try {
      Path directory = Path.of(tab.getPath()).toAbsolutePath().normalize().getParent();
      return directory != null && Files.isDirectory(directory) ? directory : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private ObservableList<ProblemRow> problemsRows = FXCollections.observableArrayList();

  void updateProblems(EditorTab tab, List<YASSDiagnostic> diagnostics) {
    int errorCount = 0;
    int warningCount = 0;
    for (YASSDiagnostic diagnostic : diagnostics) {
      if ("ERROR".equals(diagnostic.getSeverity().toString())) {
        errorCount++;
      } else if ("WARNING".equals(diagnostic.getSeverity().toString())) {
        warningCount++;
      }
    }
    tab.setDiagnosticCounts(errorCount, warningCount);
    tab.showDiagnostics(diagnostics);

    if (tab != getCurrentTab()) {
      return;
    }

    problemsRows.clear();
    for (YASSDiagnostic diagnostic : diagnostics) {
      problemsRows.add(new ProblemRow(
              tab,
              diagnostic.getSeverity().toString(),
              diagnostic.getLine(),
              diagnostic.getColumn(),
              diagnostic.getStartOffset(),
              diagnostic.getEndOffset(),
              diagnostic.getMessage()
      ));
    }
  }

  private boolean verifyCode(){
    if(getCurrentTab() == null) return false;
    String code = getCurrentTab().getEditor().getText();

    List<YASSDiagnostic> result = ZPEKit.analyseCode(code, 0, code.length());
    updateProblems(getCurrentTab(), result);

    if(result.isEmpty()) {
      return true;
    } else{
      showProblemsPane();
      return false;
    }

  }

  public void beautifyCurrentDocument() {

      if(getCurrentTab() == null) return;
      CodeEditorViewFX doc = getCurrentTab().getEditor();
      String code = doc.getText();

      String formatted = ZPEKit.beautifyCode(code);

      doc.setText(formatted);
      doc.setCaretPosition(0);


  }

  private void runCode(){

    EditorTab currentTab = getCurrentTab();
    LanguageSupport language = currentTab == null ? null : languageSupports.get(currentTab.getLanguageId());
    if (language != null && language.runner != null) {
      language.runner.accept(currentTab);
      return;
    }

    if (!getZPE()) return;

    if(!verifyCode()) return;

    Platform.runLater(() -> {
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
    });

    try {
      if(getCurrentTab() == null) return;
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = getCurrentTab();
      runBtn.getStyleClass().add("running");

      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), sourceWithLayout(tab, tab.getEditor().getText()), false);
      consoleOutputTextArea.addProcessFinishedListener(() -> {
        Platform.runLater(() -> {
          statusLabel.setText("Ready");
        });
      });
      statusLabel.setText("Executing code");
      // ZPEX embeds its compiler at native-image build time. Layout companions
      // must use the current Java runtime until the native UI compiler is
      // rebuilt with the same feature set.
      runZPEProcess(tempPath, resourceDirectoryFor(tab), false, !hasCompanionLayout(tab), "");

      //stopExecution.setDisable(false);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void runZpeedyCode(EditorTab tab) {
    if (tab == null) return;
    try {
      Path temporary = Files.createTempFile("zide-zpeedy-", ".zps");
      Files.writeString(temporary, tab.getEditor().getText(), StandardCharsets.UTF_8);
      temporary.toFile().deleteOnExit();

      ProcessBuilder process;
      if (HelperFunctions.isWindows()) {
        process = new ProcessBuilder("cmd.exe", "/c", "zpeedy", "-r", temporary.toString());
      } else {
        process = new ProcessBuilder("zpeedy", "-r", temporary.toString());
      }
      Path workingDirectory = resourceDirectoryFor(tab);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
        process.directory(workingDirectory.toFile());
      }

      consoleOutputTextArea.clear();
      consoleOutputTextArea.append("Zpeedy Script runtime\n\n", InteractiveConsoleFX.OutputKind.KEY);
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
      runBtn.getStyleClass().add("running");
      statusLabel.setText("Executing Zpeedy Script");
      consoleOutputTextArea.addProcessFinishedListener(() -> Platform.runLater(() -> {
        runBtn.getStyleClass().remove("running");
        statusLabel.setText("Ready");
      }));
      consoleOutputTextArea.runProcess(process);
    } catch (IOException exception) {
      runBtn.getStyleClass().remove("running");
      statusLabel.setText("Ready");
      consoleOutputTextArea.append(
              "[Could not start Zpeedy Script. Install its runtime so the zpeedy command is available: "
                      + exception.getMessage() + "]\n",
              InteractiveConsoleFX.OutputKind.ERROR
      );
    }
  }

  /** Runs ZPE through the shared core launcher so debug protocol setup is UI-independent. */
  private boolean runZPEProcess(Path source, Path resourceRoot, boolean debug, boolean preferNative, String extras) {
    consoleOutputTextArea.clear();
    consoleOutputTextArea.append("Using temporary file " + source.toAbsolutePath() + "\n\n", InteractiveConsoleFX.OutputKind.ADDITIONAL);
    try {
      ZPEProcessLauncher.Launch launch = ZPEProcessLauncher.prepare(source, resourceRoot, debug, preferNative, extras);
      consoleOutputTextArea.append(launch.getRuntimeDescription() + "\n\n", InteractiveConsoleFX.OutputKind.KEY);
      consoleOutputTextArea.runProcess(launch.getProcessBuilder(), launch::processStarted);
      return true;
    } catch (IOException exception) {
      consoleOutputTextArea.append("[Error starting process: " + exception.getMessage() + "]\n", InteractiveConsoleFX.OutputKind.ERROR);
      return false;
    }
  }

  ZPEMacroEditor.Handle macroInterface = null;
  private void openLayoutBuilder() {
    EditorTab tab = getCurrentTab();
    if (tab == null || tab.getPath() == null || !tab.getPath().toLowerCase(Locale.ROOT).endsWith(".yas")) {
      Alert alert = new Alert(Alert.AlertType.WARNING);
      alert.setTitle("No YASS file selected");
      alert.setHeaderText("Open or save a YASS file first.");
      alert.setContentText("The layout builder creates a companion .ui.yas file beside it.");
      alert.showAndWait();
      return;
    }
    Path script = Path.of(tab.getPath()).toAbsolutePath().normalize();
    String fileName = script.getFileName().toString();
    String stem = fileName.substring(0, fileName.length() - 4);
    Path layoutFile = script.resolveSibling(stem + ".ui.yas");
    String currentSource = tab.getEditor().getText();
    String cleanedSource = removeCompanionInclude(script, currentSource);
    if (!cleanedSource.equals(currentSource)) {
      tab.getEditor().setText(cleanedSource);
      tab.setHasChanges(true);
      saveCurrentFile();
    }
    openLayoutBuilderFile(layoutFile);
  }

  private String sourceWithLayout(EditorTab tab, String source) {
    if (tab == null || tab.getPath() == null || tab.getPath().toLowerCase(Locale.ROOT).endsWith(".ui.yas")) return source;
    Path script = Path.of(tab.getPath()).toAbsolutePath().normalize();
    String fileName = script.getFileName().toString();
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yas")) return source;
    Path layoutFile = script.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".ui.yas");
    if (!Files.isRegularFile(layoutFile)) return source;
    String includePath = layoutFile.toString().replace('\\', '/').replace("\"", "\\\"");
    String separator = source.isEmpty() || source.endsWith("\n") ? "" : "\n";
    // Keep transient execution compatible with installed ZPEX builds that predate singular `include`.
    return source + separator + "\nincludes \"" + includePath + "\"\n";
  }

  private boolean hasCompanionLayout(EditorTab tab) {
    if (tab == null || tab.getPath() == null) return false;
    String fileName = Path.of(tab.getPath()).getFileName().toString();
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    if (!lowerName.endsWith(".yas") || lowerName.endsWith(".ui.yas")) return false;
    Path script = Path.of(tab.getPath()).toAbsolutePath().normalize();
    Path layoutFile = script.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".ui.yas");
    return Files.isRegularFile(layoutFile);
  }

  private String removeCompanionInclude(Path script, String source) {
    if (script == null || source == null) return source;
    String fileName = script.getFileName().toString();
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    if (!lowerName.endsWith(".yas") || lowerName.endsWith(".ui.yas")) return source;
    String companionName = fileName.substring(0, fileName.length() - 4) + ".ui.yas";
    String generatedInclude = "(?m)^[\\t ]*includes?[\\t ]+\""
        + java.util.regex.Pattern.quote(companionName) + "\"[\\t ]*(?:\\R|$)";
    return source.replaceAll(generatedInclude, "");
  }

  private void openLayoutBuilderFile(Path layoutFile) {
    try {
      String source = Files.isRegularFile(layoutFile)
          ? Files.readString(layoutFile, StandardCharsets.UTF_8) : "";
      if (layoutBuilderOverlay != null) workspaceStack.getChildren().remove(layoutBuilderOverlay);
      ZUILayoutBuilder builder = new ZUILayoutBuilder(_stage, layoutFile, source,
          () -> statusLabel.setText("Saved " + layoutFile.getFileName()),
          () -> {
            workspaceStack.getChildren().remove(layoutBuilderOverlay);
            layoutBuilderOverlay = null;
          });
      layoutBuilderOverlay = builder.getView();
      workspaceStack.getChildren().add(layoutBuilderOverlay);
      layoutBuilderOverlay.toFront();
    } catch (IOException exception) {
      showError("Unable to open layout", exception.getMessage());
    }
  }

  private void openMSI() {
    if(getCurrentTab() == null) {
      Alert alert = new Alert(Alert.AlertType.WARNING);
      alert.setTitle("No tab selected");
      alert.setHeaderText("Select a file to open the macro editor.");
      alert.setContentText("Please open a file to open the macro editor.");
      alert.showAndWait();
      return;
    }
    ZPERuntimeEnvironment z = new ZPERuntimeEnvironment();
    if(macroInterface == null || !macroInterface.isDisplayable()) {
      macroInterface = ZPEMacroEditor.createJavaFX(z,
              new ZPEObject[]{new YASSCodeEditor.EditorObject(z,
                      ZPEKit.getGlobalFunction(z), createMacroEditorBridge())}, null);
    }
    macroInterface.open(null, null, null);
  }

  private boolean getZPE() {
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
        return true;
      } else {
        return false;
      }
    } else{
      return true;
    }
  }

  private YASSCodeEditor createMacroEditorBridge() {
    // Compatibility bridge only: it is never attached to the ZIDE scene.
    // ZPE's current Macro Editor object still accepts CodeEditorView; keeping
    // this isolated lets the visible editor remain fully JavaFX-native.
    YASSCodeEditor bridge = new YASSCodeEditor(false);
    bridge.setText(getCurrentTab().getEditor().getText());
    return bridge;
  }

  private String prepareDebugSourceWithBreakpoints(EditorTab tab, String source) {
    String[] lines = source.split("\\R", -1);
    StringBuilder out = new StringBuilder();


    for (int line = 1; line <= lines.length; line++) {
      String currentLine = lines[line - 1];

      if (tab.hasSpecialLine(line)) {
        out.append("#breakpoint# ");
      }

      out.append(currentLine);

      if (line < lines.length) {
        out.append(System.lineSeparator());
      }
    }

    return out.toString();
  }

  private void debugCode(){

    if (!getZPE()) return;
    if(getCurrentTab() == null) return;

    try {
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = getCurrentTab();
      if(tab == null){
        tab = (EditorTab) editorTabs.getTabs().get(0);
      }

      debuggingSession = true;
      debugBtn.getStyleClass().add("running");

      stopExecutionBtn.setVisible(true);
      stepOverButton.setVisible(true);
      continueButton.setVisible(true);
      debugSeparator.setVisible(true);


      String debugSource = prepareDebugSourceWithBreakpoints(tab, tab.getEditor().getText());
      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), sourceWithLayout(tab, debugSource), false);
      beginProfilerSession();
      if (!runZPEProcess(tempPath, resourceDirectoryFor(tab), true, true, "")) {
        finishDebugSession();
      }
    } catch (IOException e) {
      finishDebugSession();
      throw new RuntimeException(e);
    }
  }

  /**
   * Ends a debug session exactly once. The toolbar icon is deliberately not
   * inverted here: icon inversion belongs solely to theme changes, while this
   * method restores transient debug state after both normal and failed runs.
   */
  private void finishDebugSession() {
    if (!debuggingSession) return;
    debuggingSession = false;
    currentBreakpoint = null;
    breakpointVariables.clear();
    endProfilerSession();
    Platform.runLater(() -> {
      debugBtn.getStyleClass().remove("running");
      stopExecutionBtn.setVisible(false);
      stepOverButton.setVisible(false);
      continueButton.setVisible(false);
      debugSeparator.setVisible(false);
    });
  }

  public static Button createExpandableToolbarButton(String labelText, String iconPath, Runnable action) {

    ImageView icon = new ImageView(new Image(

            ZIDEEditor.class.getResourceAsStream(iconPath)

    ));

    icon.setFitWidth(16);
    icon.setFitHeight(16);

    icon.setPreserveRatio(true);

    Label label = new Label(labelText);
    label.setOpacity(0);
    label.setManaged(false);
    label.setVisible(false);

    HBox content = new HBox(8, icon, label);
    content.setAlignment(Pos.CENTER_LEFT);
    Button button = new Button();
    button.setGraphic(content);
    button.setMinWidth(40);
    button.setPrefWidth(40);
    button.setMaxWidth(40);
    button.setPrefHeight(32);

    button.getStyleClass().add("toolbar-button");
    button.setOnAction(e -> action.run());
    double collapsedWidth = 40;
    double expandedWidth = Math.max(90, 40 + labelText.length() * 7.5);
    button.setOnMouseEntered(e -> {

      label.setManaged(true);
      label.setVisible(true);

      Timeline widthAnim = new Timeline(

              new KeyFrame(Duration.millis(180),

                      new KeyValue(button.prefWidthProperty(), expandedWidth),

                      new KeyValue(button.maxWidthProperty(), expandedWidth)

              )

      );

      FadeTransition fadeIn = new FadeTransition(Duration.millis(140), label);

      fadeIn.setToValue(1.0);

      new ParallelTransition(widthAnim, fadeIn).play();

    });

    button.setOnMouseExited(e -> {

      FadeTransition fadeOut = new FadeTransition(Duration.millis(100), label);

      fadeOut.setToValue(0.0);

      fadeOut.setOnFinished(evt -> {

        label.setManaged(false);

        label.setVisible(false);

      });

      Timeline widthAnim = new Timeline(

              new KeyFrame(Duration.millis(180),

                      new KeyValue(button.prefWidthProperty(), collapsedWidth),

                      new KeyValue(button.maxWidthProperty(), collapsedWidth)

              )

      );

      new ParallelTransition(widthAnim, fadeOut).play();

    });

    return button;

  }

  private void debug(){
    EditorTab tab = getCurrentTab();
    if (tab != null && "python".equals(tab.getLanguageId())) {
      debugPythonCode(tab);
      return;
    }
    stepping = false;
    ZPEDebugger.addBreakPointReachedListener((b, varData) -> {
      currentBreakpoint = b;
      setVariables(varData);
      if(!stepping) {
        showVariablesPane();
      }

    });

    debugCode();
  }

  private ToolBar buildToolBar() {
    runBtn = createExpandableToolbarButton("Run", "/files/controller-play.png", this::runCode);
    runBtn.getStyleClass().add("run");

    buildBtn = createExpandableToolbarButton("Build", "/files/tools.png", this::debug);
    buildBtn.setOnAction(e -> {
      if(getCurrentTab() == null) return;
      LanguageSupport language = languageSupports.get(getCurrentTab().getLanguageId());
      if (language != null && !language.isYass()) {
        compileCurrentLanguage();
        return;
      }
      try {
        if(ZPEKit.validateCode(getCurrentTab().getEditor().getText())){
          Alert alert = new Alert(Alert.AlertType.INFORMATION, "Code builds successfully", ButtonType.OK);
          alert.setTitle("Successful build");
          alert.setHeaderText(null);
          alert.showAndWait();
        } else{
          verifyCode();
        }
      } catch (CompileException ex) {
        verifyCode();
      }
    });


    debugBtn = createExpandableToolbarButton("Debug", "/files/bug.png", this::debug);
    debugBtn.setGraphicTextGap(6);
    debugBtn.getStyleClass().add("debug");

    var sep1 = new Separator(Orientation.VERTICAL);

    stopExecutionBtn = createExpandableToolbarButton("Stop", "/files/stop.png", this::stopExecution);
    stopExecutionBtn.setVisible(false);

    stepOverButton = createExpandableToolbarButton("Step Over", "/files/stepover.png", this::stepOver);
    stepOverButton.setVisible(false);

    continueButton = createExpandableToolbarButton("Continue", "/files/continue.png", this::continueDebug);
    continueButton.setVisible(false);

    debugSeparator = new Separator(Orientation.VERTICAL);
    debugSeparator.setVisible(false);

    editorSearchField = new TextField();
    editorSearchField.setPromptText("Search");
    editorSearchField.getStyleClass().add("search-field");
    editorSearchField.setMaxWidth(280);
    editorSearchField.textProperty().addListener((observable, oldText, newText) -> findInCurrentEditor(false, true));
    editorSearchField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.ENTER) {
        findInCurrentEditor(event.isShiftDown(), false);
        event.consume();
      } else if (event.getCode() == KeyCode.ESCAPE) {
        editorSearchField.clear();
        EditorTab tab = getCurrentTab();
        if (tab != null) tab.getEditor().requestFocus();
        event.consume();
      }
    });

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);



    var toolbar = new ToolBar(runBtn, buildBtn, debugBtn, sep1, stopExecutionBtn, stepOverButton, continueButton, debugSeparator, new Label(" "), spacer, editorSearchField);
    toolbar.getStyleClass().add("app-toolbar");
    return toolbar;
  }

  private void findInCurrentEditor(boolean backwards, boolean fromStart) {
    EditorTab tab = getCurrentTab();
    String query = editorSearchField == null ? "" : editorSearchField.getText();
    if (tab == null || query == null || query.isBlank()) {
      statusLabel.setText("Ready");
      return;
    }

    CodeEditorViewFX editor = tab.getEditor();
    String source = editor.getText();
    String foldedSource = source.toLowerCase(Locale.ROOT);
    String foldedQuery = query.toLowerCase(Locale.ROOT);
    int start = fromStart ? (backwards ? source.length() : 0)
            : (backwards ? editor.getSelection().getStart() - 1 : editor.getSelection().getEnd());
    int match = backwards
            ? (start < 0 ? -1 : foldedSource.lastIndexOf(foldedQuery, start))
            : foldedSource.indexOf(foldedQuery, Math.max(0, start));
    if (match < 0) match = backwards ? foldedSource.lastIndexOf(foldedQuery) : foldedSource.indexOf(foldedQuery);
    if (match < 0) {
      statusLabel.setText("No matches for " + query);
      return;
    }

    editor.getEditor().selectRange(match, match + query.length());
    editor.getEditor().requestFollowCaret();
    int total = 0;
    int current = 0;
    for (int index = foldedSource.indexOf(foldedQuery); index >= 0;
         index = foldedSource.indexOf(foldedQuery, index + Math.max(1, foldedQuery.length()))) {
      total++;
      if (index <= match) current = total;
    }
    statusLabel.setText("Match " + current + " of " + total);
  }

  private TreeView<File> projectTree;
  boolean stepping = false;

  private void continueDebug(){
    if(currentBreakpoint == null){
      return;
    }
    stepping = false;
    currentBreakpoint.resume();
    currentBreakpoint = null;
    breakpointVariables.clear();
  }

  private void stepOver(){
    if(currentBreakpoint == null){
      return;
    }
    stepping = true;
    currentBreakpoint.stepOver();
    currentBreakpoint = null;
    breakpointVariables.clear();
  }

  private void stopExecution(){
    if(currentBreakpoint == null){
      return;
    }
    currentBreakpoint.stopExecution();
    currentBreakpoint = null;
    breakpointVariables.clear();
  }

  private Node buildProjectTree(File projectDir) {
    if (projectTree == null) {
      projectTree = new TreeView<>();
      projectTree.setShowRoot(true);
      projectTree.getStyleClass().add("project-tree");

      projectTree.setCellFactory(tv -> new TreeCell<>() {
        @Override
        protected void updateItem(File file, boolean empty) {
          super.updateItem(file, empty);

          if (empty || file == null) {
            setText(null);
            setGraphic(null);
            return;
          }

          String name = file.getName();
          setText(name.isEmpty() ? file.getPath() : name);
        }

        {
          setOnContextMenuRequested(event -> {
            File file = getItem();
            if (file == null) return;
            projectTree.getSelectionModel().select(getTreeItem());
            createProjectFileMenu(file).show(this, event.getScreenX(), event.getScreenY());
            event.consume();
          });

          setOnDragDetected(event -> {
            File source = getItem();
            if (source == null || isProjectRoot(source)) return;
            Dragboard board = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(source));
            board.setContent(content);
            event.consume();
          });

          setOnDragOver(event -> {
            File target = getItem();
            if (target != null && event.getDragboard().hasFiles()
                    && canDropInto(event.getDragboard().getFiles(), target)) {
              event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
          });

          setOnDragDropped(event -> {
            File target = getItem();
            boolean success = target != null && event.getDragboard().hasFiles()
                    && moveOrCopyDroppedFiles(event.getDragboard().getFiles(), target);
            event.setDropCompleted(success);
            event.consume();
          });
        }
      });

      projectTree.setOnMouseClicked(e -> {
        if (e.getClickCount() == 2) {
          var item = projectTree.getSelectionModel().getSelectedItem();
          if (item != null && item.isLeaf()) {
            openTab(item.getValue().getName(), item.getValue().getPath());
          }
        }
      });

    }



    projectTree.setRoot(loadDirectory(projectDir));
    projectTree.getRoot().setExpanded(true);

    return projectTree;
  }

  /** Builds the context menu for a project file or folder. */
  private ContextMenu createProjectFileMenu(File file) {
    boolean regularFile = file.isFile();
    MenuItem open = new MenuItem(regularFile ? "Open File" : "Open Folder");
    open.setOnAction(event -> {
      if (regularFile) openTab(file.getName(), file.getAbsolutePath());
      else {
        TreeItem<File> item = findProjectTreeItem(projectTree.getRoot(), file.toPath().toAbsolutePath().normalize());
        if (item != null) item.setExpanded(!item.isExpanded());
      }
    });

    MenuItem openAsText = new MenuItem("Open with Text Editor");
    openAsText.setOnAction(event -> openTab(file.getName(), file.getAbsolutePath(), false));

    MenuItem rename = new MenuItem(regularFile ? "Rename File…" : "Rename Folder…");
    rename.setOnAction(event -> renameProjectFile(file));

    MenuItem delete = new MenuItem(regularFile ? "Delete File…" : "Delete Folder…");
    delete.setOnAction(event -> deleteProjectFile(file));
    delete.setDisable(isProjectRoot(file));

    ContextMenu menu = new ContextMenu(open);
    if (regularFile && file.getName().toLowerCase(Locale.ROOT).endsWith(".ui.yas")) {
      menu.getItems().add(openAsText);
    }
    menu.getItems().addAll(new SeparatorMenuItem(), rename, delete);
    return menu;
  }

  private void renameProjectFile(File file) {
    if (isProjectRoot(file)) return;
    TextInputDialog dialog = new TextInputDialog(file.getName());
    dialog.initOwner(_stage);
    dialog.setTitle("Rename");
    dialog.setHeaderText("Rename " + file.getName());
    dialog.setContentText("New name:");
    Optional<String> value = dialog.showAndWait();
    if (value.isEmpty()) return;

    String name = value.get().trim();
    if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
      showProjectFileError("Choose a simple file or folder name.");
      return;
    }
    Path source = file.toPath().toAbsolutePath().normalize();
    Path destination = source.resolveSibling(name);
    if (source.equals(destination)) return;
    if (Files.exists(destination)) {
      showProjectFileError("An item with that name already exists.");
      return;
    }
    try {
      Files.move(source, destination);
      updateOpenTabPaths(source, destination);
      refreshTree();
    } catch (IOException exception) {
      showProjectFileError("Could not rename the item: " + exception.getMessage());
    }
  }

  private void deleteProjectFile(File file) {
    if (isProjectRoot(file)) return;
    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
    confirmation.initOwner(_stage);
    confirmation.setTitle("Delete");
    confirmation.setHeaderText("Delete " + file.getName() + "?");
    confirmation.setContentText(file.isDirectory()
            ? "This permanently deletes the folder and everything inside it."
            : "This permanently deletes the file.");
    ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
    confirmation.getButtonTypes().setAll(delete, ButtonType.CANCEL);
    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != delete) return;

    Path path = file.toPath().toAbsolutePath().normalize();
    try {
      try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
        paths.sorted(Comparator.reverseOrder()).forEach(child -> {
          try { Files.deleteIfExists(child); }
          catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
      }
      closeTabsUnder(path);
      refreshTree();
    } catch (IOException | UncheckedIOException exception) {
      Throwable cause = exception instanceof UncheckedIOException ? exception.getCause() : exception;
      showProjectFileError("Could not delete the item: " + cause.getMessage());
    }
  }

  private void revealProjectFile(File file) {
    try {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("mac")) {
        new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
      } else if (os.contains("win")) {
        new ProcessBuilder("explorer.exe", "/select," + file.getAbsolutePath()).start();
      } else if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file.isDirectory() ? file : file.getParentFile());
      }
    } catch (IOException exception) {
      showProjectFileError("Could not open the file explorer: " + exception.getMessage());
    }
  }

  /** Handles both tree moves and files dropped in from Finder/Explorer. */
  private boolean moveOrCopyDroppedFiles(List<File> sources, File target) {
    if (!canDropInto(sources, target)) return false;
    Path targetDirectory = (target.isDirectory() ? target : target.getParentFile()).toPath().toAbsolutePath().normalize();
    try {
      for (File sourceFile : sources) {
        Path source = sourceFile.toPath().toAbsolutePath().normalize();
        Path destination = targetDirectory.resolve(source.getFileName());
        if (source.equals(destination)) continue;
        if (isInsideProject(source)) {
          Files.move(source, destination);
          updateOpenTabPaths(source, destination);
        } else {
          Files.copy(source, destination);
        }
      }
      refreshTree();
      return true;
    } catch (IOException exception) {
      showProjectFileError("Could not move the item: " + exception.getMessage());
      return false;
    }
  }

  private boolean canDropInto(List<File> sources, File target) {
    if (sources == null || sources.isEmpty() || target == null) return false;
    Path targetDirectory = (target.isDirectory() ? target : target.getParentFile()).toPath().toAbsolutePath().normalize();
    for (File sourceFile : sources) {
      Path source = sourceFile.toPath().toAbsolutePath().normalize();
      if (source.equals(targetDirectory) || (Files.isDirectory(source) && targetDirectory.startsWith(source))) return false;
      if (Files.exists(targetDirectory.resolve(source.getFileName()))) return false;
    }
    return true;
  }

  private boolean isProjectRoot(File file) {
    return currentProjectRoot != null && file.toPath().toAbsolutePath().normalize()
            .equals(currentProjectRoot.toPath().toAbsolutePath().normalize());
  }

  private boolean isInsideProject(Path path) {
    return currentProjectRoot != null && path.startsWith(currentProjectRoot.toPath().toAbsolutePath().normalize());
  }

  private void updateOpenTabPaths(Path source, Path destination) {
    for (Tab tab : new ArrayList<>(editorTabs.getTabs())) {
      if (!(tab instanceof EditorTab editorTab) || editorTab.getPath() == null) continue;
      Path tabPath = Path.of(editorTab.getPath()).toAbsolutePath().normalize();
      if (!tabPath.startsWith(source)) continue;
      Path movedPath = destination.resolve(source.relativize(tabPath));
      editorTab.setPath(movedPath.toString());
      editorTab.setDisplayTitle(movedPath.getFileName().toString());
    }
  }

  private void closeTabsUnder(Path deletedPath) {
    for (Tab tab : new ArrayList<>(editorTabs.getTabs())) {
      if (tab instanceof EditorTab editorTab && editorTab.getPath() != null
              && Path.of(editorTab.getPath()).toAbsolutePath().normalize().startsWith(deletedPath)) {
        editorTabs.getTabs().remove(tab);
      }
    }
  }

  private void showProjectFileError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
    alert.initOwner(_stage);
    alert.setHeaderText(null);
    alert.showAndWait();
  }

  /**
   * Rebuilds the project tree without making the project navigator jump back
   * to its initial, collapsed state after a filesystem operation.
   */
  private void refreshTree() {
    if (currentProjectRoot == null) return;

    Set<Path> expandedDirectories = new HashSet<>();
    rememberExpandedDirectories(projectTree == null ? null : projectTree.getRoot(), expandedDirectories);

    TreeItem<File> selectedItem = projectTree == null
            ? null
            : projectTree.getSelectionModel().getSelectedItem();
    Path selectedPath = selectedItem == null ? null : normalisedProjectPath(selectedItem.getValue());

    buildProjectTree(currentProjectRoot);
    restoreProjectTreeState(projectTree.getRoot(), expandedDirectories);

    TreeItem<File> restoredSelection = findProjectTreeItem(projectTree.getRoot(), selectedPath);
    if (restoredSelection != null) {
      projectTree.getSelectionModel().select(restoredSelection);
    }
  }

  /** Records expanded folders by their absolute, normalised path. */
  private void rememberExpandedDirectories(TreeItem<File> item, Set<Path> expandedDirectories) {
    if (item == null || item.getValue() == null) return;

    File file = item.getValue();
    if (!file.isDirectory() || !item.isExpanded()) return;

    expandedDirectories.add(normalisedProjectPath(file));
    for (TreeItem<File> child : item.getChildren()) {
      rememberExpandedDirectories(child, expandedDirectories);
    }
  }

  /**
   * Expands only folders that were open before the refresh. Children are loaded
   * lazily, just as they are for a user-initiated expansion.
   */
  private void restoreProjectTreeState(TreeItem<File> item, Set<Path> expandedDirectories) {
    if (item == null || item.getValue() == null || !item.getValue().isDirectory()) return;

    if (!expandedDirectories.contains(normalisedProjectPath(item.getValue()))) return;

    item.setExpanded(true);
    loadChildrenIfNeeded(item);
    for (TreeItem<File> child : item.getChildren()) {
      restoreProjectTreeState(child, expandedDirectories);
    }
  }

  /** Finds a visible tree item after the refreshed hierarchy has been restored. */
  private TreeItem<File> findProjectTreeItem(TreeItem<File> item, Path targetPath) {
    if (item == null || targetPath == null || item.getValue() == null) return null;
    if (targetPath.equals(normalisedProjectPath(item.getValue()))) return item;

    for (TreeItem<File> child : item.getChildren()) {
      TreeItem<File> result = findProjectTreeItem(child, targetPath);
      if (result != null) return result;
    }
    return null;
  }

  private Path normalisedProjectPath(File file) {
    return file == null ? null : file.toPath().toAbsolutePath().normalize();
  }

  /** Starts a recursive, daemon watcher for the project currently shown in the tree. */
  private void startProjectDirectoryWatcher(File root) {
    stopProjectDirectoryWatcher();
    if (root == null || !root.isDirectory()) return;

    try {
      projectWatchService = FileSystems.getDefault().newWatchService();
      registerProjectDirectories(root.toPath());
      watchingProjectDirectory = true;
      projectWatchThread = new Thread(this::watchProjectDirectoryEvents, "zide-project-watcher");
      projectWatchThread.setDaemon(true);
      projectWatchThread.start();
    } catch (IOException exception) {
      ZPE.log("Unable to watch project directory: " + exception.getMessage());
      stopProjectDirectoryWatcher();
    }
  }

  /** Registers every existing directory so edits in nested project folders are observed as well. */
  private void registerProjectDirectories(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      paths.filter(Files::isDirectory).forEach(directory -> {
        try {
          directory.register(projectWatchService,
                  StandardWatchEventKinds.ENTRY_CREATE,
                  StandardWatchEventKinds.ENTRY_DELETE,
                  StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException ignored) {
          // A directory can disappear while the project is being scanned.
        }
      });
    }
  }

  /** Waits off the JavaFX thread, adds newly-created folders to the watch set, then coalesces UI refreshes. */
  private void watchProjectDirectoryEvents() {
    while (watchingProjectDirectory && projectWatchService != null) {
      WatchKey key;
      try {
        key = projectWatchService.take();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      } catch (ClosedWatchServiceException exception) {
        return;
      }

      Path directory = (Path) key.watchable();
      boolean changed = false;
      for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
          changed = true;
          continue;
        }
        changed = true;
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
          Path created = directory.resolve((Path) event.context());
          if (Files.isDirectory(created)) {
            try {
              registerProjectDirectories(created);
            } catch (IOException ignored) {
              // The directory may have been removed immediately after it was created.
            }
          }
        }
      }
      if (!key.reset()) changed = true;
      if (changed) queueProjectTreeRefresh();
    }
  }

  /** Ensures a burst of editor or Git events creates one tree rebuild rather than many. */
  private void queueProjectTreeRefresh() {
    if (!projectTreeRefreshQueued.compareAndSet(false, true)) return;
    Platform.runLater(() -> {
      try {
        refreshTree();
      } finally {
        projectTreeRefreshQueued.set(false);
      }
    });
  }

  /** Stops the previous watcher before another project is opened or ZIDE exits. */
  private void stopProjectDirectoryWatcher() {
    watchingProjectDirectory = false;
    if (projectWatchThread != null) projectWatchThread.interrupt();
    if (projectWatchService != null) {
      try {
        projectWatchService.close();
      } catch (IOException ignored) {
        // The service may already have been closed during application shutdown.
      }
    }
    projectWatchThread = null;
    projectWatchService = null;
    projectTreeRefreshQueued.set(false);
  }

  private TabPane editorTabs;

  // Call this whenever you want the label refreshed
  Runnable refreshRunText = () -> {
    if(getCurrentTab() == null) return;
    Tab t = getCurrentTab();
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

      if (newTab instanceof EditorTab) {
        ((EditorTab) newTab).scheduleAnalysis();
      }

      if (newTab instanceof EditorTab) syncLanguageSelector((EditorTab) newTab);
      if (editorSearchField != null && !editorSearchField.getText().isBlank()) {
        findInCurrentEditor(false, true);
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
    openTab(name, file, true);
  }

  private void openTab(String name, String file, boolean useLayoutEditor) {
    if(file != null) {
      if (new File(file).isDirectory()) {
        return;
      }
      if (useLayoutEditor && file.toLowerCase(Locale.ROOT).endsWith(".ui.yas")) {
        openLayoutBuilderFile(Path.of(file));
        return;
      }
    }
    // Ensure active-tab behaviour is installed once
    installActiveTabStyling();

    // If tab already exists, select it
    if(file != null) {
      for (Tab t : editorTabs.getTabs()) {
        String txt = t.getId();
        if (file.equals(txt)) {
          editorTabs.getSelectionModel().select(t);
          return;
        }
      }
    }

    CodeEditorViewFX editor = new CodeEditorViewFX();
    editor.setFontSize(14);
    editor.setWordWrap(USE_WORD_WRAP);
    editor.setDarkMode(isDarkThemeEnabled());

    String lang = languageForFile(file);
    setLanguage(lang, editor);
    if (file != null) {
      try {
        String loadedSource = FileHelperFunctions.readFileAsString(file);
        String migratedSource = removeCompanionInclude(Path.of(file), loadedSource);
        editor.setText(migratedSource);
        if (!migratedSource.equals(loadedSource)) {
          Files.writeString(Path.of(file), migratedSource, StandardCharsets.UTF_8);
          statusLabel.setText("Moved the UI include into the run and compile pipeline");
        }
      } catch (IOException exception) {
        showError("Unable to open file", exception.getMessage());
        return;
      }
    }
    editor.setCaretPosition(0);

    Tab tab = createEditorTab(name, editor, file, editor.getView());
    ((EditorTab) tab).setLanguageId(lang);
    if (file != null) tab.setId(file);
    editorTabs.getTabs().add(tab);
    editorTabs.getSelectionModel().select(tab);
    Platform.runLater(editor::requestFocus);


  }

  /** Resolves a file through the language registry used by editing, help and execution. */
  private String languageForFile(String file) {
    LanguageSupport language = languageSupportForFile(file);
    return language == null ? "txt" : language.id;
  }

  private void setLanguage(String id, CodeEditorViewFX editor) {
    registerLanguageSupports();
    LanguageSupport language = languageSupports.get(id);
    if (language == null) {
      editor.runBatchUpdate(() -> configurePlainText(editor));
      return;
    }
    editor.runBatchUpdate(() -> language.configure.accept(editor));
  }

  void refreshDocumentSymbols(EditorTab tab) {
    if (tab == null) return;
    String languageId = tab.getLanguageId();
    LanguageSupport language = languageSupports.get(languageId);
    if (language == null) return;
    tab.getEditor().runBatchUpdate(
            () -> addDocumentSymbols(tab.getEditor(), languageId, tab.getEditor().getText())
    );
  }

  private void addDocumentSymbols(CodeEditorViewFX editor, String languageId, String source) {
    if (source == null || source.isBlank()) return;
    Pattern declarations;
    if ("yass".equals(languageId)) {
      declarations = Pattern.compile("(?im)^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*"
              + "(function|module|namespace|class|structure|interface|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    } else if ("zpeedy".equals(languageId)) {
      declarations = Pattern.compile("(?im)^\\s*(routine|thing)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    } else {
      return;
    }
    Matcher matcher = declarations.matcher(source);
    while (matcher.find()) {
      String kind = matcher.group(1).toLowerCase(Locale.ROOT);
      String name = matcher.group(2);
      boolean callable = "function".equals(kind) || "routine".equals(kind);
      editor.addKeyword(name, callable ? CodeSyntaxModel.Style.FUNCTION : CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(name, callable
              ? CodeEditorViewFX.AutoCompleteItemType.Function
              : CodeEditorViewFX.AutoCompleteItemType.Type);
    }
  }

  void applyLanguageForPath(EditorTab tab) {
    if (tab == null) return;
    String languageId = languageForFile(tab.getPath());
    tab.setLanguageId(languageId);
    setLanguage(languageId, tab.getEditor());
    tab.scheduleAnalysis();
    if (tab == getCurrentTab()) syncLanguageSelector(tab);
  }

  private void selectLanguage(EditorTab tab, LanguageSupport language) {
    if (tab == null || language == null) return;
    tab.setLanguageId(language.id);
    setLanguage(language.id, tab.getEditor());
    tab.scheduleAnalysis();
    if (languageSelector != null) languageSelector.setText(language.label);
    updateLanguageCommands(language);
    statusLabel.setText(language.label + " language mode");
  }

  private void syncLanguageSelector(EditorTab tab) {
    if (languageSelector == null || tab == null) return;
    registerLanguageSupports();
    String id = tab.getLanguageId();
    if (id == null) {
      id = languageForFile(tab.getPath());
      tab.setLanguageId(id);
    }
    LanguageSupport language = languageSupports.get(id);
    languageSelector.setText(language == null ? "Text" : language.label);
    updateLanguageCommands(language);
  }

  private void updateLanguageCommands(LanguageSupport language) {
    boolean runnable = language != null && language.canRun();
    boolean compilable = language != null && language.canCompile();
    boolean yass = language != null && language.isYass();
    boolean debuggable = yass || (language != null && "python".equals(language.id));
    setMenuItemAvailable(runScriptMenuItem, runnable);
    setMenuItemAvailable(stopScriptMenuItem, runnable);
    setMenuItemAvailable(debugScriptMenuItem, debuggable);
    setMenuItemAvailable(compileScriptMenuItem, compilable);
    setMenuItemAvailable(compileNativeMenuItem, yass);
    setMenuItemAvailable(formatDocumentMenuItem, yass);
    setMenuItemAvailable(layoutBuilderMenuItem, yass);
    setMenuItemAvailable(aiBuilderMenuItem, yass && isChatGPTConfigured());
    boolean transpilable = yass || (language != null && "zpeedy".equals(language.id));
    for (Node item : transpileMenuItems) setMenuItemAvailable(item, transpilable);
    setMenuItemAvailable(scriptCompileSeparator, runnable && (compilable || transpilable));
    setMenuItemAvailable(scriptTranspileSeparator, compilable && transpilable);
    if (runBtn != null) runBtn.setDisable(!runnable);
    if (debugBtn != null) debugBtn.setDisable(!debuggable);
    if (buildBtn != null) buildBtn.setDisable(!compilable);
    if (compileScriptMenuItem != null && language != null) {
      setGlassMenuItemText(compileScriptMenuItem,
              yass ? "Compile YASS project to ZEX" : "Compile " + language.label + " to ZEX");
    }
    if (runScriptMenuItem != null && language != null) {
      setGlassMenuItemText(runScriptMenuItem, "Run " + language.label);
    }
  }

  private static void setMenuItemAvailable(Node item, boolean available) {
    if (item == null) return;
    item.setVisible(available);
    item.setManaged(available);
    item.setDisable(!available);
  }

  private static void setGlassMenuItemText(Node item, String text) {
    if (!(item instanceof Pane)) return;
    for (Node child : ((Pane) item).getChildren()) {
      if (child instanceof Label && child.getStyleClass().contains("glass-menu-item-text")) {
        ((Label) child).setText(text);
        return;
      }
    }
  }

  private void configureYass(CodeEditorViewFX editor) {
      editor.setLineCommentMarkers("\\", "//");
      editor.setBlockCommentMarkers("/*", "*/");
      editor.setQuoteDelimiters("\"'`");
      editor.setVariableDelimiters("$");
      editor.setContextSeparator("::");
      editor.clearKeywords();
      editor.clearContextualKeywords();
      editor.clearAutoCompleteItems();
      for (String keyword : ZPEKit.getKeywords()) {
        editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
        editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
      }
      for (String type : ZPEKit.getTypeKeywords()) {
        editor.addKeyword(type, CodeSyntaxModel.Style.TYPE);
        editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type);
      }
      editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
      editor.addKeyword("NULL", CodeSyntaxModel.Style.NULL);
      editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
      editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
      editor.addKeyword("this", CodeSyntaxModel.Style.VARIABLE);
      editor.addKeyword("#breakpoint#", CodeSyntaxModel.Style.SPECIAL);
      for (String directive : ZPEKit.getDirectiveKeywords()) {
        editor.addKeyword(directive, CodeSyntaxModel.Style.DOC);
        editor.addAutoCompleteItem(directive, CodeEditorViewFX.AutoCompleteItemType.Doc);
      }
      for (String function : ZPEKit.getAllFunctions()) {
        editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION);
        editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function);
      }
      for (String structure : ZPEInstance.getBuiltInStructuresNames()) {
        editor.addKeyword(structure, CodeSyntaxModel.Style.TYPE);
        editor.addAutoCompleteItem(structure, CodeEditorViewFX.AutoCompleteItemType.Type);
      }
      for (Map.Entry<String, ZPEModule> module : ZPEKit.getBuiltinModules().entrySet()) {
        editor.addKeyword(module.getKey(), CodeSyntaxModel.Style.TYPE);
        editor.addAutoCompleteItem(module.getKey(), CodeEditorViewFX.AutoCompleteItemType.Type);
        for (String method : module.getValue().getMethods()) {
          editor.addContextualKeyword(module.getKey(), method, CodeSyntaxModel.Style.FUNCTION);
        }
      }
  }

  private void configureZpeedy(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("#");
    editor.setBlockCommentMarkers("", "");
    editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters("");
    editor.setContextSeparator("");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();

    for (String keyword : getZpeedyKeywords()) {
      editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    addZpeFunctions(editor);
    addLiteral(editor, "true", CodeSyntaxModel.Style.BOOLEAN);
    addLiteral(editor, "false", CodeSyntaxModel.Style.BOOLEAN);
    addLiteral(editor, "nothing", CodeSyntaxModel.Style.NULL);
    addLiteral(editor, "unknown", CodeSyntaxModel.Style.NULL);
  }

  private void addZpeFunctions(CodeEditorViewFX editor) {
    for (String function : ZPEKit.getAllFunctions()) {
      editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION);
      editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function);
    }
  }

  private List<String> getZpeedyKeywords() {
    if (zpeedyKeywords != null) return zpeedyKeywords;
    try {
      ProcessBuilder builder = commandFor("zpeedy", "--keywords");
      builder.redirectErrorStream(true);
      Process process = builder.start();
      List<String> keywords = new BufferedReader(new InputStreamReader(
              process.getInputStream(), StandardCharsets.UTF_8)).lines()
              .map(String::trim)
              .filter(keyword -> keyword.matches("[a-z]+"))
              .collect(java.util.stream.Collectors.toList());
      if (process.waitFor() == 0 && !keywords.isEmpty()) {
        zpeedyKeywords = Collections.unmodifiableList(keywords);
        return zpeedyKeywords;
      }
    } catch (Exception ignored) {
      // Keep editing available while the optional runtime is not installed.
    }
    zpeedyKeywords = List.of(
            "a", "alternatively", "as", "at", "attempt", "back", "based", "call",
            "choice", "continue", "display", "divide", "do", "error", "every", "for",
            "forever", "give", "gives", "greater", "has", "if", "in", "is", "least",
            "less", "loop", "minus", "most", "not", "on", "otherwise", "plus", "repeat",
            "routine", "set", "stop", "takes", "than", "then", "thing", "times", "to",
            "when", "while", "with"
    );
    return zpeedyKeywords;
  }

  private void addLiteral(CodeEditorViewFX editor, String literal, CodeSyntaxModel.Style style) {
    editor.addKeyword(literal, style);
    editor.addAutoCompleteItem(literal, CodeEditorViewFX.AutoCompleteItemType.Keyword);
  }

  private void registerLanguageSupports() {
    if (languageSupportsRegistered) return;
    languageSupportsRegistered = true;
    registerLanguage(new LanguageSupport(
            "yass", "YASS", Set.of("yas"), this::configureYass,
            this::yassInfo, null
    ));
    registerLanguage(new LanguageSupport(
            "zpeedy",
            "Zpeedy Script",
            Set.of("zps"),
            this::configureZpeedy,
            token -> zpeedyInfo(token.toLowerCase(Locale.ROOT)),
            this::runZpeedyCode
    ));
    registerLanguage(new LanguageSupport(
            "python", "Python", Set.of("py"), this::configurePython,
            null, this::runPythonCode
    ));
    registerLanguage(new LanguageSupport(
            "sqarl", "SQARL", Set.of("sqarl"), this::configureSqarl,
            null, this::runSqarlCode
    ));
    registerLanguage(new LanguageSupport(
            "ywp", "YWP", Set.of("ywp"), this::configurePlainText,
            null, null
    ));
    registerLanguage(new LanguageSupport(
            "txt", "Text", Set.of("txt", "md", "log"), this::configurePlainText,
            null, null
    ));
  }

  private void registerLanguage(LanguageSupport support) {
    languageSupports.put(support.id, support);
  }

  private LanguageSupport languageSupportForFile(String file) {
    if (file == null) return null;
    registerLanguageSupports();
    String name = file.toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    String extension = dot < 0 ? "" : name.substring(dot + 1);
    for (LanguageSupport support : languageSupports.values()) {
      if (support.extensions.contains(extension)) return support;
    }
    return null;
  }

  private void configurePython(CodeEditorViewFX editor) {
      // Python files were previously treated as plain text, which made a
      // dark editor look washed out and left the source almost uncoloured.
      editor.setLineCommentMarkers("#");
      editor.setBlockCommentMarkers("", "");
      editor.setQuoteDelimiters("\"'");
      editor.setVariableDelimiters("");
      editor.setContextSeparator("");
      editor.clearKeywords();
      editor.clearContextualKeywords();
      editor.clearAutoCompleteItems();

      String[] pythonKeywords = {
              "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
              "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
              "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
              "return", "try", "while", "with", "yield"
      };
      String[] pythonTypes = {"int", "float", "str", "bool", "list", "dict", "set", "tuple", "object", "type"};
      String[] pythonFunctions = {"print", "len", "range", "enumerate", "zip", "map", "filter", "sorted", "sum", "min", "max"};
      for (String keyword : pythonKeywords) {
        editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
        editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
      }
      for (String type : pythonTypes) {
        editor.addKeyword(type, CodeSyntaxModel.Style.TYPE);
        editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type);
      }
      for (String function : pythonFunctions) {
        editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION);
        editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function);
      }
      editor.addKeyword("True", CodeSyntaxModel.Style.BOOLEAN);
      editor.addKeyword("False", CodeSyntaxModel.Style.BOOLEAN);
      editor.addKeyword("None", CodeSyntaxModel.Style.NULL);
  }

  private void runPythonCode(EditorTab tab) {
    if (tab == null) return;
    try {
      Path source = Files.createTempFile("zide-python-", ".py");
      Files.writeString(source, tab.getEditor().getText(), StandardCharsets.UTF_8);
      source.toFile().deleteOnExit();

      ProcessBuilder process = HelperFunctions.isWindows()
              ? new ProcessBuilder("py", "-3", source.toString())
              : new ProcessBuilder("python3", source.toString());
      Path workingDirectory = resourceDirectoryFor(tab);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
        process.directory(workingDirectory.toFile());
      }

      consoleOutputTextArea.clear();
      consoleOutputTextArea.append("Python runtime\n\n", InteractiveConsoleFX.OutputKind.KEY);
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
      runBtn.getStyleClass().add("running");
      statusLabel.setText("Executing Python");
      consoleOutputTextArea.addProcessFinishedListener(() -> Platform.runLater(() -> {
        runBtn.getStyleClass().remove("running");
        statusLabel.setText("Ready");
      }));
      consoleOutputTextArea.runProcess(process);
    } catch (IOException exception) {
      runBtn.getStyleClass().remove("running");
      statusLabel.setText("Ready");
      consoleOutputTextArea.append(
              "Python could not be started. Install Python 3 and ensure its command is available: "
                      + exception.getMessage() + "\n",
              InteractiveConsoleFX.OutputKind.ERROR
      );
    }
  }

  private void debugPythonCode(EditorTab tab) {
    if (tab == null) return;
    try {
      Path source = Files.createTempFile("zide-python-debug-", ".py");
      Files.writeString(source, tab.getEditor().getText(), StandardCharsets.UTF_8);
      source.toFile().deleteOnExit();

      List<String> command = new ArrayList<>();
      if (HelperFunctions.isWindows()) {
        command.addAll(List.of("py", "-3"));
      } else {
        command.add("python3");
      }
      command.addAll(List.of("-m", "pdb"));
      for (int line = 1; line <= tab.getEditor().getText().split("\\R", -1).length; line++) {
        if (tab.hasSpecialLine(line)) command.addAll(List.of("-c", "break " + line));
      }
      command.add(source.toString());

      ProcessBuilder process = new ProcessBuilder(command);
      Path workingDirectory = resourceDirectoryFor(tab);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
        process.directory(workingDirectory.toFile());
      }

      consoleOutputTextArea.clear();
      consoleOutputTextArea.append(
              "Python debugger (pdb)\nUse next, step, continue, print(expression), where, or quit.\n\n",
              InteractiveConsoleFX.OutputKind.KEY
      );
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
      debugBtn.getStyleClass().add("running");
      statusLabel.setText("Debugging Python");
      consoleOutputTextArea.addProcessFinishedListener(() -> Platform.runLater(() -> {
        debugBtn.getStyleClass().remove("running");
        statusLabel.setText("Ready");
      }));
      consoleOutputTextArea.runProcess(process);
    } catch (IOException exception) {
      debugBtn.getStyleClass().remove("running");
      statusLabel.setText("Ready");
      consoleOutputTextArea.append(
              "Python debugger could not be started: " + exception.getMessage() + "\n",
              InteractiveConsoleFX.OutputKind.ERROR
      );
    }
  }

  List<YASSDiagnostic> analyseCodeForTab(EditorTab tab, String source) {
    if (tab == null || !"python".equals(tab.getLanguageId())) {
      return ZPEKit.analyseCode(source, 0, source.length());
    }
    return analysePython(source);
  }

  private List<YASSDiagnostic> analysePython(String source) {
    Path temporary = null;
    try {
      temporary = Files.createTempFile("zide-python-check-", ".py");
      Files.writeString(temporary, source, StandardCharsets.UTF_8);
      ProcessBuilder builder = HelperFunctions.isWindows()
              ? new ProcessBuilder("py", "-3", "-m", "py_compile", temporary.toString())
              : new ProcessBuilder("python3", "-m", "py_compile", temporary.toString());
      Process process = builder.start();
      String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.waitFor() == 0) return analysePythonNames(source, temporary);
      YASSDiagnostic diagnostic = pythonDiagnostic(source, error);
      return diagnostic == null ? List.of() : List.of(diagnostic);
    } catch (IOException exception) {
      return List.of();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return List.of();
    } finally {
      if (temporary != null) {
        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
      }
    }
  }

  private List<YASSDiagnostic> analysePythonNames(String source, Path sourceFile)
          throws IOException, InterruptedException {
    String analyser = String.join("\n",
            "import ast, builtins, difflib, sys",
            "tree = ast.parse(open(sys.argv[1], encoding='utf-8').read())",
            "known = set(dir(builtins))",
            "known.update({'__name__', '__file__', '__package__', '__doc__', '__loader__', '__spec__', '__annotations__', '__builtins__', '__cached__'})",
            "match_as = getattr(ast, 'MatchAs', ())",
            "for node in ast.walk(tree):",
            "    if isinstance(node, ast.Name) and isinstance(node.ctx, (ast.Store, ast.Del)):",
            "        known.add(node.id)",
            "    elif isinstance(node, ast.arg): known.add(node.arg)",
            "    elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)): known.add(node.name)",
            "    elif isinstance(node, ast.alias): known.add(node.asname or node.name.split('.')[0])",
            "    elif isinstance(node, ast.ExceptHandler) and node.name: known.add(node.name)",
            "    elif match_as and isinstance(node, match_as) and node.name: known.add(node.name)",
            "seen = set()",
            "for node in ast.walk(tree):",
            "    if not isinstance(node, ast.Name) or not isinstance(node.ctx, ast.Load) or node.id in known: continue",
            "    key = (node.lineno, node.col_offset, node.id)",
            "    if key in seen: continue",
            "    seen.add(key)",
            "    match = difflib.get_close_matches(node.id, sorted(known), n=1, cutoff=0.72)",
            "    suggestion = match[0] if match else ''",
            "    print(node.lineno, node.col_offset + 1, getattr(node, 'end_col_offset', node.col_offset + len(node.id)) + 1, node.id, suggestion, sep='\\t')"
    );
    ProcessBuilder builder = HelperFunctions.isWindows()
            ? new ProcessBuilder("py", "-3", "-c", analyser, sourceFile.toString())
            : new ProcessBuilder("python3", "-c", analyser, sourceFile.toString());
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0 || output.isBlank()) return List.of();

    List<YASSDiagnostic> diagnostics = new ArrayList<>();
    for (String result : output.split("\\R")) {
      String[] fields = result.split("\\t", -1);
      if (fields.length < 5) continue;
      int line = Integer.parseInt(fields[0]);
      int column = Integer.parseInt(fields[1]);
      int endColumn = Integer.parseInt(fields[2]);
      String message = "Name '" + fields[3] + "' is not defined.";
      if (!fields[4].isEmpty()) message += " Did you mean '" + fields[4] + "'?";
      int start = sourceOffset(source, line, column);
      int end = Math.max(start + 1, sourceOffset(source, line, endColumn));
      YASSDiagnostic diagnostic = createPythonDiagnostic(source, message, line, column, start, end);
      if (diagnostic != null) diagnostics.add(diagnostic);
    }
    return diagnostics;
  }

  private YASSDiagnostic pythonDiagnostic(String source, String error) {
    java.util.regex.Matcher lineMatcher = java.util.regex.Pattern
            .compile("File \\\"[^\\\"]+\\\", line (\\d+)")
            .matcher(error);
    int line = lineMatcher.find() ? Integer.parseInt(lineMatcher.group(1)) : 1;
    String[] outputLines = error.split("\\R", -1);
    int column = 1;
    for (String outputLine : outputLines) {
      int caret = outputLine.indexOf('^');
      if (caret >= 0) column = Math.max(1, caret - 3);
    }
    String message = "Python syntax error";
    for (int index = outputLines.length - 1; index >= 0; index--) {
      if (!outputLines[index].isBlank()) {
        message = outputLines[index].trim();
        break;
      }
    }
    int start = sourceOffset(source, line, column);
    int end = Math.min(source.length(), Math.max(start + 1, start));
    return createPythonDiagnostic(source, message, line, column, start, end);
  }

  private YASSDiagnostic createPythonDiagnostic(String source, String message,
                                                 int line, int column, int start, int end) {
    try {
      Class<?> severityClass = Class.forName("jamiebalfour.zpe.core.YASSAnalyser$Severity");
      @SuppressWarnings({"rawtypes", "unchecked"})
      Object severity = Enum.valueOf((Class<? extends Enum>) severityClass.asSubclass(Enum.class), "ERROR");
      java.lang.reflect.Constructor<YASSDiagnostic> constructor = YASSDiagnostic.class.getConstructor(
              severityClass, String.class, String.class,
              int.class, int.class, int.class, int.class, String.class
      );
      constructor.setAccessible(true);
      return constructor.newInstance(severity, message, "", line, column, start, end, source);
    } catch (ReflectiveOperationException exception) {
      return null;
    }
  }

  private static int sourceOffset(String source, int line, int column) {
    int offset = 0;
    int currentLine = 1;
    while (currentLine < line && offset < source.length()) {
      if (source.charAt(offset++) == '\n') currentLine++;
    }
    return Math.min(source.length(), offset + Math.max(0, column - 1));
  }

  private void configurePlainText(CodeEditorViewFX editor) {
      editor.setLineCommentMarkers("//");
      editor.setBlockCommentMarkers("/*", "*/");
      editor.setQuoteDelimiters("\"'");
      editor.setVariableDelimiters("$");
      editor.setContextSeparator("");
      editor.clearKeywords();
      editor.clearContextualKeywords();
      editor.clearAutoCompleteItems();
  }

  private void configureSqarl(CodeEditorViewFX editor) {
      editor.setLineCommentMarkers("//");
      editor.setBlockCommentMarkers("/*", "*/");
      editor.setQuoteDelimiters("\"'");
      editor.setVariableDelimiters("");
      editor.setContextSeparator("");
      editor.clearKeywords();
      editor.clearContextualKeywords();
      editor.clearAutoCompleteItems();

      String[] keywords = {
              "DECLARE", "INITIALLY", "WHILE", "RECEIVE", "FROM", "KEYBOARD", "END",
              "SEND", "FOR", "EACH", "DO", "IF", "THEN", "SET", "TO", "DISPLAY",
              "ARRAY", "STRING", "RECORD", "CLASS", "INTEGER", "REAL", "BOOLEAN",
              "CHARACTER", "FUNCTION", "RETURN", "PROCEDURE", "AND", "OR", "NOT",
              "MOD", "OPEN", "CLOSE", "CREATE", "METHODS", "THIS", "WITH", "OVERRIDE",
              "INHERITS", "CONSTRUCTOR", "IS", "AS", "ELSE"
      };
      for (String keyword : keywords) {
        editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
        editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
      }
  }

  private void runSqarlCode(EditorTab tab) {
    if (tab == null) return;
    try {
      Path temporary = Files.createTempFile("zide-sqarl-", ".sqarl");
      Files.writeString(temporary, tab.getEditor().getText(), StandardCharsets.UTF_8);
      temporary.toFile().deleteOnExit();
      ProcessBuilder process = HelperFunctions.isWindows()
              ? new ProcessBuilder("cmd.exe", "/c", "sqarl", "-r", temporary.toString())
              : new ProcessBuilder("sqarl", "-r", temporary.toString());
      Path workingDirectory = resourceDirectoryFor(tab);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
        process.directory(workingDirectory.toFile());
      }
      consoleOutputTextArea.clear();
      consoleOutputTextArea.append("SQARL runtime\n\n", InteractiveConsoleFX.OutputKind.KEY);
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
      runBtn.getStyleClass().add("running");
      statusLabel.setText("Executing SQARL");
      consoleOutputTextArea.addProcessFinishedListener(() -> Platform.runLater(() -> {
        runBtn.getStyleClass().remove("running");
        statusLabel.setText("Ready");
      }));
      consoleOutputTextArea.runProcess(process);
    } catch (IOException exception) {
      runBtn.getStyleClass().remove("running");
      statusLabel.setText("Ready");
      consoleOutputTextArea.append("SQARL could not be started: " + exception.getMessage() + "\n",
              InteractiveConsoleFX.OutputKind.ERROR);
    }
  }

  EditorInfo editorInfo(String languageId, String path, String source, String token, int offset) {
    if (token == null || token.isEmpty()) return null;
    EditorInfo variable = variableInfo(languageId, source, token, offset);
    if (variable != null) return variable;
    EditorInfo userDefined = sourceInfo(languageId, source, token);
    if (userDefined != null) return userDefined;
    LanguageSupport registered = languageSupports.get(languageId);
    if (registered == null && path != null) registered = languageSupportForFile(path);
    if (registered != null && registered.information != null) return registered.information.apply(token);
    return null;
  }

  private EditorInfo variableInfo(String languageId, String source, String token, int offset) {
    if (!"yass".equals(languageId) && !"zpeedy".equals(languageId)) return null;
    String name = normaliseVariableName(token);
    RuntimeVariable live = breakpointVariables.get(name);
    if (currentBreakpoint != null && live != null) {
      String context = live.function == null || live.function.isBlank()
              ? "Paused at the current breakpoint"
              : "Paused in " + live.function;
      return new EditorInfo(token + (live.type.isBlank() ? "" : " : " + live.type),
              live.value, context, "Live debugger value", null);
    }
    return predictedVariableInfo(languageId, source, token, offset);
  }

  private EditorInfo predictedVariableInfo(String languageId, String source, String token, int offset) {
    if (source == null || source.isBlank()) return null;
    String lookup = normaliseVariableName(token);
    int lineEnd = source.indexOf('\n', Math.max(0, Math.min(offset, source.length())));
    int limit = lineEnd < 0 ? source.length() : lineEnd;
    String available = source.substring(0, limit);
    Assignment assignment = "zpeedy".equals(languageId)
            ? lastZpeedyAssignment(available, lookup)
            : lastYassAssignment(available, lookup);
    if (assignment == null) return parameterInfo(languageId, available, token, lookup);

    Prediction prediction = predictExpression(languageId, available, assignment.expression, assignment.start, 0);
    String body;
    if (prediction.known) {
      body = "Predicted value: " + prediction.description;
    } else if (prediction.call) {
      body = "Value will come from " + prediction.description + ". The result is available when the call returns.";
    } else {
      body = "Value will be calculated from " + prediction.description + ".";
    }
    return new EditorInfo(token + (prediction.type == null ? "" : " : " + prediction.type), body,
            "Assigned on line " + lineNumberAt(available, assignment.start),
            prediction.known ? "Predicted value" : "Static value origin", null);
  }

  private EditorInfo parameterInfo(String languageId, String source, String token, String lookup) {
    String name = Pattern.quote(lookup);
    Pattern pattern = "zpeedy".equals(languageId)
            ? Pattern.compile("(?im)^\\s*routine\\s+\\w+\\s+takes\\s+[^#\\r\\n]*\\b" + name + "\\b")
            : Pattern.compile("(?im)^\\s*(?:(?:public|private|protected|static|final)\\s+)*function\\s+\\w+\\s*\\([^)]*\\$?" + name + "\\b[^)]*\\)");
    if (!pattern.matcher(source).find()) return null;
    return new EditorInfo(token, "Value is supplied by the caller when this routine runs.",
            null, "Function parameter", null);
  }

  private Assignment lastYassAssignment(String source, String name) {
    Pattern pattern = Pattern.compile("(?m)^\\s*\\$" + Pattern.quote(name)
            + "\\s*=(?!=)\\s*(.+?)\\s*$");
    return lastAssignment(source, pattern);
  }

  private Assignment lastZpeedyAssignment(String source, String name) {
    Pattern pattern = Pattern.compile("(?im)^\\s*set\\s+" + Pattern.quote(name)
            + "\\s+to\\s+(.+?)(?:\\s*#.*)?$");
    return lastAssignment(source, pattern);
  }

  private Assignment lastAssignment(String source, Pattern pattern) {
    Matcher matcher = pattern.matcher(source);
    Assignment result = null;
    while (matcher.find()) result = new Assignment(matcher.start(), matcher.group(1).trim());
    return result;
  }

  private Prediction predictExpression(String languageId, String source, String expression, int before, int depth) {
    String value = expression.trim();
    if (value.matches("[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)")) {
      return Prediction.known(value, value.contains(".") ? "real" : "number");
    }
    if (value.matches("(?s)[\"'].*[\"']")) return Prediction.known(value, "string");
    if (value.matches("(?i:true|false)")) return Prediction.known(value.toLowerCase(Locale.ROOT), "boolean");
    if (value.matches("(?i:null|nothing|unknown)")) return Prediction.known(value, "null");
    if (value.matches("\\[.*]")) return Prediction.known(value, "list");
    if (value.matches("\\{.*}")) return Prediction.known(value, "map");

    Matcher call = Pattern.compile("^([A-Za-z_][A-Za-z0-9_:]*)\\s*\\(.*\\)$").matcher(value);
    if (call.matches()) return Prediction.call("the function call " + value);
    Matcher zpeedyCall = Pattern.compile("(?i)^call\\s+([A-Za-z_][A-Za-z0-9_]*)\\b.*$").matcher(value);
    if (zpeedyCall.matches()) return Prediction.call("the routine call " + value);

    Matcher reference = Pattern.compile("^\\$?([A-Za-z_][A-Za-z0-9_]*)$").matcher(value);
    if (reference.matches() && depth < 8) {
      String referenced = reference.group(1);
      Assignment earlier = "zpeedy".equals(languageId)
              ? lastZpeedyAssignment(source.substring(0, Math.min(before, source.length())), referenced)
              : lastYassAssignment(source.substring(0, Math.min(before, source.length())), referenced);
      if (earlier != null) return predictExpression(languageId, source, earlier.expression, earlier.start, depth + 1);
    }
    return Prediction.expression(value);
  }

  private static String normaliseVariableName(String token) {
    String name = token == null ? "" : token.trim();
    while (name.startsWith("$")) name = name.substring(1);
    return name;
  }

  private static int lineNumberAt(String source, int offset) {
    int line = 1;
    for (int i = 0; i < Math.min(offset, source.length()); i++) if (source.charAt(i) == '\n') line++;
    return line;
  }

  private EditorInfo yassInfo(String token) {
    if (Arrays.asList(ZPEKit.getBuiltInStructuresNames()).contains(token)) {
      return new EditorInfo(token, "Built-in ZPE structure", null, "Built-in structure", null);
    }
    boolean predefined = ZPEKit.getAllFunctions().contains(token);
    if (!predefined && token.contains("::")) {
      String[] parts = token.split("::", 2);
      ZPEModule module = ZPEKit.getBuiltinModules().get(parts[0]);
      predefined = module != null && module.getMethods().contains(parts[1]);
    }
    if (!predefined) return null;
    String header = ZPEKit.getFunctionManualHeader(token);
    String entry = ZPEKit.getFunctionManualEntry(token);
    String returnType = ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(token));
    String title = (header == null || header.isBlank() ? token + "()" : header) + " : " + returnType;
    String category = ZPEKit.getFunctionCategory(token);
    String categoryPath = category == null ? null
            : category.toLowerCase(Locale.ROOT).replace("/", "").replace(" ", "_");
    String url = categoryPath == null ? null
            : "https://www.jamiebalfour.scot/projects/zpe/documentation/functions/" + categoryPath + "/" + token;
    return new EditorInfo(title,
            entry == null || entry.isBlank() ? "Built-in YASS function" : entry,
            "Function version " + ZPEKit.getFunctionVersion(token),
            category == null ? null : "Category: " + category,
            url);
  }

  private EditorInfo zpeedyInfo(String token) {
    EditorInfo predefined = yassInfo(token);
    if (predefined != null) return predefined;
    switch (token) {
      case "display": return new EditorInfo("display value", "Writes one value to the program output.");
      case "set": return new EditorInfo("set name to value", "Creates or updates a named value.");
      case "routine": return new EditorInfo("routine name takes values", "Declares a reusable Zpeedy routine.");
      case "call": return new EditorInfo("call routine with values", "Invokes a declared or host-provided routine.");
      case "thing": return new EditorInfo("thing Name", "Declares a structured Zpeedy type and its properties.");
      case "repeat": return new EditorInfo("repeat count times", "Repeats an indented block a fixed number of times, or forever.");
      case "when": return new EditorInfo("when value", "Selects the first matching indented branch.");
      case "attempt": return new EditorInfo("attempt", "Runs a block with an optional otherwise-on-error handler.");
      case "nothing": return new EditorInfo("nothing", "Zpeedy's null value.");
      case "unknown": return new EditorInfo("unknown", "Zpeedy's undefined value.");
      default: return null;
    }
  }

  private EditorInfo sourceInfo(String languageId, String source, String token) {
    if (source == null || source.isBlank()) return null;
    if ("zpeedy".equals(languageId)) return zpeedySourceInfo(source, token);
    if (!"yass".equals(languageId)) return null;

    Matcher function = Pattern.compile(
            "(?im)^\\s*(?:(?:public|private|protected|static|final)\\s+)*function\\s+"
                    + Pattern.quote(token) + "\\s*\\(([^)]*)\\)").matcher(source);
    if (function.find()) {
      return new EditorInfo(token + "(" + function.group(1).trim() + ")",
              sourceDocumentation(source, function.start(), "User-defined function"),
              null, "User-defined function", null);
    }

    Matcher object = Pattern.compile(
            "(?im)^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*"
                    + "(module|namespace|class|structure|interface|record)\\s+"
                    + Pattern.quote(token) + "\\b").matcher(source);
    if (object.find()) {
      String kind = object.group(1).toLowerCase(Locale.ROOT);
      return new EditorInfo(kind + " " + token,
              sourceDocumentation(source, object.start(), "User-defined " + kind),
              null, "User-defined " + kind, null);
    }
    return null;
  }

  private EditorInfo zpeedySourceInfo(String source, String token) {
    Matcher routine = Pattern.compile("(?im)^\\s*routine\\s+" + Pattern.quote(token)
            + "(?:\\s+takes\\s+([^#\\r\\n]+))?").matcher(source);
    if (routine.find()) {
      String parameters = routine.group(1) == null ? "" : routine.group(1).trim();
      return new EditorInfo("routine " + token + (parameters.isEmpty() ? "" : " takes " + parameters),
              zpeedyDocumentation(source, routine.start(), "User-defined routine"),
              null, "User-defined routine", null);
    }
    Matcher thing = Pattern.compile("(?im)^\\s*thing\\s+" + Pattern.quote(token) + "\\b").matcher(source);
    if (thing.find()) {
      return new EditorInfo("thing " + token,
              zpeedyDocumentation(source, thing.start(), "User-defined thing"),
              null, "User-defined thing", null);
    }
    return null;
  }

  private String sourceDocumentation(String source, int declaration, String fallback) {
    String before = source.substring(0, declaration);
    String[] lines = before.split("\\R", -1);
    for (int i = lines.length - 1; i >= 0; i--) {
      String line = lines[i].trim();
      if (line.isEmpty()) continue;
      if (line.toLowerCase(Locale.ROOT).startsWith("@doc ")) {
        return line.substring(5).trim().replaceAll("^[\\\"']|[\\\"']$", "");
      }
      if (!line.startsWith("@")) break;
    }
    return fallback;
  }

  private String zpeedyDocumentation(String source, int declaration, String fallback) {
    int start = source.lastIndexOf('\n', Math.max(0, declaration - 1));
    if (start < 0) return fallback;
    int previous = source.lastIndexOf('\n', Math.max(0, start - 1));
    String line = source.substring(previous < 0 ? 0 : previous + 1, start).trim();
    return line.startsWith("#") ? line.substring(1).trim() : fallback;
  }

  static final class EditorInfo {
    final String title;
    final String body;
    final String version;
    final String category;
    final String url;

    EditorInfo(String title, String body) {
      this(title, body, null, null, null);
    }

    EditorInfo(String title, String body, String version, String category, String url) {
      this.title = title;
      this.body = body;
      this.version = version;
      this.category = category;
      this.url = url;
    }
  }

  private static final class LanguageSupport {
    final String id;
    final String label;
    final Set<String> extensions;
    final Consumer<CodeEditorViewFX> configure;
    final Function<String, EditorInfo> information;
    final Consumer<EditorTab> runner;

    LanguageSupport(String id, String label, Set<String> extensions,
                    Consumer<CodeEditorViewFX> configure,
                    Function<String, EditorInfo> information,
                    Consumer<EditorTab> runner) {
      this.id = id;
      this.label = label;
      this.extensions = extensions;
      this.configure = configure;
      this.information = information;
      this.runner = runner;
    }

    @Override
    public String toString() { return label; }

    boolean isYass() { return "yass".equals(id); }
    boolean canRun() { return isYass() || runner != null; }
    boolean canCompile() { return isYass() || "zpeedy".equals(id) || "sqarl".equals(id); }
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

  private Tab createEditorTab(String title, CodeEditorViewFX editor, String path, Node content) {
    EditorTab tab = new EditorTab(this, title, path, editor, content);

    // Disable JavaFX built-in close button
    tab.setClosable(false);

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("tab-title");
    tab.setTabTitleLabel(titleLabel);

    Button closeBtn = new Button();
    closeBtn.getStyleClass().add("tab-close-button");
    closeBtn.setFocusTraversable(false);


    closeBtn.setMinSize(10, 10);
    closeBtn.setPrefSize(10, 10);
    closeBtn.setMaxSize(10, 10);

    if(darkThemeMenuItem.isSelected()){
      tab.switchOnDarkMode();
    }



    closeBtn.setOnAction(e -> {
      if(tab.hasChanges()){
      if (!tab.getEditor().getText().isBlank() && !confirmClose(tab.getDisplayTitle())) {
          return;
        }
      }

      TabPane pane = tab.getTabPane();
      if (pane != null) {
        tab.dispose();
        pane.getTabs().remove(tab);
      }
    });

    HBox header = new HBox(titleLabel, closeBtn);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setSpacing(6);
    header.getStyleClass().add("tab-header");

    tab.setText("");          // IMPORTANT: text comes from our label now
    tab.setGraphic(header);   // Graphic is the whole header (so X can be right)


    return tab;
  }

  private ToggleButton consoleTab;
  private ToggleButton problemsTab;
  private ToggleButton variablesTab;
  private ToggleButton profileTab;
  private ToggleButton terminalTab;

  private StackPane bottomContentStack;
  private Node consoleView;
  private Node problemsView;
  private Node variablesView;
  private Node profileView;
  private Node terminalView;

  private Node wrapWithHeader(String titleText, Node content, Node... actions) {
    Label title = new Label(titleText);
    title.getStyleClass().add("pane-title");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox header = new HBox(8);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("pane-header");
    header.getChildren().addAll(title, spacer);
    header.getChildren().addAll(actions);

    VBox box = new VBox(header, content);
    VBox.setVgrow(content, Priority.ALWAYS);
    box.getStyleClass().add("pane-wrapper");

    return box;
  }

  private Button panelIconButton(String iconPath, String tooltip, Runnable action) {
    ImageView icon = new ImageView(new Image(
            Objects.requireNonNull(getClass().getResourceAsStream(iconPath))
    ));

    icon.setFitWidth(14);   // tweak: 12–16 is sweet spot
    icon.setFitHeight(14);
    icon.setPreserveRatio(true);
    icon.setSmooth(true);
    icon.getStyleClass().add("panel-icon");

    Button btn = new Button();
    btn.setGraphic(icon);
    btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    btn.setTooltip(new Tooltip(tooltip));

    btn.setMinSize(14, 14);
    btn.setPrefSize(14, 14);
    btn.setMaxSize(14, 14);

    icon.setFitWidth(12);
    icon.setFitHeight(12);

    btn.getStyleClass().add("panel-icon-button");
    btn.setFocusTraversable(false);

    btn.setOnAction(e -> action.run());

    return btn;
  }

  private LineChart<Number, Number> profilerChart;
  private LineChart<Number, Number> cpuProfilerChart;
  private XYChart.Series<Number, Number> memorySeries;
  private XYChart.Series<Number, Number> nonHeapMemorySeries;
  private XYChart.Series<Number, Number> cpuSeries;
  private StackPane profilerChartStack;
  private javafx.scene.shape.Line profilerHoverLine;
  private Label profilerHoverDetails;
  private Popup profilerInfoPopup;
  private Button profilerMetricButton;
  private boolean showingCpuProfiler;
  private Label profilerInactiveMessage;
  private HBox profilerStatistics;
  private Label profilerMemoryLabel;
  private Label profilerCpuLabel;
  private Label profilerThreadLabel;
  private Label profilerTimeLabel;
  private volatile boolean profilingActive;
  private boolean profilerHasRun;
  private final ConcurrentLinkedQueue<ZPEDebugger.ProfileSample> pendingProfileSamples =
          new ConcurrentLinkedQueue<>();
  private final AtomicBoolean profileUpdateScheduled = new AtomicBoolean(false);
  private final List<ZPEDebugger.ProfileSample> profilerSamples = new ArrayList<>();
  private static final int MAX_PROFILE_POINTS = 6000;

  private Node buildProfilerPane() {
    NumberAxis xAxis = new NumberAxis();
    xAxis.setLabel("Time (ms)");
    xAxis.setForceZeroInRange(true);

    NumberAxis yAxis = new NumberAxis();
    yAxis.setLabel("Memory (MB)");
    yAxis.setForceZeroInRange(true);

    profilerChart = new LineChart<>(xAxis, yAxis);
    profilerChart.setAnimated(false);
    profilerChart.setCreateSymbols(false);
    profilerChart.setLegendVisible(true);
    profilerChart.getStyleClass().add("profiler-chart");

    memorySeries = new XYChart.Series<>();
    memorySeries.setName("Heap used");

    nonHeapMemorySeries = new XYChart.Series<>();
    nonHeapMemorySeries.setName("Non-heap used");

    profilerChart.getData().addAll(memorySeries, nonHeapMemorySeries);

    NumberAxis cpuXAxis = new NumberAxis();
    cpuXAxis.setLabel("Time (ms)");
    cpuXAxis.setForceZeroInRange(true);

    NumberAxis cpuYAxis = new NumberAxis(0.0, 100.0, 10.0);
    cpuYAxis.setLabel("CPU usage (%)");

    cpuProfilerChart = new LineChart<>(cpuXAxis, cpuYAxis);
    cpuProfilerChart.setAnimated(false);
    cpuProfilerChart.setCreateSymbols(false);
    cpuProfilerChart.setLegendVisible(false);
    cpuProfilerChart.getStyleClass().add("cpu-profiler-chart");

    cpuSeries = new XYChart.Series<>();
    cpuSeries.setName("CPU usage");
    cpuProfilerChart.getData().add(cpuSeries);

    cpuProfilerChart.setVisible(false);
    cpuProfilerChart.setManaged(false);

    profilerHoverLine = new javafx.scene.shape.Line();
    profilerHoverLine.getStyleClass().add("profiler-hover-line");
    profilerHoverLine.setManaged(false);
    profilerHoverLine.setMouseTransparent(true);
    profilerHoverLine.setVisible(false);

    profilerHoverDetails = new Label();
    profilerHoverDetails.getStyleClass().add("profiler-hover-details");
    profilerHoverDetails.setManaged(false);
    profilerHoverDetails.setMouseTransparent(true);
    profilerHoverDetails.setVisible(false);

    profilerChartStack = new StackPane(
            profilerChart,
            cpuProfilerChart,
            profilerHoverLine,
            profilerHoverDetails
    );
    profilerChartStack.setOnMouseMoved(this::inspectProfilerAtMouse);
    profilerChartStack.setOnMouseExited(e -> hideProfilerInspection());
    VBox.setVgrow(profilerChartStack, Priority.ALWAYS);

    profilerMemoryLabel = new Label("Heap —");
    profilerCpuLabel = new Label("CPU —");
    profilerThreadLabel = new Label("Threads —");
    profilerTimeLabel = new Label("Time —");

    profilerMemoryLabel.getStyleClass().add("profiler-stat");
    profilerCpuLabel.getStyleClass().add("profiler-stat");
    profilerThreadLabel.getStyleClass().add("profiler-stat");
    profilerTimeLabel.getStyleClass().add("profiler-stat");

    profilerStatistics = new HBox(
            18,
            profilerMemoryLabel,
            profilerCpuLabel,
            profilerThreadLabel,
            profilerTimeLabel
    );
    profilerStatistics.setAlignment(Pos.CENTER_LEFT);
    profilerStatistics.getStyleClass().add("profiler-statistics");

    VBox content = new VBox(profilerStatistics, profilerChartStack);
    VBox.setVgrow(profilerChartStack, Priority.ALWAYS);

    profilerInactiveMessage = new Label("Debug the script to see live time statistics");
    profilerInactiveMessage.getStyleClass().add("profiler-inactive-message");
    profilerInactiveMessage.setMouseTransparent(true);

    StackPane profilerStack = new StackPane(content, profilerInactiveMessage);
    profilerStack.getStyleClass().add("profiler-pane");

    ZPEDebugger.addProfileSampleListener(this::receiveProfileSample);
    setProfilerActive(false);

    return profilerStack;
  }

  private void clearProfiler() {
    Platform.runLater(() -> {
      pendingProfileSamples.clear();
      profilerSamples.clear();
      hideProfilerInspection();
      if (memorySeries != null) {
        memorySeries.getData().clear();
      }
      if (nonHeapMemorySeries != null) {
        nonHeapMemorySeries.getData().clear();
      }
      if (cpuSeries != null) {
        cpuSeries.getData().clear();
      }
      if (!profilingActive) {
        resetProfilerLabels();
      }
    });
  }

  private void beginProfilerSession() {
    profilingActive = true;
    profilerHasRun = true;
    pendingProfileSamples.clear();

    Platform.runLater(() -> {
      profilerSamples.clear();
      hideProfilerInspection();
      if (memorySeries != null) {
        memorySeries.getData().clear();
      }
      if (nonHeapMemorySeries != null) {
        nonHeapMemorySeries.getData().clear();
      }
      if (cpuSeries != null) {
        cpuSeries.getData().clear();
      }
      resetProfilerLabels();
      setProfilerActive(true);
    });
  }

  private void endProfilerSession() {
    profilingActive = false;
    Platform.runLater(() -> setProfilerActive(false));
  }

  private void setProfilerActive(boolean active) {
    if (profilerChart == null
            || cpuProfilerChart == null
            || profilerInactiveMessage == null
            || profilerStatistics == null) {
      return;
    }

    //profilerChart.setOpacity(active ? 1.0 : 0.28);
    //cpuProfilerChart.setOpacity(active ? 1.0 : 0.28);
    //profilerStatistics.setOpacity(active ? 1.0 : 0.28);
    boolean showInitialMessage = !active && !profilerHasRun;
    profilerInactiveMessage.setVisible(showInitialMessage);
    profilerInactiveMessage.setManaged(showInitialMessage);
  }

  private void resetProfilerLabels() {
    if (profilerMemoryLabel != null) {
      profilerMemoryLabel.setText("Heap —");
    }
    if (profilerCpuLabel != null) {
      profilerCpuLabel.setText("CPU —");
    }
    if (profilerThreadLabel != null) {
      profilerThreadLabel.setText("Threads —");
    }
    if (profilerTimeLabel != null) {
      profilerTimeLabel.setText("Time —");
    }
  }

  private void receiveProfileSample(ZPEDebugger.ProfileSample sample) {
    if (!profilingActive || sample == null) {
      return;
    }

    pendingProfileSamples.offer(sample);
    scheduleProfileUpdate();
  }

  private void scheduleProfileUpdate() {
    if (profileUpdateScheduled.compareAndSet(false, true)) {
      Platform.runLater(this::drainProfileSamples);
    }
  }

  private void drainProfileSamples() {
    ZPEDebugger.ProfileSample sample;
    ZPEDebugger.ProfileSample latest = null;

    while ((sample = pendingProfileSamples.poll()) != null) {
      latest = sample;
      addProfilePoint(sample);
    }

    if (latest != null) {
      updateProfilerLabels(latest);
    }

    profileUpdateScheduled.set(false);
    if (!pendingProfileSamples.isEmpty()) {
      scheduleProfileUpdate();
    }
  }

  private void addProfilePoint(ZPEDebugger.ProfileSample sample) {
    if (memorySeries == null || nonHeapMemorySeries == null || cpuSeries == null) {
      return;
    }

    double elapsedMilliseconds = sample.elapsedNanoseconds / 1_000_000.0;
    double heapMegabytes = sample.heapUsed / (1024.0 * 1024.0);
    double nonHeapMegabytes = sample.nonHeapUsed / (1024.0 * 1024.0);

    profilerSamples.add(sample);
    memorySeries.getData().add(
            new XYChart.Data<>(elapsedMilliseconds, heapMegabytes)
    );
    nonHeapMemorySeries.getData().add(
            new XYChart.Data<>(elapsedMilliseconds, nonHeapMegabytes)
    );
    if (sample.processCpuLoad >= 0.0) {
      cpuSeries.getData().add(
              new XYChart.Data<>(elapsedMilliseconds, sample.processCpuLoad * 100.0)
      );
    }

    int excess = memorySeries.getData().size() - MAX_PROFILE_POINTS;
    if (excess > 0) {
      memorySeries.getData().remove(0, excess);
      nonHeapMemorySeries.getData().remove(0, excess);
      profilerSamples.subList(0, excess).clear();
    }

    int cpuExcess = cpuSeries.getData().size() - MAX_PROFILE_POINTS;
    if (cpuExcess > 0) {
      cpuSeries.getData().remove(0, cpuExcess);
    }
  }

  private void inspectProfilerAtMouse(MouseEvent event) {
    if (profilerSamples.isEmpty()
            || profilerChartStack == null
            || profilerHoverLine == null
            || profilerHoverDetails == null) {
      hideProfilerInspection();
      return;
    }

    LineChart<Number, Number> activeChart = showingCpuProfiler
            ? cpuProfilerChart
            : profilerChart;
    Node plotBackground = activeChart.lookup(".chart-plot-background");
    if (plotBackground == null) {
      hideProfilerInspection();
      return;
    }

    javafx.geometry.Bounds plotBounds =
            plotBackground.localToScene(plotBackground.getBoundsInLocal());
    double sceneX = event.getSceneX();
    double sceneY = event.getSceneY();

    if (!plotBounds.contains(sceneX, sceneY)) {
      hideProfilerInspection();
      return;
    }

    NumberAxis timeAxis = (NumberAxis) activeChart.getXAxis();
    javafx.geometry.Point2D axisPoint = timeAxis.sceneToLocal(sceneX, sceneY);
    Number timeValue = timeAxis.getValueForDisplay(axisPoint.getX());
    if (timeValue == null) {
      hideProfilerInspection();
      return;
    }

    ZPEDebugger.ProfileSample sample =
            findNearestProfileSample(timeValue.doubleValue() * 1_000_000.0);
    if (sample == null) {
      hideProfilerInspection();
      return;
    }

    javafx.geometry.Point2D lineTop =
            profilerChartStack.sceneToLocal(sceneX, plotBounds.getMinY());
    javafx.geometry.Point2D lineBottom =
            profilerChartStack.sceneToLocal(sceneX, plotBounds.getMaxY());

    profilerHoverLine.setStartX(lineTop.getX());
    profilerHoverLine.setEndX(lineBottom.getX());
    profilerHoverLine.setStartY(lineTop.getY());
    profilerHoverLine.setEndY(lineBottom.getY());
    profilerHoverLine.setVisible(true);

    profilerHoverDetails.setText(profileInspectionText(sample));
    profilerHoverDetails.setVisible(true);
    profilerHoverDetails.applyCss();
    profilerHoverDetails.autosize();

    double preferredX = lineTop.getX() + 10.0;
    double maximumX = Math.max(8.0,
            profilerChartStack.getWidth() - profilerHoverDetails.getWidth() - 8.0);
    if (preferredX > maximumX) {
      preferredX = lineTop.getX() - profilerHoverDetails.getWidth() - 10.0;
    }

    profilerHoverDetails.relocate(
            Math.max(8.0, Math.min(preferredX, maximumX)),
            Math.max(8.0, lineTop.getY() + 8.0)
    );
  }

  private ZPEDebugger.ProfileSample findNearestProfileSample(double elapsedNanoseconds) {
    int low = 0;
    int high = profilerSamples.size() - 1;

    while (low <= high) {
      int middle = (low + high) >>> 1;
      long sampleTime = profilerSamples.get(middle).elapsedNanoseconds;
      if (sampleTime < elapsedNanoseconds) {
        low = middle + 1;
      } else if (sampleTime > elapsedNanoseconds) {
        high = middle - 1;
      } else {
        return profilerSamples.get(middle);
      }
    }

    if (low <= 0) {
      return profilerSamples.get(0);
    }
    if (low >= profilerSamples.size()) {
      return profilerSamples.get(profilerSamples.size() - 1);
    }

    ZPEDebugger.ProfileSample before = profilerSamples.get(low - 1);
    ZPEDebugger.ProfileSample after = profilerSamples.get(low);
    return elapsedNanoseconds - before.elapsedNanoseconds
            <= after.elapsedNanoseconds - elapsedNanoseconds
            ? before
            : after;
  }

  private String profileInspectionText(ZPEDebugger.ProfileSample sample) {
    String functionName = sample.functionName == null || sample.functionName.isEmpty()
            ? "—"
            : sample.functionName;
    double heapMegabytes = sample.heapUsed / (1024.0 * 1024.0);
    String cpu = sample.processCpuLoad < 0.0
            ? "warming up"
            : String.format(Locale.ROOT, "%.1f%%", sample.processCpuLoad * 100.0);

    return "Function: " + functionName
            + "\n" + formatProfilerTime(sample.elapsedNanoseconds)
            + String.format(Locale.ROOT, "\nHeap %.1f MB   CPU %s   Threads %d",
            heapMegabytes,
            cpu,
            sample.threadCount);
  }

  private void hideProfilerInspection() {
    if (profilerHoverLine != null) {
      profilerHoverLine.setVisible(false);
    }
    if (profilerHoverDetails != null) {
      profilerHoverDetails.setVisible(false);
    }
  }

  private Button buildProfilerMetricButton() {
    profilerMetricButton = new Button("CPU");
    profilerMetricButton.getStyleClass().add("profiler-switch-button");
    profilerMetricButton.setTooltip(new Tooltip("Show CPU usage"));
    profilerMetricButton.setFocusTraversable(false);
    profilerMetricButton.setOnAction(e -> switchProfilerMetric());
    return profilerMetricButton;
  }

  private Button buildProfilerInfoButton() {
    Button button = new Button("i");
    button.getStyleClass().add("profiler-info-button");
    button.setTooltip(new Tooltip("About profiling"));
    button.setFocusTraversable(false);
    button.setOnAction(e -> toggleProfilerInformation(button));
    return button;
  }

  private void toggleProfilerInformation(Button owner) {
    if (profilerInfoPopup != null && profilerInfoPopup.isShowing()) {
      profilerInfoPopup.hide();
      return;
    }

    Label title = new Label("About profiling");
    title.getStyleClass().add("profiler-info-title");

    Label introduction = profilerInformationLabel(
            "Profiling collects timing, memory, CPU, thread and currently executing "
                    + "function samples while your program runs. Collecting and transmitting "
                    + "this information adds work and may affect the runtime's performance."
    );

    Label sampling = profilerInformationLabel(
            "ZPE samples every 10 ms and sends batches every 100 ms. ZPEX samples every "
                    + "1 ms and sends batches every 6 ms so that very fast native execution "
                    + "is easier to inspect."
    );

    Label guidance = profilerInformationLabel(
            "Samples are observations rather than an exact execution trace, and very short "
                    + "functions may still run between them. Use a normal Run—not a profiling "
                    + "session—for representative performance measurements."
    );

    VBox card = new VBox(8, title, introduction, sampling, guidance);
    card.getStyleClass().add("profiler-info-card");
    if (isDarkThemeEnabled()) {
      card.getStyleClass().add("dark");
    }
    card.getStylesheets().add(
            Objects.requireNonNull(getClass().getResource("/zide.css")).toExternalForm()
    );

    Popup popup = new Popup();
    popup.setAutoFix(true);
    popup.setAutoHide(true);
    popup.setHideOnEscape(true);
    popup.setConsumeAutoHidingEvents(false);
    popup.getContent().add(card);
    popup.setOnHidden(e -> {
      if (profilerInfoPopup == popup) {
        profilerInfoPopup = null;
      }
    });

    javafx.geometry.Bounds ownerBounds = owner.localToScreen(owner.getBoundsInLocal());
    if (ownerBounds == null) {
      return;
    }

    profilerInfoPopup = popup;
    popup.show(owner, ownerBounds.getMinX(), ownerBounds.getMaxY() + 6.0);
    popup.setX(ownerBounds.getMaxX() - popup.getWidth());
  }

  private Label profilerInformationLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("profiler-info-text");
    label.setWrapText(true);
    label.setMaxWidth(340);
    return label;
  }

  private void switchProfilerMetric() {
    showingCpuProfiler = !showingCpuProfiler;

    profilerChart.setVisible(!showingCpuProfiler);
    profilerChart.setManaged(!showingCpuProfiler);
    cpuProfilerChart.setVisible(showingCpuProfiler);
    cpuProfilerChart.setManaged(showingCpuProfiler);

    if (showingCpuProfiler) {
      profilerMetricButton.setText("Memory");
      profilerMetricButton.setTooltip(new Tooltip("Show memory usage"));
    } else {
      profilerMetricButton.setText("CPU");
      profilerMetricButton.setTooltip(new Tooltip("Show CPU usage"));
    }
  }

  private void updateProfilerLabels(ZPEDebugger.ProfileSample sample) {
    double heapMegabytes = sample.heapUsed / (1024.0 * 1024.0);
    double heapCommittedMegabytes = sample.heapCommitted / (1024.0 * 1024.0);
    double nonHeapMegabytes = sample.nonHeapUsed / (1024.0 * 1024.0);

    profilerMemoryLabel.setText(String.format(
            Locale.ROOT,
            "Heap %.1f MB / %.1f MB committed   Non-heap %.1f MB",
            heapMegabytes,
            heapCommittedMegabytes,
            nonHeapMegabytes
    ));

    if (sample.processCpuLoad < 0.0) {
      profilerCpuLabel.setText("CPU warming up…");
    } else {
      profilerCpuLabel.setText(String.format(
              Locale.ROOT,
              "CPU %.1f%%",
              sample.processCpuLoad * 100.0
      ));
    }

    profilerThreadLabel.setText("Threads " + sample.threadCount);
    profilerTimeLabel.setText(formatProfilerTime(sample.elapsedNanoseconds));
  }

  private String formatProfilerTime(long elapsedNanoseconds) {
    double elapsedSeconds = elapsedNanoseconds / 1_000_000_000.0;
    if (elapsedSeconds < 1.0) {
      return String.format(
              Locale.ROOT,
              "Time %.1f ms",
              elapsedNanoseconds / 1_000_000.0
      );
    }
    if (elapsedSeconds < 60.0) {
      return String.format(Locale.ROOT, "Time %.3f s", elapsedSeconds);
    }

    long minutes = (long) (elapsedSeconds / 60.0);
    double seconds = elapsedSeconds - (minutes * 60.0);
    return String.format(Locale.ROOT, "Time %d:%06.3f", minutes, seconds);
  }

  private Node buildconsole() {
    // JavaFX-native console: output history is immutable and the command
    // field stays separate, so neither output nor process input needs Swing.
    consoleOutputTextArea = new InteractiveConsoleFX();
    consoleOutputTextArea.getStyleClass().add("console-output");
    applyConsoleDarkMode(isDarkThemeEnabled());
    VBox consoleContainer = new VBox(consoleOutputTextArea);
    VBox.setVgrow(consoleOutputTextArea, Priority.ALWAYS);

    consoleView =  wrapWithHeader("Console", consoleContainer);

    // Console remains program output. Terminal is a separate JavaFX wrapper
    // around the user's system shell.
    systemTerminal = new ZIDESystemTerminal(currentProjectRoot == null
            ? Path.of(System.getProperty("user.home"))
            : currentProjectRoot.toPath());
    terminalView = wrapWithHeader("Terminal", systemTerminal);

    // --- Problems view ---
    problemsView = wrapWithHeader("Problems", buildProblemsPane());

    // --- Variables view ---
    variablesView = wrapWithHeader("Variable Watch", buildVariablesPane(),
            panelIconButton("/files/step-over.png", "Step over", this::stepOver),
            panelIconButton("/files/continue.png", "Continue debugging", this::continueDebug),
            panelIconButton("/files/stop.png", "Stop execution", this::stopExecution)
    );

    profileView = wrapWithHeader(
            "Profiling",
            buildProfilerPane(),
            buildProfilerInfoButton(),
            buildProfilerMetricButton(),
            panelIconButton("/files/bin.png", "Clear profiler", this::clearProfiler)
    );

    // --- Content stack ---
    bottomContentStack = new StackPane(problemsView, consoleView, terminalView, variablesView, profileView);
    bottomContentStack.getStyleClass().add("bottom-content-stack");

    consoleView.setVisible(false);
    consoleView.setManaged(false);

    terminalView.setVisible(false);
    terminalView.setManaged(false);

    variablesView.setVisible(false);
    variablesView.setManaged(false);

    profileView.setVisible(false);
    profileView.setManaged(false);

    // --- Vertical tabs ---

    problemsTab = createBottomSideTab("Problems", icon("/files/warning.png"));
    consoleTab = createBottomSideTab("Console", icon("/files/controller-play.png"));
    terminalTab = createBottomSideTab("Terminal", icon("/files/console.png"));
    variablesTab = createBottomSideTab("Variable Watch", icon("/files/watch.png"));
    profileTab = createBottomSideTab("Profiling", icon("/files/profiling.png"));

    ToggleGroup group = new ToggleGroup();
    problemsTab.setToggleGroup(group);
    consoleTab.setToggleGroup(group);
    terminalTab.setToggleGroup(group);
    variablesTab.setToggleGroup(group);
    profileTab.setToggleGroup(group);


    problemsTab.setSelected(true);

    problemsTab.setOnAction(e -> showBottomPanel(problemsView));
    consoleTab.setOnAction(e -> showBottomPanel(consoleView));
    terminalTab.setOnAction(e -> {
      showBottomPanel(terminalView);
      systemTerminal.focusCommandInput();
    });
    variablesTab.setOnAction(e -> showBottomPanel(variablesView));
    profileTab.setOnAction(e -> showBottomPanel(profileView));

    VBox tabs = new VBox(problemsTab, consoleTab, terminalTab, variablesTab, profileTab);
    tabs.getStyleClass().add("bottom-side-tabs");
    tabs.setFillWidth(true);

    // --- Main bottom pane ---
    HBox bottom = new HBox(tabs, bottomContentStack);
    HBox.setHgrow(bottomContentStack, Priority.ALWAYS);

    bottom.getStyleClass().add("bottom-panel");
    bottom.setMinHeight(220);


    consoleOutputTextArea.addProcessFinishedListener(() -> {
      stepping = false;
      finishDebugSession();
      Platform.runLater(() -> {
        runBtn.getStyleClass().remove("running");
        debugBtn.getStyleClass().remove("running");
      });
    });

    return bottom;
  }

  private Node icon(String path) {
    Image img = new Image(getClass().getResourceAsStream(path));
    ImageView iv = new ImageView(img);
    iv.setFitWidth(16);
    iv.setFitHeight(16);
    return iv;
  }

  private ToggleButton createBottomSideTab(String tooltipText, Node icon) {
    ToggleButton button = new ToggleButton();
    button.getStyleClass().add("bottom-side-tab");

    button.setGraphic(icon);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setTooltip(new Tooltip(tooltipText));

    button.setMinSize(38, 38);
    button.setPrefSize(38, 38);
    button.setMaxSize(38, 38);

    return button;
  }

  private void showBottomPanel(Node selected) {
    for (Node node : bottomContentStack.getChildren()) {
      boolean visible = node == selected;
      node.setVisible(visible);
      node.setManaged(visible);
    }
  }

  private TableView<ProblemRow> problemsTable;
  private Node buildProblemsPane() {
    problemsTable = new TableView<>();
    problemsTable.setItems(problemsRows);
    problemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    TableColumn<ProblemRow, String> typeCol = new TableColumn<>("Type");
    typeCol.setCellValueFactory(v -> v.getValue().severityProperty());

    TableColumn<ProblemRow, String> lineCol = new TableColumn<>("Line");
    lineCol.setCellValueFactory(v -> v.getValue().lineProperty());

    TableColumn<ProblemRow, String> columnCol = new TableColumn<>("Column");
    columnCol.setCellValueFactory(v -> v.getValue().columnProperty());

    TableColumn<ProblemRow, String> msgCol = new TableColumn<>("Message");
    msgCol.setCellValueFactory(v -> v.getValue().messageProperty());
    msgCol.setPrefWidth(400);

    typeCol.setMinWidth(80);
    typeCol.setMaxWidth(80);

    lineCol.setMinWidth(60);
    lineCol.setMaxWidth(60);

    columnCol.setMinWidth(70);
    columnCol.setMaxWidth(70);

    msgCol.setPrefWidth(1000); // big so it dominates

    problemsTable.getColumns().setAll(typeCol, lineCol, columnCol, msgCol);

    problemsTable.setRowFactory(tv -> new TableRow<>() {
      {
        setOnMouseClicked(event -> {
          if (event.getClickCount() == 2 && !isEmpty()) navigateToProblem(getItem());
        });
      }

      @Override
      protected void updateItem(ProblemRow item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
          getStyleClass().removeAll("problem-row-error", "problem-row-warning");
        } else {
          getStyleClass().removeAll("problem-row-error", "problem-row-warning");
          if ("ERROR".equals(item.getSeverity())) {
            getStyleClass().add("problem-row-error");
          } else if ("WARNING".equals(item.getSeverity())) {
            getStyleClass().add("problem-row-warning");
          }
        }
      }
    });

    return problemsTable;
  }

  private void navigateToProblem(ProblemRow problem) {
    if (problem == null || problem.tab == null) return;
    editorTabs.getSelectionModel().select(problem.tab);
    problem.tab.navigateToDiagnostic(problem.getLine(), problem.getColumn(),
            problem.startOffset, problem.endOffset);
  }

  public class ProblemRow {

    private final EditorTab tab;
    private final SimpleStringProperty severity;
    private final SimpleStringProperty line;
    private final SimpleStringProperty column;
    private final SimpleStringProperty message;
    private final int startOffset;
    private final int endOffset;

    public ProblemRow(EditorTab tab, String severity, int line, int column,
                      int startOffset, int endOffset, String message) {
      this.tab = tab;
      this.severity = new SimpleStringProperty(severity);
      this.line = new SimpleStringProperty(String.valueOf(line));
      this.column = new SimpleStringProperty(String.valueOf(column));
      this.message = new SimpleStringProperty(message);
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    public StringProperty severityProperty() { return severity; }
    public StringProperty lineProperty() { return line; }
    public StringProperty columnProperty() { return column; }
    public StringProperty messageProperty() { return message; }
    public String getSeverity() {
      return severity.get();
    }
    public int getLine() { return Integer.parseInt(line.get()); }
    public int getColumn() { return Integer.parseInt(column.get()); }
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

    VBox box = new VBox(varTable);
    VBox.setVgrow(varTable, Priority.ALWAYS);
    box.getStyleClass().add("variables-pane");
    box.setMinWidth(320);

    return box;
  }

  void showProblemsPane() {
    Platform.runLater(() -> {
      problemsTab.setSelected(true);
      showBottomPanel(problemsView);
    });
  }

  private void showVariablesPane() {
    Platform.runLater(() -> {
      variablesTab.setSelected(true);
      showBottomPanel(variablesView);
    });

  }

  private void showConsolePane() {
    Platform.runLater(() -> {
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
    });
  }

  public void setVariables(ZPEMap vars) {
    java.util.List<VarRow> rows = new java.util.ArrayList<>();
    Map<String, RuntimeVariable> live = new HashMap<>();

    for (ZPEType o : vars) {
      ZPEMap m = (ZPEMap) vars.get(o);

      String type = ZPEHelperFunctions.getTypeString(m.get("type"));

      String id = String.valueOf(m.get("id"));
      String function = String.valueOf(m.get("function"));
      String value = String.valueOf(m.get("value"));
      rows.add(new VarRow(id, type, function, value));
      live.put(normaliseVariableName(id), new RuntimeVariable(type, function, value));
    }
    breakpointVariables.clear();
    breakpointVariables.putAll(live);

    Platform.runLater(() -> {
      varRows.setAll(rows);   // clear + repopulate in one go
      showVariablesPane();    // if you're hiding it until needed
    });
  }

  private static final class RuntimeVariable {
    final String type;
    final String function;
    final String value;

    RuntimeVariable(String type, String function, String value) {
      this.type = type == null || "null".equals(type) ? "" : type;
      this.function = function == null || "null".equals(function) ? "" : function;
      this.value = value == null ? "null" : value;
    }
  }

  private static final class Assignment {
    final int start;
    final String expression;

    Assignment(int start, String expression) {
      this.start = start;
      this.expression = expression;
    }
  }

  private static final class Prediction {
    final boolean known;
    final boolean call;
    final String description;
    final String type;

    Prediction(boolean known, boolean call, String description, String type) {
      this.known = known;
      this.call = call;
      this.description = description;
      this.type = type;
    }

    static Prediction known(String value, String type) { return new Prediction(true, false, value, type); }
    static Prediction call(String value) { return new Prediction(false, true, value, null); }
    static Prediction expression(String value) { return new Prediction(false, false, value, null); }
  }

  private void clearRows(){
    Platform.runLater(() -> varRows.clear());
  }

  private Node buildStatusBar() {
    var centre = new Label("ZIDE " + ZIDE.getMajorVersion() + "." + ZIDE.getMinorVersion() + " build " + ZIDE.getBuildNumber() + " © Jamie Balfour 2024 - 2026.");
    registerLanguageSupports();
    languageSelector = new MenuButton("Text");
    for (LanguageSupport language : languageSupports.values()) {
      MenuItem item = new MenuItem(language.label);
      item.setOnAction(event -> selectLanguage(getCurrentTab(), language));
      languageSelector.getItems().add(item);
    }
    languageSelector.getStyleClass().add("language-selector");
    languageSelector.setAccessibleText("Editor language");
    languageSelector.setTooltip(new Tooltip("Editor language"));

    centre.setStyle("-fx-font-size: 13px;");

    var bar = new HBox(statusLabel, new Region(), centre, new Region(), languageSelector);
    HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
    HBox.setHgrow(bar.getChildren().get(3), Priority.ALWAYS);

    bar.setPadding(new Insets(2, 8, 2, 8));
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


  /*private String getTooltipFunctionInfo(String functionName) {
    String output = "";

    if (toggleTheme != null && toggleTheme.isSelected()) {
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
  }*/

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

  void invertImages(){
    invertImageView(getToolbarButtonIcon(runBtn));
    invertImageView(getToolbarButtonIcon(buildBtn));
    invertImageView(getToolbarButtonIcon(debugBtn));
    invertImageView(getToggleButtonIcon(consoleTab));
    invertImageView(getToggleButtonIcon(variablesTab));
    invertImageView(getToggleButtonIcon(problemsTab));
    invertImageView(getToggleButtonIcon(profileTab));
    invertImageView(getToggleButtonIcon(terminalTab));
  }

  private void invertImageView(ImageView imageView) {
    Image image = imageView.getImage();

    WritableImage inverted = new WritableImage(
            (int) image.getWidth(),
            (int) image.getHeight()
    );

    PixelReader reader = image.getPixelReader();
    PixelWriter writer = inverted.getPixelWriter();

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        javafx.scene.paint.Color c = reader.getColor(x, y);

        writer.setColor(x, y, javafx.scene.paint.Color.color(
                1.0 - c.getRed(),
                1.0 - c.getGreen(),
                1.0 - c.getBlue(),
                c.getOpacity()
        ));
      }
    }

    imageView.setImage(inverted);
  }

  private static ImageView getToolbarButtonIcon(Button button) {
    Node graphic = button.getGraphic();

    if (graphic instanceof HBox box && !box.getChildren().isEmpty()) {
      Node first = box.getChildren().get(0);

      if (first instanceof ImageView imageView) {
        return imageView;
      }
    }

    return null;
  }

  private static ImageView getToggleButtonIcon(ToggleButton button) {
    Node graphic = button.getGraphic();

    if (graphic instanceof ImageView imageView) {
      return imageView;
    }

    return null;
  }


}
