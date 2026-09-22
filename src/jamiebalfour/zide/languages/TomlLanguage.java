package jamiebalfour.zide.languages;

import java.util.Set;

public final class TomlLanguage extends StructuredTextLanguage {
  public TomlLanguage() {
    super("toml", "TOML", Set.of("toml"), "#", "true", "false");
  }

  public static java.util.List<Issue> validate(String source) {
    java.util.List<Issue> issues = new java.util.ArrayList<>();
    java.util.Set<String> keys = new java.util.HashSet<>();
    String[] lines = source == null ? new String[0] : source.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].replaceFirst("#.*$", "").trim();
      if (line.isEmpty()) continue;
      if (line.matches("\\[\\[?[^]]+\\]?\\]")) continue;
      int equals = line.indexOf('=');
      if (equals <= 0) {
        issues.add(new Issue(i + 1, 1, "TOML entries must use key = value or a table header."));
        continue;
      }
      String key = line.substring(0, equals).trim(), value = line.substring(equals + 1).trim();
      if (!key.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")) issues.add(new Issue(i + 1, 1, "Invalid TOML key."));
      if (!keys.add(key)) issues.add(new Issue(i + 1, 1, "Duplicate TOML key: " + key));
      if (value.isEmpty()) issues.add(new Issue(i + 1, equals + 2, "Expected a TOML value."));
      else if (!(value.matches("[+-]?\\d+(?:\\.\\d+)?") || value.matches("(?i:true|false)") || value.matches("\"(?:[^\"\\\\]|\\\\.)*\"") || value.matches("'(?:[^']*)'") || value.matches("\\[.*\\]") || value.matches("\\{.*\\}") || value.matches("\\d{4}-\\d{2}-\\d{2}.*")))
        issues.add(new Issue(i + 1, equals + 2, "Invalid TOML value."));
    }
    return issues;
  }

  public record Issue(int line, int column, String message) {
  }
}
