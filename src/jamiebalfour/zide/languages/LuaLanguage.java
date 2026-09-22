package jamiebalfour.zide.languages;
import jamiebalfour.zide.editor.ZIDEEditor;
import java.util.Set;
public final class LuaLanguage extends LanguageSupport { private final ZIDEEditor host; public LuaLanguage(ZIDEEditor e) { super("lua", "Lua", Set.of("lua")); host = e; } @Override public void configure(jamiebalfour.codeeditor.CodeEditorViewFX editor) { host.configureLua(editor); } @Override public void run(jamiebalfour.zide.editor.EditorTab tab) { host.runLuaCode(tab); } @Override public boolean canRun() { return ZIDEEditor.interpreterCommand("lua") != null; } }
