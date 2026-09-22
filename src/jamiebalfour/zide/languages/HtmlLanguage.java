package jamiebalfour.zide.languages;

import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;

import java.util.Set;

/** HTML language definition supplied to ZIDE's language registry. */
public final class HtmlLanguage extends LanguageSupport {
  public HtmlLanguage(ZIDEEditor editor) {
    super("html", "HTML", Set.of("html", "htm"));
    this.editor = editor;
  }

  private final ZIDEEditor editor;
  @Override public void configure(CodeEditorViewFX view) { view.setLineCommentMarkers(""); view.setBlockCommentMarkers("<!--", "-->"); view.setQuoteDelimiters("\""); view.setVariableDelimiters(""); view.setContextSeparator(":"); view.clearKeywords(); view.clearContextualKeywords(); view.clearAutoCompleteItems(); for (String attribute : java.util.List.of("id", "class", "style", "href", "src", "alt", "width", "height", "name", "value", "type", "rel", "charset", "lang", "data", "aria")) { view.addKeyword(attribute, CodeSyntaxModel.Style.VARIABLE); view.addAutoCompleteItem(attribute, CodeEditorViewFX.AutoCompleteItemType.Variable); } for (String tag : java.util.List.of("html", "head", "body", "title", "meta", "link", "style", "script", "main", "header", "footer", "section", "article", "nav", "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "form", "input", "button")) { view.addKeyword(tag, CodeSyntaxModel.Style.TYPE); view.addAutoCompleteItem(tag, CodeEditorViewFX.AutoCompleteItemType.Type); } }
  @Override public void run(jamiebalfour.zide.editor.EditorTab tab) { editor.runHtmlCode(tab); }
}
