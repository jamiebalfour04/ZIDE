package jamiebalfour.zide.languages;
import java.util.Set;
public final class YamlLanguage extends StructuredTextLanguage {
  public YamlLanguage() { super("yaml", "YAML", Set.of("yaml", "yml"), "#", "true", "false", "null", "yes", "no", "on", "off"); }
}
