package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.zide.editor.EditorTab;

import java.util.List;
import java.util.Set;

/** TypeScript language definition with JavaScript and type-system keywords. */
public final class TypeScriptLanguage extends LanguageSupport {
  private final ZIDEEditor editor;
  public TypeScriptLanguage(ZIDEEditor ignored) {
    super("typescript", "TypeScript", Set.of("ts", "mts", "cts"));
    editor = ignored;
  }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//");
    editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'`");
    editor.setVariableDelimiters("");
    editor.setContextSeparator(".");
    editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems();
    for (String word : List.of("as", "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "finally", "for", "from", "function", "get", "if", "implements", "import", "in", "instanceof", "interface", "keyof", "let", "namespace", "new", "of", "private", "protected", "public", "readonly", "return", "set", "static", "super", "switch", "this", "throw", "try", "typeof", "undefined", "type", "var", "void", "while", "with", "yield")) {
      editor.addKeyword(word, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(word, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    for (String type : List.of("any", "bigint", "boolean", "never", "number", "object", "string", "symbol", "unknown", "void", "Array", "Date", "Map", "Promise", "Record", "Set", "ReadonlyArray")) {
      editor.addKeyword(type, CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type);
    }
    for (String function : List.of("console", "fetch", "parseInt", "parseFloat", "setTimeout", "setInterval")) {
      editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION);
      editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function);
    }
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
  }

  @Override public void run(EditorTab tab) { editor.runExternalScript(tab, "typescript", "TypeScript", ".ts"); }
  @Override public boolean canRun() { return editor.hasInterpreter("typescript"); }
}
