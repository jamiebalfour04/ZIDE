package jamiebalfour.zide.languages;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import java.util.Set;
public final class PlainTextLanguage extends LanguageSupport { public PlainTextLanguage(jamiebalfour.zide.editor.ZIDEEditor ignored, String id, String label, Set<String> extensions) { super(id, label, extensions); } @Override public void configure(CodeEditorViewFX editor) { editor.setLineCommentMarkers("//"); editor.setBlockCommentMarkers("/*", "*/"); editor.setQuoteDelimiters("\"'"); editor.setVariableDelimiters("$"); editor.setContextSeparator(""); editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems(); } }
