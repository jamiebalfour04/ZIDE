package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.List;
import java.util.regex.Pattern;
import java.util.Set;

public final class SqarlLanguage extends LanguageSupport {
  public SqarlLanguage(ZIDEEditor editor) { super(editor, "sqarl", "SQARL", Set.of("sqarl")); }
  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//"); editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'"); editor.setVariableDelimiters(""); editor.setContextSeparator("");
    editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems();
    for (String keyword : new String[]{"DECLARE", "INITIALLY", "WHILE", "RECEIVE", "FROM", "KEYBOARD", "END", "SEND", "FOR", "EACH", "DO", "IF", "THEN", "SET", "TO", "DISPLAY", "ARRAY", "STRING", "RECORD", "CLASS", "INTEGER", "REAL", "BOOLEAN", "CHARACTER", "FUNCTION", "RETURN", "PROCEDURE", "AND", "OR", "NOT", "MOD", "OPEN", "CLOSE", "CREATE", "METHODS", "THIS", "WITH", "OVERRIDE", "INHERITS", "CONSTRUCTOR", "IS", "AS", "ELSE"}) {
      editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
  }
  @Override public void run(EditorTab tab) { runExternalScript(tab, "sqarl", "SQARL", ".sqarl", List.of("-r")); }
  @Override public boolean canRun() { return host.hasInterpreter(this); }
  @Override public Pattern variablePattern() { return Pattern.compile("[A-Za-z_][A-Za-z0-9_]*"); }
  @Override public void compile(ZIDEEditor host, EditorTab tab) { host.compileSqarl(tab); }
  @Override public boolean canCompile() { return true; }
  @Override public boolean canTranspile() { return true; }
  @Override public boolean supportsSqarlTranspilation() { return true; }
  @Override public List<String> runtimeExecutables() { return List.of("sqarl", "sqarl.exe", "sqarl.cmd"); }
}
