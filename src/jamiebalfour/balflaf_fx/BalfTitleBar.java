package jamiebalfour.balflaf_fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public final class BalfTitleBar extends HBox {

  private double dragOffsetX;
  private double dragOffsetY;

  public BalfTitleBar(Stage stage, String title) {
    getStyleClass().add("balf-titlebar");
    setAlignment(Pos.CENTER_LEFT);
    setPadding(new Insets(8, 10, 8, 10));
    setSpacing(10);

    boolean isMac = isMac();

    Region leftControls = isMac ? macTrafficLights(stage) : windowsControls(stage);

    var titleLabel = new Label(title);
    titleLabel.getStyleClass().add("balf-title");
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    getStylesheets().add(
            getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm()
    );

    var spacerL = new Region();
    var spacerR = new Region();
    HBox.setHgrow(spacerL, Priority.ALWAYS);
    HBox.setHgrow(spacerR, Priority.ALWAYS);

    // macOS convention: controls on the left, title centred.
    // Windows convention: title left, controls right (you can refine later).
    if (isMac) {
      getChildren().addAll(leftControls, spacerL, titleLabel, spacerR);
    } else {
      getChildren().addAll(titleLabel, spacerL, leftControls);
    }

    enableWindowDrag(stage);
    enableDoubleClickZoom(stage);
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
}
