package jamiebalfour.zide.languages;

import jamiebalfour.codeeditor.CodeEditorViewFX;
import jamiebalfour.zide.editor.EditorTab;
import jamiebalfour.zide.editor.ZIDEEditor;

import java.util.Set;

public final class LuaLanguage extends LanguageSupport {
  public LuaLanguage(ZIDEEditor editor) { super(editor, "lua", "Lua", Set.of("lua")); }
  @Override public void configure(CodeEditorViewFX editor) { host.configureLua(editor); }
  @Override public void run(EditorTab tab) { runExternalScript(tab, "lua", "Lua", ".lua"); }
  @Override public boolean canRun() { return host.hasInterpreter(this); }
  @Override public java.util.List<String> runtimeExecutables() { return java.util.List.of("lua", "lua5.4", "lua5.3", "lua5.2", "lua5.1", "luajit", "lua.exe", "lua54.exe", "lua53.exe", "luajit.exe"); }
}
