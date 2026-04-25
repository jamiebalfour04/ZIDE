package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import jamiebalfour.ui.components.BalfScrollbarPane;
import javafx.scene.control.Tab;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class EditorTab extends Tab {
  private final String path;
  private final jamiebalfour.codeeditor.CodeEditorView editor;
  private final BalfScrollbarPane pane;
  private boolean changes = false;

  public EditorTab(String title, String path, jamiebalfour.codeeditor.CodeEditorView editor, BalfScrollbarPane pane, javafx.scene.Node content) {
    super(title, content);
    this.path = path;
    this.editor = editor;
    this.pane = pane;
    pane.setLightColour(Color.white);

    editor.getDocument().addDocumentListener(new DocumentListener() {

      @Override
      public void insertUpdate(DocumentEvent e) {
        changes = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        changes = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        changes = true;
      }
    });

    BalfLafManager.getInstance().addThemeChangeListener(() -> {
      if(BalfLafManager.getInstance().isDarkModeEnabled()){
        editor.setBackground(new Color(0, 0, 0));
        editor.setForeground(Color.white);
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.white);
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote,    new Color(198, 120, 221)); // Soft purple
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword,  new Color(255, 85, 114));  // Strong coral pink
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 175, 239));  // Bright sky blue
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var,      new Color(102, 217, 239)); // Aqua cyan
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type,     new Color(229, 192, 123)); // Sand/gold
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool,     new Color(189, 147, 249)); // Light lavender
        editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special,     new Color(0, 200, 0)); // Green
        editor.repaint();
        editor.setText(editor.getText());
      }
    });
  }
  

  public String getPath() {
    return path;
  }

  public jamiebalfour.codeeditor.CodeEditorView getEditor() { return editor; }
  public BalfScrollbarPane getScrollPane() { return pane; }

  void switchOnDarkMode() {


    Color dark = Color.decode("#282D37");


    BalfLafManager.getInstance().toggleDarkMode(true);

    pane.setDarkColour(dark);

    editor.setBackground(dark);
    editor.setForeground(Color.WHITE);
    editor.setCaretColor(Color.WHITE);

    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.white);
    /*editor.setAttributeColor(ATTR_TYPE.Quote, new Color(152, 195, 119));
    editor.setAttributeColor(ATTR_TYPE.Keyword, new Color(255, 123, 114));
    editor.setAttributeColor(ATTR_TYPE.Function, new Color(210, 168, 255));
    editor.setAttributeColor(ATTR_TYPE.Var, new Color(0, 106, 44));
    editor.setAttributeColor(ATTR_TYPE.Type, new Color(121, 192, 255));
    editor.setAttributeColor(ATTR_TYPE.Bool, new Color(208, 154, 102));*/

    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote,    new Color(198, 120, 221)); // Soft purple
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword,  new Color(255, 85, 114));  // Strong coral pink
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 175, 239));  // Bright sky blue
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var,      new Color(102, 217, 239)); // Aqua cyan
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type,     new Color(229, 192, 123)); // Sand/gold
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool,     new Color(189, 147, 249)); // Light lavender
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special,     new Color(0, 200, 0)); // Green



    //autoCompleteItemForeColor = new Color(255, 255, 255, 255);
    editor.setAutoCompleteItemBackgroundColor(new Color(30, 39, 75, 255));
    resetScroll();

  }

  void switchOffDarkMode() {

    BalfLafManager.getInstance().toggleDarkMode(false);


    Color light = new Color(255, 255, 255);
    pane.setLightColour(Color.white);
    editor.setBackground(Color.white);
    editor.setForeground(Color.black);
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.black);
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote, new Color(0, 128, 0));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword, new Color(135, 16, 148));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 172, 231));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var, new Color(255, 138, 0));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type, new Color(2, 87, 172));
    editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special, new Color(255, 0, 0)); // Pink
    editor.setCaretColor(Color.black);
    //autoCompleteItemForeColor = new Color(31, 31, 31, 255);
    //autoCompleteItemBackgroundColor = new Color(241, 241, 241, 255);

    editor.setAutoCompleteItemBackgroundColor(new Color(241, 241, 241, 255));

    resetScroll();


  }

  void setHasChanges(boolean hasChanges) {
    this.changes = hasChanges;
  }

  boolean hasChanges() {
    return changes;
  }

  private void resetScroll() {
    int caretPosition = editor.getCaretPosition();
    int scrollPosition = pane.getVerticalScrollBar().getValue();
    editor.setText(editor.getText());
    SwingUtilities.invokeLater(() -> {
      editor.requestFocus();
      editor.setCaretPosition(caretPosition);
      pane.getVerticalScrollBar().setValue(scrollPosition);
    });
  }

}
