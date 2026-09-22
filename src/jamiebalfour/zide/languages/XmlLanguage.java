package jamiebalfour.zide.languages;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import java.util.Set;
public final class XmlLanguage extends LanguageSupport { public XmlLanguage(jamiebalfour.zide.editor.ZIDEEditor ignored) { super("xml", "XML", Set.of("xml", "xhtml")); } @Override public void configure(CodeEditorViewFX editor) { editor.setLineCommentMarkers(""); editor.setBlockCommentMarkers("<!--", "-->"); editor.setQuoteDelimiters("\""); editor.setVariableDelimiters(""); editor.setContextSeparator(":"); editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems(); for (String attribute : java.util.List.of("id", "class", "style", "href", "src", "alt", "width", "height", "name", "value", "type", "rel", "charset", "lang", "data", "aria")) { editor.addKeyword(attribute, CodeSyntaxModel.Style.VARIABLE); editor.addAutoCompleteItem(attribute, CodeEditorViewFX.AutoCompleteItemType.Variable); } } }
