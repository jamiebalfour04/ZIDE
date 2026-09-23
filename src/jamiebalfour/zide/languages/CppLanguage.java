package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

/** C++ language support backed by the local clang++/g++ toolchain. */
public final class CppLanguage extends LanguageSupport {
  private final ZIDEEditor editor;

  public CppLanguage(ZIDEEditor editor) { super("cpp", "C++", Set.of("cpp", "cc", "cxx", "hpp", "hh", "hxx")); this.editor = editor; }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("//"); editor.setBlockCommentMarkers("/*", "*/"); editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters(""); editor.setContextSeparator("."); editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems();
    String[] keywords = {"alignas", "alignof", "and", "asm", "auto", "bool", "break", "case", "catch", "class", "const", "constexpr", "continue", "decltype", "default", "delete", "do", "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "for", "friend", "goto", "if", "inline", "mutable", "namespace", "new", "noexcept", "nullptr", "operator", "or", "private", "protected", "public", "reinterpret_cast", "requires", "return", "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "template", "this", "throw", "thread_local", "try", "typedef", "typeid", "typename", "union", "using", "virtual", "void", "volatile", "while", "xor"};
    String[] types = {"char", "double", "float", "int", "long", "short", "signed", "unsigned", "size_t", "string", "vector", "array", "map", "set", "unordered_map", "optional", "unique_ptr", "shared_ptr", "iostream"};
    String[] functions = {"main", "cout", "cin", "cerr", "endl", "move", "forward", "make_unique", "make_shared", "sort", "find", "push_back", "emplace_back", "begin", "end"};
    for (String value : keywords) add(editor, value, CodeSyntaxModel.Style.KEYWORD, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    for (String value : types) add(editor, value, CodeSyntaxModel.Style.TYPE, CodeEditorViewFX.AutoCompleteItemType.Type);
    for (String value : functions) add(editor, value, CodeSyntaxModel.Style.FUNCTION, CodeEditorViewFX.AutoCompleteItemType.Function);
    addContext(editor, "std", "array", "begin", "cerr", "cin", "cout", "endl", "find", "forward", "map", "max", "min", "move", "optional", "ostream", "set", "sort", "string", "unordered_map", "vector");
    addContext(editor, "cmath", "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "fabs", "floor", "fmod", "log", "log10", "pow", "sin", "sqrt", "tan");
    addContext(editor, "string", "append", "c_str", "compare", "empty", "find", "length", "replace", "size", "substr");
    addContext(editor, "vector", "at", "back", "begin", "clear", "empty", "end", "front", "insert", "pop_back", "push_back", "resize", "size");
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN); editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN); editor.addKeyword("nullptr", CodeSyntaxModel.Style.NULL);
  }

  @Override public String defaultExtension() { return "cpp"; }
  @Override public void run(EditorTab tab) { editor.runNativeCode(tab, true); }
  @Override public boolean canRun() { return editor.hasNativeCompiler(true); }
  @Override public boolean canCompile() { return canRun(); }

  private static void add(CodeEditorViewFX editor, String value, CodeSyntaxModel.Style style, CodeEditorViewFX.AutoCompleteItemType type) { editor.addKeyword(value, style); editor.addAutoCompleteItem(value, type); }
  private static void addContext(CodeEditorViewFX editor, String context, String... members) { for (String member : members) editor.addContextualKeyword(context, member, CodeSyntaxModel.Style.FUNCTION); }
}
