package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;
import java.util.Set;

/** PHP language definition supplied to ZIDE's language registry. */
public final class PHPLanguage extends LanguageSupport {
  public PHPLanguage(ZIDEEditor editor) {
    super("php", "PHP", Set.of("php"), null, null, null);
    this.editor = editor;
  }

  private final ZIDEEditor editor;

  @Override public void run(EditorTab tab) { editor.runExternalScript(tab, "php", "PHP", ".php"); }
  @Override public boolean canRun() { return editor.hasInterpreter("php"); }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("#", "//");
    editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters("$");
    editor.setContextSeparator("");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
    String[] keywords = {"abstract", "and", "array", "as", "break", "callable", "case", "catch", "class", "clone", "const", "continue", "declare", "default", "do", "echo", "else", "elseif", "empty", "endfor", "endforeach", "endif", "endswitch", "endwhile", "enum", "extends", "false", "final", "finally", "fn", "for", "foreach", "function", "global", "if", "implements", "include", "include_once", "instanceof", "interface", "match", "namespace", "new", "or", "print", "private", "protected", "public", "readonly", "require", "require_once", "return", "static", "switch", "throw", "trait", "true", "try", "use", "var", "while", "xor", "yield"};
    String[] functions = {"array_filter", "array_map", "array_merge", "count", "explode", "implode", "in_array", "isset", "json_decode", "json_encode", "print_r", "str_contains", "str_ends_with", "str_starts_with", "strlen", "strpos", "str_replace", "strtolower", "strtoupper", "substr", "trim", "var_dump"};
    for (String keyword : keywords) {
      CodeSyntaxModel.Style style = keyword.equals("true") || keyword.equals("false") ? CodeSyntaxModel.Style.BOOLEAN : CodeSyntaxModel.Style.KEYWORD;
      editor.addKeyword(keyword, style);
      editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
    for (String function : functions) { editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION); editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function); }
  }
}
