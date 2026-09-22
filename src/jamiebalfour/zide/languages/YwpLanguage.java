package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.List;
import java.util.Set;

/** YWP language definition: HTML markup with embedded YWP expressions. */
public final class YwpLanguage extends LanguageSupport {
  public YwpLanguage(ZIDEEditor ignored) {
    super("ywp", "YWP", Set.of("ywp"));
  }

  @Override
  public void configure(CodeEditorViewFX editor) {
    editor.setLineCommentMarkers("");
    editor.setBlockCommentMarkers("<!--", "-->");
    editor.setQuoteDelimiters("\"");
    editor.setVariableDelimiters("");
    editor.setContextSeparator(":");
    editor.clearKeywords();
    editor.clearContextualKeywords();
    editor.clearAutoCompleteItems();

    for (String attribute : List.of("id", "class", "style", "href", "src", "alt", "width", "height", "name", "value", "type", "rel", "charset", "lang", "data", "aria")) {
      editor.addKeyword(attribute, CodeSyntaxModel.Style.VARIABLE);
      editor.addAutoCompleteItem(attribute, CodeEditorViewFX.AutoCompleteItemType.Variable);
    }
    for (String tag : List.of("html", "head", "body", "title", "meta", "link", "style", "script", "main", "header", "footer", "section", "article", "nav", "div", "span", "p", "a", "img", "ul", "ol", "li", "table", "form", "input", "button", "h1", "h2", "h3", "br", "hr")) {
      editor.addKeyword(tag, CodeSyntaxModel.Style.TYPE);
      editor.addAutoCompleteItem(tag, CodeEditorViewFX.AutoCompleteItemType.Type);
    }
    for (String expression : List.of("ywp", "print", "echo", "if", "else", "for", "end")) {
      editor.addKeyword(expression, CodeSyntaxModel.Style.KEYWORD);
      editor.addAutoCompleteItem(expression, CodeEditorViewFX.AutoCompleteItemType.Keyword);
    }
  }
}
