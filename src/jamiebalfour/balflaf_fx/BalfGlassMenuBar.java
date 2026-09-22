package jamiebalfour.balflaf_fx;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BalfGlassMenuBar extends HBox {

  private Popup activeMenu;
  private Popup activeSubmenu;
  private Label activeMenuOwner;
  private Scene submenuDismissScene;
  private final EventHandler<MouseEvent> dismissSubmenuOnMouse = event -> hideActiveMenu();
  private final EventHandler<ScrollEvent> dismissSubmenuOnScroll = event -> hideActiveMenu();
  private final EventHandler<KeyEvent> dismissSubmenuOnKey = event -> hideActiveMenu();
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
    if (getChildren().isEmpty()) {
      return true;
    }
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

    // Reapply the current theme immediately before showing a detached popup.
    // This covers popups created before the main scene finished applying its
    // dark stylesheet.
    menu.setDarkMode(darkMode);
    rootCssAndLayout(menu.root);
    double popupWidth = menu.root.prefWidth(-1);
    double popupHeight = menu.root.prefHeight(popupWidth);
    Bounds titleBounds = menu.owner.getBoundsInLocal();
    Point2D titleTopLeft = menu.owner.localToScreen(titleBounds.getMinX(), titleBounds.getMinY());
    Point2D titleTopRight = menu.owner.localToScreen(titleBounds.getMaxX(), titleBounds.getMinY());
    Point2D titleBottomLeft = menu.owner.localToScreen(titleBounds.getMinX(), titleBounds.getMaxY());
    if (titleTopLeft == null || titleTopRight == null || titleBottomLeft == null) return;

    double x = menu.opensAbove ? titleTopRight.getX() - popupWidth : titleTopLeft.getX();
    double y = menu.opensAbove ? titleTopLeft.getY() - popupHeight : titleBottomLeft.getY();
    popup.setAnchorLocation(javafx.stage.PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
    // Use the window as the popup owner: passing the title node makes
    // PopupWindow apply the node's position a second time, leaving a visible
    // gap between the menu title and its surface.
    popup.show(menu.owner.getScene().getWindow(), x, y);

    // CSS can change the popup's measured size once it is attached to its
    // separate popup scene. Re-anchor using the final bounds so an upward
    // menu stays flush with the selector instead of leaving a visible gap.
    Platform.runLater(() -> {
      if (!popup.isShowing()) return;
      double actualWidth = menu.root.getBoundsInParent().getWidth();
      double actualHeight = menu.root.getBoundsInParent().getHeight();
      if (actualWidth <= 0 || actualHeight <= 0) return;
      popup.setX(menu.opensAbove ? titleTopRight.getX() - actualWidth : titleTopLeft.getX());
      popup.setY(menu.opensAbove ? titleTopLeft.getY() - actualHeight : titleBottomLeft.getY());
    });

    activeMenu = popup;
  }

  private Insets menuItemPadding() {
    return getStyleClass().contains("language-selector-menu")
            ? new Insets(3, 8, 3, 6) : new Insets(8, 11, 8, 11);
  }

  public class GlassMenu {
    private final Label owner;
    private final Popup popup;
    private final VBox box;
    private final StackPane root;
    private final boolean opensAbove;
    private final List<GlassMenu> submenus = new ArrayList<>();
    private GlassMenu parentMenu;

    private GlassMenu(Label owner, boolean opensAbove) {
      this.owner = owner;
      this.opensAbove = opensAbove;
      this.popup = new Popup();
      this.popup.setAutoHide(true);
      this.popup.setConsumeAutoHidingEvents(false);

      root = new StackPane();
      root.getStyleClass().add("glass-menu-container");
      if (BalfGlassMenuBar.this.getStyleClass().contains("language-selector-menu")) {
      root.getStyleClass().add("language-selector-popup");
      }
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
      this.box.setPadding(BalfGlassMenuBar.this.getStyleClass().contains("language-selector-menu")
              ? new Insets(4) : new Insets(6));
      // Apply the popup surface after the content node exists. Popup scenes
      // can otherwise retain the light base surface despite the dark state.
      setDarkMode(darkMode);

// Stack them
      root.getChildren().addAll(shadow, box);
      root.setOnMouseExited(e -> clearHoverState(root));

// Add to popup
      this.popup.getContent().add(root);

      this.popup.setOnHidden(e -> {
        clearHoverState(root);
        if (activeMenu == this.popup) {
          if (activeSubmenu != null) {
            activeSubmenu.hide();
            activeSubmenu = null;
          }
          activeMenu = null;
          setActiveOwner(null);
        }
        if (activeSubmenu == this.popup) {
          activeSubmenu = null;
          if (parentMenu != null && parentMenu.popup.isShowing()) parentMenu.popup.setAutoHide(true);
        }
      });
    }

    private void setDarkMode(boolean enabled) {
      root.pseudoClassStateChanged(DARK, enabled);
      // Popup content is hosted in its own scene, so keep an explicit class
      // as well as the pseudo-class. This makes theme styling reliable when
      // the popup is shown after the application scene changes theme.
      root.getStyleClass().remove("dark-mode");
      // The rounded shadow and popup own the surface. A background on the
      // rectangular root leaks through the popup corners as dark squares.
      root.setStyle("-fx-background-color: transparent;");
      if (enabled) root.getStyleClass().add("dark-mode");
      if (box != null) {
        box.setStyle(enabled
                ? "-fx-background-color: #24262b; -fx-border-color: #4a535f;"
                : "");
      }
      for (GlassMenu submenu : submenus) submenu.setDarkMode(enabled);
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

    public GlassSubmenu submenu(String title) {
      GlassMenu submenu = new GlassMenu(new Label(title), false);
      submenu.parentMenu = this;
      submenus.add(submenu);

      HBox row = new HBox();
      row.getStyleClass().addAll("glass-menu-item", "glass-menu-submenu-item");
      row.setAlignment(Pos.CENTER_LEFT);
      row.setMinWidth(200);
      row.setPadding(new Insets(4, 11, 4, 11));

      Label label = new Label(title);
      label.getStyleClass().add("glass-menu-item-text");
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      Label arrow = new Label("›");
      arrow.getStyleClass().add("glass-menu-submenu-arrow");
      row.getChildren().addAll(label, spacer, arrow);

      row.setOnMouseEntered(event -> showSubmenu(row, submenu));
      row.setOnMouseClicked(event -> {
        if (!row.isDisabled()) showSubmenu(row, submenu);
        event.consume();
      });
      box.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
        if (activeSubmenu == submenu.popup
                && !row.contains(row.screenToLocal(event.getScreenX(), event.getScreenY()))) {
          activeSubmenu.hide();
          activeSubmenu = null;
          removeSubmenuDismissFilters();
        }
      });
      box.getChildren().add(row);
      return new GlassSubmenu(row, submenu);
    }

    public class GlassSubmenu {
      private final Node row;
      private final GlassMenu menu;

      private GlassSubmenu(Node row, GlassMenu menu) {
        this.row = row;
        this.menu = menu;
      }

      public Node getNode() {
        return row;
      }

      public Node createItem(String title, String shortcut, Runnable action) {
        return menu.createItem(title, shortcut, action);
      }

      public GlassCheckMenuItem checkItem(String title, boolean selected, Consumer<Boolean> action) {
        return menu.checkItem(title, selected, action);
      }

      public void setVisible(boolean visible) {
        row.setVisible(visible);
        row.setManaged(visible);
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
        row.setPadding(menuItemPadding());

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

    /** Wraps existing menu rows in a styled group while preserving their actions. */
    public Node groupItems(List<? extends Node> items, String styleClass) {
      if (items == null || items.isEmpty()) {
        return null;
      }
      int insertionIndex = box.getChildren().indexOf(items.getFirst());
      if (insertionIndex < 0 || !box.getChildren().containsAll(items)) {
        return null;
      }
      VBox group = new VBox(0);
      group.getStyleClass().add(styleClass);
      boolean compactGroup = "language-selector-group".equals(styleClass);
      group.setPadding(compactGroup ? new Insets(1) : new Insets(2));
      VBox.setMargin(group, compactGroup ? new Insets(1, 1, 1, 1) : new Insets(3, 2, 3, 2));
      box.getChildren().removeAll(items);
      box.getChildren().add(insertionIndex, group);
      group.getChildren().addAll(items);
      return group;
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
      row.setPadding(menuItemPadding());

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
      row.setPadding(menuItemPadding());

      Label tick = new Label(selected ? "✓" : "");
      tick.setMinWidth(22);
      tick.setAlignment(Pos.CENTER_RIGHT);
      tick.getStyleClass().add("glass-menu-check");

      Label title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      row.getChildren().addAll(title, spacer, tick);

      final boolean[] state = { selected };

      row.setOnMouseClicked(e -> {
        state[0] = !state[0];
        tick.setText(state[0] ? "✓" : "×");
        hideActiveMenu();
        action.accept(state[0]);
        e.consume();
      });

      return row;
    }
  }


  private void showSubmenu(Node owner, GlassMenu menu) {
    if (activeMenu == null || !activeMenu.isShowing()) return;
    if (activeSubmenu == menu.popup && menu.popup.isShowing()) return;
    if (activeSubmenu != null) activeSubmenu.hide();

    rootCssAndLayout(menu.root);
    Point2D point = owner.localToScreen(owner.getBoundsInLocal().getMaxX() - 6,
            owner.getBoundsInLocal().getMinY());
    if (point == null) return;
    activeMenu.setAutoHide(false);
    menu.popup.setAnchorLocation(javafx.stage.PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
    menu.popup.show(owner.getScene().getWindow(), point.getX(), point.getY());
    activeSubmenu = menu.popup;
    installSubmenuDismissFilters(owner.getScene());
  }



  private void hideActiveMenu() {
    removeSubmenuDismissFilters();
    if (activeSubmenu != null) {
      activeSubmenu.hide();
      activeSubmenu = null;
    }
    if (activeMenu != null) {
      for (Node content : activeMenu.getContent()) clearHoverState(content);
      activeMenu.hide();
      activeMenu = null;
    }

    setActiveOwner(null);
  }

  private void installSubmenuDismissFilters(Scene scene) {
    if (scene == null) return;
    if (submenuDismissScene != null && submenuDismissScene != scene) removeSubmenuDismissFilters();
    submenuDismissScene = scene;
    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, dismissSubmenuOnMouse);
    scene.addEventFilter(ScrollEvent.SCROLL, dismissSubmenuOnScroll);
    scene.addEventFilter(KeyEvent.KEY_PRESSED, dismissSubmenuOnKey);
  }

  private void removeSubmenuDismissFilters() {
    if (submenuDismissScene == null) return;
    submenuDismissScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, dismissSubmenuOnMouse);
    submenuDismissScene.removeEventFilter(ScrollEvent.SCROLL, dismissSubmenuOnScroll);
    submenuDismissScene.removeEventFilter(KeyEvent.KEY_PRESSED, dismissSubmenuOnKey);
    submenuDismissScene = null;
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
      row.setPadding(menuItemPadding());

      tick = new Label(selected ? "✓" : "");
      tick.setMinWidth(22);
      tick.setAlignment(Pos.CENTER_RIGHT);
      tick.getStyleClass().add("glass-menu-check");

      title = new Label(text);
      title.getStyleClass().add("glass-menu-item-text");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      row.getChildren().addAll(title, spacer, tick);

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
      row.setPadding(menuItemPadding());

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
