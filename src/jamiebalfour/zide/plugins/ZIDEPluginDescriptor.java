package jamiebalfour.zide.plugins;

import java.util.List;
import java.util.Objects;

/** Immutable plugin metadata and menu contributions. */
public record ZIDEPluginDescriptor(String id, String name, String version, List<ZIDEPluginMenu> menus) {
  public ZIDEPluginDescriptor {
    id = Objects.requireNonNull(id, "id");
    name = Objects.requireNonNull(name, "name");
    version = version == null ? "" : version;
    menus = menus == null ? List.of() : List.copyOf(menus);
  }
}
