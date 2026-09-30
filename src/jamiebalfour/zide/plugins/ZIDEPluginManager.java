package jamiebalfour.zide.plugins;

import jamiebalfour.balflaf_fx.BalfGlassMenuBarFX;
import javafx.scene.Node;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
  private final List<NativeMenu> nativeMenus = new ArrayList<>();
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
    File[] nativeFiles = directory.listFiles(file -> file.isFile() && file.canExecute() && !file.getName().endsWith(".jar"));
    if (nativeFiles != null) for (File file : nativeFiles) loadNativeManifest(file);
    return List.copyOf(plugins);
  }

  private void loadNativeManifest(File executable) {
    try {
      Process process = new ProcessBuilder(executable.getAbsolutePath(), "--manifest")
          .redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return;
      }
      String target = null;
      String title = null;
      List<String> languages = List.of();
      List<NativeItem> items = new ArrayList<>();
      for (String line : output.split("\\R")) {
        String[] fields = line.split("\\t", -1);
        if (fields.length == 0) continue;
        if ("ZIDE_MENU".equals(fields[0]) && fields.length >= 4) {
          target = fields[1];
          title = fields[2];
          languages = fields[3].isBlank() ? List.of() : List.of(fields[3].split(","));
        } else if ("ZIDE_ITEM".equals(fields[0]) && fields.length >= 4) {
          items.add(new NativeItem(fields[1], fields[2], new String(Base64.getDecoder().decode(fields[3]), StandardCharsets.UTF_8)));
        }
      }
      if (target != null && title != null && !items.isEmpty()) nativeMenus.add(new NativeMenu(target, title, languages, items));
    } catch (Exception exception) {
      LOGGER.log(Level.WARNING, "Could not read native plugin " + executable.getName(), exception);
    }
  }

  public void installMenus(Map<String, BalfGlassMenuBarFX.GlassMenu> targetMenus) {
    for (ZIDEPlugin plugin : plugins) {
      for (ZIDEPluginMenu contribution : plugin.descriptor().menus()) {
        BalfGlassMenuBarFX.GlassMenu target = targetMenus.get(contribution.targetMenu());
        if (target == null || contribution.items().isEmpty()) continue;
        BalfGlassMenuBarFX.GlassMenu.GlassSubmenu submenu = target.submenu(contribution.title());
        nodes.add(new PluginNode(submenu.getNode(), contribution.languageIds()));
        for (ZIDEPluginMenuItem item : contribution.items()) addItem(submenu, item, contribution.languageIds());
      }
    }
    for (NativeMenu contribution : nativeMenus) {
      BalfGlassMenuBarFX.GlassMenu target = targetMenus.get(contribution.targetMenu());
      if (target == null) continue;
      BalfGlassMenuBarFX.GlassMenu.GlassSubmenu submenu = target.submenu(contribution.title());
      nodes.add(new PluginNode(submenu.getNode(), contribution.languageIds()));
      Map<String, BalfGlassMenuBarFX.GlassMenu.GlassSubmenu> groups = new java.util.HashMap<>();
      for (NativeItem item : contribution.items()) {
        BalfGlassMenuBarFX.GlassMenu.GlassSubmenu parent = groups.computeIfAbsent(item.group(), submenu::submenu);
        Node node = parent.createItem(item.name(), "", () -> {
          if (context != null) context.insertText(item.text());
        });
        nodes.add(new PluginNode(node, contribution.languageIds()));
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

  private void addItem(BalfGlassMenuBarFX.GlassMenu.GlassSubmenu parent, ZIDEPluginMenuItem item, List<String> languageIds) {
    if (item.isSubmenu()) {
      BalfGlassMenuBarFX.GlassMenu.GlassSubmenu nested = parent.submenu(item.name());
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
  private record NativeMenu(String targetMenu, String title, List<String> languageIds, List<NativeItem> items) { }
  private record NativeItem(String group, String name, String text) { }
}
