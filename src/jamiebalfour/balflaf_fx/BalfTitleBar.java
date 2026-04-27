package jamiebalfour.balflaf_fx;

import jamiebalfour.HelperFunctions;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

public class BalfTitleBar extends Region {

  private double dragOffsetX;
  private double dragOffsetY;

  private final Stage stage;
  private final String title;

  private final Region leftNode;
  private Region rightNode;

  private final Button jbMenu;
  private ContextMenu jbContextMenu;
  private final Canvas titleCanvas = new Canvas();

  private final Insets padding = new Insets(0, 10, 0, 10);
  private final double barHeight = 32;
  private boolean enableWindowClose = true;

  // Keep references so we can swap max/restore glyph on Windows
  private SVGPath winMaxGlyph;

  private final boolean enableWindowMaximise;
  private final boolean enableWindowMinimise;

  private final EventHandler<ActionEvent> onAbout;

  public BalfTitleBar(Stage stage, String title, EventHandler<ActionEvent> aboutAction) {
    this(stage, title, aboutAction, true, true, true, true);
  }

  public BalfTitleBar(Stage stage, String title, EventHandler<ActionEvent> aboutAction, boolean isDialog) {
    this(stage, title, aboutAction, false, false, false, false);
  }

  public BalfTitleBar(Stage stage, String title, EventHandler<ActionEvent> aboutAction, boolean enableWindowMinimise, boolean enableWindowMaximise, boolean enableWindowDrag, boolean exitApplicationOnClose) {
    this.stage = stage;
    this.title = title;
    this.onAbout = aboutAction;

    this.enableWindowMinimise = enableWindowMinimise;
    this.enableWindowMaximise = enableWindowMaximise;
    this.enableWindowClose = exitApplicationOnClose;

    getStyleClass().add("balf-titlebar");
    setMinHeight(barHeight);
    setPrefHeight(barHeight);
    setMaxHeight(barHeight);

    jbMenu = createJBMenu();
    clampToTitlebarHeight(jbMenu, 22);
    jbMenu.setFocusTraversable(false);


    // Canvas is just for drawing; don't let it steal mouse events
    titleCanvas.setMouseTransparent(true);

    boolean isMac = isMac();

    if (isMac) {
      leftNode = macTrafficLights(stage);
      // Add canvas FIRST so controls are on top visually
      getChildren().addAll(titleCanvas, leftNode);
      if(stage.getModality() != Modality.WINDOW_MODAL) {
        rightNode = jbMenu;                 // JB menu on right (mac)
        getChildren().add(rightNode);
      }
    } else {
      leftNode = new Region();            // nothing on the left (windows)
      rightNode = windowsRightCluster(stage); // JB menu + win buttons on right
      // Add canvas FIRST so controls are on top visually
      getChildren().addAll(titleCanvas, leftNode, rightNode);
    }



    getStylesheets().add(getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm());

    widthProperty().addListener((obs, o, n) -> requestLayout());
    heightProperty().addListener((obs, o, n) -> requestLayout());

    if(enableWindowDrag){
      enableWindowDrag(stage);
    }


    if(enableWindowMaximise) {
      enableDoubleClickZoom(stage);
    }

    // Windows: update max/restore glyph live
    stage.maximizedProperty().addListener((obs, oldV, newV) -> updateWinMaximiseGlyph(newV));
    updateWinMaximiseGlyph(stage.isMaximized());


  }

  public static void addWindowResizing(Stage stage, Node root){
    WindowResizer resizer = new WindowResizer();
    resizer.install(stage, root);
  }

  @Override
  protected double computePrefHeight(double width) {
    return barHeight;
  }

  @Override
  protected void layoutChildren() {
    double w = getWidth();
    double h = getHeight();

    double leftX = padding.getLeft();
    double rightPad = padding.getRight();

    // Layout left node
    double leftW = snapSizeX(leftNode.prefWidth(h));
    double leftH = snapSizeY(leftNode.prefHeight(-1));
    double leftY = Math.round((h - leftH) / 2.0);
    leftNode.resizeRelocate(leftX, leftY, leftW, leftH);

    // Layout right node
    double rightW;

    if(rightNode == null) {
      rightW = 0;
    } else{
      rightW = snapSizeX(rightNode.prefWidth(-1));
    }


    double rightH;
    if(rightNode == null) {
      rightH = 0;
    } else{
      rightH = snapSizeY(rightNode.prefHeight(-1));
    }

    double rightX = Math.round(w - rightPad - rightW);
    double rightY = Math.round((h - rightH) / 2.0);

    if(rightNode != null) {
      rightNode.resizeRelocate(rightX, rightY, rightW, rightH);
    }

    // Canvas covers whole titlebar
    titleCanvas.setWidth(w);
    titleCanvas.setHeight(h);

    double rightN = 0;

    if(rightNode != null) {
      rightN = rightNode.getLayoutX();
    }

    redrawTitle(w, h, leftNode.getLayoutX() + leftNode.getWidth(), rightN);
  }

