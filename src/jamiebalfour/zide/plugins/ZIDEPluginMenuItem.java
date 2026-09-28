package jamiebalfour.zide.plugins;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A menu item or nested submenu supplied by a plugin. */
public record ZIDEPluginMenuItem(String name, Runnable action, List<ZIDEPluginMenuItem> children,
                                 Map<String, Runnable> languageActions) {
  public ZIDEPluginMenuItem(String name, Runnable action, List<ZIDEPluginMenuItem> children) {
    this(name, action, children, Map.of());
  }

  public ZIDEPluginMenuItem {
    name = Objects.requireNonNull(name, "name");
    children = children == null ? List.of() : List.copyOf(children);
    languageActions = languageActions == null ? Map.of() : Map.copyOf(languageActions);
  }

  public boolean isSubmenu() { return !children.isEmpty(); }
}
