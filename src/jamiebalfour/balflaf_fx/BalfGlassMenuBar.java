package jamiebalfour.balflaf_fx;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.css.PseudoClass;
import javafx.scene.Node;
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

  public BalfGlassMenuBar() {
    getStyleClass().add("glass-menubar");
    setAlignment(Pos.CENTER_LEFT);
    setSpacing(4);
    setPadding(new Insets(4, 8, 4, 8));
    getStylesheets().add(getClass().getResource("/jamiebalfour/balflaf_fx/balflaf_fx.css").toExternalForm());
  }

  public GlassMenu menu(String title) {
    Label label = createMenuTitle(title);

    StackPane hitBox = new StackPane(label);
    hitBox.getStyleClass().add("glass-menu-title-hitbox");
    hitBox.setAlignment(Pos.CENTER);
    hitBox.setPickOnBounds(true);
    hitBox.setMinHeight(30);
    hitBox.setPrefHeight(30);


    GlassMenu menu = new GlassMenu(label);
    menus.add(menu);

    getChildren().add(hitBox);
    attachMenu(hitBox, menu.popup);

    return menu;
  }

  public void setDarkMode(boolean enabled) {
    darkMode = enabled;
    pseudoClassStateChanged(DARK, enabled);
    for (GlassMenu menu : menus) menu.setDarkMode(enabled);
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

  private void attachMenu(Node owner, Popup popup) {
    owner.setOnMouseEntered(e -> {
      if (activeMenu != null && activeMenu != popup) {
        showMenu(owner, popup);
      }
    });

    owner.setOnMouseClicked(e -> {
      if (activeMenu == popup && popup.isShowing()) {
        hideActiveMenu();
        e.consume();
        return;
      }

      showMenu(owner, popup);
      e.consume();
    });
  }

  private void showMenu(Node owner, Popup popup) {
    if (activeMenu == popup && popup.isShowing()) {
      return;
    }

    hideActiveMenu();

    Point2D p = owner.localToScreen(-20, owner.getBoundsInLocal().getHeight() - 10);
    popup.show(owner.getScene().getWindow(), p.getX(), p.getY());

    activeMenu = popup;
  }

  public class GlassMenu {
    private final Label owner;
    private final Popup popup;
    private final VBox box;
    private final StackPane root;

    private GlassMenu(Label owner) {
      this.owner = owner;
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

// Add to popup
      this.popup.getContent().add(root);

      this.popup.setOnHidden(e -> {
        if (activeMenu == this.popup) {
          activeMenu = null;
          activeMenuOwner = null;
        }
      });
      this.popup.setOnHidden(e -> {
        if (activeMenu == this.popup) {
          hideActiveMenu();
        }
      });
    }

    private void setDarkMode(boolean enabled) {
      root.pseudoClassStateChanged(DARK, enabled);
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

    public GlassMenu separator() {
      Region line = new Region();
      line.getStyleClass().add("glass-menu-separator");
      line.setPrefHeight(1);
      line.setMaxHeight(1);
      VBox.setMargin(line, new Insets(5, 10, 5, 10));
      box.getChildren().add(line);
      return this;
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
        if (action != null) {
          action.run();
        }

        if (hideAfterClick) {
          hideActiveMenu();
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
    }

    setActiveOwner(null);
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

  public static class GlassCheckMenuItem {

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

        if (this.action != null) {
          this.action.accept(this.selected);
        }

        //hideActiveMenu();
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
