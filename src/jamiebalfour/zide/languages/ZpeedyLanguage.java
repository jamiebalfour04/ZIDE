package jamiebalfour.zide.languages;

import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zpe.core.ZPEKit;

import java.util.Set;

/** Zpeedy Script language definition supplied to ZIDE's language registry. */
public final class ZpeedyLanguage extends LanguageSupport {
  private final ZIDEEditor editor;
  public ZpeedyLanguage(ZIDEEditor editor) {
    super("zpeedy", "Zpeedy Script", Set.of("zps"));
    this.editor = editor;
  }
  @Override public void configure(CodeEditorViewFX view) {
    view.setLineCommentMarkers("#"); view.setBlockCommentMarkers("", ""); view.setQuoteDelimiters("\"'");
    view.setVariableDelimiters(""); view.setContextSeparator(""); view.clearKeywords(); view.clearContextualKeywords(); view.clearAutoCompleteItems();
    for (String keyword : java.util.List.of("a", "alternatively", "as", "at", "attempt", "back", "based", "call", "choice", "continue", "display", "divide", "do", "error", "every", "for", "forever", "give", "gives", "greater", "has", "if", "in", "is", "least", "less", "loop", "minus", "most", "not", "on", "otherwise", "plus", "repeat", "routine", "set", "stop", "takes", "than", "then", "thing", "times", "to", "when", "while", "with")) { view.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD); view.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword); }
    for (String function : ZPEKit.getAllFunctions()) { view.addKeyword(function, CodeSyntaxModel.Style.FUNCTION); view.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function); }
    addLiteral(view, "true", CodeSyntaxModel.Style.BOOLEAN); addLiteral(view, "false", CodeSyntaxModel.Style.BOOLEAN); addLiteral(view, "nothing", CodeSyntaxModel.Style.NULL); addLiteral(view, "unknown", CodeSyntaxModel.Style.NULL);
  }
  private void addLiteral(CodeEditorViewFX view, String value, CodeSyntaxModel.Style style) { view.addKeyword(value, style); view.addAutoCompleteItem(value, CodeEditorViewFX.AutoCompleteItemType.Keyword); }
  @Override public ZIDEEditor.EditorInfo information(String token) {
    if (java.util.Arrays.asList(ZPEKit.getBuiltInStructuresNames()).contains(token)) return new ZIDEEditor.EditorInfo(token, "Built-in ZPE structure", null, "Built-in structure", null);
    if (ZPEKit.getAllFunctions().contains(token)) return new ZIDEEditor.EditorInfo(token + "()", "Built-in YASS function");
    return switch (token) { case "display" -> new ZIDEEditor.EditorInfo("display value", "Writes one value to the program output."); case "set" -> new ZIDEEditor.EditorInfo("set name to value", "Creates or updates a named value."); case "routine" -> new ZIDEEditor.EditorInfo("routine name takes values", "Declares a reusable Zpeedy routine."); case "call" -> new ZIDEEditor.EditorInfo("call routine with values", "Invokes a declared or host-provided routine."); default -> null; };
  }
  @Override public void run(jamiebalfour.zide.editor.EditorTab tab) { editor.runZpeedyCode(tab); }
  @Override public boolean canRun() { return editor.hasInterpreter("zpeedy"); }
}
