package jamiebalfour.zide.languages;
import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.codeeditor.CodeSyntaxModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
public final class JbmlLanguage extends StructuredTextLanguage {
  public record Issue(int line, int column, String message) { }
  public JbmlLanguage() { super("jbml", "JBML", Set.of("jbml"), "", "true", "false", "TRUE", "FALSE"); }
  @Override public void configure(CodeEditorViewFX editor) { editor.setLineCommentMarkers(""); editor.setBlockCommentMarkers("", ""); editor.setQuoteDelimiters("\"'"); editor.setVariableDelimiters(""); editor.setContextSeparator(""); editor.clearKeywords(); editor.clearContextualKeywords(); editor.clearAutoCompleteItems(); for (String value : List.of("true", "false", "TRUE", "FALSE")) { editor.addKeyword(value, CodeSyntaxModel.Style.BOOLEAN); editor.addAutoCompleteItem(value, CodeEditorViewFX.AutoCompleteItemType.Keyword); } }
  public static List<Issue> validate(String source) { List<Issue> issues = new ArrayList<>(); String[] lines = source == null ? new String[0] : source.split("\\R", -1); for (int index = 0; index < lines.length; index++) { if (lines[index].isBlank()) continue; try { new Parser(lines[index]).rule(); } catch (ParseFailure failure) { issues.add(new Issue(index + 1, failure.position + 1, failure.getMessage())); } } return issues; }
  private static final class ParseFailure extends Exception { final int position; ParseFailure(int position, String message) { super(message); this.position = Math.max(0, position); } }
  private static final class Parser {
    final String input; int position;
    Parser(String input) { this.input = input; }
    void rule() throws ParseFailure { key(); skip(); expect('=', "Expected '=' after the key."); skip(); value(); skip(); if (!end()) throw error("Unexpected text after the value."); }
    void value() throws ParseFailure { atom(); skip(); if (peek('&')) { position++; skip(); value(); } }
    void atom() throws ParseFailure { if (end()) throw error("Expected a value."); char c = input.charAt(position); if (c == '\"' || c == '\'') { string(); return; } if (c == '[') { array(); return; } if (c == '{') { map(); return; } if (Character.isDigit(c)) { number(); return; } if (Character.isLetter(c)) { word(); return; } throw error("Expected a string, number, boolean, array, map, or property."); }
    void array() throws ParseFailure { position++; skip(); if (peek(']')) { position++; return; } value(); skip(); while (peek(',')) { position++; skip(); value(); skip(); } expect(']', "Expected ']' to close the array."); }
    void map() throws ParseFailure { position++; skip(); if (peek('}')) { position++; return; } key(); skip(); expect(':', "Expected ':' between map key and value."); skip(); value(); skip(); while (peek(',')) { position++; skip(); key(); skip(); expect(':', "Expected ':' between map key and value."); skip(); value(); skip(); } expect('}', "Expected '}' to close the map."); }
    void string() throws ParseFailure { char quote = input.charAt(position++); while (!end()) if (input.charAt(position++) == quote) return; throw error("Unterminated string."); }
    void number() throws ParseFailure { while (!end() && Character.isDigit(input.charAt(position))) position++; if (peek('.')) { position++; int start = position; while (!end() && Character.isDigit(input.charAt(position))) position++; if (start == position) throw error("Expected digits after the decimal point."); } }
    void word() throws ParseFailure { int start = position; while (!end() && Character.isLetter(input.charAt(position))) position++; String word = input.substring(start, position); if (!(word.equals("true") || word.equals("false") || word.equals("TRUE") || word.equals("FALSE") || word.matches("[A-Za-z0-9]+"))) throw error("Invalid property."); }
    void key() throws ParseFailure { int start = position; while (!end() && Character.isLetterOrDigit(input.charAt(position))) position++; if (start == position) throw error("Expected a key."); }
    void skip() { while (!end() && Character.isWhitespace(input.charAt(position))) position++; }
    void expect(char c, String message) throws ParseFailure { if (!peek(c)) throw error(message); position++; }
    boolean peek(char c) { return !end() && input.charAt(position) == c; } boolean end() { return position >= input.length(); } ParseFailure error(String message) { return new ParseFailure(position, message); }
  }
}
