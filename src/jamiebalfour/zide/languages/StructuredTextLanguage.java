package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;

import java.util.Set;

/** Shared editor rules for data/configuration formats without a runtime. */
abstract class StructuredTextLanguage extends LanguageSupport {
  private final String lineComment;
  private final String[] keywords;

  StructuredTextLanguage(String id, String label, Set<String> extensions, String lineComment, String... keywords) {
    super(id, label, extensions);
    this.lineComment = lineComment;
    this.keywords = keywords;
  }

  @Override public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers(lineComment);
    editor.setBlockCommentMarkers("", "");
    editor.setQuoteDelimiters("\"'");
    editor.setVariableDelimiters("");
    editor.setContextSeparator("");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();
    for (String keyword : keywords) {
      editor.addKeyword(keyword, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(keyword, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
    editor.addKeyword("true", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("false", CodeSyntaxModel.Style.BOOLEAN);
    editor.addKeyword("null", CodeSyntaxModel.Style.NULL);
  }
}
