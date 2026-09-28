package jamiebalfour.zide.plugins;

import java.util.List;
import java.util.Objects;

/** A menu item or nested submenu supplied by a plugin. */
public record ZIDEPluginMenuItem(String name, Runnable action, List<ZIDEPluginMenuItem> children) {
  public ZIDEPluginMenuItem {
    name = Objects.requireNonNull(name, "name");
    children = children == null ? List.of() : List.copyOf(children);
  }

  public boolean isSubmenu() { return !children.isEmpty(); }
}
