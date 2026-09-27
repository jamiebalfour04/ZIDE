package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.zpe.core.ZPEInstance;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.core.ZPEModule;

import java.util.Set;

/**
 * YASS language definition supplied to ZIDE's language registry.
 */
public final class YassLanguage extends LanguageSupport {
  public YassLanguage(ZIDEEditor editor) {
    super("yass", "YASS", Set.of("yas"));

    this.editor = editor;
  }

  private final ZIDEEditor editor;

  @Override
  public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("\\", "//");
    editor.setBlockCommentMarkers("/*", "*/");
    editor.setQuoteDelimiters("\"'`");
    editor.setVariableDelimiters("$");
    editor.setContextSeparator("::");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
    editor.clearAutoCompletionRules();
    editor.addAutoCompletionRule("if", "if ${condition}\n\t\nend if", 3);
    editor.addAutoCompletionRule("while", "while ${condition}\n\t\nend while", 6);
    editor.addAutoCompletionRule("for", "for (${variable} = ${start} to ${end})\n\t\nend for", 5);
    editor.addAutoCompletionRule("function", "function ${name}()\n\t\nend function", 9);
    editor.addAutoCompletionRule("class", "class ${name}\n\t\nend class", 6);
    editor.addAutoCompletionRule("structure", "structure ${name}\n\t\nend structure", 10);
    editor.addAutoCompletionRule("namespace", "namespace ${name}\n\t\nend namespace", 10);
    editor.addAutoCompletionRule("try", "try\n\t\ncatch\n\t\nend try", 5);
    editor.addAutoCompletionRule("switch", "switch ${value}\n\tcase ${case}\n\t\t\nend switch", 7);
    editor.addAutoCompletionRule("when", "when ${value}\n\tis ${case}\n\t\t\nend when", 5);
    for (String keyword : ZPEKit.getKeywords()) {
      editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    for (String type : ZPEKit.getTypeKeywords()) {
      editor.addKeyword(type, CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(type, CodeEditorViewFX.AutoCompleteItemType.Type);
    }
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
    editor.addKeyword("NULL", CodeSyntaxModel.Style.NULL);
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("this", CodeSyntaxModel.Style.VARIABLE);
    editor.addKeyword("#breakpoint#", CodeSyntaxModel.Style.SPECIAL);
    for (String directive : ZPEKit.getDirectiveKeywords()) {
      editor.addKeyword(directive, CodeSyntaxModel.Style.DOC);
      editor.addAutoCompleteItem(directive, CodeEditorViewFX.AutoCompleteItemType.Doc);
    }
    for (String function : ZPEKit.getAllFunctions()) {
      editor.addKeyword(function, CodeSyntaxModel.Style.FUNCTION);
      editor.addAutoCompleteItem(function, CodeEditorViewFX.AutoCompleteItemType.Function);
    }
    for (String structure : ZPEInstance.getBuiltInStructuresNames()) {
      editor.addKeyword(structure, CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(structure, CodeEditorViewFX.AutoCompleteItemType.Type);
    }
    for (java.util.Map.Entry<String, ZPEModule> module : ZPEKit.getBuiltinModules().entrySet()) {
      editor.addKeyword(module.getKey(), CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(module.getKey(), CodeEditorViewFX.AutoCompleteItemType.Type);
      for (String method : module.getValue().getMethods())
        editor.addContextualKeyword(module.getKey(), method, CodeSyntaxModel.Style.FUNCTION);
    }
  }

  @Override
  public ZIDEEditor.EditorInfo information(String token) {
    if (java.util.Arrays.asList(ZPEKit.getBuiltInStructuresNames()).contains(token))
      return new ZIDEEditor.EditorInfo(token, "Built-in ZPE structure", null, "Built-in structure", null);
    if (!ZPEKit.getAllFunctions().contains(token)) return null;
    String header = ZPEKit.getFunctionManualHeader(token);
    String entry = ZPEKit.getFunctionManualEntry(token);
    String category = ZPEKit.getFunctionCategory(token);
    return new ZIDEEditor.EditorInfo((header == null || header.isBlank() ? token + "()" : header), entry == null || entry.isBlank() ? "Built-in YASS function" : entry, "Function version " + ZPEKit.getFunctionVersion(token), category == null ? null : "Category: " + category, null);
  }

  @Override public void run(EditorTab tab) { editor.runYassCode(tab); }
}
