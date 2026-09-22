package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

/** JavaScript language definition supplied to ZIDE's language registry. */
public final class JavaScriptLanguage extends LanguageSupport {
  private final ZIDEEditor editor;

  public JavaScriptLanguage(ZIDEEditor editor) {
    super("js", "JavaScript", Set.of("js", "mjs", "cjs"), null, null, null);
    this.editor = editor;
  }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//");
    editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'`");
    editor.setVariableDelimiters("");
    editor.setContextSeparator(".");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
    String[] keywords = {"as", "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "finally", "for", "from", "function", "get", "if", "import", "in", "instanceof", "let", "new", "of", "return", "set", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield"};
    String[] types = {"Array", "BigInt", "Boolean", "Date", "Error", "Function", "Map", "Number", "Object", "Promise", "Proxy", "Reflect", "RegExp", "Set", "String", "Symbol", "WeakMap", "WeakSet"};
    String[] functions = {"alert", "atob", "btoa", "clearInterval", "clearTimeout", "decodeURI", "decodeURIComponent", "encodeURI", "encodeURIComponent", "eval", "fetch", "isFinite", "isNaN", "parseFloat", "parseInt", "queueMicrotask", "setInterval", "setTimeout", "structuredClone"};
    String[][] namespaces = {{"console", "assert", "clear", "count", "debug", "dir", "error", "group", "info", "log", "table", "time", "timeEnd", "trace", "warn"}, {"Math", "abs", "ceil", "floor", "max", "min", "random", "round", "sqrt", "trunc"}, {"JSON", "parse", "stringify"}, {"Promise", "all", "allSettled", "any", "race", "reject", "resolve"}, {"Array", "from", "isArray", "of"}, {"Object", "assign", "create", "entries", "freeze", "fromEntries", "keys", "values"}, {"String", "fromCharCode", "fromCodePoint"}, {"Number", "isFinite", "isInteger", "isNaN", "parseFloat", "parseInt"}};
    for (String keyword : keywords) { editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD); editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword); }
    for (String type : types) { editor.addKeyword(type, CodeSyntaxModel.Style.TYPE); editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type); }
    for (String function : functions) { editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION); editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function); }
    for (String[] namespace : namespaces) for (int i = 1; i < namespace.length; i++) {
      editor.addContextualKeyword(namespace[0], namespace[i], CodeSyntaxModel.Style.FUNCTION);
      editor.addAutoCompleteItem(namespace[i], CodeEditorViewFX.AutoCompleteItemType.Function);
    }
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
    editor.addKeyword("undefined", CodeSyntaxModel.Style.NULL);
  }

  @Override public void run(EditorTab tab) { editor.runExternalScript(tab, "javascript", "JavaScript", ".js"); }

  @Override public boolean canRun() { return editor.hasInterpreter("javascript"); }
}
