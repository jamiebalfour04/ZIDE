package jamiebalfour.zide;

import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.zpe.core.ZPEInstance;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;
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
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.swing.*;
import java.awt.*;

public class ZIDEMain extends Application {

  CodeEditorView mainSyntax;

  @Override
  public void start(Stage stage) {
    stage.initStyle(StageStyle.UNDECORATED);

    var root = new BorderPane();
    root.getStyleClass().add("app-root");

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
    scene.getStylesheets().add(getClass().getResource("/flatfx.css").toExternalForm());

    stage.setTitle("FlatFX IDE");
    stage.setScene(scene);
    stage.show();
  }

  private MenuBar buildMenuBar() {
    var file = new Menu("_File");
    var newFile = new MenuItem("New File");
    newFile.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));

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
    var toggleTheme = new CheckMenuItem("Dark theme");
    toggleTheme.setOnAction(e -> {
      var scene = toggleTheme.getParentPopup().getOwnerWindow().getScene();
      scene.getRoot().pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("dark"),
              toggleTheme.isSelected());
    });
    view.getItems().add(toggleTheme);

    var run = new Menu("_Run");
    var runProject = new MenuItem("Run");
    runProject.setAccelerator(KeyCombination.keyCombination("Shortcut+R"));
    run.getItems().add(runProject);

    var help = new Menu("_Help");
    help.getItems().add(new MenuItem("About"));

    var bar = new MenuBar(file, edit, view, run, help);
    bar.getStyleClass().add("app-menubar");
    return bar;
  }

  private ToolBar buildToolBar() {
    var runBtn = new Button("Run");
    runBtn.getStyleClass().add("accent");

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

    var src = new TreeItem<>("src");
    src.getChildren().addAll(
            new TreeItem<>("Main.java"),
            new TreeItem<>("App.java"),
            new TreeItem<>("ui/"),
            new TreeItem<>("core/")
    );

    var resources = new TreeItem<>("resources");
    resources.getChildren().addAll(new TreeItem<>("flatfx.css"), new TreeItem<>("icons/"));

    root.getChildren().addAll(src, resources, new TreeItem<>("README.md"));

    var tree = new TreeView<>(root);
    tree.setShowRoot(true);
    tree.getStyleClass().add("project-tree");

    // Simple behaviour: double-click opens a new tab (demo)
    tree.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2) {
        var item = tree.getSelectionModel().getSelectedItem();
        if (item != null && item.isLeaf()) {
          openTab(item.getValue());
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

    openTab("Welcome.md");

    return editorTabs;
  }

  private void openTab(String name) {
    for (var t : editorTabs.getTabs()) {
      if (t.getText().equals(name)) {
        editorTabs.getSelectionModel().select(t);
        return;
      }
    }

    // Host Swing inside JavaFX
    SwingNode swingNode = new SwingNode();
    swingNode.getStyleClass().add("swing-editor-host");

    ZPERuntimeEnvironment r = new ZPERuntimeEnvironment();

    // Build Swing UI on the EDT
    SwingUtilities.invokeLater(() -> {
      // Your Swing editor
      mainSyntax =
              new jamiebalfour.codeeditor.CodeEditorView(ZPEKit.getKeywordSet(), "\"'`", "$");

      mainSyntax.setFontSize(14);

      for (String keyword : ZPEKit.getKeywords()) {
        mainSyntax.addAutoCompleteItem(keyword, CodeEditorView.AutoCompleteItemType.Keyword);

      }

      // Filter through type keywords and add to suggestions if they start with the current word
      for (String keyword : ZPEKit.getTypeKeywords()) {
        mainSyntax.addAutoCompleteItem(keyword, CodeEditorView.AutoCompleteItemType.Type);
      }

      // Do the same for functions
      for (String function : ZPEKit.getBuiltInFunctions()) {
        mainSyntax.addAutoCompleteItem(function, CodeEditorView.AutoCompleteItemType.Function);

      }

      for(String s : ZPEInstance.getBuiltInStructuresNames()){
        mainSyntax.addAutoCompleteItem(s, CodeEditorView.AutoCompleteItemType.Type);
      }
      // Wrap so it sizes nicely
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(mainSyntax.getEditPane(), BorderLayout.CENTER);
      wrapper.setOpaque(true);

      wrapper.setBackground(Color.WHITE);

      // 👇 Padding around the editor
      wrapper.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

      swingNode.setContent(wrapper);
    });



    var tab = new Tab(name, swingNode);
    tab.setClosable(true);
    editorTabs.getTabs().add(tab);
    editorTabs.getSelectionModel().select(tab);

    // Helps focus when you click into the editor region
    swingNode.setOnMousePressed(e -> swingNode.requestFocus());
  }

  private Node buildTerminal() {
    var title = new Label("Terminal");
    title.getStyleClass().add("pane-title");

    var output = new TextArea();
    output.setEditable(false);
    output.getStyleClass().add("terminal-output");
    output.setText("FlatFX IDE terminal ready.\n");

    var input = new TextField();
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
    });

    var header = new HBox(title);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("pane-header");

    var box = new VBox(header, output, input);
    VBox.setVgrow(output, Priority.ALWAYS);
    box.getStyleClass().add("terminal-pane");
    box.setMinHeight(180);
    return box;
  }

  private Node buildStatusBar() {
    var left = new Label("Ready");
    var centre = new Label("Ln 1, Col 1");
    var right = new Label("UTF-8  |  Spaces: 4");

    var bar = new HBox(left, new Region(), centre, new Region(), right);
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
    launch(args);
  }
}
