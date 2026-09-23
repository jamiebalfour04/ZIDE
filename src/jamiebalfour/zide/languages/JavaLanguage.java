package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

/** Java syntax definition. Java files are edited in ZIDE but are not run by the editor yet. */
public final class JavaLanguage extends LanguageSupport {
  private final ZIDEEditor editor;

  public JavaLanguage(ZIDEEditor editor) {
    super("java", "Java", Set.of("java"));
    this.editor = editor;
  }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//");
    editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters("");
    editor.setContextSeparator(".");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();

    String[] keywords = {"abstract", "assert", "break", "case", "catch", "class", "const", "continue", "default", "do", "else", "enum", "extends", "final", "finally", "for", "if", "implements", "import", "instanceof", "interface", "module", "native", "new", "package", "private", "protected", "public", "record", "return", "sealed", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while", "non-sealed", "permits", "uses", "provides", "with", "opens", "open", "requires", "to", "transitive"};
    String[] types = {"boolean", "byte", "char", "double", "float", "int", "long", "short", "String", "Object", "Boolean", "Byte", "Character", "Double", "Float", "Integer", "Long", "Number", "Short", "Void", "Math", "System", "Exception", "RuntimeException", "Throwable", "Thread", "Runnable", "List", "Map", "Set", "Collection", "Optional", "Stream"};
    String[] functions = {"equals", "hashCode", "toString", "length", "charAt", "substring", "valueOf", "parseInt", "parseLong", "parseDouble", "println", "print", "printf", "format", "add", "remove", "get", "put", "contains", "isEmpty", "size", "stream", "collect", "of", "max", "min", "abs", "sqrt"};
    for (String keyword : keywords) add(editor, keyword, CodeSyntaxModel.Style.KEYWORD, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    for (String type : types) add(editor, type, CodeSyntaxModel.Style.TYPE, CodeEditorViewFX.AutoCompleteItemType.Type);
    for (String function : functions) add(editor, function, CodeSyntaxModel.Style.FUNCTION, CodeEditorViewFX.AutoCompleteItemType.Function);
    addContext(editor, "Math", "abs", "acos", "asin", "atan", "atan2", "cbrt", "ceil", "cos", "exp", "floor", "log", "log10", "max", "min", "pow", "random", "round", "sin", "sqrt", "tan", "toDegrees", "toRadians");
    addContext(editor, "System", "arraycopy", "currentTimeMillis", "err", "exit", "gc", "in", "lineSeparator", "nanoTime", "out", "setProperty", "getProperty");
    addContext(editor, "Arrays", "asList", "binarySearch", "copyOf", "equals", "fill", "sort", "stream", "toString");
    addContext(editor, "Collections", "binarySearch", "copy", "emptyList", "max", "min", "reverse", "rotate", "shuffle", "sort", "unmodifiableList");
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
  }

  @Override public void run(EditorTab tab) { editor.runJavaCode(tab); }

  @Override public boolean canRun() { return true; }

  @Override public boolean canDebug() { return true; }

  private static void add(CodeEditorViewFX editor, String value, CodeSyntaxModel.Style style, CodeEditorViewFX.AutoCompleteItemType type) {
    editor.addKeyword(value, style);
    editor.addAutoCompleteItem(value, type);
  }

  private static void addContext(CodeEditorViewFX editor, String context, String... members) {
    for (String member : members) editor.addContextualKeyword(context, member, CodeSyntaxModel.Style.FUNCTION);
  }
}
