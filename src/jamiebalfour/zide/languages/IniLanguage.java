package jamiebalfour.zide.languages;
import java.util.Set;
public final class IniLanguage extends StructuredTextLanguage {
  public IniLanguage() { super("ini", "INI", Set.of("ini"), ";", "section", "true", "false"); }
}
