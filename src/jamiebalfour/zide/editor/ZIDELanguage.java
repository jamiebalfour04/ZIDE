package jamiebalfour.zide.editor;

import jamiebalfour.codeeditor.CodeEditorViewFX;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Describes one language to every part of ZIDE that needs to reason about it.
 * Implementations supply editing rules, file identity and capabilities; ZIDE
 * supplies the common tabs, menus, diagnostics and process console.
 */
interface ZIDELanguage {
  String id();
  String label();
  Set<String> extensions();
  String defaultExtension();
  String iconStyleClass();
  Pattern variablePattern();
  void configure(CodeEditorViewFX editor);
  ZIDEEditor.EditorInfo information(String token);
  void run(EditorTab tab);
  boolean canRun();
  boolean canCompile();
  boolean canDebug();
  boolean canCompileNative();
  boolean canTranspile();
}
