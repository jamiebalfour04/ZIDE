package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.zide.editor.ZIDELanguage;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Callback-backed language implementation used by the built-in languages.
 * Keeping this outside ZIDEEditor means new languages can be supplied as
 * ordinary ZIDELanguage implementations without editing the editor class.
 */
public class LanguageSupport implements ZIDELanguage {
  final String id;
  final String label;
  final Set<String> extensions;
  final Consumer<CodeEditorViewFX> configure;
  final Function<String, ZIDEEditor.EditorInfo> information;
  final Consumer<EditorTab> runner;

  protected LanguageSupport(String id, String label, Set<String> extensions,
                  Consumer<CodeEditorViewFX> configure,
                  Function<String, ZIDEEditor.EditorInfo> information,
                  Consumer<EditorTab> runner) {
    this.id = id;
    this.label = label;
    this.extensions = Set.copyOf(extensions);
    this.configure = configure;
    this.information = information;
    this.runner = runner;
  }

  protected LanguageSupport(String id, String label, Set<String> extensions) {
    this(id, label, extensions, null, null, null);
  }

  @Override public String id() { return id; }
  @Override public String label() { return label; }
  @Override public Set<String> extensions() { return extensions; }
  @Override public String toString() { return label; }

  @Override public String defaultExtension() {
    return switch (id) {
      case "yass" -> "yas";
      case "zpeedy" -> "zps";
      case "python" -> "py";
      case "js" -> "js";
      case "typescript" -> "ts";
      case "jsx" -> "jsx";
      case "json" -> "json";
      case "xml" -> "xml";
      case "html" -> "html";
      case "css" -> "css";
      case "csv" -> "csv";
      case "sqarl" -> "sqarl";
      case "ywp" -> "ywp";
      case "md" -> "md";
      default -> extensions.iterator().next();
    };
  }

  @Override public String iconStyleClass() { return "language-icon-" + id; }

  @Override public Pattern variablePattern() {
    if ("yass".equals(id)) return Pattern.compile("\\$?[A-Za-z_][A-Za-z0-9_]*");
    if (Set.of("zpeedy", "python", "sqarl").contains(id)) {
      return Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    }
    return Pattern.compile("(?!)");
  }

  @Override public void configure(CodeEditorViewFX editor) { if (configure != null) configure.accept(editor); }
  @Override public ZIDEEditor.EditorInfo information(String token) {
    return information == null ? null : information.apply(token);
  }
  @Override public void run(EditorTab tab) { if (runner != null) runner.accept(tab); }

  @Override public boolean isYass() { return "yass".equals(id); }
  @Override public boolean canRun() {
    return isYass() || (runner != null
            && (!Set.of("python", "php", "lua").contains(id)
            || ZIDEEditor.interpreterCommand(id) != null));
  }
  @Override public boolean canCompile() { return isYass() || Set.of("zpeedy", "sqarl").contains(id); }
  @Override public boolean canDebug() {
    return isYass() || "html".equals(id)
            || ("python".equals(id) && ZIDEEditor.interpreterCommand(id) != null);
  }
  @Override public boolean canCompileNative() { return isYass(); }
  @Override public boolean canTranspile() { return isYass() || "zpeedy".equals(id); }
}
