package jamiebalfour.balflaf_fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class BalfTitleBar extends Region {

  private double dragOffsetX;
  private double dragOffsetY;
  private Region leftControls;
  private final Stage stage;
  private final String title;

  private final MenuButton jbMenu;
  private final Canvas titleCanvas = new Canvas();

  private final Insets padding = new Insets(0, 10, 0, 10);
  private final double barHeight = 32;

  public BalfTitleBar(Stage stage, String title) {
    this.stage = stage;
    this.title = title;

    getStyleClass().add("balf-titlebar");
    setMinHeight(barHeight);
    setPrefHeight(barHeight);
    setMaxHeight(barHeight);

    boolean isMac = isMac();
    leftControls = isMac ? macTrafficLights(stage) : windowsControls(stage);


    jbMenu = createJBMenu();
    clampToTitlebarHeight(jbMenu, 22);
    jbMenu.setFocusTraversable(false);

    // Canvas is just for drawing; don't let it steal mouse events
    titleCanvas.setMouseTransparent(true);

    // IMPORTANT: add canvas FIRST so controls are on top visually
    getChildren().addAll(titleCanvas, leftControls, jbMenu);

    getStylesheets().add(
            getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm()
    );

    widthProperty().addListener((obs, o, n) -> requestLayout());
    heightProperty().addListener((obs, o, n) -> requestLayout());

    enableWindowDrag(stage);
    enableDoubleClickZoom(stage);
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

    // Lay out left controls
    double lcW = snapSizeX(leftControls.prefWidth(h));
    double lcH = snapSizeY(leftControls.prefHeight(-1));
    double lcY = Math.round((h - lcH) / 2.0);
    leftControls.resizeRelocate(leftX, lcY, lcW, lcH);

    // Lay out right menu
    double menuW = snapSizeX(jbMenu.prefWidth(-1));
    double menuH = snapSizeY(jbMenu.prefHeight(-1));
    double menuX = Math.round(w - rightPad - menuW);
    double menuY = Math.round((h - menuH) / 2.0);
    jbMenu.resizeRelocate(menuX, menuY, menuW, menuH);

    // Canvas covers whole titlebar
    titleCanvas.setWidth(w);
    titleCanvas.setHeight(h);

    redrawTitle(w, h, leftControls.getLayoutX() + leftControls.getWidth(), jbMenu.getLayoutX());
  }

  private void redrawTitle(double w, double h, double leftOccupiedEndX, double rightOccupiedStartX) {
    GraphicsContext g = titleCanvas.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);

    // Choose a font that matches your UI. Swap to your bundled font if you like.
    Font font = Font.font("System", FontWeight.MEDIUM, 13);
    g.setFont(font);
    g.setFill(Color.WHITE);

    Text t = new Text(title);
    t.setFont(font);

    double textW = t.getLayoutBounds().getWidth();
    double textH = t.getLayoutBounds().getHeight();

    // IntelliJ-like centring: centre within the "free" band between left & right controls.
    double bandLeft = leftOccupiedEndX + 10;            // small gap after traffic lights
    double bandRight = rightOccupiedStartX - 10;        // small gap before JB menu
    double bandWidth = Math.max(0, bandRight - bandLeft);

    double x = bandLeft + (bandWidth - textW) / 2.0;
    // Clamp so it never overlaps if window is tiny
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

  private Region macTrafficLights(Stage stage) {
    var box = new HBox(8);
    box.setAlignment(Pos.CENTER_LEFT);
    box.getStyleClass().add("balf-traffic");

    var close = trafficLight("balf-close", Color.web("#ff5f57"));
    var minimise = trafficLight("balf-minimise", Color.web("#febc2e"));
    var zoom = trafficLight("balf-zoom", Color.web("#28c840"));

    close.setOnMouseClicked(e -> stage.close());
    minimise.setOnMouseClicked(e -> stage.setIconified(true));
    zoom.setOnMouseClicked(e -> stage.setMaximized(!stage.isMaximized()));

    box.getChildren().addAll(close, minimise, zoom);
    return box;
  }

  private Region windowsControls(Stage stage) {
    // Minimal placeholder: you can replace with proper glyph buttons later
    var box = new HBox(8);
    box.setAlignment(Pos.CENTER_RIGHT);
    box.getStyleClass().add("balf-win-controls");

    var minimise = trafficLight("balf-minimise", Color.web("#b0b0b0"));
    var maximise = trafficLight("balf-zoom", Color.web("#b0b0b0"));
    var close = trafficLight("balf-close", Color.web("#d9534f"));

    minimise.setOnMouseClicked(e -> stage.setIconified(true));
    maximise.setOnMouseClicked(e -> stage.setMaximized(!stage.isMaximized()));
    close.setOnMouseClicked(e -> stage.close());

    box.getChildren().addAll(minimise, maximise, close);
    return box;
  }

  private Circle trafficLight(String styleClass, Color fill) {
    var c = new Circle(6, fill);
    c.getStyleClass().addAll("balf-traffic-light", styleClass);
    c.setCursor(Cursor.HAND);
    return c;
  }

  private void enableWindowDrag(Stage stage) {
    setOnMousePressed(e -> {
      dragOffsetX = e.getSceneX();
      dragOffsetY = e.getSceneY();
    });
    setOnMouseDragged(e -> {
      // Don’t drag when maximised; feels wrong.
      if (!stage.isMaximized()) {
        stage.setX(e.getScreenX() - dragOffsetX);
        stage.setY(e.getScreenY() - dragOffsetY);
      }
    });
  }

  private void enableDoubleClickZoom(Stage stage) {
    setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
        stage.setMaximized(!stage.isMaximized());
      }
    });
  }

  private static boolean isMac() {
    String os = System.getProperty("os.name", "").toLowerCase();
    return os.contains("mac");
  }

  private MenuButton createJBMenu() {
    /*ImageView icon = new ImageView(new Image(
            getClass().getResourceAsStream("/files/balflaf_fx/icons/jb.png")
    ));
    icon.setFitWidth(20);
    icon.setFitHeight(20);
    icon.setPreserveRatio(true);*/

    MenuItem about = new MenuItem("About ZIDE");
    //about.setOnAction(e -> showAboutDialog());

    MenuItem settings = new MenuItem("Settings…");
    //settings.setOnAction(e -> openSettings());

    MenuItem checkUpdates = new MenuItem("Check for Updates…");
    //checkUpdates.setOnAction(e -> checkForUpdates());

    MenuItem separator = new MenuItem(); // quick separator alternative below

    MenuItem quit = new MenuItem("Quit");
    //quit.setOnAction(e -> quitApp());

    MenuButton jb = new MenuButton();
    //jb.setGraphic(icon);
    jb.getStyleClass().add("jb-menu");
    jb.getItems().addAll(
            about,
            settings,
            checkUpdates,
            new javafx.scene.control.SeparatorMenuItem(),
            quit
    );

    jb.setMaxWidth(20);

    // Optional: make it drop “downwards” (it will anyway at top bar)
    jb.setPopupSide(Side.BOTTOM);

    // Optional: no visible arrow
    jb.getStyleClass().add("no-arrow");

    return jb;
  }
}
