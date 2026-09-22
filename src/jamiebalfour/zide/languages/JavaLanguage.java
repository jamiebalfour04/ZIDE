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
}
