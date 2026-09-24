package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

public final class LuaLanguage extends LanguageSupport {
  public LuaLanguage(ZIDEEditor editor) { super(editor, "lua", "Lua", Set.of("lua")); }
  @Override public void configure(CodeEditorViewFX editor) { host.configureLua(editor); }
  @Override public void run(EditorTab tab) { runExternalScript(tab, "lua", "Lua", ".lua"); }
  @Override public boolean canRun() { return ZIDEEditor.interpreterCommand("lua") != null; }
}
