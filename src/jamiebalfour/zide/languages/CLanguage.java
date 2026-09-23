package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

/** C language support backed by the local clang/gcc toolchain. */
public final class CLanguage extends LanguageSupport {
  private final ZIDEEditor editor;

  public CLanguage(ZIDEEditor editor) { super("c", "C", Set.of("c", "h")); this.editor = editor; }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//"); editor.setBlockCommentMarkers("/*", "*/"); editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters(""); editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems();
    String[] keywords = {"auto", "break", "case", "char", "const", "continue", "default", "do", "else", "enum", "extern", "for", "goto", "if", "inline", "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "_Alignas", "_Atomic", "_Bool", "_Generic", "_Noreturn", "_Static_assert", "_Thread_local"};
    String[] types = {"bool", "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t", "FILE", "NULL"};
    String[] functions = {"printf", "scanf", "puts", "malloc", "calloc", "realloc", "free", "memcpy", "memset", "strlen", "strcmp", "strcpy", "fopen", "fclose", "fread", "fwrite", "exit"};
    for (String value : keywords) add(editor, value, CodeSyntaxModel.Style.KEYWORD, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    for (String value : types) add(editor, value, CodeSyntaxModel.Style.TYPE, CodeEditorViewFX.AutoCompleteItemType.Type);
    for (String value : functions) add(editor, value, CodeSyntaxModel.Style.FUNCTION, CodeEditorViewFX.AutoCompleteItemType.Function);
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN); editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
  }

  @Override public String defaultExtension() { return "c"; }
  @Override public void run(EditorTab tab) { editor.runNativeCode(tab, false); }
  @Override public boolean canRun() { return editor.hasNativeCompiler(false); }
  @Override public boolean canCompile() { return canRun(); }

  private static void add(CodeEditorViewFX editor, String value, CodeSyntaxModel.Style style, CodeEditorViewFX.AutoCompleteItemType type) { editor.addKeyword(value, style); editor.addAutoCompleteItem(value, type); }
}
