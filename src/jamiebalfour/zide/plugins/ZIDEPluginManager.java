package jamiebalfour.zide.plugins;

import jamiebalfour.balflaf_fx.BalfGlassMenuBar;
import javafx.scene.Node;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Discovers Java plugin providers from ZIDE's plugins directory. */
public final class ZIDEPluginManager implements AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(ZIDEPluginManager.class.getName());
  private final List<URLClassLoader> loaders = new ArrayList<>();
  private final List<ZIDEPlugin> plugins = new ArrayList<>();
  private final List<PluginNode> nodes = new ArrayList<>();
  private ZIDEPluginContext context;

  public List<ZIDEPlugin> load(File directory, ZIDEPluginContext context) {
    this.context = context;
    if (directory == null || !directory.isDirectory()) return List.of();
    File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
    if (files == null) return List.of();
    for (File file : files) {
      try {
        URLClassLoader loader = new URLClassLoader(new URL[]{file.toURI().toURL()}, getClass().getClassLoader());
        loaders.add(loader);
        ServiceLoader.load(ZIDEPlugin.class, loader).forEach(plugin -> {
          try { plugin.initialize(context); plugins.add(plugin); }
          catch (RuntimeException exception) { LOGGER.log(Level.WARNING, "Could not initialise plugin " + file.getName(), exception); }
        });
      } catch (Exception exception) {
        LOGGER.log(Level.WARNING, "Could not load plugin " + file.getName(), exception);
      }
    }
    return List.copyOf(plugins);
  }

  public void installMenus(Map<String, BalfGlassMenuBar.GlassMenu> targetMenus) {
    for (ZIDEPlugin plugin : plugins) {
      for (ZIDEPluginMenu contribution : plugin.descriptor().menus()) {
        BalfGlassMenuBar.GlassMenu target = targetMenus.get(contribution.targetMenu());
        if (target == null || contribution.items().isEmpty()) continue;
        BalfGlassMenuBar.GlassMenu.GlassSubmenu submenu = target.submenu(contribution.title());
        nodes.add(new PluginNode(submenu.getNode(), contribution.languageIds()));
        for (ZIDEPluginMenuItem item : contribution.items()) addItem(submenu, item, contribution.languageIds());
      }
    }
  }

  public void updateLanguage(String languageId) {
    for (PluginNode node : nodes) {
      boolean visible = node.languageIds.isEmpty() || (languageId != null && node.languageIds.contains(languageId));
      node.node.setVisible(visible);
      node.node.setManaged(visible);
    }
  }

  private void addItem(BalfGlassMenuBar.GlassMenu.GlassSubmenu parent, ZIDEPluginMenuItem item, List<String> languageIds) {
    if (item.isSubmenu()) {
      BalfGlassMenuBar.GlassMenu.GlassSubmenu nested = parent.submenu(item.name());
      nodes.add(new PluginNode(nested.getNode(), languageIds));
      for (ZIDEPluginMenuItem child : item.children()) addItem(nested, child, languageIds);
    } else {
      Node node = parent.createItem(item.name(), "", () -> run(item));
      nodes.add(new PluginNode(node, languageIds));
    }
  }

  private void run(ZIDEPluginMenuItem item) {
    String languageId = context == null ? null : context.activeLanguageId();
    Runnable action = languageId == null ? null : item.languageActions().get(languageId);
    if (action == null && languageId != null) action = item.languageActions().get(languageId.toLowerCase());
    if (action == null) action = item.action();
    if (action != null) action.run();
  }

  @Override public void close() {
    loaders.forEach(loader -> { try { loader.close(); } catch (Exception ignored) { } });
    loaders.clear();
  }

  private record PluginNode(Node node, List<String> languageIds) { }
}
