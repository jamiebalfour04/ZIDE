package jamiebalfour.zide.plugins;

import java.util.List;
import java.util.Objects;

/** A group of plugin items contributed to one of ZIDE's top-level menus. */
public record ZIDEPluginMenu(String targetMenu, String title, List<String> languageIds, List<ZIDEPluginMenuItem> items) {
  public ZIDEPluginMenu {
    targetMenu = Objects.requireNonNull(targetMenu, "targetMenu");
    title = Objects.requireNonNull(title, "title");
    languageIds = languageIds == null ? List.of() : List.copyOf(languageIds);
    items = items == null ? List.of() : List.copyOf(items);
  }
}
