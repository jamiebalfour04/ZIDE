package jamiebalfour.zide.plugins;

/** Entry point implemented by Java plugins before they are compiled to a native image. */
public interface ZIDEPlugin {
  ZIDEPluginDescriptor descriptor();

  default void initialize(ZIDEPluginContext context) { }
}
