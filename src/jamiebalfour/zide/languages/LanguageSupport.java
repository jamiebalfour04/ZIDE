package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;
import jamiebalfour.zide.editor.ZIDELanguage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
  protected ZIDEEditor host;

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
    this.host = null;
  }

  protected LanguageSupport(String id, String label, Set<String> extensions) {
    this(id, label, extensions, null, null, null);
  }

  protected LanguageSupport(ZIDEEditor host, String id, String label, Set<String> extensions) {
    this(id, label, extensions);
    this.host = host;
  }

  @Override public String id() { return id; }
  @Override public String label() { return label; }
  @Override public Set<String> extensions() { return extensions; }
  @Override public String toString() { return label; }

  @Override public String defaultExtension() {
    return extensions.iterator().next();
  }

  @Override public String iconStyleClass() { return "language-icon-" + id; }

  @Override public Pattern variablePattern() {
    return Pattern.compile("(?!)");
  }

  @Override public void configure(CodeEditorViewFX editor) { if (configure != null) configure.accept(editor); }
  @Override public ZIDEEditor.EditorInfo information(String token) {
    return information == null ? null : information.apply(token);
  }
  @Override public void run(EditorTab tab) { if (runner != null) runner.accept(tab); }

  /** Runs a source file through an interpreter owned by this language. */
  protected final void runExternalScript(EditorTab tab, String runtime, String displayName, String suffix) {
    runExternalScript(tab, runtime, displayName, suffix, List.of());
  }

  protected final void runExternalScript(EditorTab tab, String runtime, String displayName, String suffix, List<String> prefixArguments) {
    if (tab == null || host == null) return;
    Path source = null;
    try {
      Path workingDirectory = host.languageResourceDirectory(tab);
      source = workingDirectory != null && Files.isDirectory(workingDirectory)
          ? Files.createTempFile(workingDirectory, ".zide-", suffix)
          : Files.createTempFile("zide-" + runtime + "-", suffix);
      Files.writeString(source, tab.getEditor().getText(), StandardCharsets.UTF_8);
      source.toFile().deleteOnExit();
      List<String> command = host.languageRuntimeCommand(this);
      if (command == null) throw new FileNotFoundException(displayName + " runtime was not found");
      host.languageRememberRuntimePath("RUNTIME_" + runtime.toUpperCase(Locale.ROOT) + "_PATH", Path.of(command.getFirst()));
      if (prefixArguments != null) command.addAll(prefixArguments);
      command.add(source.toString());
      ProcessBuilder process = new ProcessBuilder(command);
      host.configureLanguageProcess(runtime, process);
      if (workingDirectory != null && Files.isDirectory(workingDirectory)) process.directory(workingDirectory.toFile());
      Path executionSource = source;
      host.runLanguageProcess(runtime + " > ", displayName, process, () -> {
        try { Files.deleteIfExists(executionSource); } catch (IOException ignored) { }
      });
    } catch (IOException exception) {
      if (source != null) try { Files.deleteIfExists(source); } catch (IOException ignored) { }
      host.reportLanguageFailure(displayName, exception.getMessage());
    }
  }

  @Override public boolean canRun() { return runner != null && (!usesExternalRuntime() || host == null || host.hasInterpreter(this)); }
  @Override public boolean canDebug() { return false; }
  @Override public boolean canCompileNative() { return false; }
  @Override public boolean canTranspile() { return false; }
}
