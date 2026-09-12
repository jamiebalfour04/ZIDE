package jamiebalfour.balflaf_fx;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BalfGlassMenuBar extends HBox {

  private Popup activeMenu;
  private Label activeMenuOwner;
  private final List<GlassMenu> menus = new ArrayList<>();
  private boolean darkMode;
  private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");
  private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");

  public BalfGlassMenuBar() {
    getStyleClass().add("glass-menubar");
    setAlignment(Pos.CENTER_LEFT);
    setSpacing(4);
    setPadding(new Insets(4, 8, 4, 8));
    getStylesheets().add(getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm());
  }

  public GlassMenu menu(String title) {
    return menu(title, false);
  }

  /** Creates a menu whose popup opens above its owner, for bottom status bars. */
  public GlassMenu menuAbove(String title) {
    return menu(title, true);
  }

  private GlassMenu menu(String title, boolean opensAbove) {
    Label label = createMenuTitle(title);

    StackPane hitBox = new StackPane(label);
    hitBox.getStyleClass().add("glass-menu-title-hitbox");
    hitBox.setAlignment(Pos.CENTER);
    hitBox.setPickOnBounds(true);
    hitBox.setMinHeight(30);
    hitBox.setPrefHeight(30);


    GlassMenu menu = new GlassMenu(label, opensAbove);
    menus.add(menu);

    getChildren().add(hitBox);
    attachMenu(hitBox, menu);

    return menu;
  }

  public void setDarkMode(boolean enabled) {
    darkMode = enabled;
    pseudoClassStateChanged(DARK, enabled);
    for (GlassMenu menu : menus) menu.setDarkMode(enabled);
  }

  /** Closes the currently displayed top-level menu, if any. */
  public void hideMenus() {
    hideActiveMenu();
  }

  /** Returns whether x lies beyond the outer edges of all menu titles. */
  public boolean isOutsideMenuTitles(double x) {
    if (getChildren().isEmpty()) return true;
    Node first = getChildren().getFirst();
    Node last = getChildren().getLast();
    return x < first.getBoundsInParent().getMinX()
            || x > last.getBoundsInParent().getMaxX();
  }

  private Label createMenuTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("glass-menu-title");

    label.setPadding(new Insets(4, 10, 4, 10));
    label.setMinHeight(28);
    label.setPrefHeight(28);
    label.setMaxHeight(Double.MAX_VALUE);


    label.setAlignment(Pos.CENTER);
    label.setPickOnBounds(true);

    return label;
  }

  private void attachMenu(Node owner, GlassMenu menu) {
    Popup popup = menu.popup;
    owner.setOnMouseEntered(e -> {
      if (activeMenu != null && activeMenu != popup) {
        showMenu(owner, menu);
      }
    });

    owner.setOnMouseClicked(e -> {
      if (activeMenu == popup && popup.isShowing()) {
        hideActiveMenu();
        e.consume();
        return;
      }

      showMenu(owner, menu);
      e.consume();
    });
  }

  private void showMenu(Node owner, GlassMenu menu) {
    Popup popup = menu.popup;
    if (activeMenu == popup && popup.isShowing()) {
      return;
    }

    hideActiveMenu();

    rootCssAndLayout(menu.root);
    double popupWidth = menu.root.prefWidth(-1);
    double popupHeight = menu.root.prefHeight(popupWidth);
    Point2D p = menu.opensAbove
            ? owner.localToScreen(owner.getBoundsInLocal().getWidth(), 0)
            : owner.localToScreen(-20, owner.getBoundsInLocal().getHeight() - 10);
    double x = menu.opensAbove ? p.getX() - popupWidth : p.getX();
    double y = menu.opensAbove ? p.getY() - popupHeight + 2 : p.getY();
    popup.show(owner.getScene().getWindow(), x, y);

    activeMenu = popup;
  }

  public class GlassMenu {
    private final Label owner;
    private final Popup popup;
    private final VBox box;
    private final StackPane root;
    private final boolean opensAbove;

    private GlassMenu(Label owner, boolean opensAbove) {
      this.owner = owner;
      this.opensAbove = opensAbove;
      this.popup = new Popup();
      this.popup.setAutoHide(true);
      this.popup.setConsumeAutoHidingEvents(false);

      root = new StackPane();
      root.getStyleClass().add("glass-menu-container");
      root.getStylesheets().add(
              getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm()
      );
      setDarkMode(darkMode);

// Shadow layer (non-interactive)
      Region shadow = new Region();
      shadow.getStyleClass().add("glass-menu-shadow");
      shadow.setMouseTransparent(true);

// Actual menu content
      this.box = new VBox(1);
      this.box.getStyleClass().add("glass-menu-popup");
      this.box.setPadding(new Insets(6));

// Stack them
      root.getChildren().addAll(shadow, box);
      root.setOnMouseExited(e -> clearHoverState(root));

// Add to popup
      this.popup.getContent().add(root);

      this.popup.setOnHidden(e -> {
        clearHoverState(root);
        if (activeMenu == this.popup) {
          activeMenu = null;
          setActiveOwner(null);
        }
      });
    }

    private void setDarkMode(boolean enabled) {
      root.pseudoClassStateChanged(DARK, enabled);
    }

    public void setText(String text) {
      owner.setGraphic(null);
      owner.setContentDisplay(javafx.scene.control.ContentDisplay.TEXT_ONLY);
      owner.setText(text);
    }

    public void setGraphic(Node graphic) {
      owner.setText("");
      owner.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
      owner.setGraphic(graphic);
      owner.setAccessibleText(graphic instanceof Label label ? label.getText() : "Language");
    }

    /** Shows or removes the complete top-level menu, including its hit target. */
    public void setVisible(boolean visible) {
      Node hitBox = owner.getParent();
      if (hitBox != null) {
        hitBox.setVisible(visible);
        hitBox.setManaged(visible);
      }
      if (!visible && popup.isShowing()) popup.hide();
    }

    public GlassCheckMenuItem checkItem(String text, boolean selected, Consumer<Boolean> action) {
      GlassCheckMenuItem item = new GlassCheckMenuItem(text, selected, action);
      box.getChildren().add(item.getNode());
      return item;
    }

    public class GlassMenuItem {

      private final HBox row;
      private final Label title;
      private final Label keys;
      private final Runnable action;
      private final boolean hideAfterClick;

      private GlassMenuItem(String text, String shortcut, Runnable action, boolean hideAfterClick) {
        this.action = action;
        this.hideAfterClick = hideAfterClick;

        row = new HBox();
        row.getStyleClass().add("glass-menu-item");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(200);
        row.setPadding(new Insets(3, 8, 3, 6));

        title = new Label(text);
        title.getStyleClass().add("glass-menu-item-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        keys = new Label(shortcut == null ? "" : shortcut);
        keys.getStyleClass().add("glass-menu-shortcut");

        row.getChildren().addAll(title, spacer, keys);

        row.setOnMouseClicked(e -> {
          if (row.isDisabled()) {
            e.consume();
            return;
          }

          if (this.hideAfterClick) {
            hideActiveMenu();
          }

          if (this.action != null) {
            this.action.run();
          }

          e.consume();
        });
      }

      public Node getNode() {
        return row;
      }

      public void setText(String text) {
        title.setText(text);
      }

      public String getText() {
        return title.getText();
      }

      public void setShortcut(String shortcut) {
        keys.setText(shortcut == null ? "" : shortcut);
      }

      public String getShortcut() {
        return keys.getText();
      }

      public void setVisible(boolean visible) {
        row.setVisible(visible);
        row.setManaged(visible);
      }

      public void setDisable(boolean disabled) {
        row.setDisable(disabled);
      }

      public boolean isDisabled() {
        return row.isDisabled();
      }
    }

    public GlassMenu separator() {
      separatorNode();
      return this;
    }

    /** Adds a purpose-built control row while retaining the menu popup styling. */
    public Node customItem(Node node) {
      box.getChildren().add(node);
      return node;
    }

    /** Runs just before this menu is displayed. */
    public GlassMenu onShowing(Runnable action) {
      popup.setOnShowing(e -> action.run());
      return this;
    }

    /** Adds a separator and returns it so dynamic menus can manage its visibility. */
    public Node separatorNode() {
      Region line = new Region();
      line.getStyleClass().add("glass-menu-separator");
      line.setMinHeight(1);
      line.setPrefHeight(1);
      line.setMaxHeight(1);
      VBox.setMargin(line, new Insets(1, 10, 1, 10));
      box.getChildren().add(line);
      return line;
    }

    public Node createItem(String text, String shortcut, Runnable action) {
      return createItem(text, shortcut, action, true);
    }

    public Node createItem(String text, String shortcut, Runnable action, boolean hideAfterClick) {
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
        if (hideAfterClick) {
          hideActiveMenu();
        }

        if (action != null) {
          action.run();
        }

        e.consume();
      });

      box.getChildren().add(row);

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
        hideActiveMenu();
        action.accept(state[0]);
        e.consume();
      });

      return row;
    }
  }



  private void hideActiveMenu() {
    if (activeMenu != null) {
      for (Node content : activeMenu.getContent()) clearHoverState(content);
      activeMenu.hide();
      activeMenu = null;
    }

    setActiveOwner(null);
  }

  /** Clears JavaFX's latched hover state when a popup disappears under the pointer. */
  private static void clearHoverState(Node node) {
    node.pseudoClassStateChanged(HOVER, false);
    if (node instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) clearHoverState(child);
    }
  }

  private static void rootCssAndLayout(Region root) {
    root.applyCss();
    root.layout();
  }

  private void setActiveOwner(Label owner) {
    if (activeMenuOwner != null) {
      activeMenuOwner.getStyleClass().remove("active");
    }

    activeMenuOwner = owner;

    if (activeMenuOwner != null && !activeMenuOwner.getStyleClass().contains("active")) {
      activeMenuOwner.getStyleClass().add("active");
    }
  }

  public class GlassCheckMenuItem {

    private final HBox row;
    private final Label tick;
    private final Label title;
    private final Consumer<Boolean> action;

    private boolean selected;

    private GlassCheckMenuItem(String text, boolean selected, Consumer<Boolean> action) {
      this.selected = selected;
      this.action = action;

      row = new HBox();
      row.getStyleClass().add("glass-menu-item");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinWidth(200);
      row.setPadding(new Insets(3, 8, 3, 6));

      tick = new Label(selected ? "✓" : "");
      tick.setMinWidth(22);
      tick.getStyleClass().add("glass-menu-check");

      title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      row.getChildren().addAll(tick, title);

      row.setOnMouseClicked(e -> {
        setSelected(!this.selected);

        hideActiveMenu();

        if (this.action != null) {
          this.action.accept(this.selected);
        }

        e.consume();
      });
    }

    public Node getNode() {
      return row;
    }

    public boolean isSelected() {
      return selected;
    }

    public void setSelected(boolean selected) {
      this.selected = selected;
      tick.setText(selected ? "✓" : "");
    }

    public void setText(String text) {
      title.setText(text);
    }

    public String getText() {
      return title.getText();
    }

    public void setVisible(boolean visible) {
      row.setVisible(visible);
      row.setManaged(visible);
    }

    public void setDisable(boolean disabled) {
      row.setDisable(disabled);
    }

    public boolean isDisabled() {
      return row.isDisabled();
    }
  }

  public class GlassMenuItem {

    private final HBox row;
    private final Label title;
    private final Label keys;
    private final Runnable action;
    private final boolean hideAfterClick;

    private GlassMenuItem(String text, String shortcut, Runnable action, boolean hideAfterClick) {
      this.action = action;
      this.hideAfterClick = hideAfterClick;

      row = new HBox();
      row.getStyleClass().add("glass-menu-item");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinWidth(200);
      row.setPadding(new Insets(3, 8, 3, 6));

      title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      keys = new Label(shortcut == null ? "" : shortcut);
      keys.getStyleClass().add("glass-menu-shortcut");

      row.getChildren().addAll(title, spacer, keys);

      row.setOnMouseClicked(e -> {
        if (row.isDisabled()) {
          e.consume();
          return;
        }

        if (this.action != null) {
          this.action.run();
        }

        if (this.hideAfterClick) {
          hideActiveMenu();
        }

        e.consume();
      });
    }

    public Node getNode() {
      return row;
    }

    public void setText(String text) {
      title.setText(text);
    }

    public String getText() {
      return title.getText();
    }

    public void setShortcut(String shortcut) {
      keys.setText(shortcut == null ? "" : shortcut);
    }

    public String getShortcut() {
      return keys.getText();
    }

    public void setVisible(boolean visible) {
      row.setVisible(visible);
      row.setManaged(visible);
    }

    public void setDisable(boolean disabled) {
      row.setDisable(disabled);
    }

    public boolean isDisabled() {
      return row.isDisabled();
    }
  }
}
