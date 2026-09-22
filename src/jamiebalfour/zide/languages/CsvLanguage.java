package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;

import java.util.Set;

public final class CsvLanguage extends LanguageSupport {
  public CsvLanguage(jamiebalfour.zide.editor.ZIDEEditor ignored) {
    super("csv", "CSV", Set.of("csv", "tsv"));
  }

  @Override
  public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("");
    editor.setBlockCommentMarkers("", "");
    editor.setQuoteDelimiters("\"");
    editor.setVariableDelimiters("");
    editor.setContextSeparator("");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
  }
}
