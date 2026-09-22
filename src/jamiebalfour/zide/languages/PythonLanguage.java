package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;
import java.util.Set;

/** Python language definition supplied to ZIDE's language registry. */
public final class PythonLanguage extends LanguageSupport {
  public PythonLanguage(ZIDEEditor editor) {
    super("python", "Python", Set.of("py"), null, null, null);
    this.editor = editor;
  }

  private final ZIDEEditor editor;

  @Override public void run(EditorTab tab) { editor.runExternalScript(tab, "python", "Python", ".py"); }
  @Override public boolean canRun() { return editor.hasInterpreter("python"); }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("#");
    editor.setBlockCommentMarkers("", "");
    editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters("");
    editor.setContextSeparator("");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
    String[] keywords = {"and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"};
    String[] types = {"int", "float", "complex", "str", "bool", "list", "dict", "set", "tuple", "bytes", "bytearray", "object", "type"};
    String[] functions = {"print", "len", "range", "enumerate", "zip", "map", "filter", "sorted", "sum", "min", "max", "abs", "all", "any", "ascii", "bin", "callable", "chr", "dir", "divmod", "eval", "exec", "format", "getattr", "hasattr", "hash", "help", "hex", "id", "input", "isinstance", "issubclass", "iter", "next", "open", "ord", "pow", "repr", "reversed", "round", "slice", "vars"};
    String[] methods = {"capitalize", "casefold", "center", "count", "endswith", "expandtabs", "find", "format", "index", "isalnum", "isalpha", "isascii", "isdecimal", "isdigit", "islower", "isspace", "istitle", "isupper", "join", "lower", "lstrip", "partition", "removeprefix", "removesuffix", "replace", "rfind", "rindex", "rjust", "rpartition", "rsplit", "rstrip", "split", "splitlines", "startswith", "strip", "swapcase", "title", "upper", "zfill", "append", "clear", "copy", "extend", "insert", "pop", "remove", "reverse", "sort", "get", "items", "keys", "setdefault", "update", "values", "add", "discard", "difference", "intersection", "union"};
    for (String keyword : keywords) { editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD); editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword); }
    for (String type : types) { editor.addKeyword(type, CodeSyntaxModel.Style.TYPE); editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type); }
    for (String function : functions) { editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION); editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function); }
    for (String method : methods) editor.addAutoCompleteItem(method, CodeEditorViewFX.AutoCompleteItemType.Function);
    editor.addKeyword("True", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("False", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("None", CodeSyntaxModel.Style.NULL);
  }
}
