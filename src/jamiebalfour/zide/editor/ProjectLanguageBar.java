package jamiebalfour.zide.editor;

import javafx.scene.control.Tooltip;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** A proportional character-count bar for the languages used by a project. */
public final class ProjectLanguageBar extends HBox {
  public record LanguageUsage(String language, String colour, long characters) { }
  private record Language(String name, String colour) { }

  private static final Map<String, Language> LANGUAGES = Map.ofEntries(
      Map.entry("yas", new Language("YASS", "#a900b5")), Map.entry("ywp", new Language("YWP", "#087f91")),
      Map.entry("zps", new Language("Zpeedy", "#7b4ca8")), Map.entry("js", new Language("JavaScript", "#f0c500")),
      Map.entry("mjs", new Language("JavaScript", "#f0c500")), Map.entry("cjs", new Language("JavaScript", "#f0c500")),
      Map.entry("ts", new Language("TypeScript", "#3178c6")), Map.entry("tsx", new Language("TSX", "#61dafb")),
      Map.entry("jsx", new Language("JSX", "#61dafb")), Map.entry("py", new Language("Python", "#3776ab")),
      Map.entry("java", new Language("Java", "#f89820")), Map.entry("c", new Language("C", "#283593")),
      Map.entry("h", new Language("C", "#283593")), Map.entry("cpp", new Language("C++", "#00599c")),
      Map.entry("cc", new Language("C++", "#00599c")), Map.entry("cxx", new Language("C++", "#00599c")),
      Map.entry("php", new Language("PHP", "#777bb4")), Map.entry("lua", new Language("Lua", "#00007d")),
      Map.entry("html", new Language("HTML", "#e44d26")), Map.entry("htm", new Language("HTML", "#e44d26")),
      Map.entry("css", new Language("CSS", "#563d7c")), Map.entry("json", new Language("JSON", "#b8a000")),
      Map.entry("xml", new Language("XML", "#6a9955")), Map.entry("csv", new Language("CSV", "#237346")),
      Map.entry("md", new Language("Markdown", "#6b7280")), Map.entry("markdown", new Language("Markdown", "#6b7280")),
      Map.entry("pad", new Language("PAD", "#a27b2c"))
  );

  public ProjectLanguageBar(Path project) {
    getStyleClass().add("project-language-bar");
    setMaxWidth(Double.MAX_VALUE);
    List<LanguageUsage> usage = characterUsage(project);
    long total = usage.stream().mapToLong(LanguageUsage::characters).sum();
    for (LanguageUsage entry : usage) {
      double fraction = (double) entry.characters() / total;
      Region segment = new Region();
      segment.getStyleClass().add("project-language-segment");
      segment.setCursor(Cursor.HAND);
      segment.setStyle("-fx-background-color: " + entry.colour() + ";");
      segment.prefWidthProperty().bind(widthProperty().multiply(fraction));
      Tooltip tooltip = new Tooltip(entry.language() + " · " + Math.round(fraction * 1000.0) / 10.0 + "% · " + entry.characters() + " characters");
      tooltip.setShowDelay(Duration.ZERO);
      tooltip.setHideDelay(Duration.ZERO);
      Tooltip.install(segment, tooltip);
      getChildren().add(segment);
    }
  }

  /** Returns character totals in display order for persistence in .zide.project. */
  public static List<LanguageUsage> characterUsage(Path project) {
    Map<Language, Long> counts = new LinkedHashMap<>();
    if (project == null || !Files.isDirectory(project)) return List.of();
    try (Stream<Path> files = Files.walk(project)) {
      files.filter(Files::isRegularFile).forEach(file -> {
        String name = file.getFileName().toString();
        // Hidden/project metadata files are not source languages.
        if (name.startsWith(".")) return;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return;
        Language language = LANGUAGES.get(name.substring(dot + 1).toLowerCase());
        if (language == null) return;
        try { counts.merge(language, (long) Files.readString(file, StandardCharsets.UTF_8).length(), Long::sum); }
        catch (IOException | RuntimeException ignored) { }
      });
    } catch (IOException ignored) { return List.of(); }
    return counts.entrySet().stream()
        .filter(entry -> entry.getValue() > 0)
        .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
        .map(entry -> new LanguageUsage(entry.getKey().name(), entry.getKey().colour(), entry.getValue()))
        .toList();
  }
}
