package jamiebalfour.zide.editor;

import jamiebalfour.FileHelperFunctions;
import jamiebalfour.HelperFunctions;
import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.balflaf_fx.BalfGlassMenuBar;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfPanel;
import jamiebalfour.ui.components.BalfScrollbarPane;
import jamiebalfour.ui.components.BalfSearchBox;
import jamiebalfour.zide.ZIDEHelperFunctions;
import jamiebalfour.zide.core.ZIDE;
import jamiebalfour.zpe.core.*;
import jamiebalfour.zpe.core.exceptions.CompileException;
import jamiebalfour.zpe.gui.editor.ConsoleOutputTextArea;
import jamiebalfour.zpe.core.interfaces.ZPEType;
import jamiebalfour.zpe.core.types.ZPEMap;
import javafx.animation.*;
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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
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
  Button buildBtn;
  Button debugBtn;
  Button stopExecutionBtn;
  Button stepOverButton;
  Button continueButton;
  Separator debugSeparator;
  BalfGlassMenuBar.GlassCheckMenuItem darkThemeMenuItem;
  BalfGlassMenuBar.GlassCheckMenuItem wordWrapMenuItem;
  private TableView<VarRow> varTable;
  private ObservableList<VarRow> varRows;
  private VBox variablesPane;
  private ZPEDebugger.BreakPoint currentBreakpoint;
  MenuItem runProject;
  MenuItem debugProject;
  MenuItem stopExecution = new MenuItem("Stop Execution");
  private File currentProjectRoot;
  File projectDir;
  final Label statusLabel = new Label("Ready");
  final static String INSTALL_PATH = HelperFunctions.getAppDataDirectory("jamiebalfour/zide", System.getProperty("user.home") + "/jb/zide").getAbsolutePath() + "/"; //;
  Properties MAIN_PROPERTIES;
  boolean USE_WORD_WRAP = false;
  Node loadFromOnline;
  Node saveToOnline;



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

  @Override
  public void start(Stage stage) {

    stage.getIcons().add(new Image(

            getClass().getResourceAsStream("/files/ZIDE mini macos.png")

    ));
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

    editorTabs = new TabPane();

    var root = new BorderPane();
    root.getStyleClass().add("app-root");

    EventHandler<ActionEvent> aboutHandler = e -> ZIDEAboutWindow.show(_stage);

    // Top: menu + toolbar
    var top = new VBox(new BalfTitleBar(stage, "ZIDE", aboutHandler), buildMenuBar(), buildToolBar());
    root.setTop(top);


    BalfTitleBar.addWindowResizing(stage, root);

    projectDir = new File(System.getProperty("user.home") + "/Documents/YASS Projects/");

    if(!projectDir.exists()) {
      projectDir.mkdirs();
    }

    // Left: project tree
    Node projectTree = buildProjectTree(projectDir);
    currentProjectRoot = projectDir;
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

    root.setCenter(verticalSplit);

    var scene = new Scene(root, 1280, 800);
    //scene.setFill(Color.TRANSPARENT);
    scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/zide.css")).toExternalForm());

    stage.setTitle("ZIDE");
    stage.setScene(scene);
    stage.show();

    boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

    if (isMac) {
      root.getStyleClass().add("mac-window");
    }
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
    bar.getStyleClass().add("main-menu-bar");

    var file = bar.menu("File");
    file.createItem("New Project", "⇧⌘N", this::newProject, true);
    file.createItem("New File", "⌘N", this::newFile, true);
    file.createItem("Open project folder", "⌘O", this::openProjectFolder, true);
    file.separator();
    file.createItem("Save", "⌘S", this::saveCurrentFile, true);
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

    edit.createItem("Format document", "", this::beautifyCurrentDocument);

    var viewMenu = bar.menu("View");

    darkThemeMenuItem = viewMenu.checkItem("Dark theme",
            false,
            selected -> {
              BalfLafManager.getInstance().toggleDarkMode(selected);
              invertImages();

              var scene = _stage.getScene();
              scene.getRoot().pseudoClassStateChanged(
                      javafx.css.PseudoClass.getPseudoClass("dark"),
                      selected
              );

              for (Tab t : editorTabs.getTabs()) {
                EditorTab tab = (EditorTab) t;

                if (selected) {
                  tab.switchOnDarkMode();
                } else {
                  tab.switchOffDarkMode();
                }

                int scrollPosition = tab.getScrollPane().getVerticalScrollBar().getValue();

                SwingUtilities.invokeLater(() ->
                        tab.getScrollPane().getVerticalScrollBar().setValue(scrollPosition)
                );
              }
            });

    wordWrapMenuItem = viewMenu.checkItem("Word-wrap", USE_WORD_WRAP, selected -> {

      USE_WORD_WRAP = selected;

      MAIN_PROPERTIES.setProperty("USE_WORD_WRAP", selected.toString());

      saveProps();

    });


    var script = bar.menu("Script");

    script.createItem("Run", "F5", this::runCode);
    script.createItem("Debug", "⇧⌘R", this::debug);
    script.separator();
    script.createItem("Stop Execution", "⇧⌘S", () -> consoleOutputTextArea.destroyCurrentProcess());
    script.separator();
    script.createItem("Compile", "", this::compileProject);
    script.createItem("Compile Native", "", this::compileNative);


    var git = bar.menu("Git");

    git.createItem("Clone", "", this::cloneRepo);
    git.separator();
    git.createItem("Commit", "", null);
    git.createItem("Push", "", null);


    var zpeOnline = bar.menu("ZPE Online");

    zpeOnline.createItem("Login to ZPE Online", "", this::loginToZPEOnline);
    loadFromOnline = zpeOnline.createItem("Load from ZPE Online", "", () -> {});
    saveToOnline = zpeOnline.createItem("Save to ZPE Online", "", () -> {});


    loadFromOnline.setDisable(true);
    saveToOnline.setDisable(true);

    var help = bar.menu("Help");

    help.createItem("About", "", () -> ZIDEAboutWindow.show(_stage));
    help.separator();
    help.createItem("Download ZPE Runtime Environment", "", this::downloadZPERuntime);
    help.createItem("Download ZPE Native", "", this::downloadZPENative);

    return bar;
  }



  private void openProjectFolder() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Open project folder");

    File chosen = chooser.showDialog(_stage);
    if (chosen != null) {
      buildProjectTree(chosen);
      currentProjectRoot = chosen;
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


  private String askForRepoUrl() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Clone Repository");
    dialog.setHeaderText("Clone from GitHub");
    dialog.setContentText("Repository URL:");

    Optional<String> result = dialog.showAndWait();

    return result.orElse(null);
  }

  private String getFolderName(String repoUrl) {
    // remove trailing slash if present
    repoUrl = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;

    String[] parts = repoUrl.split("/");

    if (parts.length < 2) return "repo";

    // second last part = owner/org
    return parts[parts.length - 2];
  }

  private void cloneRepo() {
    try {
      // Ask for repo URL
      String repoUrl = askForRepoUrl();

      if (repoUrl == null || repoUrl.trim().isEmpty()) {
        return;
      }

      repoUrl = repoUrl.trim();

      // Extract repo name
      String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1)
              .replace(".git", "");

      // Build destination path
      File baseDir = new File(System.getProperty("user.home"),
              "Documents/YASS Projects");

      if (!baseDir.exists()) {
        baseDir.mkdirs();
      }

      String folderName = getFolderName(repoUrl);
      File destination = new File(baseDir, folderName);

      if(!new File(destination.getAbsolutePath()).exists()) {
        //new File(destination.getAbsolutePath()).mkdirs();
      }

      // Clone
      Git.cloneRepository()
              .setURI(repoUrl)
              .setDirectory(destination)
              .setCredentialsProvider(new UsernamePasswordCredentialsProvider("", ""))
              .call()
              .close();


      // Optional: open in your IDE
      buildProjectTree(currentProjectRoot);

    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(null,
              "Clone failed:\n" + e.getMessage(),
              "Error",
              JOptionPane.ERROR_MESSAGE);
    }
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

  private void compileProject() {
    if (getCurrentTab() == null) {
      showError("Error compiling project", "No project open");
      return;
    }

    File outputLocation = chooseOutputFile(
            _stage,
            null,
            new FileChooser.ExtensionFilter("YASS Executable", "*.yex")
    );

    if (outputLocation == null) return;

    try {
      ZPEKit.compile(
              getCurrentTab().getEditor().getText(),
              outputLocation.getAbsolutePath(),
              "",
              "",
              true
      );
    } catch (IOException | CompileException ex) {
      showError("Error compiling project", ex.getMessage());
    }
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

    if(ZPEKit.compileNativeBinary(getCurrentTab().getEditor().getText(),"", outputLocation.getAbsolutePath(),true)){
      Alert a = new Alert(Alert.AlertType.INFORMATION, "Successfully compiled native binary");
      a.setHeaderText(null);
      a.showAndWait();
    } else{
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to compile native binary");
      a.setHeaderText(null);
      a.showAndWait();
    }
  }

  private void loginToZPEOnline() {
    ZIDELoginWindow.LoginResult result = ZIDELoginWindow.show(_stage, " ZPE Online");
    if(result == null) return;
    String username = result.getUsername();
    String password = result.getPassword();

    try {
      ZPEMap res = ZPEOnline.loginToZPEOnline(username, password);

      if (Integer.parseInt(res.get("result").toString()) == -1) {
        showError("Error logging in to ZPE Online", res.get("message").toString());
        return;
      }

      new Alert(Alert.AlertType.INFORMATION, "Successfully logged in to ZPE Online").showAndWait();

    } catch (Exception ex) {
      showError("Error logging in to ZPE Online", ex.getMessage());
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

  private ObservableList<ProblemRow> problemsRows = FXCollections.observableArrayList();

  private boolean verifyCode(){
    if(getCurrentTab() == null) return false;
    String code = getCurrentTab().getEditor().getText();

    List<YASSDiagnostic> result = ZPEKit.analyseCode(code);

    if(result.isEmpty()) {
      return true;
    } else{
      problemsRows.clear();

      for (YASSDiagnostic d : result) {

        problemsRows.add(new ProblemRow(
                d.getSeverity().toString(),
                d.getLine(),
                d.getMessage()
        ));

      }
      showProblemsPane();
      return false;
    }

  }

  public void beautifyCurrentDocument() {

      if(getCurrentTab() == null) return;
      ZIDESyntaxEditor doc = getCurrentTab().getEditor();
      String code = doc.getText();

      String formatted = ZPEKit.beautifyCode(code);

      doc.setText(formatted);
      doc.rehighlightAll();

      doc.setCaretPosition(0);


  }

  private void runCode(){

    if (!getZPE()) return;

    if(!verifyCode()) return;

    try {
      if(getCurrentTab() == null) return;
      Path tempPath = Files.createTempFile(ZPEHelperFunctions.generateRandomWord(12), ".tmp");
      EditorTab tab = getCurrentTab();
      runBtn.getStyleClass().add("running");

      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), tab.getEditor().getText(), false);
      consoleOutputTextArea.addProcessFinishedListener(() -> {
        Platform.runLater(() -> {
          System.out.println("Finished");
          statusLabel.setText("Ready");
        });
      });
      statusLabel.setText("Executing code");
      consoleOutputTextArea.runAsProcess(tempPath, false, false, "");

      //stopExecution.setDisable(false);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  private String prepareDebugSourceWithBreakpoints(CodeEditorView mainSyntax, String source) {
    String[] lines = source.split("\\R", -1);
    StringBuilder out = new StringBuilder();


    for (int line = 1; line <= lines.length; line++) {
      String currentLine = lines[line - 1];

      if (mainSyntax.hasSpecialLine(line)) {
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
      debugBtn.getStyleClass().add("running");
      invertImageView(getToolbarButtonIcon(debugBtn));

      stopExecutionBtn.setVisible(true);
      stepOverButton.setVisible(true);
      continueButton.setVisible(true);
      debugSeparator.setVisible(true);


      FileHelperFunctions.writeFile(tempPath.toAbsolutePath().toString(), prepareDebugSourceWithBreakpoints(tab.getEditor(), tab.getEditor().getText()), false);
      consoleOutputTextArea.runAsProcess(tempPath, true, false, "");
      consoleOutputTextArea.addProcessFinishedListener(() -> {
        System.out.println("Finished");
        invertImageView(getToolbarButtonIcon(debugBtn));
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    ZPEDebugger.addBreakPointReachedListener((b, varData) -> {
      currentBreakpoint = b;
      showVariablesPane();
      setVariables(varData);
    });

    debugCode();
  }

  private ToolBar buildToolBar() {
    runBtn = createExpandableToolbarButton("Run", "/files/controller-play.png", this::runCode);
    runBtn.getStyleClass().add("run");

    buildBtn = createExpandableToolbarButton("Build", "/files/tools.png", this::debug);
    buildBtn.setOnAction(e -> {
      if(getCurrentTab() == null) return;
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

    var search = new TextField();
    search.setPromptText("Search");
    search.getStyleClass().add("search-field");
    search.setMaxWidth(280);

    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);



    var toolbar = new ToolBar(runBtn, buildBtn, debugBtn, sep1, stopExecutionBtn, stepOverButton, continueButton, debugSeparator, new Label(" "), spacer, search);
    toolbar.getStyleClass().add("app-toolbar");
    return toolbar;
  }

  private TreeView<File> projectTree;

  private void continueDebug(){
    if(currentBreakpoint == null){
      return;
    }
    currentBreakpoint.resume();
  }

  private void stepOver(){
    if(currentBreakpoint == null){
      return;
    }
    currentBreakpoint.stepOver();
  }

  private void stopExecution(){
    if(currentBreakpoint == null){
      return;
    }
    currentBreakpoint.stopExecution();
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
      });

      projectTree.setOnMouseClicked(e -> {
        if (e.getClickCount() == 2) {
          var item = projectTree.getSelectionModel().getSelectedItem();
          if (item != null && item.isLeaf()) {
            openTab(item.getValue().getName(), item.getValue().getPath());
          }
        }
      });

      projectTree.setOnDragOver(event -> {
        if (event.getDragboard().hasFiles()) {
          event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
      });

      projectTree.setOnDragDropped(event -> {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasFiles()) {

          TreeItem<File> targetItem = projectTree.getSelectionModel().getSelectedItem();

          if (targetItem != null) {

            File target = targetItem.getValue();

            // If it's a file, use its parent folder
            File targetDir = target.isDirectory() ? target : target.getParentFile();

            for (File file : db.getFiles()) {
              copyFileToDirectory(file, targetDir);
            }

            refreshTree();
            success = true;
          }
        }

        event.setDropCompleted(success);
        event.consume();
      });
    }



    projectTree.setRoot(loadDirectory(projectDir));
    projectTree.getRoot().setExpanded(true);

    return projectTree;
  }

  private void copyFileToDirectory(File source, File targetDir) {
    try {
      File dest = new File(targetDir, source.getName());

      Files.copy(
              source.toPath(),
              dest.toPath(),
              StandardCopyOption.REPLACE_EXISTING
      );

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void refreshTree() {
    buildProjectTree(projectDir);
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
    if(new File(file).isDirectory()) {
      return;
    }
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

        ZIDESyntaxEditor mainSyntax = new ZIDESyntaxEditor(this, USE_WORD_WRAP);

        mainSyntax.setFontSize(14);

        mainSyntax.addLineNumberClickListener(lineNumber -> {
          if(mainSyntax.hasSpecialLine(lineNumber)){
            mainSyntax.removeSpecialLine(lineNumber);
          } else{
            mainSyntax.addSpecialLine(lineNumber);
          }

        });

        Font jbMono = loadAndRegister("/files/JetBrainsMono-Regular.ttf");
        Font editorFont = jbMono.deriveFont(Font.PLAIN, 14);
        mainSyntax.setFont(editorFont);

        String lang = "txt";

        if(file != null && file.endsWith(".yas")) {
          lang = "yass";
        }

        setLanguage(lang, mainSyntax);





        Color normal   = new Color(35, 35, 38);      // #232326
        Color comment  = new Color(72, 145, 85);     // #489155
        Color quote    = new Color(220, 95, 60);     // #DC5F3C
        Color keyword  = new Color(138, 43, 226);    // #8A2BE2
        Color function = new Color(0, 122, 255);     // #007AFF
        Color heredoc  = new Color(235, 120, 55);    // #EB7837
        Color bool     = new Color(200, 55, 135);    // #C83787
        Color var      = new Color(210, 60, 110);    // #D23C6E
        Color doc      = new Color(34, 150, 120);    // #229678
        Color type     = new Color(0, 150, 170);     // #0096AA
        Color special  = new Color(180, 90, 20);     // #B45A14

        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, normal);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Comment, comment);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote, quote);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword, keyword);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, function);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Heredoc, heredoc);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool, bool);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Var, var);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Doc, doc);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Type, type);
        mainSyntax.setAttributeColor(CodeEditorView.ATTR_TYPE.Special, special);

        mainSyntax.setAttributeFontStyle(CodeEditorView.ATTR_TYPE.Keyword, Font.PLAIN);


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
        if(!USE_WORD_WRAP){
          scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        } else{
          scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }


        mainSyntax.repaint();
        mainSyntax.requestFocus();


        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> mainSyntax.hideTooltip());

        mainSyntax.setWrapper(scrollPane);

        wrapper.add(scrollPane, BorderLayout.CENTER);


        // Load file (still on EDT)
        if (file != null) {
          mainSyntax.setText(FileHelperFunctions.readFileAsString(file));
        }

        mainSyntax.loadAllCitizens();

        mainSyntax.setCaretPosition(0);

        // Track “has content” safely
        hasNonBlankContent.set(!mainSyntax.getText().isBlank());

        mainSyntax.getDocument().addDocumentListener(new DocumentListener() {
          private void update() {
            // always EDT already, but keep it simple
            hasNonBlankContent.set(!mainSyntax.getText().isBlank());
            mainSyntax.loadAllCitizens();
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

  private void setLanguage(String lang, ZIDESyntaxEditor mainSyntax) {
    if(lang.equals("yass")) {
      String[] keywords = ZPEKit.getBuiltInFunctions();

      for (String s : keywords) {
        mainSyntax.setTooltipInfo(s, name -> ZIDESyntaxEditor.getBuiltInFunctionTooltip(s, mainSyntax));
      }

      mainSyntax.setKeywords(ZPEKit.getKeywordSet(mainSyntax));
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

  private Tab createEditorTab(String title, ZIDESyntaxEditor syntax, BalfScrollbarPane scrollPane, String path, Node content, Supplier<Boolean> hasContent) {
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

    if(darkThemeMenuItem.isSelected()){
      tab.switchOnDarkMode();
    }



    closeBtn.setOnAction(e -> {
      if(tab.hasChanges()){
        if (hasContent.get() && !confirmClose(title)) {
          return;
        }
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

  private ToggleButton consoleTab;
  private ToggleButton problemsTab;
  private ToggleButton variablesTab;
  private ToggleButton profileTab;

  private StackPane bottomContentStack;
  private Node consoleView;
  private Node problemsView;
  private Node variablesView;
  private Node profileView;

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
  private XYChart.Series<Number, Number> memorySeries;

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
    profilerChart.setLegendVisible(false);
    profilerChart.getStyleClass().add("profiler-chart");

    memorySeries = new XYChart.Series<>();
    memorySeries.setName("Memory");
    profilerChart.getData().add(memorySeries);

    VBox content = new VBox(profilerChart);
    VBox.setVgrow(profilerChart, Priority.ALWAYS);
    content.getStyleClass().add("profiler-pane");
    return content;
  }

  private void clearProfiler() {
    Platform.runLater(() -> {
      if (memorySeries != null) {
        memorySeries.getData().clear();
      }
    });
  }

  private Node buildconsole() {

    // --- console view ---
    SwingNode consoleNode = new SwingNode();

    consoleOutputTextArea = new ConsoleOutputTextArea("", Color.WHITE);


    consoleScrollbar = new BalfScrollbarPane(consoleOutputTextArea);
    consoleScrollbar.setLightColour(Color.WHITE);
    consoleScrollbar.setDarkColour(Color.BLACK);
    consoleScrollbar.setBorder(BorderFactory.createEmptyBorder());
    consoleScrollbar.getVerticalScrollBar().setUnitIncrement(16);

    consoleNode.setContent(consoleScrollbar);

    VBox consoleContainer = new VBox(consoleNode);
    VBox.setVgrow(consoleNode, Priority.ALWAYS);

    consoleView =  wrapWithHeader("Console", consoleContainer);

    // --- Problems view ---
    problemsView = wrapWithHeader("Problems", buildProblemsPane());

    // --- Variables view ---
    variablesView = wrapWithHeader("Variable Watch", buildVariablesPane(),
            panelIconButton("/files/stepover.png", "Step over", this::stepOver),
            panelIconButton("/files/continue.png", "Continue debugging", this::continueDebug),
            panelIconButton("/files/stop.png", "Stop execution", this::stopExecution)
    );

    profileView = wrapWithHeader("Profiling", buildProfilerPane(), panelIconButton("/files/bin.png", "Clear profiler", this::clearProfiler));

    // --- Content stack ---
    bottomContentStack = new StackPane(consoleView, problemsView, variablesView, profileView);
    bottomContentStack.getStyleClass().add("bottom-content-stack");

    problemsView.setVisible(false);
    problemsView.setManaged(false);

    variablesView.setVisible(false);
    variablesView.setManaged(false);

    profileView.setVisible(false);
    profileView.setManaged(false);

    // --- Vertical tabs ---
    consoleTab = createBottomSideTab("Console", icon("/files/console.png"));
    problemsTab = createBottomSideTab("Problems", icon("/files/warning.png"));
    variablesTab = createBottomSideTab("Variable Watch", icon("/files/watch.png"));
    profileTab = createBottomSideTab("Profiling", icon("/files/profiling.png"));

    ToggleGroup group = new ToggleGroup();
    consoleTab.setToggleGroup(group);
    problemsTab.setToggleGroup(group);
    variablesTab.setToggleGroup(group);
    profileTab.setToggleGroup(group);


    consoleTab.setSelected(true);

    consoleTab.setOnAction(e -> showBottomPanel(consoleView));
    problemsTab.setOnAction(e -> showBottomPanel(problemsView));
    variablesTab.setOnAction(e -> showBottomPanel(variablesView));
    profileTab.setOnAction(e -> showBottomPanel(profileView));

    VBox tabs = new VBox(consoleTab, problemsTab, variablesTab, profileTab);
    tabs.getStyleClass().add("bottom-side-tabs");
    tabs.setFillWidth(true);

    // --- Main bottom pane ---
    HBox bottom = new HBox(tabs, bottomContentStack);
    HBox.setHgrow(bottomContentStack, Priority.ALWAYS);

    bottom.getStyleClass().add("bottom-panel");
    bottom.setMinHeight(180);


    consoleOutputTextArea.addProcessFinishedListener(() ->
            Platform.runLater(() -> {
              runBtn.getStyleClass().remove("running");
              debugBtn.getStyleClass().remove("running");
            })
    );

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

    TableColumn<ProblemRow, String> msgCol = new TableColumn<>("Message");
    msgCol.setCellValueFactory(v -> v.getValue().messageProperty());
    msgCol.setPrefWidth(400);

    typeCol.setMinWidth(80);
    typeCol.setMaxWidth(80);

    lineCol.setMinWidth(60);
    lineCol.setMaxWidth(60);

    msgCol.setPrefWidth(1000); // big so it dominates

    problemsTable.getColumns().setAll(typeCol, lineCol, msgCol);

    problemsTable.setRowFactory(tv -> {
      TableRow<ProblemRow> row = new TableRow<>();

      row.setOnMouseClicked(e -> {
        if (e.getClickCount() == 2 && !row.isEmpty()) {
          int line = Integer.parseInt(row.getItem().lineProperty().get());
          getCurrentTab().getEditor().goToLine(line);
        }
      });

      return row;
    });

    problemsTable.setRowFactory(tv -> new TableRow<>() {
      @Override
      protected void updateItem(ProblemRow item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
          setStyle("");
        } else {
          if ("ERROR".equals(item.getSeverity())) {
            setStyle("-fx-background-color: #ffecec;");
          } else if ("WARNING".equals(item.getSeverity())) {
            setStyle("-fx-background-color: #fff6e5;");
          } else {
            setStyle("");
          }
        }
      }
    });

    return problemsTable;
  }

  public class ProblemRow {

    private final SimpleStringProperty severity;
    private final SimpleStringProperty line;
    private final SimpleStringProperty message;

    public ProblemRow(String severity, int line, String message) {
      this.severity = new SimpleStringProperty(severity);
      this.line = new SimpleStringProperty(String.valueOf(line));
      this.message = new SimpleStringProperty(message);
    }

    public StringProperty severityProperty() { return severity; }
    public StringProperty lineProperty() { return line; }
    public StringProperty messageProperty() { return message; }
    public String getSeverity() {
      return severity.get();
    }
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

  private void showProblemsPane() {
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
    Platform.runLater(() -> varRows.clear());
  }

  private Node buildStatusBar() {
    var centre = new Label("ZIDE " + ZIDE.getMajorVersion() + "." + ZIDE.getMinorVersion() + " build " + ZIDE.getBuildNumber() + " © Jamie Balfour 2024 - 2026.");
    rightFooterLabel = new Label("Text");

    centre.setStyle("-fx-font-size: 13px;");

    rightFooterLabel.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
      ZIDESyntaxEditor c = (ZIDESyntaxEditor) getCurrentTab().getEditor();
      setLanguage("yass", c);
    });

    var bar = new HBox(statusLabel, new Region(), centre, new Region(), rightFooterLabel);
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
