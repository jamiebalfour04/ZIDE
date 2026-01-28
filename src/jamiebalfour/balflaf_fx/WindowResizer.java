package jamiebalfour.balflaf_fx;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public final class WindowResizer {

  private static final int RESIZE_MARGIN = 6; // px

  private double startX, startY;
  private double startScreenX, startScreenY;
  private double startWidth, startHeight;
  private double startStageX, startStageY;

  private ResizeMode mode = ResizeMode.NONE;

  private enum ResizeMode {
    NONE,
    N, S, E, W,
    NE, NW, SE, SW
  }

  public void install(Stage stage, Node root) {
    root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> updateCursor(stage, root, e));
    root.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
      if (!e.isPrimaryButtonDown()) root.setCursor(Cursor.DEFAULT);
    });

    root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (stage.isMaximized()) return;

      mode = getMode(root, e);
      if (mode == ResizeMode.NONE) return;

      startX = stage.getX();
      startY = stage.getY();
      startStageX = stage.getX();
      startStageY = stage.getY();

      startScreenX = e.getScreenX();
      startScreenY = e.getScreenY();

      startWidth = stage.getWidth();
      startHeight = stage.getHeight();

      e.consume();
    });

    root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
      if (stage.isMaximized()) return;
      if (mode == ResizeMode.NONE) return;

      double dx = e.getScreenX() - startScreenX;
      double dy = e.getScreenY() - startScreenY;

      double minW = Math.max(stage.getMinWidth(), 300);
      double minH = Math.max(stage.getMinHeight(), 200);

      double newX = startStageX;
      double newY = startStageY;
      double newW = startWidth;
      double newH = startHeight;

      switch (mode) {
        case E -> newW = clampMin(startWidth + dx, minW);
        case S -> newH = clampMin(startHeight + dy, minH);
        case SE -> {
          newW = clampMin(startWidth + dx, minW);
          newH = clampMin(startHeight + dy, minH);
        }
        case W -> {
          double w = clampMin(startWidth - dx, minW);
          newX = startX + (startWidth - w);
          newW = w;
        }
        case N -> {
          double h = clampMin(startHeight - dy, minH);
          newY = startY + (startHeight - h);
          newH = h;
        }
        case NW -> {
          double w = clampMin(startWidth - dx, minW);
          double h = clampMin(startHeight - dy, minH);
          newX = startX + (startWidth - w);
          newY = startY + (startHeight - h);
          newW = w;
          newH = h;
        }
        case NE -> {
          double w = clampMin(startWidth + dx, minW);
          double h = clampMin(startHeight - dy, minH);
          newY = startY + (startHeight - h);
          newW = w;
          newH = h;
        }
        case SW -> {
          double w = clampMin(startWidth - dx, minW);
          double h = clampMin(startHeight + dy, minH);
          newX = startX + (startWidth - w);
          newW = w;
          newH = h;
        }
        default -> {}
      }

      stage.setX(newX);
      stage.setY(newY);
      stage.setWidth(newW);
      stage.setHeight(newH);

      e.consume();
    });

    root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> mode = ResizeMode.NONE);
  }

  private void updateCursor(Stage stage, Node root, MouseEvent e) {
    if (stage.isMaximized()) {
      root.setCursor(Cursor.DEFAULT);
      return;
    }
    ResizeMode m = getMode(root, e);
    root.setCursor(cursorFor(m));
  }

  private ResizeMode getMode(Node root, MouseEvent e) {
    double x = e.getX();
    double y = e.getY();
    double w = root.getBoundsInLocal().getWidth();
    double h = root.getBoundsInLocal().getHeight();

    boolean left = x <= RESIZE_MARGIN;
    boolean right = x >= w - RESIZE_MARGIN;
    boolean top = y <= RESIZE_MARGIN;
    boolean bottom = y >= h - RESIZE_MARGIN;

    if (top && left) return ResizeMode.NW;
    if (top && right) return ResizeMode.NE;
    if (bottom && left) return ResizeMode.SW;
    if (bottom && right) return ResizeMode.SE;
    if (top) return ResizeMode.N;
    if (bottom) return ResizeMode.S;
    if (left) return ResizeMode.W;
    if (right) return ResizeMode.E;

    return ResizeMode.NONE;
  }

  private Cursor cursorFor(ResizeMode mode) {
    return switch (mode) {
      case N -> Cursor.N_RESIZE;
      case S -> Cursor.S_RESIZE;
      case E -> Cursor.E_RESIZE;
      case W -> Cursor.W_RESIZE;
      case NE -> Cursor.NE_RESIZE;
      case NW -> Cursor.NW_RESIZE;
      case SE -> Cursor.SE_RESIZE;
      case SW -> Cursor.SW_RESIZE;
      default -> Cursor.DEFAULT;
    };
  }

  private static double clampMin(double v, double min) {
    return Math.max(v, min);
  }
}