  private void redrawTitle(double w, double h, double leftOccupiedEndX, double rightOccupiedStartX) {
    GraphicsContext g = titleCanvas.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);

    Font font = Font.font("System", FontWeight.MEDIUM, 13);
    g.setFont(font);
    g.setFill(Color.WHITE);

    Text t = new Text(title);
    t.setFont(font);

    double textW = t.getLayoutBounds().getWidth();
    double textH = t.getLayoutBounds().getHeight();

    double bandLeft = leftOccupiedEndX + 10;
    double bandRight = rightOccupiedStartX - 10;
    double bandWidth = Math.max(0, bandRight - bandLeft);

    double x = bandLeft + (bandWidth - textW) / 2.0;
    x = clamp(x, bandLeft, bandRight - textW);

    double y = (h + textH / 2.0) / 2.0;

    g.fillText(title, x, y);
  }

  private static double clamp(double v, double min, double max) {
    if (max < min) return min;
    return Math.max(min, Math.min(max, v));
  }

  private static void clampToTitlebarHeight(Region n, double h) {
    n.setMinHeight(h);
    n.setPrefHeight(h);
    n.setMaxHeight(h);
  }

  private boolean confirmClose() {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Close " + title);
    alert.setContentText("Are you sure you want to close " + title + "?");

    ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancel = ButtonType.CANCEL;

    alert.getButtonTypes().setAll(close, cancel);
    return alert.showAndWait().orElse(cancel) == close;
  }

  private Region windowsRightCluster(Stage stage) {
    HBox box = new HBox(6);
    box.setAlignment(Pos.CENTER_RIGHT);
    box.getStyleClass().add("balf-win-right-cluster");

    if(stage.getModality() != Modality.WINDOW_MODAL) {
      // keep JB menu height tidy
      clampToTitlebarHeight(jbMenu, 22);
    }

    Region win = windowsControls(stage);

    if(stage.getModality() != Modality.WINDOW_MODAL){
      box.getChildren().add(jbMenu);
    }

    box.getChildren().add(win);
    return box;
  }

  // ===== macOS traffic lights =====

  private Region macTrafficLights(Stage stage) {
    var box = new HBox(8);
    box.setAlignment(Pos.CENTER_LEFT);
    box.getStyleClass().add("balf-traffic");

    if(enableWindowClose) {
      var close = trafficLight("balf-close", Color.web("#ff5f57"));
      close.setOnMouseClicked(e -> {
        if (confirmClose()) {
          Platform.exit();
          System.exit(0);
        }
      });

      box.getChildren().add(close);
    }

    if(enableWindowMinimise) {
      var minimise = trafficLight("balf-minimise", Color.web("#febc2e"));
      minimise.setOnMouseClicked(e -> stage.setIconified(true));
      box.getChildren().add(minimise);
    }



    if(enableWindowMaximise) {

      var zoom = trafficLight("balf-zoom", Color.web("#28c840"));
      zoom.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
        boolean ctrlDown = e.isControlDown();
        boolean metaDown = e.isMetaDown();

        if (ctrlDown && metaDown) {
          enterMacFullScreen();
        } else {
          toggleMacZoom(stage);
        }

        e.consume();
      });
      box.getChildren().add(zoom);
    }

    return box;
  }

  private boolean zoomed = false;

  private double oldX;
  private double oldY;
  private double oldW;
  private double oldH;


  private void toggleMacZoom(Stage stage) {
    if (!zoomed) {
      oldX = stage.getX();
      oldY = stage.getY();
      oldW = stage.getWidth();
      oldH = stage.getHeight();

      Screen screen = Screen.getScreensForRectangle(
              stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()
      ).get(0);

      Rectangle2D bounds = screen.getVisualBounds();

      stage.setX(bounds.getMinX());
      stage.setY(bounds.getMinY());
      stage.setWidth(bounds.getWidth());
      stage.setHeight(bounds.getHeight());

      zoomed = true;
    } else {
      stage.setX(oldX);
      stage.setY(oldY);
      stage.setWidth(oldW);
      stage.setHeight(oldH);

      zoomed = false;
    }
  }

  private void enterMacFullScreen() {
    stage.setFullScreenExitHint("");
    stage.setFullScreen(!stage.isFullScreen());
  }

  private Circle trafficLight(String styleClass, Color fill) {
    var c = new Circle(6, fill);
    c.getStyleClass().addAll("balf-traffic-light", styleClass);
    c.setCursor(Cursor.HAND);
    return c;
  }

  // ===== Windows buttons =====

  private Region windowsControls(Stage stage) {
    var box = new HBox(0);
    box.setAlignment(Pos.CENTER_RIGHT);
    box.getStyleClass().add("balf-win-controls");



    Button close = winButton("balf-win-close", glyphClose());

    if(enableWindowMinimise) {
      Button min = winButton("balf-win-min", glyphMinimise());
      min.setOnAction(e -> stage.setIconified(true));
      box.getChildren().add(min);
    }
    if(enableWindowMaximise) {
      Button max = winButton("balf-win-max", glyphMaximise()); // will toggle to restore
      max.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
      box.getChildren().add(max);
      // keep handle so we can swap max/restore path
      winMaxGlyph = (SVGPath) max.getGraphic();

    }

    if(enableWindowClose) {
      close.setOnAction(e -> {
        if (confirmClose()) {
          Platform.exit();
          System.exit(0);
        }
      });
      box.getChildren().add(close);
    }



    return box;
  }

  private Button winButton(String styleClass, SVGPath glyph) {
    Button b = new Button();
    b.setFocusTraversable(false);
    b.getStyleClass().addAll("balf-win-btn", styleClass);

    // Standard-ish Windows caption button hit area
    b.setMinSize(40, barHeight);
    b.setPrefSize(40, barHeight);
    b.setMaxSize(40, barHeight);

    glyph.getStyleClass().add("balf-win-glyph");
    b.setGraphic(glyph);

    return b;
  }

  private void updateWinMaximiseGlyph(boolean maximised) {
    if (winMaxGlyph == null) return;
    winMaxGlyph.setContent(maximised ? PATH_RESTORE : PATH_MAXIMISE);
  }

  private SVGPath glyphMinimise() {
    SVGPath p = new SVGPath();
    p.setContent(PATH_MINIMISE);
    return p;
  }

  private SVGPath glyphMaximise() {
    SVGPath p = new SVGPath();
    p.setContent(PATH_MAXIMISE);
    return p;
  }

  private SVGPath glyphClose() {
    SVGPath p = new SVGPath();
    p.setContent(PATH_CLOSE);
    return p;
  }

  // Simple, clean SVG paths (stroke via CSS)
  private static final String PATH_MINIMISE = "M4 16 H20";
  private static final String PATH_MAXIMISE = "M5 5 H19 V19 H5 Z";
  private static final String PATH_RESTORE  = "M7 5 H19 V17 H17 V7 H7 Z M5 7 H15 V19 H5 Z";
  private static final String PATH_CLOSE    = "M6 6 L18 18 M18 6 L6 18";

  // ===== dragging / maximise =====

  private void enableWindowDrag(Stage stage) {
    setOnMousePressed(e -> {
      dragOffsetX = e.getSceneX();
      dragOffsetY = e.getSceneY();
    });
    setOnMouseDragged(e -> {
      System.out.println(stage.isMaximized());
      if (!((!HelperFunctions.isMac() && stage.isMaximized()) || (HelperFunctions.isMac() && !zoomed))) {
        if(isMac()){
          toggleMacZoom(stage);
        } else {
          stage.setMaximized(!stage.isMaximized());
        }
      }

      stage.setX(e.getScreenX() - dragOffsetX);
      stage.setY(e.getScreenY() - dragOffsetY);

    });
  }

  private void enableDoubleClickZoom(Stage stage) {
    setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        if(isMac()){
          toggleMacZoom(stage);
        } else{
          stage.setMaximized(!stage.isMaximized());
        }
      }
    });
  }

  private static boolean isMac() {
    String os = System.getProperty("os.name", "").toLowerCase();
    return os.contains("mac");
  }

  private Button createJBMenu() {

    Label jbChevron = new Label("⌄");
    jbChevron.getStyleClass().add("jb-chevron");

    HBox content = new HBox(8, jbChevron);
    content.setAlignment(Pos.CENTER);
    content.getStyleClass().add("jb-menu-content");

    Button jb = new Button();
    jb.setGraphic(content);
    jb.getStyleClass().add("jb-menu-button");
    jb.setFocusTraversable(false);

    jbChevron.setTranslateY(-1.5);

    // Context menu contents
    MenuItem website = new MenuItem("Go to jamieBalfour.scot");

    website.setOnAction(e -> {
      try {
        HelperFunctions.openWebsite("https://www.jamiebalfour.scot");
      } catch (URISyntaxException | IOException ex) {
        //Ignore
      }
    });

    MenuItem github   = new MenuItem("GitHub");

    github.setOnAction(e -> {
      try {
        HelperFunctions.openWebsite("https://github.com/jamiebalfour04");
      }catch (URISyntaxException | IOException ex) {
        //Ignore
      }
    });

    MenuItem about    = new MenuItem("About " + title);
    about.setOnAction(this.onAbout);
    MenuItem settings = new MenuItem("Settings");
    MenuItem quit     = new MenuItem("Quit");

    quit.setOnAction(e -> {
      if(confirmClose()){
        Platform.exit();
        System.exit(0);
      }
    });

    jbContextMenu = new ContextMenu(
            website,
            github,
            new SeparatorMenuItem(),
            about,
            settings,
            new SeparatorMenuItem(),
            quit
    );

    // Show/hide on click
    jb.setOnAction(e -> {
      if (jbContextMenu.isShowing()) {
        jbContextMenu.hide();
      } else {
        jbContextMenu.show(jb, Side.BOTTOM, 0, 6);
      }
    });

    // Optional: make chevron feel “live”
    //jbContextMenu.setOnShowing(e -> jbChevron.setText("⌃"));
    //jbContextMenu.setOnHiding(e -> jbChevron.setText("⌄"));

    return jb;
  }
}