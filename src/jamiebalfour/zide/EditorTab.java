package jamiebalfour.zide;

import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.ui.BalfLafManager;
import javafx.scene.control.Tab;

import java.awt.*;

public class EditorTab extends Tab {
  private final String path;
  private final jamiebalfour.codeeditor.CodeEditorView editor;

  public EditorTab(String title, String path, jamiebalfour.codeeditor.CodeEditorView editor, javafx.scene.Node content) {
    super(title, content);
    this.path = path;
    this.editor = editor;

    BalfLafManager.getInstance().addThemeChangeListener(new BalfLafManager.ThemeChangeListener() {

      @Override
      public void onThemeChanged() {
        if(BalfLafManager.getInstance().isDarkModeEnabled()){
          editor.setBackground(new Color(30, 30, 30));
          editor.setForeground(Color.white);
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Normal, Color.white);
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Quote,    new Color(198, 120, 221)); // Soft purple
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Keyword,  new Color(255, 85, 114));  // Strong coral pink
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Function, new Color(97, 175, 239));  // Bright sky blue
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Var,      new Color(102, 217, 239)); // Aqua cyan
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Type,     new Color(229, 192, 123)); // Sand/gold
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Bool,     new Color(189, 147, 249)); // Light lavender
          editor.setAttributeColor(CodeEditorView.ATTR_TYPE.Special,     new Color(0, 200, 0)); // Green
        }
      }
    });
  }

  public String getPath() {
    return path;
  }
  public jamiebalfour.codeeditor.CodeEditorView getEditor() { return editor; }
}
