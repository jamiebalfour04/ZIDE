package jamiebalfour.balflaf_fx;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.util.function.Consumer;

public class BalfGlassMenuBar extends HBox {

  private Popup activeMenu;
  private Label activeMenuOwner;

  public BalfGlassMenuBar() {
    getStyleClass().add("glass-menubar");
    setAlignment(Pos.CENTER_LEFT);
    setSpacing(4);
    setPadding(new Insets(4, 8, 4, 8));
    getStylesheets().add(getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm());
  }

  public GlassMenu menu(String title) {
    Label label = createMenuTitle(title);
    GlassMenu menu = new GlassMenu(label);
    getChildren().add(label);
    attachMenu(label, menu.popup);
    return menu;
  }

  private Label createMenuTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("glass-menu-title");
    label.setPadding(new Insets(4, 10, 4, 10));
    return label;
  }

  private void attachMenu(Label owner, Popup popup) {
    owner.setOnMouseEntered(e -> {
      if (activeMenu != null && activeMenu != popup) {
        showMenu(owner, popup);
      }
    });

    owner.setOnMouseClicked(e -> {
      if (activeMenu == popup && popup.isShowing()) {
        e.consume();
        return;
      }

      showMenu(owner, popup);
      e.consume();
    });
  }

  private void showMenu(Label owner, Popup popup) {
    if (activeMenu == popup && popup.isShowing()) {
      return;
    }

    if (activeMenu != null) {
      activeMenu.hide();
    }

    Point2D p = owner.localToScreen(-20, owner.getHeight() - 12);

    popup.show(owner.getScene().getWindow(), p.getX(), p.getY());

    activeMenu = popup;
    activeMenuOwner = owner;
  }

  public class GlassMenu {
    private final Label owner;
    private final Popup popup;
    private final VBox box;

    private GlassMenu(Label owner) {
      this.owner = owner;
      this.popup = new Popup();
      this.popup.setAutoHide(true);

      this.box = new VBox(1);
      this.box.getStyleClass().add("glass-menu-popup");
      this.box.setPadding(new Insets(6));

      this.popup.getContent().add(box);
    }

    public GlassMenu item(String text, String shortcut, Runnable action) {
      box.getChildren().add(createItem(text, shortcut, action, true));
      return this;
    }

    public GlassMenu item(String text, Runnable action) {
      return item(text, "", action);
    }

    public GlassMenu itemNoHide(String text, String shortcut, Runnable action) {
      box.getChildren().add(createItem(text, shortcut, action, false));
      return this;
    }

    public GlassMenu checkItem(String text, boolean selected, Consumer<Boolean> action) {
      box.getChildren().add(createCheckItem(text, selected, action));
      return this;
    }

    public GlassMenu separator() {
      Region line = new Region();
      line.getStyleClass().add("glass-menu-separator");
      line.setPrefHeight(1);
      line.setMaxHeight(1);
      VBox.setMargin(line, new Insets(3, 0, 3, 0));
      box.getChildren().add(line);
      return this;
    }

    private Node createItem(String text, String shortcut, Runnable action, boolean hideAfterClick) {
      HBox row = new HBox();
      row.getStyleClass().add("glass-menu-item");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinWidth(200);
      row.setPadding(new Insets(3, 8, 3, 6));

      Label title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      Label keys = new Label(shortcut == null ? "" : shortcut);
      keys.getStyleClass().add("glass-menu-shortcut");

      row.getChildren().addAll(title, spacer, keys);

      row.setOnMouseClicked(e -> {
        action.run();

        if (hideAfterClick && activeMenu != null) {
          activeMenu.hide();
        }

        e.consume();
      });

      return row;
    }

    private Node createCheckItem(String text, boolean selected, Consumer<Boolean> action) {
      HBox row = new HBox();
      row.getStyleClass().add("glass-menu-item");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinWidth(200);
      row.setPadding(new Insets(3, 8, 3, 6));

      Label tick = new Label(selected ? "✓" : "");
      tick.setMinWidth(22);
      tick.getStyleClass().add("glass-menu-check");

      Label title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      row.getChildren().addAll(tick, title);

      final boolean[] state = { selected };

      row.setOnMouseClicked(e -> {
        state[0] = !state[0];
        tick.setText(state[0] ? "✓" : "");
        action.accept(state[0]);

        hideActiveMenu();
        e.consume();
      });

      return row;
    }
  }

  private void hideActiveMenu() {
    if (activeMenu != null) {
      activeMenu.hide();
      activeMenu = null;
      activeMenuOwner = null;
    }
  }
}