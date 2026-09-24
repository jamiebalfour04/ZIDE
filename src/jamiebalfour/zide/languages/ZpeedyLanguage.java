package jamiebalfour.zide.languages;

import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zpe.core.ZPEKit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Zpeedy Script language definition supplied to ZIDE's language registry. */
public final class ZpeedyLanguage extends LanguageSupport {
  public ZpeedyLanguage(ZIDEEditor editor) {
    super(editor, "zpeedy", "Zpeedy Script", Set.of("zps"));
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
  @Override public void run(jamiebalfour.zide.editor.EditorTab tab) {
    if (tab == null) return;
    Path temporary = null;
    try {
      temporary = Files.createTempFile("zide-zpeedy-", ".zps");
      Files.writeString(temporary, tab.getEditor().getText(), StandardCharsets.UTF_8);
      temporary.toFile().deleteOnExit();
      List<String> command = host.languageInterpreterCommand("zpeedy");
      if (command == null) throw new FileNotFoundException("Zpeedy runtime was not found");
      host.languageRememberRuntimePath("RUNTIME_ZPEEDY_PATH", Path.of(command.getFirst()));
      command.add("-r");
      command.add(temporary.toString());
      ProcessBuilder process = new ProcessBuilder(command);
      Path workingDirectory = host.languageResourceDirectory(tab);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) process.directory(workingDirectory.toFile());
      Path executionSource = temporary;
      host.runLanguageProcess("zpeedy > ", "Zpeedy Script", process, () -> {
        try { Files.deleteIfExists(executionSource); } catch (IOException ignored) { }
      });
    } catch (IOException exception) {
      if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
      host.reportLanguageFailure("Zpeedy Script", exception.getMessage());
    }
  }
  @Override public boolean canRun() { return host.hasInterpreter("zpeedy"); }
}
