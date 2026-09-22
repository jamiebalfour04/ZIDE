package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.zide.editor.EditorTab;

import java.util.List;
import java.util.Set;

/** JSX/React language definition: markup tags plus embedded JavaScript terms. */
public final class JsxLanguage extends LanguageSupport {
  private final ZIDEEditor editor;
  public JsxLanguage(ZIDEEditor ignored) {
    super("jsx", "JSX / React", Set.of("jsx", "tsx"));
    editor = ignored;
  }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//");
    editor.setBlockCommentMarkers("{/*", "*/}");
    editor.setQuoteDelimiters("\"'`");
    editor.setVariableDelimiters("");
    editor.setContextSeparator(".");
    editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems();
    for (String tag : List.of("div", "span", "main", "section", "header", "footer", "nav", "p", "h1", "h2", "h3", "button", "input", "form", "img", "ul", "li", "Fragment", "React", "Component")) {
      editor.addKeyword(tag, CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(tag, CodeEditorViewFX.AutoCompleteItemType.Type);
    }
    for (String word : List.of("as", "async", "await", "class", "const", "else", "export", "extends", "function", "if", "import", "interface", "let", "new", "return", "type", "typeof", "useEffect", "useMemo", "useState", "useContext", "useRef")) {
      editor.addKeyword(word, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(word, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    for (String attribute : List.of("className", " htmlFor", "onClick", "onChange", "onSubmit", "style", "id", "key", "ref", "value", "children")) {
      String value = attribute.trim();
      editor.addKeyword(value, CodeSyntaxModel.Style.VARIABLE);
      editor.addAutoCompleteItem(value, CodeEditorViewFX.AutoCompleteItemType.Variable);
    }
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
  }

  @Override public void run(EditorTab tab) {
    String suffix = tab != null && tab.getPath() != null && tab.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".tsx") ? ".tsx" : ".jsx";
    editor.runExternalScript(tab, "jsx", "JSX / React", suffix);
  }
  @Override public boolean canRun() { return editor.hasInterpreter("jsx"); }
}
