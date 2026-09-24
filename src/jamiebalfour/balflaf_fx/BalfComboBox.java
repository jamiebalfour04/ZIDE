package jamiebalfour.balflaf_fx;

import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.collections.ObservableList;

import java.util.Objects;

/** A reusable, themed JavaFX combo box for BalfLaf interfaces. */
public class BalfComboBox<T> extends ComboBox<T> {
  private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");
  private static final String STYLESHEET = "/jamiebalfour/balflaf_fx/balflaf_fx.css";

  private boolean darkMode;
  private ListView<T> popupList;

  public BalfComboBox() {
    initialize();
  }

  public BalfComboBox(ObservableList<T> items) {
    super(items);
    initialize();
  }

  private void initialize() {
    if (!getStyleClass().contains("balf-combo-box")) getStyleClass().add("balf-combo-box");
    setCellFactory(list -> {
      popupList = list;
      if (!list.getStyleClass().contains("balf-combo-popup-list")) {
        list.getStyleClass().add("balf-combo-popup-list");
      }
      list.pseudoClassStateChanged(DARK, darkMode);
      // Combo-box popups use their own scene; install the shared stylesheet
      // there as well so hover and selected-cell states are not lost.
      list.sceneProperty().addListener((observable, oldScene, newScene) -> installStylesheet(newScene));
      return new ListCell<T>() {
        {
          getStyleClass().add("balf-combo-popup-cell");
          pseudoClassStateChanged(DARK, darkMode);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
          super.updateItem(item, empty);
          setText(empty || item == null ? null : BalfComboBox.this.getConverter() == null
                  ? Objects.toString(item, "") : BalfComboBox.this.getConverter().toString(item));
          setGraphic(null);
          pseudoClassStateChanged(DARK, darkMode);
        }
      };
    });
    sceneProperty().addListener((observable, oldScene, newScene) -> installStylesheet(newScene));
  }

  /** Applies the dark palette to the control and its open popup. */
  public void setDarkMode(boolean enabled) {
    darkMode = enabled;
    pseudoClassStateChanged(DARK, enabled);
    if (popupList != null) {
      popupList.pseudoClassStateChanged(DARK, enabled);
      for (javafx.scene.Node cell : popupList.lookupAll(".balf-combo-popup-cell")) {
        cell.pseudoClassStateChanged(DARK, enabled);
      }
    }
  }

  private static void installStylesheet(Scene scene) {
    if (scene == null) return;
    var resource = BalfComboBox.class.getResource(STYLESHEET);
    if (resource == null) return;
    String stylesheet = resource.toExternalForm();
    if (!scene.getStylesheets().contains(stylesheet)) scene.getStylesheets().add(stylesheet);
  }
}
