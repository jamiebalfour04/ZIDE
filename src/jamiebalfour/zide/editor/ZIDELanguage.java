package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorViewFX;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Describes one language to every part of ZIDE that needs to reason about it.
 * Implementations supply editing rules, file identity and capabilities; ZIDE
 * supplies the common tabs, menus, diagnostics and process console.
 */
public interface ZIDELanguage {
  String id();
  String label();
  Set<String> extensions();
  String defaultExtension();
  String iconStyleClass();
  Pattern variablePattern();
  void configure(CodeEditorViewFX editor);
  /** Line-comment prefixes supported by this language, in preferred order. */
  default List<String> lineCommentMarkers() {
    return switch (id()) {
      case "yass" -> List.of("//", "\\");
      case "php" -> List.of("//", "#");
      case "python", "zpeedy", "toml", "yaml" -> List.of("#");
      case "ini" -> List.of(";");
      case "lua" -> List.of("--");
      case "html", "xml", "ywp", "css", "jbml", "csv" -> List.of();
      default -> List.of("//");
    };
  }
  /** Opening and closing delimiters for a block comment, or empty strings when unsupported. */
  default List<String> blockCommentMarkers() {
    return switch (id()) {
      case "html", "xml", "ywp" -> List.of("<!--", "-->");
      case "jsx" -> List.of("{/*", "*/}");
      case "css" -> List.of("/*", "*/");
      case "lua" -> List.of("--[[", "]]" );
      case "python", "zpeedy", "ini", "toml", "yaml", "jbml", "csv" -> List.of();
      default -> List.of("/*", "*/");
    };
  }
  /** Opening delimiter used by selection commenting. A line-comment language uses this on every line. */
  default String commentOpeningTag() {
    List<String> lineMarkers = lineCommentMarkers();
    if (lineMarkers != null) {
      for (String marker : lineMarkers) if (marker != null && !marker.isEmpty()) return marker;
    }
    List<String> blockMarkers = blockCommentMarkers();
    return blockMarkers.size() >= 2 ? blockMarkers.get(0) : "";
  }
  /** Closing delimiter used by selection commenting, or an empty string for line comments. */
  default String commentClosingTag() {
    if (lineCommentMarkers().stream().anyMatch(marker -> marker != null && !marker.isEmpty())) return "";
    List<String> blockMarkers = blockCommentMarkers();
    return blockMarkers.size() >= 2 ? blockMarkers.get(1) : "";
  }
  ZIDEEditor.EditorInfo information(String token);
  void run(EditorTab tab);
  default boolean isYass() { return "yass".equals(id()); }
  boolean canRun();
  boolean canCompile();
  boolean canDebug();
  boolean canCompileNative();
  boolean canTranspile();
}
