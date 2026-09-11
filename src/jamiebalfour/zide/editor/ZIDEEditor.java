package jamiebalfour.zide.editor;

import jamiebalfour.helpers.FileHelperFunctions;
import jamiebalfour.helpers.HelperFunctions;
import jamiebalfour.helpers.MacApplicationMenuJNA;
import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.balflaf_fx.BalfGlassMenuBar;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.console.InteractiveConsoleFX;
import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zide.ZIDEHelperFunctions;
import jamiebalfour.zide.ai.ZIDEOpenAIClient;
import jamiebalfour.zide.core.ZIDE;
import jamiebalfour.zide.git.GitHubApi;
import jamiebalfour.zide.git.GitHubCredentialStore;
import jamiebalfour.zide.git.GitHubDeviceFlow;
import jamiebalfour.zpe.core.*;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZIDEEditor extends Application {

  static {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
      // These must be set before JavaFX/AWT creates the native application menu.
      System.setProperty("apple.awt.application.name", "ZIDE");
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", "ZIDE");
    }
  }

  Stage _stage;
  ZPERuntimeEnvironment runtime;
  BalfGlassMenuBar.GlassMenu languageSelector;
  private BalfGlassMenuBar languageMenuBar;
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
  private Label zoomPercentageLabel;
  private BalfGlassMenuBar applicationMenuBar;
  private BalfTitleBar titleBar;
  private BalfGlassMenuBar.GlassMenu scriptMenu;
  private BalfGlassMenuBar.GlassMenu projectMenu;
  private BalfGlassMenuBar.GlassMenu zpeOnlineMenu;
  private TextField editorSearchField;
  private boolean darkThemeEnabled;
  private boolean darkIconsApplied;
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
  private Node toolsMsiSeparator;
  private Node toolsAiSeparator;
  private Node layoutBuilderMenuItem;
  private Node aiBuilderMenuItem;
  private Node aiProblemMenuItem;
  private Node aiValidateMenuItem;
  private final List<Node> transpileMenuItems = new ArrayList<>();
  private File currentProjectRoot;
  File projectDir;
  final Label statusLabel = new Label("Ready");
  final static String INSTALL_PATH = HelperFunctions.getAppDataDirectory("jamiebalfour/zide", System.getProperty("user.home") + "/jb/zide").getAbsolutePath() + "/"; //;
  private final ZIDERuntimeManager zideRuntimes = new ZIDERuntimeManager(Path.of(INSTALL_PATH));
  private ZIDERuntimeManager.RuntimeKind selectedYassRuntime;
  private final GitHubDeviceFlow githubDeviceFlow = new GitHubDeviceFlow(GitHubDeviceFlow.ZIDE_CLIENT_ID);
  private final GitHubCredentialStore githubCredentials = new GitHubCredentialStore(Path.of(INSTALL_PATH));
  private volatile GitHubDeviceFlow.Token githubToken;
  Properties MAIN_PROPERTIES;
  boolean USE_WORD_WRAP = false;
  Node loadFromOnline;
  Node saveToOnline;
  Node loginToZPEOnlineMenuItem;
  Node zpeOnlineAuthSeparator;
  Node recentOnlineFilesMenuItem;
  private ZPEList recentOnlineFiles;
  String username = null;
  String password = null;
  boolean loggedIn = false;
  /** True only while a debug launch owns the debugger controls and profiler session. */
  private volatile boolean debuggingSession;
  private WatchService projectWatchService;
  private Thread projectWatchThread;
  private StackPane workspaceStack;
  private StackPane windowStack;
  private BorderPane appRoot;
  private StackPane activeModalOverlay;
  private Runnable activeModalDismiss;
  private Node layoutBuilderOverlay;
  private Node languageBuilderOverlay;
  private volatile boolean watchingProjectDirectory;
  private final AtomicBoolean projectTreeRefreshQueued = new AtomicBoolean(false);
  private final Set<Path> pendingExternalFileChanges = ConcurrentHashMap.newKeySet();
  private PauseTransition externalFileChangeTimer;
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
    String maximised = MAIN_PROPERTIES.getProperty("MAXIMISE",
            MAIN_PROPERTIES.getProperty("MAXIMISED",
                    MAIN_PROPERTIES.getProperty("MAXIMIZED", "false")));
    stage.setMaximized(Boolean.parseBoolean(maximised));
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
    MAIN_PROPERTIES.setProperty("MAXIMISE", Boolean.toString(_stage.isMaximized()));
    saveProps();
  }

  @Override
  public void start(Stage stage) {

    stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream(HelperFunctions.isMac() ? "/files/zide_macos.png" : "/files/zide.png"))));
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.setResizable(true);
    stage.setMinWidth(720);
    stage.setMinHeight(480);

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

    username = MAIN_PROPERTIES.getProperty("LOGIN_USERNAME", "");
    password = MAIN_PROPERTIES.getProperty("LOGIN_PASSCODE", "");


    USE_WORD_WRAP = Boolean.parseBoolean(MAIN_PROPERTIES.getProperty("USE_WORD_WRAP", "false"));

    stage.setMinWidth(600);
    stage.setMinHeight(400);

  /*  stage.getIcons().add(
            new Image(Objects.requireNonNull(getClass().getResourceAsStream("/files/balflaf_fx/icons/jb.png")))
    );
*/
    //Application.setUserAgentStylesheet(STYLESHEET_CASPIAN);

    runtime = new ZPERuntimeEnvironment();
    boolean systemDark = isSystemDarkMode();
    darkThemeEnabled = "dark".equalsIgnoreCase(MAIN_PROPERTIES.getProperty(
            "THEME", systemDark ? "dark" : "light"));

    editorTabs = new TabPane();

    appRoot = new BorderPane();
    BorderPane root = appRoot;
    root.getStyleClass().add("app-root");

    EventHandler<ActionEvent> aboutHandler = e -> showAboutPanel();

    // Keep the title bar outside the workspace stack so full-workspace tools
    // can cover the menus and editor without covering the window controls.
    titleBar = new BalfTitleBar(stage, "ZIDE", aboutHandler);
    titleBar.setDarkMode(darkThemeEnabled);
    titleBar.setOnCloseRequest(this::requestApplicationClose);
    if (isMacPlatform()) titleBar.setOnSettings(event -> openSettings());
    root.setTop(titleBar);
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
    updateProjectMenuVisibility();
    startProjectDirectoryWatcher(projectDir);
    Node leftPane = projectTree;
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

    windowStack = new StackPane(root);
    windowStack.getStyleClass().add("window-stack");
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
      windowStack.getStyleClass().add("mac-window");
      var roundedClip = new javafx.scene.shape.Rectangle();
      roundedClip.setArcWidth(20);
      roundedClip.setArcHeight(20);
      roundedClip.widthProperty().bind(root.widthProperty());
      roundedClip.heightProperty().bind(root.heightProperty());
      root.setClip(roundedClip);
    }
    Region resizeOverlay = new Region();
    resizeOverlay.setMouseTransparent(true);
    resizeOverlay.setPickOnBounds(false);
    resizeOverlay.getStyleClass().add("window-resize-overlay");
    windowStack.getChildren().add(resizeOverlay);
    Region resizeHandle = new Region();
    resizeHandle.setMinSize(18, 18);
    resizeHandle.setPrefSize(18, 18);
    resizeHandle.setMaxSize(18, 18);
    resizeHandle.setCursor(javafx.scene.Cursor.SE_RESIZE);
    final double[] resizeStart = new double[4];
    resizeHandle.setOnMousePressed(e -> {
      resizeStart[0] = e.getScreenX(); resizeStart[1] = e.getScreenY();
      resizeStart[2] = stage.getWidth(); resizeStart[3] = stage.getHeight();
      e.consume();
    });
    resizeHandle.setOnMouseDragged(e -> {
      stage.setWidth(Math.max(stage.getMinWidth(), resizeStart[2] + e.getScreenX() - resizeStart[0]));
      stage.setHeight(Math.max(stage.getMinHeight(), resizeStart[3] + e.getScreenY() - resizeStart[1]));
      e.consume();
    });
    StackPane.setAlignment(resizeHandle, Pos.BOTTOM_RIGHT);
    windowStack.getChildren().add(resizeHandle);
    addResizeHandle(windowStack, stage, Pos.TOP_CENTER, javafx.scene.Cursor.N_RESIZE, 0, -1);
    addResizeHandle(windowStack, stage, Pos.BOTTOM_CENTER, javafx.scene.Cursor.S_RESIZE, 0, 1);
    addResizeHandle(windowStack, stage, Pos.CENTER_LEFT, javafx.scene.Cursor.W_RESIZE, -1, 0);
    addResizeHandle(windowStack, stage, Pos.CENTER_RIGHT, javafx.scene.Cursor.E_RESIZE, 1, 0);
    addResizeHandle(windowStack, stage, Pos.TOP_LEFT, javafx.scene.Cursor.NW_RESIZE, -1, -1);
    addResizeHandle(windowStack, stage, Pos.TOP_RIGHT, javafx.scene.Cursor.NE_RESIZE, 1, -1);
    addResizeHandle(windowStack, stage, Pos.BOTTOM_LEFT, javafx.scene.Cursor.SW_RESIZE, -1, 1);
    var scene = new Scene(windowStack, 1280, 800);
    // Leave the area outside the rounded frame genuinely transparent.
    scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/zide.css")).toExternalForm());
    setDarkStyleClass(root, isDarkThemeEnabled());

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
    restoreZPEOnlineSession();
    externalFileChangeTimer = new PauseTransition(Duration.millis(350));
    externalFileChangeTimer.setOnFinished(event -> checkPendingExternalFileChanges());
    installMacApplicationMenuItems();
    Platform.runLater(() -> {
      captureNormalWindowBounds(stage);
      applyThemePreference(isDarkThemeEnabled(), false);
    });

    boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

    if (isMac) {
      root.getStyleClass().add("mac-window");
    }
  }

  private void addResizeHandle(StackPane host, Stage stage, Pos position, javafx.scene.Cursor cursor, int horizontal, int vertical) {
    Region handle = new Region(); handle.setMinSize(14, 14); handle.setPrefSize(14, 14); handle.setMaxSize(14, 14); handle.setCursor(cursor);
    final double[] start = new double[6];
    handle.setOnMousePressed(e -> { start[0]=e.getScreenX(); start[1]=e.getScreenY(); start[2]=stage.getWidth(); start[3]=stage.getHeight(); start[4]=stage.getX(); start[5]=stage.getY(); e.consume(); });
    handle.setOnMouseDragged(e -> { double dx=e.getScreenX()-start[0], dy=e.getScreenY()-start[1]; if(horizontal<0){stage.setX(start[4]+dx); stage.setWidth(Math.max(stage.getMinWidth(),start[2]-dx));} else if(horizontal>0) stage.setWidth(Math.max(stage.getMinWidth(),start[2]+dx)); if(vertical<0){stage.setY(start[5]+dy); stage.setHeight(Math.max(stage.getMinHeight(),start[3]-dy));} else if(vertical>0) stage.setHeight(Math.max(stage.getMinHeight(),start[3]+dy)); e.consume(); });
    StackPane.setAlignment(handle, position); host.getChildren().add(handle);
  }

  /** Adds About and Settings to JavaFX's Cocoa-owned application menu. */
  private void installMacApplicationMenuItems() {
    if (!isMacPlatform()) return;
    if (!MacApplicationMenuJNA.isAvailable()) return;
    PauseTransition menuReady = new PauseTransition(Duration.millis(500));
    menuReady.setOnFinished(event -> {
      MacApplicationMenuJNA.setAllowMenuMutation(true);
      // The helper inserts at a fixed Cocoa index, so calls are in reverse display order.
      MacApplicationMenuJNA.addApplicationMenuSeparator();
      MacApplicationMenuJNA.addApplicationMenuItem(
              "Settings...", () -> Platform.runLater(this::openSettings));
      MacApplicationMenuJNA.addApplicationMenuSeparator();
      MacApplicationMenuJNA.addApplicationMenuItem(
              "About ZIDE", () -> Platform.runLater(this::showAboutPanel));
      PauseTransition renamedMenuReady = new PauseTransition(Duration.millis(250));
      renamedMenuReady.setOnFinished(ignored -> ZIDEMacApplicationMenu.setApplicationName("ZIDE"));
      renamedMenuReady.play();
    });
    menuReady.play();
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
              EditorTab tab = getCurrentTab();
              if (tab != null) tab.showFindReplace(false);
            }
    );
    scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN),
            () -> {
              EditorTab tab = getCurrentTab();
              if (tab != null) tab.showFindReplace(true);
            }
    );
  }

  private static final Preferences PREFS = Preferences.userNodeForPackage(ZIDEEditor.class);
  private static final String KEY_LAST_DIR = System.getProperty("user.home");//"/Users/jamiebalfour/Documents/";

  private void newFile() {
    File targetDir = getTargetDirectoryForNewFile();

    if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
      showError("New File", "No valid target folder is selected.");
      return;
    }

    TextField nameField = new TextField("Untitled");
    nameField.setPromptText("File name");

    registerLanguageSupports();
    ToggleGroup fileTypeGroup = new ToggleGroup();
    TilePane fileTypes = new TilePane(10, 10);
    fileTypes.setPrefColumns(3);
    fileTypes.setTileAlignment(Pos.CENTER);
    fileTypes.getStyleClass().add("new-file-types");
    for (LanguageSupport language : languageSupports.values()) {
      Label name = new Label(language.label());
      name.getStyleClass().add("new-file-type-name");
      Label extension = new Label("." + language.defaultExtension());
      extension.getStyleClass().add("new-file-type-extension");
      VBox details = new VBox(3, languageFileIcon(language), name, extension);
      details.setAlignment(Pos.CENTER);

      RadioButton choice = new RadioButton();
      choice.setGraphic(details);
      choice.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      choice.setToggleGroup(fileTypeGroup);
      choice.setUserData(language);
      choice.setAccessibleText(language.label() + " file, ." + language.defaultExtension());
      choice.getStyleClass().add("new-file-type-choice");
      fileTypes.getChildren().add(choice);
      if ("yass".equals(language.id())) choice.setSelected(true);
    }

    VBox form = new VBox(9);
    Label nameLabel = new Label("File name");
    nameLabel.getStyleClass().add("new-file-type-heading");
    Label typeLabel = new Label("File type");
    typeLabel.getStyleClass().add("new-file-type-heading");
    form.getChildren().addAll(nameLabel, nameField, typeLabel, fileTypes);

    showInWindowModal("New File", "Create a file in " + targetDir.getName(), form, "Create", () -> {
      String baseName = nameField.getText().trim();
      Toggle selected = fileTypeGroup.getSelectedToggle();
      if (baseName.isEmpty() || selected == null) return false;
      String ext = ((LanguageSupport) selected.getUserData()).defaultExtension();
      String fullName = baseName.endsWith("." + ext) ? baseName : baseName + "." + ext;
      File newFile = new File(targetDir, fullName);
      if (newFile.exists()) {
        showProjectFileError("That file already exists.");
        return false;
      }
      try {
        if (!newFile.createNewFile()) return false;
        buildProjectTree(currentProjectRoot);
        openTab(newFile.getName(), newFile.getAbsolutePath());
        return true;
      } catch (IOException exception) {
        showProjectFileError("Failed to create the file: " + exception.getMessage());
        return false;
      }
    });
    Platform.runLater(() -> {
      nameField.requestFocus();
      nameField.selectAll();
    });
  }

  /** Displays a modal surface inside ZIDE rather than creating another OS window. */
  private void showInWindowModal(String title, String subtitle, Node content, String primaryText,
                                 BooleanSupplier onConfirm) {
    showInWindowModal(title, subtitle, content, primaryText, onConfirm, true);
  }

  private void showInWindowModal(String title, String subtitle, Node content, String primaryText,
                                 BooleanSupplier onConfirm, boolean showCancel) {
    ArrayList<ModalAction> actions = new ArrayList<>();
    if (showCancel) actions.add(new ModalAction("Cancel", false, () -> true));
    actions.add(new ModalAction(primaryText, true, onConfirm));
    showInWindowModal(title, subtitle, content, actions);
  }

  private record ModalAction(String text, boolean primary, BooleanSupplier action) { }

  /** Installs a modal card whose actions decide independently when it should close. */
  private void showInWindowModal(String title, String subtitle, Node content,
                                 List<ModalAction> modalActions) {
    if (windowStack == null) return;
    closeInWindowModal();
    Label heading = new Label(title);
    heading.getStyleClass().add("in-window-modal-title");
    Label supporting = new Label(subtitle);
    supporting.getStyleClass().add("in-window-modal-subtitle");
    HBox actions = new HBox(8);
    actions.setAlignment(Pos.CENTER_RIGHT);
    for (ModalAction modalAction : modalActions) {
      Button button = new Button(modalAction.text());
      button.getStyleClass().add(modalAction.primary()
              ? "in-window-modal-primary" : "in-window-modal-secondary");
      button.setOnAction(event -> {
        if (modalAction.action().getAsBoolean()) closeInWindowModal();
      });
      actions.getChildren().add(button);
    }
    VBox panel = new VBox(14, heading, supporting, content, actions);
    panel.getStyleClass().add("in-window-modal-panel");
    panel.setMaxWidth(1080);
    panel.setMaxHeight(Region.USE_PREF_SIZE);
    panel.setOnMouseClicked(MouseEvent::consume);

    activeModalOverlay = new StackPane(panel);
    activeModalOverlay.getStyleClass().add("in-window-modal-overlay");
    if (isDarkThemeEnabled()) activeModalOverlay.getStyleClass().add("in-window-modal-dark");
    activeModalOverlay.setOnMouseClicked(event -> {
      if (event.getTarget() == activeModalOverlay) closeInWindowModal();
    });
    activeModalOverlay.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        closeInWindowModal();
        event.consume();
      } else if (event.getCode() == KeyCode.ENTER) {
        modalActions.stream().filter(ModalAction::primary).findFirst().ifPresent(action -> {
          if (action.action().getAsBoolean()) closeInWindowModal();
        });
        event.consume();
      }
    });
    windowStack.getChildren().add(activeModalOverlay);
    activeModalOverlay.toFront();
  }

  /** Keeps short forms compact while retaining the shared in-window modal treatment. */
  private void setActiveModalWidth(double width) {
    if (activeModalOverlay == null || activeModalOverlay.getChildren().isEmpty()) return;
    if (activeModalOverlay.getChildren().getFirst() instanceof Region panel) {
      panel.setPrefWidth(width);
      panel.setMaxWidth(width);
    }
  }

  /** Shows application and runtime details without creating a second native window. */
  private void showAboutPanel() {
    ImageView icon = new ImageView(new Image(Objects.requireNonNull(
            getClass().getResourceAsStream("/files/zide.png"))));
    icon.setFitWidth(64);
    icon.setFitHeight(64);
    icon.setPreserveRatio(true);

    Label product = new Label("ZIDE");
    product.getStyleClass().add("about-title");
    Label version = new Label("Version " + ZIDE.getVersion() + " build " + ZIDE.getBuildNumber());
    version.getStyleClass().add("about-version");
    Label zpeVersion = new Label("ZPE " + ZPE.getVersionNumber() + " [" + ZPE.getVersionName() + "]");
    zpeVersion.getStyleClass().add("about-version");
    VBox identity = new VBox(3, product, version, zpeVersion);
    HBox header = new HBox(16, icon, identity);
    header.setAlignment(Pos.CENTER_LEFT);

    GridPane system = new GridPane();
    system.setHgap(14);
    system.setVgap(7);
    addAboutDetail(system, 0, "Java", System.getProperty("java.version", "Unknown"));
    addAboutDetail(system, 1, "JavaFX", System.getProperty("javafx.runtime.version", "Unknown"));
    addAboutDetail(system, 2, "Operating system",
            System.getProperty("os.name", "Unknown") + " " + System.getProperty("os.version", ""));

    VBox content = new VBox(14, header, new Separator(), system);
    content.getStyleClass().add("about-content");
    content.setPrefWidth(410);
    showInWindowModal("About ZIDE", "A modern IDE for ZPE and YASS.", content,
            "Done", () -> true, false);
    if (activeModalOverlay != null && !activeModalOverlay.getChildren().isEmpty()
            && activeModalOverlay.getChildren().getFirst() instanceof Region aboutPanel) {
      aboutPanel.setPrefWidth(470);
      aboutPanel.setMaxWidth(470);
    }
  }

  private static void addAboutDetail(GridPane grid, int row, String name, String value) {
    Label key = new Label(name);
    key.getStyleClass().add("about-key");
    Label detail = new Label(value);
    detail.getStyleClass().add("about-val");
    grid.addRow(row, key, detail);
  }

  private void closeInWindowModal() {
    Runnable dismiss = activeModalDismiss;
    activeModalDismiss = null;
    if (dismiss != null) dismiss.run();
    if (windowStack != null && activeModalOverlay != null) windowStack.getChildren().remove(activeModalOverlay);
    activeModalOverlay = null;
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
    bar.setOnMouseMoved(event -> {
      if (event.getTarget() == bar && bar.isOutsideMenuTitles(event.getX())) {
        bar.hideMenus();
      }
    });
    bar.setOnMouseClicked(event -> {
      if (event.getTarget() == bar
              && event.getButton() == javafx.scene.input.MouseButton.PRIMARY
              && event.getClickCount() == 2) {
        bar.hideMenus();
        titleBar.toggleMaximise();
        event.consume();
      }
    });

    var file = bar.menu("File");
    file.createItem("New Project", "⇧⌘N", this::newProject, true);
    file.createItem("New File", "⌘N", this::newFile, true);
    file.createItem("Open project folder", "⌘O", this::openProjectFolder, true);
    file.separator();
    file.createItem("Save", "⌘S", this::saveCurrentFile, true);
    file.createItem("Save As...", "", this::saveCurrentFileAs, true);
    file.separator();
    if (!isMacPlatform()) {
      file.createItem("Settings", "", this::openSettings, true);
      file.separator();
    }
    file.createItem("Exit", "", this::requestApplicationClose, true);

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

    edit.separator();
    edit.createItem("Find", "⌘F", () -> {
      EditorTab tab = getCurrentTab();
      if (tab != null) tab.showFindReplace(false);
    });
    edit.createItem("Find and Replace", "⌘H", () -> {
      EditorTab tab = getCurrentTab();
      if (tab != null) tab.showFindReplace(true);
    });

    formatDocumentMenuItem = edit.createItem("Format document", "", this::beautifyCurrentDocument);

    projectMenu = bar.menu("Project");
    projectMenu.createItem("Edit project settings", "", this::editCurrentProjectSettings);
    projectMenu.setVisible(false);

    var viewMenu = bar.menu("View");

    HBox zoomRow = new HBox(4);
    zoomRow.getStyleClass().add("glass-menu-zoom-row");
    Label zoomTitle = new Label("Zoom");
    zoomTitle.getStyleClass().add("glass-menu-item-text");
    Region zoomSpacer = new Region();
    HBox.setHgrow(zoomSpacer, Priority.ALWAYS);

    Button zoomOut = new Button("-");
    zoomOut.getStyleClass().add("glass-menu-zoom-button");
    zoomOut.setTooltip(new Tooltip("Zoom out"));
    zoomOut.setOnAction(e -> changeEditorZoom(-0.1));

    zoomPercentageLabel = new Label("100%");
    Button resetZoom = new Button();
    resetZoom.getStyleClass().addAll("glass-menu-zoom-button", "glass-menu-zoom-value");
    resetZoom.setGraphic(zoomPercentageLabel);
    resetZoom.setTooltip(new Tooltip("Reset zoom to 100%"));
    resetZoom.setOnAction(e -> resetEditorZoom());

    Button zoomIn = new Button("+");
    zoomIn.getStyleClass().add("glass-menu-zoom-button");
    zoomIn.setTooltip(new Tooltip("Zoom in"));
    zoomIn.setOnAction(e -> changeEditorZoom(0.1));

    zoomRow.getChildren().addAll(zoomTitle, zoomSpacer, zoomOut, resetZoom, zoomIn);
    viewMenu.customItem(zoomRow);
    viewMenu.separator();
    viewMenu.onShowing(this::updateZoomPercentage);

    darkThemeMenuItem = viewMenu.checkItem("Dark theme",
            darkThemeEnabled,
            selected -> applyThemePreference(selected, true));

    scriptMenu = bar.menu("Script");

    runScriptMenuItem = scriptMenu.createItem("Run", "F5", this::runCode);
    debugScriptMenuItem = scriptMenu.createItem("Debug", "⇧⌘R", this::debug);
    stopScriptMenuItem = scriptMenu.createItem("Stop Execution", "⇧⌘S", () -> consoleOutputTextArea.destroyCurrentProcess());
    scriptCompileSeparator = scriptMenu.separatorNode();
    compileScriptMenuItem = scriptMenu.createItem("Compile project to ZEX", "", this::compileCurrentLanguage);
    compileNativeMenuItem = scriptMenu.createItem("Compile project Native", "", this::compileNative);
    scriptTranspileSeparator = scriptMenu.separatorNode();
    List<String> transpilers = ZPEKit.listTranspilerNames();
    if (!transpilers.isEmpty()) {
      for (String language : transpilers) {
        String transpilerName = ZPEKit.getTranspilerByName(language).transpilerName();
        Node transpileItem = scriptMenu.createItem(
                "Transpile to " + language + " (" + transpilerName + ")",
                "",
                () -> transpileCurrentFile(language)
        );
        transpileMenuItems.add(transpileItem);
      }
    }

    var tools = bar.menu("Tools");

    tools.createItem("Open Macro Scripting Interface", "", this::openMSI);
    toolsMsiSeparator = tools.separatorNode();
    layoutBuilderMenuItem = tools.createItem("Open ZUI Layout Builder", "", this::openLayoutBuilder);
    toolsAiSeparator = tools.separatorNode();
    aiBuilderMenuItem = tools.createItem("Build with AI Assistant", "", this::openAIBuilder);
    aiProblemMenuItem = tools.createItem("Solve problem with AI", "", this::openAIProblemSolver);
    aiValidateMenuItem = tools.createItem("Validate with AI", "", this::openAIValidation);
    var git = bar.menu("Git");

    git.createItem("Sign in to GitHub", "", this::signInToGitHub);
    git.createItem("Sign out of GitHub", "", this::signOutOfGitHub);
    git.separator();
    git.createItem("Clone", "", this::cloneRepo);
    git.separator();
    git.createItem("Repository Status", "", this::showGitStatus);
    git.createItem("Commit All Changes", "", this::commitGitChanges);
    git.separator();
    git.createItem("Pull", "", this::pullGitChanges);
    git.createItem("Push", "", this::pushGitChanges);


    zpeOnlineMenu = bar.menu("ZPE Online");

    loginToZPEOnlineMenuItem = zpeOnlineMenu.createItem("Login to ZPE Online", "", this::toggleZPEOnlineLogin);
    zpeOnlineMenu.createItem("Register for ZPE Online", "", () -> openZPEOnlinePage(
            "https://www.jamiebalfour.scot/projects/zpe/online/register/"));
    zpeOnlineMenu.separator();
    zpeOnlineMenu.createItem("Visit ZPE Online website", "", () -> openZPEOnlinePage(
            "https://www.jamiebalfour.scot/projects/zpe/online/"));
    zpeOnlineMenu.createItem("View public uploads", "", () -> openZPEOnlinePage(
            "https://www.jamiebalfour.scot/projects/zpe/online/code/"));
    zpeOnlineMenu.separator();
    zpeOnlineMenu.createItem("Load from public repository", "", this::loadFromPublicCloudFX);
    zpeOnlineAuthSeparator = zpeOnlineMenu.separatorNode();
    recentOnlineFilesMenuItem = zpeOnlineMenu.createItem("Recent online files", "", this::showRecentZPEOnlineFiles);
    loadFromOnline = zpeOnlineMenu.createItem("Load from ZPE Online", "", this::loadFromUsersCloudFX);
    saveToOnline = zpeOnlineMenu.createItem("Save to ZPE Online", "", this::saveToZPEOnline);


    setMenuItemAvailable(zpeOnlineAuthSeparator, false);
    setMenuItemAvailable(recentOnlineFilesMenuItem, false);
    setMenuItemAvailable(loadFromOnline, true);
    setMenuItemAvailable(saveToOnline, false);

    var help = bar.menu("Help");

    if (!isMacPlatform()) {
      help.createItem("About", "", this::showAboutPanel);
      help.separator();
    }
    help.createItem("Download ZPE Runtime Environment", "", this::downloadZPERuntime);
    help.createItem("Download ZPE Native", "", this::downloadZPENative);

    updateLanguageCommands(null);
    bar.setDarkMode(darkThemeEnabled);
    return bar;
  }

  private void changeEditorZoom(double amount) {
    EditorTab tab = getCurrentTab();
    if (tab == null) return;
    tab.getEditor().setZoomFactor(tab.getEditor().getZoomFactor() + amount);
    updateZoomPercentage();
  }

  private void resetEditorZoom() {
    EditorTab tab = getCurrentTab();
    if (tab == null) return;
    if (applicationMenuBar != null) applicationMenuBar.hideMenus();
    tab.getEditor().resetZoom();
    updateZoomPercentage();
  }

  private void updateZoomPercentage() {
    if (zoomPercentageLabel == null) return;
    EditorTab tab = getCurrentTab();
    double zoom = tab == null ? 1.0 : tab.getEditor().getZoomFactor();
    zoomPercentageLabel.setText(Math.round(zoom * 100) + "%");
  }

  private void applyWorkspaceDarkMode(boolean enabled) {
    Scene scene = _stage == null ? null : _stage.getScene();
    if (scene == null || scene.getRoot() == null) return;

    if (appRoot != null) setDarkStyleClass(appRoot, enabled);
    if (titleBar != null) titleBar.setDarkMode(enabled);
    if (languageMenuBar != null) languageMenuBar.setDarkMode(enabled);
    if (activeModalOverlay != null) {
      if (enabled && !activeModalOverlay.getStyleClass().contains("in-window-modal-dark")) {
        activeModalOverlay.getStyleClass().add("in-window-modal-dark");
      } else if (!enabled) {
        activeModalOverlay.getStyleClass().remove("in-window-modal-dark");
      }
    }
    applyConsoleDarkMode(enabled);
    scene.getRoot().applyCss();
  }

  /** Mirrors the web convention of toggling one .dark class on the application root. */
  private static void setDarkStyleClass(Node root, boolean enabled) {
    if (root == null) return;
    if (enabled) {
      if (!root.getStyleClass().contains("dark")) root.getStyleClass().add("dark");
    } else {
      root.getStyleClass().remove("dark");
    }
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

  private static boolean isSystemDarkMode() {
    try {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("mac")) return HelperFunctions.isDarkModeEnabledMac();
      if (os.contains("win")) return HelperFunctions.isDarkModeEnabledWindows();
      return HelperFunctions.isDarkModeEnabledGnome();
    } catch (Exception ignored) {
      return false;
    }
  }

  private void applyThemePreference(boolean enabled, boolean persist) {
    darkThemeEnabled = enabled;
    if (darkThemeMenuItem != null) darkThemeMenuItem.setSelected(enabled);
    if (applicationMenuBar != null) applicationMenuBar.setDarkMode(enabled);
    if (darkIconsApplied != enabled) {
      invertImages();
      darkIconsApplied = enabled;
    }
    applyWorkspaceDarkMode(enabled);
    if (editorTabs != null) {
      for (Tab item : editorTabs.getTabs()) {
        if (!(item instanceof EditorTab)) continue;
        if (enabled) ((EditorTab) item).switchOnDarkMode();
        else ((EditorTab) item).switchOffDarkMode();
      }
    }
    if (persist && MAIN_PROPERTIES != null) {
      MAIN_PROPERTIES.setProperty("THEME", enabled ? "dark" : "light");
      saveProps();
    }
  }

  private static boolean isMacPlatform() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
  }



  private void openProjectFolder() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Open project folder");

    File chosen = chooser.showDialog(_stage);
    if (chosen != null) {
      currentProjectRoot = chosen;
      projectDir = chosen;
      updateProjectMenuVisibility();
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
      getCurrentTab().setLastDiskContent(getCurrentTab().getEditor().getText());
      getCurrentTab().setHasChanges(false);
    } catch (IOException ex) {
      showError("Error saving file", ex.getMessage());
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
      tab.setLastDiskContent(tab.getEditor().getText());
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
    if (!loggedIn || !hasText(username) || !hasText(password)) {
      loginToZPEOnline();
      return;
    }
    HashMap<String, String> arguments = new HashMap<>();
    arguments.put("username", username);
    arguments.put("password", password);
    ZPEList files = getUsersCloudFileList();

    if (files == null) {
      return;
    }

    showZPEOnlineBrowser(arguments, files, false);
  }

  private void loadFromPublicCloudFX() {
    try {
      String response = HelperFunctions.makePOSTRequest(
              ZPEInstance.getOnlinePathProperty() + "/public.php?type=list&version=10",
              new HashMap<>());
      ZPEMap json = (ZPEMap) new ZenithJSONParser().jsonDecode(response, false);
      if (!json.containsKey(new ZPEString("result"))
              || HelperFunctions.stringToInteger(json.get(new ZPEString("result")).toString()) != 1) {
        showError("ZPE Online", "The public repository could not be loaded.");
        return;
      }
      showZPEOnlineBrowser(new HashMap<>(), (ZPEList) json.get(new ZPEString("list")), true);
    } catch (Exception exception) {
      showError("ZPE Online", exception.getMessage());
    }
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
      var command = Git.cloneRepository().setURI(repoUrl.trim()).setDirectory(destination);
      UsernamePasswordCredentialsProvider credentials = optionalGitHubCredentials();
      if (credentials != null) command.setCredentialsProvider(credentials);
      try (Git ignored = command.call()) {
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
    updateProjectMenuVisibility();
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
        var command = git.pull();
        UsernamePasswordCredentialsProvider credentials = optionalGitHubCredentials();
        if (credentials != null) command.setCredentialsProvider(credentials);
        var result = command.call();
        return result.isSuccessful() ? "Pull completed." : "Pull completed with conflicts; resolve them before committing.";
      }
    });
  }

  private void pushGitChanges() {
    runGitTask("Pushing changes", () -> {
      try (Git git = openCurrentGitProject()) {
        var command = git.push();
        UsernamePasswordCredentialsProvider credentials = optionalGitHubCredentials();
        if (credentials != null) command.setCredentialsProvider(credentials);
        command.call();
        return "Push completed.";
      }
    });
  }

  private void signInToGitHub() {
    statusLabel.setText("Requesting GitHub sign-in code…");
    Thread worker = new Thread(() -> {
      try {
        GitHubDeviceFlow.DeviceCode code = githubDeviceFlow.requestCode();
        Platform.runLater(() -> {
          Label instructions = new Label("ZIDE will copy this one-time code before opening GitHub:");
          Label userCode = new Label(code.userCode());
          userCode.getStyleClass().add("github-device-code");
          Label waiting = new Label("Paste the copied code, approve access, then return to ZIDE. Sign-in finishes automatically.");
          waiting.setWrapText(true);
          VBox content = new VBox(10, instructions, userCode, waiting);
          showInWindowModal("Sign in to GitHub", "Authorize ZIDE using your browser", content,
                  "Copy code and open GitHub", () -> {
                    ClipboardContent clipboard = new ClipboardContent();
                    clipboard.putString(code.userCode());
                    Clipboard.getSystemClipboard().setContent(clipboard);
                    try {
                      HelperFunctions.openWebsite(code.verificationUri().toString());
                      statusLabel.setText("GitHub code copied; waiting for authorization…");
                    }
                    catch (Exception exception) { showError("Could not open GitHub", exception.getMessage()); }
                    return false;
                  });
          setActiveModalWidth(640);
          statusLabel.setText("Waiting for GitHub authorization…");
        });
        githubDeviceFlow.awaitToken(code).whenComplete((token, failure) -> {
          if (failure != null) {
            Platform.runLater(() -> {
              closeInWindowModal();
              statusLabel.setText("Ready");
              showError("GitHub sign-in failed", rootMessage(failure));
            });
            return;
          }
          try {
            githubCredentials.save(token);
            githubToken = token;
            ZPEMap user = new GitHubApi(token.accessToken()).currentUser();
            Platform.runLater(() -> {
              closeInWindowModal();
              statusLabel.setText("Signed in to GitHub as " + user.get("login"));
            });
          } catch (Exception exception) {
            Platform.runLater(() -> {
              closeInWindowModal();
              statusLabel.setText("Ready");
              showError("GitHub sign-in failed", exception.getMessage());
            });
          }
        });
      } catch (Exception exception) {
        Platform.runLater(() -> {
          statusLabel.setText("Ready");
          showError("GitHub sign-in failed", exception.getMessage());
        });
      }
    }, "zide-github-device-login");
    worker.setDaemon(true);
    worker.start();
  }

  private void signOutOfGitHub() {
    try {
      githubCredentials.clear();
      githubToken = null;
      statusLabel.setText("Signed out of GitHub");
    } catch (IOException exception) {
      showError("Could not sign out of GitHub", exception.getMessage());
    }
  }

  private UsernamePasswordCredentialsProvider optionalGitHubCredentials() throws IOException, InterruptedException {
    GitHubDeviceFlow.Token token = githubToken;
    if (token == null) token = githubCredentials.load().orElse(null);
    if (token == null) return null;
    if (token.needsRefresh()) {
      if (token.refreshToken() == null) return null;
      token = githubDeviceFlow.refresh(token.refreshToken());
      githubCredentials.save(token);
    }
    githubToken = token;
    return new UsernamePasswordCredentialsProvider("x-access-token", token.accessToken());
  }

  private static String rootMessage(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) current = current.getCause();
    return current.getMessage() == null ? current.toString() : current.getMessage();
  }

  /** Executes Git work away from the JavaFX application thread and reports the final outcome on it. */
  private void runGitTask(String activity, Callable<String> action) {
    statusLabel.setText(activity + "…");
    Thread worker = new Thread(() -> {
      try {
        String result = action.call();
        Platform.runLater(() -> {
          statusLabel.setText("Ready");
          showMessage(activity, result);
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

  private Node createZPEOnlineFileRow(String name, Runnable openAction) {
    HBox row = new HBox(10);
    row.getStyleClass().add("zpe-online-file-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setUserData(name);

    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("zpe-online-file-name");
    nameLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(nameLabel, Priority.ALWAYS);

    Button openButton = new Button("Open");
    openButton.getStyleClass().add("zpe-online-open-button");
    openButton.setOnAction(e -> openAction.run());

    row.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        openAction.run();
      }
    });

    row.getChildren().addAll(nameLabel, openButton);
    return row;
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

  private void showZPEOnlineBrowser(Map<String, String> arguments, ZPEList files, boolean publicRepository) {
    TextField search = new TextField();
    search.setPromptText("Search files");
    search.getStyleClass().add("zpe-online-search");

    VBox fileList = new VBox(1);
    fileList.getStyleClass().add("zpe-online-file-list");

    ScrollPane scrollPane = new ScrollPane(fileList);
    scrollPane.setFitToWidth(true);
    scrollPane.setPrefViewportHeight(360);
    scrollPane.getStyleClass().add("zpe-online-scroll");

    ArrayList<Node> rows = new ArrayList<>();

    for (Object item : files) {
      ZPEMap file = (ZPEMap) item;

      String name = file.get(new ZPEString("name")).toString();
      String id = file.get(new ZPEString("id")).toString();

      Node row = createZPEOnlineFileRow(name, () -> {
        closeInWindowModal();
        if (publicRepository) {
          Map<String, String> loadArgs = new HashMap<>(arguments);
          loadArgs.put("id", id);
          loadArgs.put("name", name);
          loadFromCloudFile(id, loadArgs, new ZPEMap(), true);
        } else {
          loadZPEOnlineFile(arguments, id, name);
        }
      });

      rows.add(row);
      fileList.getChildren().add(row);
    }

    search.textProperty().addListener((obs, oldText, newText) -> {
      String q = newText == null ? "" : newText.strip().toLowerCase(Locale.ROOT);
      List<Node> matches = rows.stream()
                      .filter(row -> {
                        Object name = row.getUserData();
                        return name != null && name.toString().toLowerCase(Locale.ROOT).contains(q);
                      })
                      .toList();
      if (matches.isEmpty()) {
        Label empty = new Label(q.isEmpty() ? "No files are available." : "No matching files.");
        empty.getStyleClass().add("zpe-online-empty");
        fileList.getChildren().setAll(empty);
      } else {
        fileList.getChildren().setAll(matches);
      }
    });

    if (rows.isEmpty()) {
      Label empty = new Label("No files are available.");
      empty.getStyleClass().add("zpe-online-empty");
      fileList.getChildren().setAll(empty);
    }

    VBox content = new VBox(10, search, scrollPane);
    content.getStyleClass().add("zpe-online-browser");
    content.setPrefWidth(600);
    showInWindowModal(
            publicRepository ? "ZPE Online public repository" : "Load from ZPE Online",
            publicRepository ? "Choose a shared program to open" : "Choose a file from your account",
            content,
            List.of(new ModalAction("Cancel", false, () -> true))
    );
    setActiveModalWidth(660);
    Platform.runLater(search::requestFocus);
  }

  private void newProject() {
    if (currentProjectRoot == null) {
      showMessage("New Project", "Please open a folder first.");
      return;
    }

    TextField name = new TextField();
    name.setPromptText("Project name");
    name.setMaxWidth(Double.MAX_VALUE);
    Label validation = modalValidationLabel();
    VBox content = new VBox(8, new Label("Project name"), name, validation);
    content.setPrefWidth(440);
    showInWindowModal("New Project", "Create a folder in " + currentProjectRoot.getName(), content,
        "Create", () -> {
      String trimmed = name.getText().trim();

      if (trimmed.isEmpty()) {
        showModalValidation(validation, "Project name cannot be empty.");
        return false;
      }

      File newDir = new File(currentProjectRoot, trimmed);

      if (newDir.exists()) {
        showModalValidation(validation, "A folder with that name already exists.");
        return false;
      }

      if (!newDir.mkdir()) {
        showModalValidation(validation, "Failed to create project folder.");
        return false;
      }
      try {
        Files.writeString(new File(newDir, ".zpe.project").toPath(),
                "{\n  \"files\": []\n}\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
      } catch (IOException exception) {
        showModalValidation(validation, "Project folder created, but .zpe.project could not be created.");
        return false;
      }

      buildProjectTree(currentProjectRoot);
      return true;
    });
    setActiveModalWidth(520);
    Platform.runLater(name::requestFocus);
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
      showMessage("Compilation complete", "Successfully compiled " + projectDirectory.getName() + ".");
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
    showMessage("Compilation complete",
            "Successfully compiled " + language + " to " + output.getAbsolutePath() + ".");
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
      showMessage("Compilation complete", "Successfully compiled native binary.");
    } else{
      showError("Compilation failed", "Failed to compile native binary.");
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

      showMessage("Code transpiled to " + language, "Saved at:\n" + outputPath);
    } catch (Exception ex) {
      showError("Transpilation failed", ex.getMessage());
    }
  }

  private void loginToZPEOnline() {
    TextField usernameField = new TextField(username == null ? "" : username);
    usernameField.setPromptText("Username");
    PasswordField passwordField = new PasswordField();
    passwordField.setPromptText("Password");
    Label status = new Label();
    status.getStyleClass().add("editor-info-meta");
    GridPane fields = new GridPane();
    fields.setHgap(12);
    fields.setVgap(12);
    fields.addRow(0, new Label("Username"), usernameField);
    fields.addRow(1, new Label("Password"), passwordField);
    GridPane.setHgrow(usernameField, Priority.ALWAYS);
    GridPane.setHgrow(passwordField, Priority.ALWAYS);
    VBox content = new VBox(10, fields, status);
    content.setPrefWidth(440);
    showInWindowModal("Sign in to ZPE Online", "Enter your ZPE Online credentials", content,
            "Sign in", () -> {
      if (usernameField.getText().isBlank() || passwordField.getText().isBlank()) {
        status.setText("Enter your username and password.");
        return false;
      }
      username = usernameField.getText().trim();
      password = passwordField.getText();
      status.setText("Signing in...");
      usernameField.setDisable(true);
      passwordField.setDisable(true);
      authenticateZPEOnline(usernameField, passwordField, status);
      return false;
    });
    setActiveModalWidth(560);
    passwordField.setOnAction(event -> {
      if (!usernameField.getText().isBlank() && !passwordField.getText().isBlank()) {
        username = usernameField.getText().trim();
        password = passwordField.getText();
        status.setText("Signing in...");
        usernameField.setDisable(true);
        passwordField.setDisable(true);
        authenticateZPEOnline(usernameField, passwordField, status);
      }
    });
    Platform.runLater(usernameField::requestFocus);
  }

  private void toggleZPEOnlineLogin() {
    if (loggedIn) {
      logoutOfZPEOnline();
    } else {
      loginToZPEOnline();
    }
  }

  private void restoreZPEOnlineSession() {
    if (!hasText(username) || !hasText(password)) return;
    authenticateZPEOnline(null, null, null);
  }

  private void logoutOfZPEOnline() {
    username = "";
    password = "";
    loggedIn = false;
    recentOnlineFiles = null;
    MAIN_PROPERTIES.remove("LOGIN_USERNAME");
    MAIN_PROPERTIES.remove("LOGIN_PASSCODE");
    saveProps();
    updateZPEOnlineLoginState(false);
    statusLabel.setText("Signed out of ZPE Online");
  }

  private void updateZPEOnlineLoginState(boolean signedIn) {
    loggedIn = signedIn;
    setMenuItemText(loginToZPEOnlineMenuItem,
            signedIn ? "Logout of ZPE Online" : "Login to ZPE Online");
    setMenuItemAvailable(zpeOnlineAuthSeparator, signedIn);
    setMenuItemAvailable(recentOnlineFilesMenuItem,
            signedIn && recentOnlineFiles != null && !recentOnlineFiles.isEmpty());
    setMenuItemAvailable(loadFromOnline, true);
    updateZPEOnlineSaveAvailability();
  }

  private void updateZPEOnlineSaveAvailability() {
    EditorTab tab = getCurrentTab();
    boolean supported = tab != null && "yass".equals(tab.getLanguageId());
    setMenuItemAvailable(saveToOnline, loggedIn && supported);
  }

  private void showRecentZPEOnlineFiles() {
    if (recentOnlineFiles == null || recentOnlineFiles.isEmpty()) return;
    Map<String, String> arguments = new HashMap<>();
    arguments.put("username", username);
    arguments.put("password", password);
    showZPEOnlineBrowser(arguments, recentOnlineFiles, false);
  }

  private static void setMenuItemText(Node item, String text) {
    if (!(item instanceof Pane pane)) return;
    for (Node child : pane.getChildren()) {
      if (child instanceof Label label && label.getStyleClass().contains("glass-menu-item-text")) {
        label.setText(text);
        return;
      }
    }
  }

  private void openZPEOnlinePage(String url) {
    try {
      HelperFunctions.openWebsite(url);
    } catch (Exception exception) {
      showError("Could not open ZPE Online", exception.getMessage());
    }
  }

  private void authenticateZPEOnline(TextField usernameField, PasswordField passwordField, Label status) {
    Task<ZPEMap> task = new Task<>() {
      @Override protected ZPEMap call() throws Exception {
        return ZPEOnline.loginToZPEOnline(username, password);
      }
    };
    task.setOnSucceeded(event -> {
      ZPEMap res = task.getValue();
      if (res == null || !res.containsKey(new ZPEString("result"))) {
        if (status != null) status.setText("The server returned an invalid response.");
      } else if (Integer.parseInt(res.get(new ZPEString("result")).toString()) != 1) {
        Object message = res.get(new ZPEString("message"));
        if (status != null) status.setText(message == null ? "Login was not accepted." : message.toString());
        if (usernameField == null) logoutOfZPEOnline();
      } else {
        Object token = res.get(new ZPEString("login_token"));
        if (token != null && !token.toString().isBlank()) password = token.toString();
        Object recent = res.get(new ZPEString("list"));
        recentOnlineFiles = recent instanceof ZPEList ? (ZPEList) recent : null;
        MAIN_PROPERTIES.setProperty("LOGIN_USERNAME", username);
        MAIN_PROPERTIES.setProperty("LOGIN_PASSCODE", password);
        saveProps();
        updateZPEOnlineLoginState(true);
        if (usernameField != null) {
          closeInWindowModal();
          showMessage("ZPE Online", "Successfully signed in to ZPE Online.");
        } else {
          statusLabel.setText("Signed in to ZPE Online as " + username);
        }
        return;
      }
      if (usernameField != null) {
        usernameField.setDisable(false);
        passwordField.setDisable(false);
        passwordField.requestFocus();
      }
    });
    task.setOnFailed(event -> {
      if (status != null) status.setText(rootMessage(task.getException()));
      else logoutOfZPEOnline();
      if (usernameField != null) {
        usernameField.setDisable(false);
        passwordField.setDisable(false);
      }
    });
    Thread worker = new Thread(task, "zide-zpe-online-login");
    worker.setDaemon(true);
    worker.start();
  }

  /** Saves the active editor to the user's ZPE Online account using the established v10 API. */
  private void saveToZPEOnline() {
    EditorTab tab = getCurrentTab();
    if (tab == null || !"yass".equals(tab.getLanguageId())) {
      showError("Save to ZPE Online", "Only YASS files can be saved online.");
      return;
    }
    if (!loggedIn || username == null || password == null) {
      loginToZPEOnline();
      if (!loggedIn) return;
    }

    String code = tab.getEditor().getText();
    try {
      if ("ywp".equals(tab.getLanguageId())
              ? !ZPEKit.validateYWP(code)
              : !ZPEKit.validateCode(code)) {
        showError("Save to ZPE Online", "Validate and fix the YASS code before uploading it.");
        return;
      }
    } catch (Exception exception) {
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

    boolean isPublic = confirmInWindow("ZPE Online visibility", "Make this script public?",
            "Publish it for other ZPE Online users, or cancel to keep it private.", "Make public");

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
        showMessage("Cloud save complete", "Saved " + cloudFileName + " to ZPE Online.");
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
    downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPEX, null);
  }

  private void showError(String header, String message) {
    showMessage(header, message);
  }

  private void showMessage(String title, String message) {
    Label body = new Label(message == null || message.isBlank() ? "Unknown error" : message);
    body.setWrapText(true);
    body.setMaxWidth(540);
    body.getStyleClass().add("in-window-modal-message");
    showInWindowModal(title, "", body, "OK", () -> true, false);
    setActiveModalWidth(520);
  }

  private void requestApplicationClose() {
    Label body = new Label("Are you sure you want to quit ZIDE?");
    body.setWrapText(true);
    body.getStyleClass().add("in-window-modal-message");
    showInWindowModal("Quit ZIDE", "Close the application", body, "Quit", () -> {
      Platform.runLater(_stage::close);
      return true;
    });
    setActiveModalWidth(440);
  }

  /** Provides a synchronous answer for APIs such as tab-close handlers while keeping the UI in-window. */
  private boolean confirmInWindow(String title, String subtitle, String message, String confirmText) {
    return confirmInWindow(title, subtitle, message, "Cancel", confirmText);
  }

  private boolean confirmInWindow(String title, String subtitle, String message,
                                  String cancelText, String confirmText) {
    Object nestedLoop = new Object();
    AtomicBoolean confirmed = new AtomicBoolean(false);
    AtomicBoolean loopExited = new AtomicBoolean(false);
    Runnable exitLoop = () -> {
      if (loopExited.compareAndSet(false, true)) Platform.exitNestedEventLoop(nestedLoop, null);
    };
    Label body = new Label(message);
    body.setWrapText(true);
    body.setMaxWidth(540);
    showInWindowModal(title, subtitle, body, List.of(
            new ModalAction(cancelText, false, () -> {
              exitLoop.run();
              return true;
            }),
            new ModalAction(confirmText, true, () -> {
              confirmed.set(true);
              exitLoop.run();
              return true;
            })));
    setActiveModalWidth(520);
    activeModalDismiss = exitLoop;
    Platform.enterNestedEventLoop(nestedLoop);
    return confirmed.get();
  }

  private boolean isChatGPTConfigured() {
    return hasText(MAIN_PROPERTIES.getProperty("CHATGPT_KEY"))
            && hasText(MAIN_PROPERTIES.getProperty("CHATGPT_URL"))
            && hasText(MAIN_PROPERTIES.getProperty("CHATGPT_MODEL"));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void openAIBuilder() {
    openAIGenerator(false);
  }

  private void openAIProblemSolver() {
    openAIGenerator(true);
  }

  private void openAIGenerator(boolean solveProblem) {
    EditorTab tab = getCurrentTab();
    if (tab == null || !"yass".equals(tab.getLanguageId())) {
      showError("AI Assistant", "Open a YASS file before using the AI Assistant.");
      return;
    }
    if (!isChatGPTConfigured()) {
      showError("AI Assistant", "Add your ChatGPT details in Tools > Settings first.");
      return;
    }

    javafx.scene.control.TextArea request = new javafx.scene.control.TextArea();
    request.setPromptText(solveProblem
            ? "Explain what is wrong, what you expected, and any constraints..."
            : "Describe the program, its inputs and what it should do...");
    request.setWrapText(true);
    request.setPrefRowCount(12);
    request.setPrefColumnCount(60);
    Label progress = new Label();
    progress.getStyleClass().add("editor-info-meta");
    VBox content = new VBox(10, request, progress);
    VBox.setVgrow(request, Priority.ALWAYS);
    content.setPrefSize(640, 340);
    showInWindowModal("AI Assistant", solveProblem
                    ? "Describe the problem you want ChatGPT to solve"
                    : "Describe the YASS program you want to build",
            content, List.of(
                    new ModalAction("Cancel", false, () -> true),
                    new ModalAction("Generate", true, () -> {
      String description = request.getText().trim();
      if (description.isEmpty() || request.isDisabled()) return false;
      request.setDisable(true);
      progress.setText("Generating and validating YASS code...");

      Task<String> task = new Task<>() {
        @Override
        protected String call() throws Exception {
          return solveProblem
                  ? solveYassWithAI(tab.getEditor().getText(), description)
                  : generateYassWithAI(description);
        }
      };
      task.setOnSucceeded(ignored -> {
        closeInWindowModal();
        showAIResult(tab, task.getValue());
      });
      task.setOnFailed(ignored -> {
        request.setDisable(false);
        progress.setText("");
        Throwable failure = task.getException();
        showError("ChatGPT could not generate valid YASS code",
                failure == null || failure.getMessage() == null ? "Unknown error" : failure.getMessage());
      });
      Thread worker = new Thread(task, "zide-ai-builder");
      worker.setDaemon(true);
      worker.start();
      return false;
    })));
    Platform.runLater(request::requestFocus);
  }

  private String solveYassWithAI(String source, String problem) throws Exception {
    ZIDEOpenAIClient chatGPT = createOpenAIClient();
    String system = "Repair the supplied YASS program. Return the complete corrected program and no prose.\n\n"
            + ZPEHelperFunctions.getAIRules();
    String generated = requestAIMessage(chatGPT, system,
            "Problem:\n" + problem + "\n\nCurrent program:\n" + source);
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (validateGeneratedYass(generated)) return generated;
      } catch (CompileException ignored) { }
      generated = requestAIMessage(chatGPT, system,
              "The proposed repair does not compile. Correct it and return code only:\n" + generated);
    }
    throw new IOException("ChatGPT did not produce valid YASS code.");
  }

  private void openAIValidation() {
    EditorTab tab = getCurrentTab();
    if (tab == null || !"yass".equals(tab.getLanguageId())) {
      showError("AI validation", "Open a YASS file before validating it.");
      return;
    }
    if (!isChatGPTConfigured()) {
      showError("AI validation", "Add your ChatGPT details in Tools > Settings first.");
      return;
    }
    try {
      if (!validateGeneratedYass(tab.getEditor().getText())) {
        showError("AI validation", "Fix the syntax errors before requesting an AI review.");
        return;
      }
    } catch (CompileException exception) {
      showError("AI validation", "Syntax validation failed: " + exception.getMessage());
      return;
    }
    statusLabel.setText("ChatGPT is reviewing the current YASS program...");
    Task<String> task = new Task<>() {
      @Override protected String call() throws Exception {
        return createOpenAIClient().sendMessage(
                "Review this valid YASS program for logic errors, unsafe assumptions and clearer solutions. "
                        + "Be concise and do not rewrite it unless a correction is necessary.\n\n"
                        + ZPEHelperFunctions.getAIRules(), tab.getEditor().getText());
      }
    };
    task.setOnSucceeded(event -> {
      statusLabel.setText("AI validation complete");
      showAIValidationResult(task.getValue());
    });
    task.setOnFailed(event -> {
      statusLabel.setText("Ready");
      showError("AI validation failed", rootMessage(task.getException()));
    });
    Thread worker = new Thread(task, "zide-ai-validator");
    worker.setDaemon(true);
    worker.start();
  }

  private void showAIValidationResult(String review) {
    javafx.scene.control.TextArea text = new javafx.scene.control.TextArea(review);
    text.setEditable(false);
    text.setWrapText(true);
    text.setPrefSize(620, 360);
    showInWindowModal("AI validation", "Review of the current YASS program", text, "Done", () -> true);
  }

  private ZIDEOpenAIClient createOpenAIClient() {
    return new ZIDEOpenAIClient(MAIN_PROPERTIES.getProperty("CHATGPT_URL"),
            MAIN_PROPERTIES.getProperty("CHATGPT_KEY"), MAIN_PROPERTIES.getProperty("CHATGPT_MODEL"));
  }

  private String generateYassWithAI(String description) throws Exception {
    ZIDEOpenAIClient chatGPT = createOpenAIClient();
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

  private String requestAIMessage(ZIDEOpenAIClient chatGPT, String systemMessage, String userMessage)
          throws IOException {
    return stripCodeFence(chatGPT.sendMessage(systemMessage, userMessage));
  }

  /** Opens the sectioned settings surface; further groups can be added to the sidebar later. */
  private void openSettings() {
    ListView<String> sections = new ListView<>(FXCollections.observableArrayList("GUI", "ChatGPT"));
    sections.setPrefWidth(145);
    sections.setMaxWidth(145);
    sections.getSelectionModel().selectFirst();

    ComboBox<String> theme = new ComboBox<>(FXCollections.observableArrayList("Light", "Dark"));
    theme.setValue(isDarkThemeEnabled() ? "Dark" : "Light");
    theme.setMaxWidth(Double.MAX_VALUE);
    CheckBox wordWrap = new CheckBox();
    wordWrap.setSelected(USE_WORD_WRAP);
    GridPane guiFields = new GridPane();
    guiFields.setHgap(12);
    guiFields.setVgap(12);
    guiFields.addRow(0, new Label("Theme"), theme);
    guiFields.addRow(1, new Label("Word wrap"), wordWrap);
    GridPane.setHgrow(theme, Priority.ALWAYS);
    Label guiTitle = new Label("GUI");
    guiTitle.getStyleClass().add("settings-section-title");
    VBox gui = new VBox(12, guiTitle, guiFields);
    gui.getStyleClass().add("settings-section-content");

    TextField url = new TextField(MAIN_PROPERTIES.getProperty(
            "CHATGPT_URL", "https://api.openai.com/v1/responses"));
    PasswordField key = new PasswordField();
    key.setText(MAIN_PROPERTIES.getProperty("CHATGPT_KEY", ""));
    ComboBox<String> model = new ComboBox<>(FXCollections.observableArrayList(
            "gpt-5-mini", "gpt-5", "gpt-4.1-mini", "gpt-4o-mini"));
    model.setEditable(true);
    model.setMaxWidth(Double.MAX_VALUE);
    model.getEditor().setText(MAIN_PROPERTIES.getProperty("CHATGPT_MODEL", "gpt-5-mini"));

    Label sectionTitle = new Label("ChatGPT");
    sectionTitle.getStyleClass().add("settings-section-title");
    GridPane fields = new GridPane();
    fields.setHgap(12);
    fields.setVgap(12);
    fields.addRow(0, new Label("API URL"), url);
    fields.addRow(1, new Label("API key"), key);
    fields.addRow(2, new Label("Model"), model);
    GridPane.setHgrow(url, Priority.ALWAYS);
    GridPane.setHgrow(key, Priority.ALWAYS);
    GridPane.setHgrow(model, Priority.ALWAYS);
    VBox chatGPT = new VBox(12, sectionTitle, fields);
    chatGPT.getStyleClass().add("settings-section-content");
    StackPane settingsPage = new StackPane(gui);
    settingsPage.setMinWidth(450);
    HBox.setHgrow(settingsPage, Priority.ALWAYS);
    sections.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) ->
            settingsPage.getChildren().setAll("ChatGPT".equals(selected) ? chatGPT : gui));
    HBox content = new HBox(18, sections, settingsPage);
    content.getStyleClass().add("settings-content");
    content.setPrefWidth(620);
    HBox.setHgrow(chatGPT, Priority.ALWAYS);

    showInWindowModal("Settings", "Configure ZIDE", content, "Save", () -> {
      String selectedModel = model.getEditor().getText().trim();
      if (hasText(key.getText()) && (!hasText(url.getText()) || !hasText(selectedModel))) {
        showError("ChatGPT settings", "Enter the API URL, API key and model.");
        return false;
      }
      MAIN_PROPERTIES.setProperty("CHATGPT_URL", url.getText().trim());
      MAIN_PROPERTIES.setProperty("CHATGPT_KEY", key.getText().trim());
      MAIN_PROPERTIES.setProperty("CHATGPT_MODEL", selectedModel);
      applyThemePreference("Dark".equals(theme.getValue()), true);
      applyWordWrapPreference(wordWrap.isSelected());
      EditorTab tab = getCurrentTab();
      updateLanguageCommands(tab == null ? null : languageSupports.get(tab.getLanguageId()));
      statusLabel.setText("Settings saved");
      return true;
    });
  }

  /** Saves and immediately applies word wrapping to every open editor. */
  private void applyWordWrapPreference(boolean enabled) {
    USE_WORD_WRAP = enabled;
    MAIN_PROPERTIES.setProperty("USE_WORD_WRAP", Boolean.toString(enabled));
    saveProps();
    if (editorTabs == null) return;
    for (Tab tab : editorTabs.getTabs()) {
      if (tab instanceof EditorTab editorTab) editorTab.getEditor().setWordWrap(enabled);
    }
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
    CodeEditorViewFX preview = new CodeEditorViewFX();
    preview.setDarkMode(isDarkThemeEnabled());
    preview.setFontSize(13);
    configureYass(preview);
    preview.setText(code);
    preview.setEditable(false);
    Node view = preview.getView();
    if (view instanceof Region region) region.setPrefSize(720, 480);
    showInWindowModal("AI Generated Code", "Review the generated YASS code", view, List.of(
            new ModalAction("Cancel", false, () -> true),
            new ModalAction("Insert code", false, () -> {
              String existing = target.getEditor().getText();
              target.getEditor().setText(existing +
                      (existing.endsWith("\n") || existing.isEmpty() ? "" : "\n") + code);
              return true;
            }),
            new ModalAction("Replace code", true, () -> {
              target.getEditor().setText(code);
              return true;
            })));
  }

  private void downloadZPERuntime(){
    downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPE, null);
  }

  private void downloadYassRuntime(ZIDERuntimeManager.RuntimeKind kind, Runnable onInstalled) {
    try {
      Files.createDirectories(Path.of(INSTALL_PATH));
      downloadWithPopup("Latest " + kind, _stage, zideRuntimes.downloadUrl(kind),
              zideRuntimes.path(kind), false, kind == ZIDERuntimeManager.RuntimeKind.ZPEX,
              () -> {
                selectedYassRuntime = kind;
                MAIN_PROPERTIES.setProperty("YASS_RUNTIME", kind.name());
                saveProps();
                if (onInstalled != null) onInstalled.run();
              });
    } catch (Exception exception) {
      showError("Could not download " + kind, exception.getMessage());
    }
  }

  private EditorTab getCurrentTab(){
    Tab selected = editorTabs.getSelectionModel().getSelectedItem();
    return selected instanceof EditorTab ? (EditorTab) selected : null;
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

    displayProblems(tab, diagnostics);
  }

  private void displayProblems(EditorTab tab, List<YASSDiagnostic> diagnostics) {
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

    if (!getZPE(this::runCode)) return;

    if(!verifyCode()) return;

    Platform.runLater(() -> {
      consoleTab.setSelected(true);
      showBottomPanel(consoleView);
    });

    try {
      if(getCurrentTab() == null) return;
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = getCurrentTab();
      if (!ensureProjectManifestHasScripts(tab)) return;
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
      Path manifest = projectManifestFor(tab);
      Path runSource = manifest == null ? tempPath : manifest;
      runZPEProcess(runSource, resourceDirectoryFor(tab), false, manifest == null && !hasCompanionLayout(tab), "");

      //stopExecution.setDisable(false);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Path projectManifestFor(EditorTab tab) {
    if (tab == null || tab.getPath() == null) return null;
    Path file = Path.of(tab.getPath()).toAbsolutePath().normalize();
    Path folder = Files.isDirectory(file) ? file : file.getParent();
    if (folder == null) return null;
    Path manifest = folder.resolve(".project.yas");
    return Files.isRegularFile(manifest) ? manifest : null;
  }

  private boolean ensureProjectManifestHasScripts(EditorTab tab) {
    Path manifest = projectManifestFor(tab);
    if (manifest == null) return true;
    try {
      boolean hasScripts = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
              .map(String::trim).anyMatch(line -> line.matches("(?i)^includes?\\s+.+"));
      if (hasScripts) return true;
      showMessage("Project has no scripts", "Add at least one YASS file to the project before running it.");
      openProjectManifest(manifest);
      return false;
    } catch (IOException exception) {
      showError("Project manifest", exception.getMessage());
      return false;
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
      ZIDERuntimeManager.RuntimeKind kind = selectedYassRuntime;
      if (kind == null || !zideRuntimes.isInstalled(kind)) {
        kind = zideRuntimes.isInstalled(ZIDERuntimeManager.RuntimeKind.ZPEX)
                ? ZIDERuntimeManager.RuntimeKind.ZPEX : ZIDERuntimeManager.RuntimeKind.ZPE;
      }
      ZIDERuntimeManager.Launch launch = zideRuntimes.prepare(kind, source, resourceRoot, debug, extras);
      consoleOutputTextArea.append(launch.description() + "\n\n", InteractiveConsoleFX.OutputKind.KEY);
      consoleOutputTextArea.runProcess(launch.processBuilder(), launch::processStarted);
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
      showMessage("No YASS file selected",
              "Open or save a YASS file first. The layout builder creates a companion .ui.yas file beside it.");
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

  private void openLanguageBuilderFile(Path initialFile) {
    if (languageBuilderOverlay != null) workspaceStack.getChildren().remove(languageBuilderOverlay);
    ZIDELanguageBuilder builder = new ZIDELanguageBuilder(_stage, initialFile,
            this::trainZenLanguage, this::testZenLanguage, () -> {
      workspaceStack.getChildren().remove(languageBuilderOverlay);
      languageBuilderOverlay = null;
    });
    languageBuilderOverlay = builder.getView();
    workspaceStack.getChildren().add(languageBuilderOverlay);
    languageBuilderOverlay.toFront();
  }

  private void trainZenLanguage(Path definition) {
    if (!zideRuntimes.isInstalled(ZIDERuntimeManager.RuntimeKind.ZPE)) {
      downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPE, () -> trainZenLanguage(definition));
      return;
    }
    try {
      ZIDERuntimeManager.Launch launch = zideRuntimes.prepareZenLanguageTraining(definition);
      consoleOutputTextArea.clear();
      consoleOutputTextArea.append(launch.description() + "\n\n", InteractiveConsoleFX.OutputKind.KEY);
      showBottomPanel(consoleView);
      consoleOutputTextArea.runProcess(launch.processBuilder());
      statusLabel.setText("Training " + definition.getFileName());
    } catch (IOException exception) {
      showError("Unable to train language", exception.getMessage());
    }
  }

  private void testZenLanguage(Path definition, Path source) {
    if (!zideRuntimes.isInstalled(ZIDERuntimeManager.RuntimeKind.ZPE)) {
      downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPE,
              () -> testZenLanguage(definition, source));
      return;
    }
    try {
      ZIDERuntimeManager.Launch launch = zideRuntimes.prepareZenLanguageTest(definition, source);
      consoleOutputTextArea.clear();
      consoleOutputTextArea.append(launch.description() + "\n\n", InteractiveConsoleFX.OutputKind.KEY);
      showBottomPanel(consoleView);
      consoleOutputTextArea.runProcess(launch.processBuilder());
      statusLabel.setText("Testing " + source.getFileName());
    } catch (IOException exception) {
      showError("Unable to test language", exception.getMessage());
    }
  }

  private String sourceWithLayout(EditorTab tab, String source) {
    // UI companions are explicit files now; never append an implicit include.
    return source;
    /*
    if (tab == null || tab.getPath() == null || tab.getPath().toLowerCase(Locale.ROOT).endsWith(".ui.yas")) return source;
    Path script = Path.of(tab.getPath()).toAbsolutePath().normalize();
    String fileName = script.getFileName().toString();
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yas")) return source;
    Path layoutFile = script.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".ui.yas");
    if (!Files.isRegularFile(layoutFile)) return source;
    String includePath = layoutFile.toString().replace('\\', '/').replace("\"", "\\\"");
    String separator = source.isEmpty() || source.endsWith("\n") ? "" : "\n";
    // Keep transient execution compatible with installed ZPEX builds that predate singular `include`.
    return source + separator + "\nincludes \"" + includePath + "\"\n"; */
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
      Path manifest = layoutFile.getParent() == null ? null : layoutFile.getParent().resolve(".project.yas");
      if (manifest != null && Files.isRegularFile(manifest)) {
        StringBuilder projectSource = new StringBuilder();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
          String trimmed = line.trim();
          if (!trimmed.matches("(?i)^includes?\\s+.+")) continue;
          String value = trimmed.replaceFirst("(?i)^includes?\\s+", "").trim();
          if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
          Path included = manifest.getParent().resolve(value).normalize();
          if (Files.isRegularFile(included) && included.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yas"))
            projectSource.append(Files.readString(included, StandardCharsets.UTF_8)).append(System.lineSeparator());
        }
        source = projectSource.append(source).toString();
      }
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
      showMessage("No tab selected", "Select a file to open the macro editor.");
      return;
    }
    ZPERuntimeEnvironment z = new ZPERuntimeEnvironment();
    if(macroInterface == null || !macroInterface.isDisplayable()) {
      macroInterface = ZPEMacroEditor.createJavaFX(z,
              new ZPEObject[]{new YASSCodeEditor.EditorObject(z,
                      ZPEKit.getGlobalFunction(z), createMacroEditorBridge())}, null);
    }
    Node macroView = null;
    try {
      Object view = macroInterface.getClass().getMethod("getView").invoke(macroInterface);
      if (view instanceof Node node) macroView = node;
    } catch (ReflectiveOperationException ignored) {
      // Older ZPE packages only expose the standalone macro window.
    }
    if (macroView == null) {
      showError("Macro Scripting Interface", "This ZPE build does not provide an embeddable macro editor.");
      return;
    }
    if (macroView.getParent() instanceof Pane parent) parent.getChildren().remove(macroView);
    if (macroView instanceof Region region) region.setPrefSize(960, 640);
    showInWindowModal("Macro Scripting Interface", "Create and run a YASS automation macro",
            macroView, "Close", () -> true, false);
  }

  private boolean getZPE(Runnable retry) {
    if (selectedYassRuntime == null) {
      try {
        selectedYassRuntime = ZIDERuntimeManager.RuntimeKind.valueOf(
                MAIN_PROPERTIES.getProperty("YASS_RUNTIME", "ZPEX"));
      } catch (IllegalArgumentException ignored) {
        selectedYassRuntime = ZIDERuntimeManager.RuntimeKind.ZPEX;
      }
    }
    if (zideRuntimes.isInstalled(selectedYassRuntime)) return true;
    for (ZIDERuntimeManager.RuntimeKind kind : ZIDERuntimeManager.RuntimeKind.values()) {
      if (zideRuntimes.isInstalled(kind)) {
        selectedYassRuntime = kind;
        return true;
      }
    }

      Label explanation = new Label(
              "No runtime is bundled or downloaded initially. ZPE uses Java; ZPEX is the native package.");
      explanation.setWrapText(true);
      explanation.setMaxWidth(540);
      showInWindowModal("ZPE Runtime Environment missing", "Choose the YASS runtime for ZIDE",
              explanation, List.of(
                      new ModalAction("Cancel", false, () -> true),
                      new ModalAction("Download ZPE", false, () -> {
                        downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPE, retry);
                        return true;
                      }),
                      new ModalAction("Download ZPEX", true, () -> {
                        downloadYassRuntime(ZIDERuntimeManager.RuntimeKind.ZPEX, retry);
                        return true;
                      })));
      return false;
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

    if (!getZPE(this::debugCode)) return;
    if(getCurrentTab() == null) return;

    try {
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = getCurrentTab();
      if(tab == null){
        tab = (EditorTab) editorTabs.getTabs().get(0);
      }
      if (!ensureProjectManifestHasScripts(tab)) return;

      debuggingSession = true;
      debugBtn.getStyleClass().add("running");

      stopExecutionBtn.setVisible(true);
      stepOverButton.setVisible(true);
      continueButton.setVisible(true);
      debugSeparator.setVisible(true);


      String debugSource = prepareDebugSourceWithBreakpoints(tab, tab.getEditor().getText());
      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), sourceWithLayout(tab, debugSource), false);
      beginProfilerSession();
      Path manifest = projectManifestFor(tab);
      Path debugSourcePath = manifest == null ? tempPath : manifest;
      if (!runZPEProcess(debugSourcePath, resourceDirectoryFor(tab), true, manifest == null, "")) {
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
          showMessage("Successful build", "Code builds successfully.");
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

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);



    var toolbar = new ToolBar(runBtn, buildBtn, debugBtn, sep1, stopExecutionBtn, stepOverButton, continueButton, debugSeparator, new Label(" "), spacer);
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
      projectTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
      projectTree.getStyleClass().add("project-tree");

      projectTree.setCellFactory(tv -> new TreeCell<>() {
        @Override
        protected void updateItem(File file, boolean empty) {
          super.updateItem(file, empty);

          if (empty || file == null) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll("active-project-root", "active-project-file");
            return;
          }

          String name = file.getName();
          setText(name.isEmpty() ? file.getPath() : name);
          getStyleClass().removeAll("active-project-root", "active-project-file");
          Path cellPath = file.toPath().toAbsolutePath().normalize();
          if (currentProjectRoot != null
                  && cellPath.equals(currentProjectRoot.toPath().toAbsolutePath().normalize())) {
            getStyleClass().add("active-project-root");
          }
          EditorTab active = getCurrentTab();
          if (active != null && active.getPath() != null
                  && cellPath.equals(Path.of(active.getPath()).toAbsolutePath().normalize())) {
            getStyleClass().add("active-project-file");
          }
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
    delete.setOnAction(event -> {
      ContextMenu parent = delete.getParentPopup();
      if (parent != null) parent.hide();
      Platform.runLater(() -> deleteProjectFile(file));
    });
    delete.setDisable(isProjectRoot(file));

    ContextMenu menu = new ContextMenu(open);
    menu.getStyleClass().add("glass-context-menu");
    MenuItem systemExplorer = new MenuItem("Open in system explorer");
    systemExplorer.setOnAction(event -> {
      try {
        java.awt.Desktop.getDesktop().open(file.isDirectory() ? file : file.getParentFile());
      } catch (Exception exception) {
        showError("System explorer", exception.getMessage());
      }
    });
    menu.getItems().add(systemExplorer);
    if (regularFile && (file.getName().toLowerCase(Locale.ROOT).endsWith(".ui.yas")
            || file.getName().toLowerCase(Locale.ROOT).endsWith(".zenlang"))) {
      menu.getItems().add(openAsText);
    }
    if (file.isDirectory()) {
      registerLanguageSupports();
      MenuItem refreshFolder = new MenuItem("Refresh Folder");
      refreshFolder.setOnAction(event -> refreshProjectFolder(file));
      javafx.scene.control.Menu newFile = new javafx.scene.control.Menu("New File");
      for (LanguageSupport language : languageSupports.values()) {
        MenuItem item = new MenuItem(language.label(), languageFileIcon(language));
        item.setOnAction(event -> createLanguageFile(file, language));
        newFile.getItems().add(item);
      }
      menu.getItems().addAll(new SeparatorMenuItem(), refreshFolder, newFile);
      long yassCount = Arrays.stream(file.listFiles() == null ? new File[0] : file.listFiles())
              .filter(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".yas")
                      && !f.getName().equalsIgnoreCase(".project.yas")).count();
      File manifest = new File(file, ".project.yas");
      if (manifest.isFile()) {
        MenuItem editManifest = new MenuItem("Edit project settings");
        editManifest.setOnAction(event -> openProjectManifest(manifest.toPath()));
        menu.getItems().add(editManifest);
      }
      if (yassCount > 1 && !manifest.exists()) {
        MenuItem createManifest = new MenuItem("Create .project.yas");
        createManifest.setOnAction(event -> {
          try { Files.writeString(manifest.toPath(), "// ZIDE project manifest\n", StandardCharsets.UTF_8); refreshProjectFolder(file); openProjectManifest(manifest.toPath()); }
          catch (IOException exception) { showError("Project manifest", exception.getMessage()); }
        });
        menu.getItems().add(createManifest);
      }
    }
    menu.getItems().addAll(new SeparatorMenuItem(), rename, delete);
    return menu;
  }

  /** Creates a typed file directly inside the folder that opened the context menu. */
  private void createLanguageFile(File folder, LanguageSupport language) {
    TextField name = new TextField("Untitled");
    name.setPromptText("File name");
    name.setMaxWidth(Double.MAX_VALUE);
    Label validation = modalValidationLabel();
    VBox content = new VBox(8, new Label("File name"), name, validation);
    content.setPrefWidth(440);

    showInWindowModal("New " + language.label() + " File",
            "Create a file in " + folder.getName(), content, "Create", () -> {
      String fileName = name.getText().trim();
      if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")) {
        showModalValidation(validation, "Choose a simple file name.");
        return false;
      }
      String suffix = "." + language.defaultExtension();
      if (!fileName.toLowerCase(Locale.ROOT).endsWith(suffix)) fileName += suffix;
      Path destination = folder.toPath().resolve(fileName);
      try {
        Files.createFile(destination);
        refreshTree();
        openTab(fileName, destination.toAbsolutePath().toString());
        return true;
      } catch (FileAlreadyExistsException exception) {
        showModalValidation(validation, "That file already exists.");
      } catch (IOException exception) {
        showModalValidation(validation, "Could not create the file: " + exception.getMessage());
      }
      return false;
    });
    setActiveModalWidth(520);
    Platform.runLater(() -> {
      name.requestFocus();
      name.selectAll();
    });
  }

  private Label modalValidationLabel() {
    Label validation = new Label();
    validation.getStyleClass().add("in-window-modal-validation");
    validation.setManaged(false);
    validation.setVisible(false);
    return validation;
  }

  private void showModalValidation(Label validation, String message) {
    validation.setText(message);
    validation.setManaged(true);
    validation.setVisible(true);
  }

  private Node languageFileIcon(LanguageSupport language) {
    String abbreviation = "yass".equals(language.id) ? "YAS"
            : "ywp".equals(language.id) ? "YWP"
            : "python".equals(language.id) ? "Py"
            : "zpeedy".equals(language.id) ? "ZPS"
            : "sqarl".equals(language.id) ? "SQ"
            : "zenlang".equals(language.id) ? "ZEN"
            : "md".equals(language.id) ? "MD" : "TXT";
    Label icon = new Label(abbreviation);
    icon.getStyleClass().addAll("language-file-icon", language.iconStyleClass());
    return icon;
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
    if (!confirmInWindow("Delete", "Delete " + file.getName() + "?",
            file.isDirectory()
                    ? "This permanently deletes the folder and everything inside it."
                    : "This permanently deletes the file.", "Delete")) return;

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
    showError("Project file operation failed", message);
  }

  /**
   * Rebuilds the project tree without making the project navigator jump back
   * to its initial, collapsed state after a filesystem operation.
   */
  private void refreshTree() {
    if (currentProjectRoot == null) return;
    updateProjectMenuVisibility();

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

  /** Reloads one visible directory branch without disturbing the rest of the navigator. */
  private void refreshProjectFolder(File folder) {
    if (projectTree == null || projectTree.getRoot() == null || folder == null) return;
    if (currentProjectRoot != null && folder.toPath().toAbsolutePath().normalize()
            .equals(currentProjectRoot.toPath().toAbsolutePath().normalize())) {
      updateProjectMenuVisibility();
    }
    TreeItem<File> item = findProjectTreeItem(projectTree.getRoot(), normalisedProjectPath(folder));
    if (item == null) {
      refreshTree();
      return;
    }

    boolean expanded = item.isExpanded();
    Set<Path> expandedDirectories = new HashSet<>();
    rememberExpandedDirectories(item, expandedDirectories);
    item.getChildren().setAll(new TreeItem<>(null));
    if (expanded) {
      loadChildrenIfNeeded(item);
      restoreProjectTreeState(item, expandedDirectories);
    }
    projectTree.getSelectionModel().select(item);
    projectTree.refresh();
    statusLabel.setText("Refreshed " + folder.getName());
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
        Path affected = directory.resolve((Path) event.context()).toAbsolutePath().normalize();
        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                || event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
          queueExternalFileCheck(affected);
        }
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
          Path created = affected;
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

  private void queueExternalFileCheck(Path path) {
    if (path == null) return;
    pendingExternalFileChanges.add(path.toAbsolutePath().normalize());
    Platform.runLater(() -> {
      if (externalFileChangeTimer != null) externalFileChangeTimer.playFromStart();
    });
  }

  private void checkPendingExternalFileChanges() {
    if (activeModalOverlay != null) {
      externalFileChangeTimer.playFromStart();
      return;
    }
    List<Path> changedPaths = new ArrayList<>(pendingExternalFileChanges);
    pendingExternalFileChanges.removeAll(changedPaths);
    for (Path changedPath : changedPaths) checkOpenTabForExternalChange(changedPath);
  }

  private void checkOpenTabForExternalChange(Path changedPath) {
    if (!Files.isRegularFile(changedPath)) return;
    for (Tab candidate : editorTabs.getTabs()) {
      if (!(candidate instanceof EditorTab tab) || tab.getPath() == null) continue;
      Path tabPath = Path.of(tab.getPath()).toAbsolutePath().normalize();
      if (!tabPath.equals(changedPath)) continue;
      try {
        String diskContent = Files.readString(changedPath, StandardCharsets.UTF_8);
        if (Objects.equals(diskContent, tab.getLastDiskContent())) return;
        if (Objects.equals(diskContent, tab.getEditor().getText())) {
          tab.setLastDiskContent(diskContent);
          tab.setHasChanges(false);
          return;
        }

        boolean reload = confirmInWindow("File Changed",
                changedPath.getFileName() + " changed outside ZIDE.",
                "Reload the file from disk? Keeping the current version marks this tab as unsaved.",
                "Keep Current", "Reload from Disk");
        tab.setLastDiskContent(diskContent);
        if (reload) {
          int caret = tab.getEditor().getCaretPosition();
          tab.getEditor().setText(diskContent);
          tab.getEditor().setCaretPosition(Math.min(caret, diskContent.length()));
          tab.setHasChanges(false);
          statusLabel.setText("Reloaded " + changedPath.getFileName());
        } else {
          tab.setHasChanges(true);
          statusLabel.setText("Kept current version of " + changedPath.getFileName());
        }
      } catch (IOException exception) {
        ZPE.log("Unable to check changed file " + changedPath + ": " + exception.getMessage());
      }
      return;
    }
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
    pendingExternalFileChanges.clear();
    if (externalFileChangeTimer != null) externalFileChangeTimer.stop();
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
        EditorTab selected = (EditorTab) newTab;
        if (isDarkThemeEnabled()) selected.switchOnDarkMode();
        else selected.switchOffDarkMode();
        displayProblems(selected, selected.getDiagnostics());
      }

      if (newTab instanceof EditorTab) syncLanguageSelector((EditorTab) newTab);
      updateZoomPercentage();
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
      if (useLayoutEditor && file.toLowerCase(Locale.ROOT).endsWith(".zenlang")) {
        openLanguageBuilderFile(Path.of(file));
        return;
      }
      if (file.toLowerCase(Locale.ROOT).endsWith(".project.yas")) {
        openProjectManifest(Path.of(file));
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

  private void openProjectManifest(Path manifest) {
    File folder = manifest.toFile().getParentFile();
    if (folder == null) return;
    List<String> all = new ArrayList<>();
    File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yas") && !name.equalsIgnoreCase(".project.yas"));
    if (files != null) for (File f : files) all.add(f.getName());
    all.sort(String.CASE_INSENSITIVE_ORDER);
    List<String> included = new ArrayList<>();
    try {
      for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (!trimmed.matches("(?i)^includes?\\s+.+")) continue;
        String value = trimmed.replaceFirst("(?i)^includes?\\s+", "").trim();
        if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        String name = Path.of(value).getFileName().toString();
        if (all.contains(name) && !included.contains(name)) included.add(name);
      }
    } catch (IOException e) { showError("Unable to open project manifest", e.getMessage()); return; }
    all.removeAll(included);
    ListView<String> available = new ListView<>(FXCollections.observableArrayList(all));
    ListView<String> selected = new ListView<>(FXCollections.observableArrayList(included));
    available.getStyleClass().add("project-manifest-list");
    selected.getStyleClass().add("project-manifest-list");
    Button add = new Button(">>"), remove = new Button("<<"), up = new Button("Up"), down = new Button("Down");
    add.setOnAction(e -> moveItem(available, selected)); remove.setOnAction(e -> moveItem(selected, available));
    up.setOnAction(e -> reorderItem(selected, -1)); down.setOnAction(e -> reorderItem(selected, 1));
    VBox actions = new VBox(8, add, remove, up, down); actions.setAlignment(Pos.CENTER);
    VBox left = new VBox(5, new Label("Not included"), available), right = new VBox(5, new Label("Included"), selected);
    HBox lists = new HBox(10, left, actions, right); HBox.setHgrow(left, Priority.ALWAYS); HBox.setHgrow(right, Priority.ALWAYS);
    Button save = new Button("Save");
    save.setOnAction(e -> { try { StringBuilder out = new StringBuilder("// ZIDE project manifest\n"); for (String name : selected.getItems()) out.append("includes \"").append(name).append("\"\n"); Files.writeString(manifest, out.toString(), StandardCharsets.UTF_8); statusLabel.setText("Project manifest saved"); } catch (IOException ex) { showError("Unable to save project manifest", ex.getMessage()); } });
    VBox content = new VBox(12, new Label("Project files"), lists, save); content.getStyleClass().add("project-manifest-view"); content.setPadding(new Insets(16)); VBox.setVgrow(lists, Priority.ALWAYS);
    Tab tab = new Tab(); tab.setId(manifest.toString()); tab.setClosable(false);
    Button close = new Button(); close.getStyleClass().add("tab-close-button"); close.setFocusTraversable(false); close.setOnAction(e -> closeTab(tab));
    close.setMinSize(10, 10); close.setPrefSize(10, 10); close.setMaxSize(10, 10); tab.setGraphic(close);
    Label manifestTitle = new Label(manifest.getFileName().toString()); manifestTitle.getStyleClass().add("tab-title");
    tab.setGraphic(new HBox(6, manifestTitle, close));
    tab.setContent(content);
    editorTabs.getTabs().add(tab); editorTabs.getSelectionModel().select(tab);
  }

  private void editCurrentProjectSettings() {
    if (currentProjectRoot == null) return;
    Path manifest = currentProjectRoot.toPath().resolve(".project.yas");
    if (Files.isRegularFile(manifest)) openProjectManifest(manifest);
  }

  private void updateProjectMenuVisibility() {
    if (projectMenu == null) return;
    boolean hasManifest = currentProjectRoot != null
            && Files.isRegularFile(currentProjectRoot.toPath().resolve(".project.yas"));
    projectMenu.setVisible(hasManifest);
  }

  private static void moveItem(ListView<String> from, ListView<String> to) { String value = from.getSelectionModel().getSelectedItem(); if (value != null) { from.getItems().remove(value); to.getItems().add(value); } }
  private static void reorderItem(ListView<String> list, int delta) { int index = list.getSelectionModel().getSelectedIndex(), target = index + delta; if (index < 0 || target < 0 || target >= list.getItems().size()) return; String value = list.getItems().remove(index); list.getItems().add(target, value); list.getSelectionModel().select(target); }

  /** Resolves a file through the language registry used by editing, help and execution. */
  private String languageForFile(String file) {
    LanguageSupport language = languageSupportForFile(file);
    return language == null ? "txt" : language.id;
  }

  private void setLanguage(String id, CodeEditorViewFX editor) {
    registerLanguageSupports();
    editor.setDocumentAutoCompleteItems(Map.of());
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
    Map<String, CodeEditorViewFX.AutoCompleteItemType> symbols = new LinkedHashMap<>();
    if (source == null || source.isBlank()) {
      editor.setDocumentAutoCompleteItems(symbols);
      return;
    }
    if ("yass".equals(languageId)) {
      collectSymbols(symbols, source, Pattern.compile("(?im)^\\s*(?:(?:public|private|protected|static|abstract|final)\\s+)*"
              + "(function|module|namespace|class|structure|interface|record)\\s+([A-Za-z_][A-Za-z0-9_]*)"), 2, 1);
      collectVariables(symbols, source, Pattern.compile(
              "(\\$-?[A-Za-z_][A-Za-z0-9_]*)(?=\\s*(?:=|\\+=|-=|\\*=|/=|%=))"));
      Matcher headers = Pattern.compile("(?im)^\\s*(?:(?:public|private|protected|static)\\s+)*function\\s+[^\\n(]*\\(([^)]*)\\)")
              .matcher(source);
      while (headers.find()) collectVariables(symbols, headers.group(1),
              Pattern.compile("(\\$-?[A-Za-z_][A-Za-z0-9_]*)"));
    } else if ("zpeedy".equals(languageId)) {
      collectSymbols(symbols, source,
              Pattern.compile("(?im)^\\s*(routine|thing)\\s+([A-Za-z_][A-Za-z0-9_]*)"), 2, 1);
      collectVariables(symbols, source, Pattern.compile(
              "(?im)^\\s*(?:set|receive)\\s+([A-Za-z_][A-Za-z0-9_]*)"));
    } else if ("python".equals(languageId)) {
      collectSymbols(symbols, source,
              Pattern.compile("(?m)^\\s*(def|class)\\s+([A-Za-z_][A-Za-z0-9_]*)"), 2, 1);
      collectVariables(symbols, source, Pattern.compile(
              "(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?::[^=\\n]+)?="));
      collectVariables(symbols, source, Pattern.compile(
              "(?m)\\bfor\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b"));
      Matcher definitions = Pattern.compile("(?m)^\\s*def\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(([^)]*)\\)")
              .matcher(source);
      while (definitions.find()) {
        collectVariables(symbols, definitions.group(1), Pattern.compile(
                "(?:^|,)\\s*([A-Za-z_][A-Za-z0-9_]*)"));
      }
    } else if ("sqarl".equals(languageId)) {
      collectSymbols(symbols, source,
              Pattern.compile("(?im)^\\s*(FUNCTION|PROCEDURE|CLASS|RECORD)\\s+([A-Za-z_][A-Za-z0-9_]*)"), 2, 1);
      collectVariables(symbols, source, Pattern.compile(
              "(?im)^\\s*DECLARE\\s+([A-Za-z_][A-Za-z0-9_]*)"));
    }
    editor.setDocumentAutoCompleteItems(symbols);
  }

  private void collectSymbols(Map<String, CodeEditorViewFX.AutoCompleteItemType> symbols,
                              String source, Pattern pattern, int nameGroup, int kindGroup) {
    Matcher matcher = pattern.matcher(source);
    while (matcher.find()) {
      String kind = matcher.group(kindGroup).toLowerCase(Locale.ROOT);
      String name = matcher.group(nameGroup);
      boolean callable = "function".equals(kind) || "procedure".equals(kind)
              || "routine".equals(kind) || "def".equals(kind);
      symbols.put(name, callable
              ? CodeEditorViewFX.AutoCompleteItemType.Function
              : CodeEditorViewFX.AutoCompleteItemType.Type);
    }
  }

  private void collectVariables(Map<String, CodeEditorViewFX.AutoCompleteItemType> symbols,
                                String source, Pattern pattern) {
    Matcher matcher = pattern.matcher(source);
    while (matcher.find()) {
      symbols.putIfAbsent(matcher.group(1), CodeEditorViewFX.AutoCompleteItemType.Variable);
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
    updateZPEOnlineSaveAvailability();
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
    updateZPEOnlineSaveAvailability();
  }

  private void updateLanguageCommands(LanguageSupport language) {
    boolean runnable = language != null && language.canRun();
    boolean compilable = language != null && language.canCompile();
    boolean yass = language != null && language.isYass();
    boolean debuggable = language != null && language.canDebug();
    boolean transpilable = language != null && language.canTranspile();
    if (scriptMenu != null) scriptMenu.setVisible(runnable || compilable || debuggable || transpilable);
    // Keep ZPE Online browsing and account actions available for every file
    // type; only the save item is restricted to validated YASS documents.
    if (zpeOnlineMenu != null) zpeOnlineMenu.setVisible(true);
    setMenuItemAvailable(runScriptMenuItem, runnable);
    setMenuItemAvailable(stopScriptMenuItem, runnable);
    setMenuItemAvailable(debugScriptMenuItem, debuggable);
    setMenuItemAvailable(compileScriptMenuItem, compilable);
    setMenuItemAvailable(compileNativeMenuItem, language != null && language.canCompileNative());
    setMenuItemAvailable(formatDocumentMenuItem, yass);
    setMenuItemAvailable(toolsMsiSeparator, yass);
    setMenuItemAvailable(layoutBuilderMenuItem, yass);
    setMenuItemAvailable(toolsAiSeparator, yass);
    setMenuItemAvailable(aiBuilderMenuItem, yass);
    setMenuItemAvailable(aiProblemMenuItem, yass);
    setMenuItemAvailable(aiValidateMenuItem, yass);
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
            "zenlang", "ZenLang", Set.of("zenlang"), this::configurePlainText,
            null, null
    ));
    registerLanguage(new LanguageSupport(
            "ywp", "YWP", Set.of("ywp"), this::configurePlainText,
            null, null
    ));
    registerLanguage(new LanguageSupport(
            "md", "Markdown", Set.of("md", "markdown"), this::configurePlainText,
            null, null
    ));
    registerLanguage(new LanguageSupport(
            "txt", "Text", Set.of("txt", "log"), this::configurePlainText,
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
      beginProfilerSession(ProfileKind.PYTHON);
      Process python = consoleOutputTextArea.runProcess(process);
      startPythonProfiler(python);
      python.onExit().thenRun(() -> Platform.runLater(() -> {
        debugBtn.getStyleClass().remove("running");
        statusLabel.setText("Ready");
        endProfilerSession();
      }));
    } catch (IOException exception) {
      endProfilerSession();
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
    LanguageSupport language = languageSupports.get(languageId);
    if (language == null || !language.variablePattern().matcher(token).matches()) return null;
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
    Pattern pattern = Pattern.compile("(?m)^\\s*\\$?" + Pattern.quote(name)
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

  /** A compact built-in implementation of the same contract third-party languages use. */
  private static final class LanguageSupport implements ZIDELanguage {
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

    @Override public String id() { return id; }
    @Override public String label() { return label; }
    @Override public Set<String> extensions() { return extensions; }
    @Override public String defaultExtension() {
      switch (id) {
        case "yass": return "yas";
        case "zpeedy": return "zps";
        case "python": return "py";
        case "sqarl": return "sqarl";
        case "ywp": return "ywp";
        case "md": return "md";
        default: return extensions.iterator().next();
      }
    }
    @Override public String iconStyleClass() { return "language-icon-" + id; }
    @Override public Pattern variablePattern() {
      if ("yass".equals(id)) return Pattern.compile("\\$?[A-Za-z_][A-Za-z0-9_]*");
      if ("zpeedy".equals(id) || "python".equals(id) || "sqarl".equals(id)) {
        return Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
      }
      return Pattern.compile("(?!)");
    }
    @Override public void configure(CodeEditorViewFX editor) { configure.accept(editor); }
    @Override public EditorInfo information(String token) {
      return information == null ? null : information.apply(token);
    }
    @Override public void run(EditorTab tab) { if (runner != null) runner.accept(tab); }
    boolean isYass() { return "yass".equals(id); }
    @Override public boolean canRun() { return isYass() || runner != null; }
    @Override public boolean canCompile() { return isYass() || "zpeedy".equals(id) || "sqarl".equals(id); }
    @Override public boolean canDebug() { return isYass() || "python".equals(id); }
    @Override public boolean canCompileNative() { return isYass(); }
    @Override public boolean canTranspile() { return isYass() || "zpeedy".equals(id); }
  }

  private boolean confirmClose(String tabTitle) {
    return confirmInWindow("Close Tab", "Close \"" + tabTitle + "\"?",
            "You have unsaved content. Are you sure you want to close this tab?", "Close");
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

    MenuItem closeOthers = new MenuItem("Close other tabs");
    closeOthers.setOnAction(e -> closeTabsExcept(tab));
    MenuItem closeLeft = new MenuItem("Close tabs to the left");
    closeLeft.setOnAction(e -> closeTabsSide(tab, true));
    MenuItem closeRight = new MenuItem("Close tabs to the right");
    closeRight.setOnAction(e -> closeTabsSide(tab, false));
    MenuItem closeSaved = new MenuItem("Close saved tabs");
    closeSaved.setOnAction(e -> closeSavedTabs());
    MenuItem rename = new MenuItem("Rename");
    rename.setOnAction(e -> { if (tab.getPath() != null) renameProjectFile(new File(tab.getPath())); });
    MenuItem reveal = new MenuItem(revealLabel());
    reveal.setOnAction(e -> revealInFileManager(tab.getPath()));
    ContextMenu tabMenu = new ContextMenu(closeOthers, closeLeft, closeRight, closeSaved, new SeparatorMenuItem(), rename, reveal);
    tabMenu.getStyleClass().add("glass-context-menu");
    header.setOnContextMenuRequested(e -> tabMenu.show(header, e.getScreenX(), e.getScreenY()));


    return tab;
  }

  private void closeTabsExcept(Tab keep) { for (Tab t : new ArrayList<>(editorTabs.getTabs())) if (t != keep) closeTab(t); }
  private void closeTabsSide(Tab anchor, boolean left) { int i=editorTabs.getTabs().indexOf(anchor); for (Tab t : new ArrayList<>(editorTabs.getTabs())) { int n=editorTabs.getTabs().indexOf(t); if ((left&&n<i)||(!left&&n>i)) closeTab(t); } }
  private void closeSavedTabs() { for (Tab t : new ArrayList<>(editorTabs.getTabs())) if (t instanceof EditorTab et && !et.hasChanges()) closeTab(t); }
  private void closeTab(Tab t) { if (t instanceof EditorTab et && et.hasChanges() && !confirmClose(et.getDisplayTitle())) return; if (t instanceof EditorTab et) et.dispose(); editorTabs.getTabs().remove(t); }
  private String revealLabel() { return HelperFunctions.isMac() ? "Reveal in Finder" : HelperFunctions.isWindows() ? "Reveal in File Explorer" : "Reveal in File Manager"; }
  private void revealInFileManager(String path) { if (path == null) return; try { File file = new File(path); if (HelperFunctions.isMac()) new ProcessBuilder("open", "-R", file.getAbsolutePath()).start(); else if (HelperFunctions.isWindows()) new ProcessBuilder("explorer", "/select,", file.getAbsolutePath()).start(); else new ProcessBuilder("xdg-open", file.getParent()).start(); } catch (IOException exception) { showError("File manager", exception.getMessage()); } }

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
  private final java.util.List<ImageView> panelIconImages = new java.util.ArrayList<>();

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
    String symbol = tooltip.startsWith("Step over") ? "↱"
            : tooltip.startsWith("Continue") ? "▶"
            : tooltip.startsWith("Stop") ? "■" : "⌫";
    Button symbolButton = new Button(symbol);
    symbolButton.setTooltip(new Tooltip(tooltip));
    symbolButton.setOnAction(e -> action.run());
    symbolButton.getStyleClass().addAll("panel-icon-button", "panel-action-symbol");
    symbolButton.setFocusTraversable(false);
    symbolButton.setMinSize(28, 28);
    symbolButton.setPrefSize(28, 28);
    symbolButton.setMaxSize(28, 28);
    return symbolButton;
    /* Existing image assets retained below as backups.
    ImageView icon = new ImageView(new Image(
            Objects.requireNonNull(getClass().getResourceAsStream(iconPath))
    ));

    icon.setFitWidth(14);   // tweak: 12–16 is sweet spot
    icon.setFitHeight(14);
    icon.setPreserveRatio(true);
    icon.setSmooth(true);
    icon.getStyleClass().add("panel-icon");
    panelIconImages.add(icon);

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

    return btn; */
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
  private enum ProfileKind { ZPE, PYTHON }

  /** UI-neutral sample shared by ZPE's debugger and external runtimes. */
  private record ProfilerSample(
          long elapsedNanoseconds,
          long primaryMemory,
          long secondaryMemory,
          long committedMemory,
          double processCpuLoad,
          int threadCount,
          String location
  ) {}

  private ProfileKind profileKind = ProfileKind.ZPE;
  private final ConcurrentLinkedQueue<ProfilerSample> pendingProfileSamples =
          new ConcurrentLinkedQueue<>();
  private final AtomicBoolean profileUpdateScheduled = new AtomicBoolean(false);
  private final List<ProfilerSample> profilerSamples = new ArrayList<>();
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
    beginProfilerSession(ProfileKind.ZPE);
  }

  private void beginProfilerSession(ProfileKind kind) {
    profileKind = kind;
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
      if (memorySeries != null) {
        memorySeries.setName(kind == ProfileKind.PYTHON ? "Resident memory" : "Heap used");
      }
      if (nonHeapMemorySeries != null) {
        nonHeapMemorySeries.setName("Non-heap used");
      }
      profilerChart.setLegendVisible(kind != ProfileKind.PYTHON);
      profilerThreadLabel.setVisible(kind != ProfileKind.PYTHON);
      profilerThreadLabel.setManaged(kind != ProfileKind.PYTHON);
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
      profilerMemoryLabel.setText(profileKind == ProfileKind.PYTHON ? "Resident memory —" : "Heap —");
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

    pendingProfileSamples.offer(new ProfilerSample(
            sample.elapsedNanoseconds,
            sample.heapUsed,
            sample.nonHeapUsed,
            sample.heapCommitted,
            sample.processCpuLoad,
            sample.threadCount,
            sample.functionName
    ));
    scheduleProfileUpdate();
  }

  /** Samples an external Python process without requiring a Python package. */
  private void startPythonProfiler(Process process) {
    Thread sampler = new Thread(() -> {
      long started = System.nanoTime();
      long previousWall = started;
      long previousCpu = process.info().totalCpuDuration()
              .map(java.time.Duration::toNanos).orElse(-1L);

      while (profilingActive && process.isAlive()) {
        long now = System.nanoTime();
        long currentCpu = process.info().totalCpuDuration()
                .map(java.time.Duration::toNanos).orElse(-1L);
        double cpuLoad = -1.0;
        if (previousCpu >= 0 && currentCpu >= previousCpu && now > previousWall) {
          cpuLoad = Math.min(1.0, (double) (currentCpu - previousCpu) / (now - previousWall));
        }

        if (cpuLoad < 0.0) cpuLoad = readProcessCpuLoad(process.pid());
        long residentBytes = readResidentMemory(process.pid());
        pendingProfileSamples.offer(new ProfilerSample(
                now - started, Math.max(0L, residentBytes), 0L,
                Math.max(0L, residentBytes), cpuLoad, 1, "Python"
        ));
        scheduleProfileUpdate();
        previousWall = now;
        previousCpu = currentCpu;

        try {
          Thread.sleep(250);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }, "zide-python-profiler");
    sampler.setDaemon(true);
    sampler.start();
  }

  private long readResidentMemory(long pid) {
    ProcessBuilder command = HelperFunctions.isWindows()
            ? new ProcessBuilder("powershell", "-NoProfile", "-Command",
            "(Get-Process -Id " + pid + ").WorkingSet64")
            : new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(pid));
    command.redirectErrorStream(true);
    try {
      Process probe = command.start();
      String value = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (!probe.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) || value.isEmpty()) return 0L;
      long bytes = Long.parseLong(value.split("\\s+")[0]);
      return HelperFunctions.isWindows() ? bytes : bytes * 1024L;
    } catch (IOException | InterruptedException | NumberFormatException exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      return 0L;
    }
  }

  private double readProcessCpuLoad(long pid) {
    try {
      Process probe = new ProcessBuilder("ps", "-o", "%cpu=", "-p", Long.toString(pid)).start();
      String value = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      probe.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
      return value.isEmpty() ? -1.0 : Math.max(0.0, Math.min(1.0, Double.parseDouble(value) / 100.0));
    } catch (IOException | InterruptedException | NumberFormatException exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      return -1.0;
    }
  }

  private void scheduleProfileUpdate() {
    if (profileUpdateScheduled.compareAndSet(false, true)) {
      Platform.runLater(this::drainProfileSamples);
    }
  }

  private void drainProfileSamples() {
    ProfilerSample sample;
    ProfilerSample latest = null;

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

  private void addProfilePoint(ProfilerSample sample) {
    if (memorySeries == null || nonHeapMemorySeries == null || cpuSeries == null) {
      return;
    }

    double elapsedMilliseconds = sample.elapsedNanoseconds / 1_000_000.0;
    double heapMegabytes = sample.primaryMemory / (1024.0 * 1024.0);
    double nonHeapMegabytes = sample.secondaryMemory / (1024.0 * 1024.0);

    profilerSamples.add(sample);
    memorySeries.getData().add(
            new XYChart.Data<>(elapsedMilliseconds, heapMegabytes)
    );
    if (profileKind != ProfileKind.PYTHON) {
      nonHeapMemorySeries.getData().add(
              new XYChart.Data<>(elapsedMilliseconds, nonHeapMegabytes)
      );
    }
    if (sample.processCpuLoad >= 0.0) {
      cpuSeries.getData().add(
              new XYChart.Data<>(elapsedMilliseconds, sample.processCpuLoad * 100.0)
      );
    }

    int excess = memorySeries.getData().size() - MAX_PROFILE_POINTS;
    if (excess > 0) {
      memorySeries.getData().remove(0, excess);
      if (nonHeapMemorySeries.getData().size() >= excess) {
        nonHeapMemorySeries.getData().remove(0, excess);
      }
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

    ProfilerSample sample =
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

  private ProfilerSample findNearestProfileSample(double elapsedNanoseconds) {
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

    ProfilerSample before = profilerSamples.get(low - 1);
    ProfilerSample after = profilerSamples.get(low);
    return elapsedNanoseconds - before.elapsedNanoseconds
            <= after.elapsedNanoseconds - elapsedNanoseconds
            ? before
            : after;
  }

  private String profileInspectionText(ProfilerSample sample) {
    String functionName = sample.location == null || sample.location.isEmpty()
            ? "—"
            : sample.location;
    double heapMegabytes = sample.primaryMemory / (1024.0 * 1024.0);
    String cpu = sample.processCpuLoad < 0.0
            ? "warming up"
            : String.format(Locale.ROOT, "%.1f%%", sample.processCpuLoad * 100.0);

    String locationLabel = profileKind == ProfileKind.PYTHON ? "Runtime: " : "Function: ";
    String memoryLabel = profileKind == ProfileKind.PYTHON ? "Resident" : "Heap";
    String details = String.format(Locale.ROOT, "\n%s %.1f MB   CPU %s",
            memoryLabel, heapMegabytes, cpu);
    if (profileKind != ProfileKind.PYTHON) {
      details += "   Threads " + sample.threadCount;
    }
    return locationLabel + functionName
            + "\n" + formatProfilerTime(sample.elapsedNanoseconds)
            + details;
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

  private void updateProfilerLabels(ProfilerSample sample) {
    double heapMegabytes = sample.primaryMemory / (1024.0 * 1024.0);
    double heapCommittedMegabytes = sample.committedMemory / (1024.0 * 1024.0);
    double nonHeapMegabytes = sample.secondaryMemory / (1024.0 * 1024.0);

    profilerMemoryLabel.setText(profileKind == ProfileKind.PYTHON
            ? String.format(Locale.ROOT, "Resident memory %.1f MB", heapMegabytes)
            : String.format(
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
    terminalView = wrapWithHeader("Terminal", systemTerminal,
            panelIconButton("/files/bin.png", "Clear screen", systemTerminal::clearScreen));

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
    Label terminalGlyph = new Label("$");
    terminalGlyph.getStyleClass().addAll("bottom-tab-icon", "terminal-tab-icon");
    terminalTab = createBottomSideTab("Terminal", terminalGlyph);
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
    languageMenuBar = new BalfGlassMenuBar();
    languageMenuBar.getStyleClass().add("language-selector-menu");
    languageMenuBar.setDarkMode(darkThemeEnabled);
    languageSelector = languageMenuBar.menuAbove("Text");
    for (LanguageSupport language : languageSupports.values()) {
      languageSelector.createItem(language.label, "", () -> selectLanguage(getCurrentTab(), language));
    }

    centre.setStyle("-fx-font-size: 13px;");

    var bar = new HBox(statusLabel, new Region(), centre, new Region(), languageMenuBar);
    bar.setAlignment(Pos.CENTER_LEFT);
    statusLabel.setAlignment(Pos.CENTER_LEFT);
    centre.setAlignment(Pos.CENTER);
    languageMenuBar.setMinHeight(24);
    languageMenuBar.setPrefHeight(24);
    languageMenuBar.setMaxHeight(24);
    HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
    HBox.setHgrow(bar.getChildren().get(3), Priority.ALWAYS);

    bar.setPadding(new Insets(2, 8, 2, 8));
    bar.getStyleClass().add("status-bar");
    if (HelperFunctions.isMac()) {
      var statusClip = new javafx.scene.shape.Rectangle();
      statusClip.setArcWidth(28);
      statusClip.setArcHeight(28);
      statusClip.widthProperty().bind(bar.widthProperty());
      statusClip.heightProperty().bind(bar.heightProperty());
      bar.setClip(statusClip);
    }
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
    downloadWithPopup(title, owner, url, target, unzipOnComplete, executable, null);
  }

  public void downloadWithPopup(String title, javafx.stage.Window owner, String url, Path target,
                                boolean unzipOnComplete, boolean executable, Runnable onSuccess) {

    DownloadDialog dlg = new DownloadDialog(owner, "Downloading " + title, "Starting…");
    dlg.show();

    Path location = target;

    if(unzipOnComplete){
      try {
        location = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".zip");
      } catch (IOException e) {
        Platform.runLater(() -> showError("Download failed", e.getMessage()));
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
          Platform.runLater(() -> showError("Extraction failed", ex.getMessage()));
        }
      }
      if(executable){
        try {
          FileHelperFunctions.makeExecutable(target);
        } catch (Exception ex) {
          Platform.runLater(() -> showError("Could not make the file executable", ex.getMessage()));
        }
      }
      // Use the file
      System.out.println("Downloaded to " + downloaded);
      if (onSuccess != null) onSuccess.run();
    });

    task.setOnFailed(e -> {
      dlg.close();
      Throwable ex = task.getException();
      showError("Download failed", ex.getMessage());
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
    panelIconImages.forEach(this::invertImageView);
  }

  private void invertImageView(ImageView imageView) {
    // Some controls use text or CSS graphics rather than bitmap icons.
    if (imageView == null || imageView.getImage() == null) return;

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
      Node first = box.getChildren().getFirst();

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
