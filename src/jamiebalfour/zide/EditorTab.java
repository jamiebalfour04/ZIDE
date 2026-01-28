package jamiebalfour.zide;

import javafx.scene.control.Tab;

public class EditorTab extends Tab {
  private final String path;
  private final jamiebalfour.codeeditor.CodeEditorView editor;

  public EditorTab(String title, String path, jamiebalfour.codeeditor.CodeEditorView editor, javafx.scene.Node content) {
    super(title, content);
    this.path = path;
    this.editor = editor;
  }

  public String getPath() {
    return path;
  }
  public jamiebalfour.codeeditor.CodeEditorView getEditor() { return editor; }
}
